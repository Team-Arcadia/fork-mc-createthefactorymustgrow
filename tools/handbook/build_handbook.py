"""Build The Factory Handbook (a Patchouli book) from its chapter sources.

The chapters are written once, in both languages, in src/guide/chapters/*.json
(one file per chapter, see README.md next to this script). This script turns
them into Patchouli's layout under
src/main/resources/assets/tfmg/patchouli_books/handbook/<lang>/:

- one category per chapter,
- one entry per chapter page, holding text pages (split to fit a book page),
  a multiblock page when the page has a schematic, and a spotlight page
  cycling through the items the page mentions.

Run it after editing a chapter:  python tools/handbook/build_handbook.py

Author: vyrriox
"""
import json
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SOURCE = os.environ.get('HANDBOOK_SOURCE') or os.path.join(ROOT, 'src', 'guide', 'chapters')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'tfmg', 'patchouli_books', 'handbook')
LANGS = ['en_us', 'fr_fr']
NAMESPACE = 'tfmg'

# Patchouli text pages are 116 px wide. The first page of an entry carries
# the entry title, so it holds fewer lines. Line counts come from wrapping the
# text with the real widths of Minecraft's font (glyph_widths.json).
PAGE_WIDTH = 116
BULLET_INDENT = 10
FIRST_PAGE_LINES = 12
OTHER_PAGE_LINES = 15
GLYPHS = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'glyph_widths.json'), encoding='utf-8'))

ITEMS_TEXT = {
    'en_us': 'The items this page talks about. Hover for details; JEI shows their recipes.',
    'fr_fr': 'Les objets dont parle cette page. Survolez-les pour les détails ; JEI montre leurs recettes.',
}
VIEW_NAME = {'en_us': 'Structure', 'fr_fr': 'Structure'}


def localized(value, lang):
    if value is None:
        return ''
    if isinstance(value, str):
        return value
    text = value.get(lang, value.get('en_us', ''))
    if isinstance(text, list):
        return '\n'.join(text)
    return text


def inline(text):
    """**highlight** -> Patchouli item colour."""
    return re.sub(r'\*\*(.+?)\*\*', r'$(item)\1$()', text)


def text_width(text, bold=False):
    return sum(GLYPHS.get(c, 6) + (1 if bold else 0) for c in text)


def wrapped_lines(text, width, bold=False):
    """How many lines Patchouli needs for this text at this width."""
    plain = re.sub(r'\*\*', '', text)
    lines, current = 1, 0
    space = text_width(' ', bold)
    for word in plain.split(' '):
        w = text_width(word, bold)
        if current == 0:
            current = w
        elif current + space + w <= width:
            current += space + w
        else:
            lines += 1
            current = w
        while current > width:  # a single word wider than the page
            lines += 1
            current -= width
    return lines


def blocks_of(text):
    """Groups the source lines into blocks: headings, bullets and paragraphs."""
    blocks = []
    current = []
    for raw in text.split('\n'):
        line = raw.rstrip()
        if not line.strip():
            if current:
                blocks.append(current)
                current = []
            continue
        if line.startswith('## ') and current:
            blocks.append(current)
            current = []
        current.append(line)
    if current:
        blocks.append(current)
    return blocks


def render_line(line):
    """Patchouli markup for one source line and the number of lines it takes."""
    if line.startswith('## '):
        return '$(l)' + inline(line[3:]) + '$()', wrapped_lines(line[3:], PAGE_WIDTH, bold=True)
    if line.startswith('- '):
        return '$(li)' + inline(line[2:]), wrapped_lines(line[2:], PAGE_WIDTH - BULLET_INDENT)
    return inline(line), wrapped_lines(line, PAGE_WIDTH)


def join(pieces):
    text = ''
    for i, piece in enumerate(pieces):
        if i == 0 or piece.startswith('$(li)'):
            text += piece
        else:
            text += '$(br)' + piece
    return text


def paginate(text):
    """Splits the text over book pages by real line count."""
    pages = []
    current, used = [], 0

    def capacity():
        return FIRST_PAGE_LINES if not pages else OTHER_PAGE_LINES

    def flush():
        nonlocal current, used
        if current:
            pages.append('$(br2)'.join(current))
        current, used = [], 0

    for block in blocks_of(text):
        rendered = [render_line(l) for l in block]
        need = sum(n for _, n in rendered)
        gap = 1 if current else 0
        if used + gap + need <= capacity():
            current.append(join([t for t, _ in rendered]))
            used += gap + need
            continue
        # Does not fit: start a new page with it if it fits a whole page,
        # otherwise spill it line by line (sentence by sentence when a single
        # line is longer than a page).
        if need <= OTHER_PAGE_LINES:
            flush()
            current.append(join([t for t, _ in rendered]))
            used = need
            continue
        # A block taller than a page: spill it line by line, and a line taller
        # than a page sentence by sentence (keeping its bullet or heading).
        pieces = []
        for source in block:
            markup, lines = render_line(source)
            if lines <= OTHER_PAGE_LINES:
                pieces.append((markup, lines))
                continue
            prefix = '- ' if source.startswith('- ') else ''
            body = source[2:] if prefix else source
            for i, sentence in enumerate(split_sentences(body)):
                pieces.append(render_line((prefix if i == 0 else '') + sentence))
        part = []
        for markup, lines in pieces:
            gap = 1 if (current and not part) else 0
            if used + gap + lines > capacity():
                if part:
                    current.append(join(part))
                flush()
                part, gap = [], 0
            part.append(markup)
            used += gap + lines
        if part:
            current.append(join(part))
    flush()
    return pages or ['']


def split_sentences(text):
    parts = re.split(r'(?<=[.!?;:])\s+', text)
    return [p for p in parts if p]


def multiblock(schematic):
    layers = schematic['layers']
    key = dict(schematic['key'])
    size_x = max(len(row) for layer in layers for row in layer)
    size_z = max(len(layer) for layer in layers)
    grid = []
    for layer in layers:
        rows = [row.ljust(size_x).replace('.', ' ') for row in layer]
        rows += [' ' * size_x] * (size_z - len(rows))
        grid.append(rows)
    # Patchouli reserves '0' (centre) and '_' (any block): move them away.
    spare = iter('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz123456789')
    for reserved in ('0', '_'):
        if reserved in key:
            new = next(c for c in spare if c not in key)
            key[new] = key.pop(reserved)
            grid = [[row.replace(reserved, new) for row in layer] for layer in grid]
    # Exactly one '0': the bottom layer centre.
    cx, cz = size_x // 2, size_z // 2
    centre_char = grid[0][cz][cx]
    mapping = {c: s for c, s in key.items()}
    mapping['0'] = key[centre_char] if centre_char != ' ' else 'minecraft:air'
    row = grid[0][cz]
    grid[0][cz] = row[:cx] + '0' + row[cx + 1:]
    # Patchouli lists layers top first.
    return {'pattern': list(reversed(grid)), 'mapping': mapping}


def slug(text, index):
    s = re.sub(r'[^a-z0-9]+', '_', text.lower()).strip('_')
    return '%02d_%s' % (index, s[:40] or 'page')


def build():
    if not os.path.isdir(SOURCE):
        sys.exit('no chapter sources in ' + SOURCE)
    if os.path.isdir(OUT):
        for lang in LANGS:
            shutil.rmtree(os.path.join(OUT, lang), ignore_errors=True)
    chapters = []
    for name in sorted(os.listdir(SOURCE)):
        if name.endswith('.json'):
            with open(os.path.join(SOURCE, name), encoding='utf-8') as f:
                chapters.append((name[:-5], json.load(f)))
    stats = {}
    for lang in LANGS:
        cat_dir = os.path.join(OUT, lang, 'categories')
        os.makedirs(cat_dir, exist_ok=True)
        entries = 0
        for chapter_id, chapter in chapters:
            cid = re.sub(r'^\d+_', '', chapter_id)
            title = localized(chapter.get('title'), lang)
            first_text = localized(chapter['pages'][0].get('text'), lang)
            description = re.sub(r'\*\*(.+?)\*\*', r'\1', next((l for l in first_text.split('\n') if l.strip() and not l.startswith('## ')), title))
            category = {
                'name': title,
                'description': description,
                'icon': chapter.get('icon', 'minecraft:book'),
                'sortnum': chapter.get('order', 1000),
            }
            write(os.path.join(cat_dir, cid + '.json'), category)
            entry_dir = os.path.join(OUT, lang, 'entries', cid)
            os.makedirs(entry_dir, exist_ok=True)
            for index, page in enumerate(chapter['pages']):
                page_title = localized(page.get('title'), lang) or title
                patch_pages = [{'type': 'patchouli:text', 'text': t} for t in paginate(localized(page.get('text'), lang))]
                if page.get('schematic'):
                    patch_pages.append({
                        'type': 'patchouli:multiblock',
                        'name': page_title,
                        'multiblock': multiblock(page['schematic']),
                        'enable_visualize': True,
                    })
                items = page.get('items') or []
                if items:
                    patch_pages.append({
                        'type': 'patchouli:spotlight',
                        'item': ','.join(items),
                        'link_recipe': False,
                        'text': ITEMS_TEXT[lang],
                    })
                entry = {
                    'name': page_title,
                    'icon': items[0] if items else chapter.get('icon', 'minecraft:book'),
                    'category': '%s:%s' % (NAMESPACE, cid),
                    'sortnum': index,
                    'pages': patch_pages,
                }
                write(os.path.join(entry_dir, slug(localized(page.get('title'), 'en_us') or cid, index) + '.json'), entry)
                entries += 1
        stats[lang] = entries
    print('chapters: %d, entries per language: %s' % (len(chapters), stats))
    build_blueprints(chapters)


BLUEPRINTS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'tfmg', 'blueprints')

# Which Factory Blueprint (assembly line) shows the structures of a chapter.
# Must match BlueprintLines.LINES in the mod.
LINE_OF_CHAPTER = {
    'coke_oven': 'steel', 'hot_air': 'steel', 'blast_furnace': 'steel', 'casting_and_steel': 'steel',
    'chemical_vats': 'chemistry', 'aluminium': 'chemistry', 'arc_furnace': 'chemistry',
    'fireboxes_and_gases': 'chemistry', 'winding_and_magnets': 'chemistry',
    'finding_oil': 'oil', 'pumpjack': 'oil', 'distillation': 'oil',
    'power': 'power', 'electricity_basics': 'power', 'network_control': 'power',
    'electric_machines': 'power', 'engines': 'power', 'engine_upgrades': 'power',
}


def build_blueprints(chapters):
    """One blueprint per schematic, for the Factory Blueprint's in-world
    layer-by-layer projection. Layers stay bottom first."""
    shutil.rmtree(BLUEPRINTS, ignore_errors=True)
    os.makedirs(BLUEPRINTS, exist_ok=True)
    count = 0
    for chapter_id, chapter in chapters:
        order = chapter.get('order', 1000)
        for index, page in enumerate(chapter['pages']):
            schematic = page.get('schematic')
            if not schematic:
                continue
            cid = re.sub(r'^\d+_', '', chapter_id)
            bid = '%s_%d' % (cid, index)
            name = {}
            for lang in LANGS:
                chapter_title = localized(chapter.get('title'), lang)
                page_title = localized(page.get('title'), lang)
                sep = ' : ' if lang == 'fr_fr' else ': '
                name[lang] = chapter_title + sep + page_title if page_title else chapter_title
            if cid not in LINE_OF_CHAPTER:
                sys.exit('chapter %s has a schematic but no blueprint line' % cid)
            write(os.path.join(BLUEPRINTS, bid + '.json'), {
                'line': LINE_OF_CHAPTER[cid],
                'order': order * 100 + index,
                'name': name,
                'layers': [[row.replace('.', ' ') for row in layer] for layer in schematic['layers']],
                'key': schematic['key'],
            })
            count += 1
    print('blueprints: %d' % count)


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    build()
