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
import math
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
# Patchouli's landing page only fits about three rows of category icons, so
# the chapters are grouped under a few top-level categories.
GROUPS = [
    ('getting_started', 'tfmg:factory_guide', {'en_us': 'Getting Started', 'fr_fr': 'Premiers pas'},
     {'en_us': 'The first steps, raw resources and what to do when a machine refuses to work.',
      'fr_fr': 'Les premiers pas, les ressources brutes et que faire quand une machine refuse de marcher.'},
     ['introduction', 'resources', 'first_materials', 'troubleshooting']),
    ('steel', 'tfmg:steel_ingot', {'en_us': 'Steel', 'fr_fr': 'Acier'},
     {'en_us': 'From coal and iron to steel: coke oven, hot air, blast furnace and casting.',
      'fr_fr': "Du charbon et du fer jusqu'à l'acier : four à coke, air chaud, haut fourneau et moulage."},
     ['coke_oven', 'hot_air', 'blast_furnace', 'casting_and_steel']),
    ('electricity', 'tfmg:generator', {'en_us': 'Electricity', 'fr_fr': 'Électricité'},
     {'en_us': 'Making power, carrying it and using it in machines.',
      'fr_fr': "Produire l'énergie, la transporter et l'utiliser dans les machines."},
     ['electricity_basics', 'power', 'network_control', 'electric_machines', 'winding_and_magnets']),
    ('chemistry', 'tfmg:steel_chemical_vat', {'en_us': 'Chemistry', 'fr_fr': 'Chimie'},
     {'en_us': 'Chemical vats, aluminium electrolysis and the arc furnace.',
      'fr_fr': "Cuves chimiques, électrolyse de l'aluminium et four à arc."},
     ['chemical_vats', 'aluminium', 'arc_furnace']),
    ('oil', 'tfmg:pumpjack_base', {'en_us': 'Oil', 'fr_fr': 'Pétrole'},
     {'en_us': 'Finding oil, pumping it, refining it and burning the gases.',
      'fr_fr': 'Trouver le pétrole, le pomper, le raffiner et brûler les gaz.'},
     ['finding_oil', 'pumpjack', 'distillation', 'fireboxes_and_gases']),
    ('engines', 'tfmg:regular_engine', {'en_us': 'Engines', 'fr_fr': 'Moteurs'},
     {'en_us': 'Engines, their fuels, upgrades and large engines.',
      'fr_fr': 'Les moteurs, leurs carburants, leurs améliorations et les grands moteurs.'},
     ['engines', 'engine_upgrades']),
    ('building_and_tools', 'tfmg:flamethrower', {'en_us': 'Building and Tools', 'fr_fr': 'Construction et outils'},
     {'en_us': 'Concrete, construction blocks, weapons and tools.',
      'fr_fr': 'Béton, blocs de construction, armes et outils.'},
     ['construction', 'weapons_and_tools']),
]
GROUP_OF = {chapter: group[0] for group in GROUPS for chapter in group[4]}

ITEMS_TITLE = {'en_us': 'Items', 'fr_fr': 'Objets'}
LANG_FILES = {
    'en_us': [os.path.join(ROOT, 'src', 'generated', 'resources', 'assets', 'tfmg', 'lang', 'en_us.json')],
    'fr_fr': [os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'tfmg', 'lang', 'fr_fr.json')],
}
_names = {}


def item_name(item, lang):
    """Display name of an item id, from the mod's lang files (TFMG items only)."""
    if lang not in _names:
        _names[lang] = {}
        for path in LANG_FILES[lang]:
            if os.path.exists(path):
                with open(path, encoding='utf-8') as f:
                    _names[lang].update(json.load(f))
    item = item.split('{')[0].split('[')[0]
    namespace, _, path = item.partition(':')
    for kind in ('item', 'block'):
        name = _names[lang].get('%s.%s.%s' % (kind, namespace, path))
        if name:
            return name
    return ''


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
    pad = view_padding(size_x, len(grid), size_z)
    if pad:
        grid = [[' ' * (size_x + 2 * pad)] * pad
                + [' ' * pad + row + ' ' * pad for row in layer]
                + [' ' * (size_x + 2 * pad)] * pad for layer in grid]
    return {'pattern': list(reversed(grid)), 'mapping': mapping}


# Patchouli fits a multiblock page by scaling 90 / max(horizontal diagonal,
# height), but draws it tilted 30 degrees: a tall, narrow structure then comes
# out taller than the frame. Empty columns around it shrink the scale without
# moving it (the padding is symmetric and spaces match anything).
VIEW_FRAME = 90
VIEW_LIMIT = 82


def view_scale(size_x, size_y, size_z, pad):
    diag = math.hypot(size_x + 2 * pad, size_z + 2 * pad)
    return VIEW_FRAME / max(diag, size_y)


def view_padding(size_x, size_y, size_z):
    drawn = size_y * math.cos(math.radians(30)) + math.hypot(size_x, size_z) * math.sin(math.radians(30))
    pad = 0
    while view_scale(size_x, size_y, size_z, pad) * drawn > VIEW_LIMIT:
        pad += 1
    return pad


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
        for order, (gid, icon, name, description, _) in enumerate(GROUPS):
            write(os.path.join(cat_dir, gid + '.json'), {
                'name': name[lang],
                'description': description[lang],
                'icon': icon,
                'sortnum': order,
            })
        for chapter_id, chapter in chapters:
            cid = re.sub(r'^\d+_', '', chapter_id)
            if cid not in GROUP_OF:
                sys.exit('chapter %s has no group in GROUPS' % cid)
            title = localized(chapter.get('title'), lang)
            first_text = localized(chapter['pages'][0].get('text'), lang)
            description = re.sub(r'\*\*(.+?)\*\*', r'\1', next((l for l in first_text.split('\n') if l.strip() and not l.startswith('## ')), title))
            category = {
                'name': title,
                'description': description,
                'icon': chapter.get('icon', 'minecraft:book'),
                'sortnum': chapter.get('order', 1000),
                'parent': '%s:%s' % (NAMESPACE, GROUP_OF[cid]),
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
                    spotlight = {
                        'type': 'patchouli:spotlight',
                        'item': ','.join(items),
                        'link_recipe': False,
                        'text': ITEMS_TEXT[lang],
                    }
                    # The title defaults to the item name and is never wrapped.
                    if any(text_width(item_name(i, lang)) > PAGE_WIDTH for i in items):
                        spotlight['title'] = ITEMS_TITLE[lang]
                    patch_pages.append(spotlight)
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

# The assembly lines a Factory Blueprint can carry. Must match
# BlueprintLines.LINES in the mod.
LINES = ['coke', 'steel', 'aluminium', 'chemistry', 'oil', 'refining', 'engines', 'power', 'electricity']

# Which Factory Blueprint (assembly line) shows the structures of a chapter.
# A page can name another line with "blueprint_line", so one chapter can feed
# several lines.
LINE_OF_CHAPTER = {
    'coke_oven': 'coke',
    'hot_air': 'steel', 'blast_furnace': 'steel', 'casting_and_steel': 'steel',
    'aluminium': 'aluminium',
    'chemical_vats': 'chemistry', 'arc_furnace': 'chemistry',
    'finding_oil': 'oil', 'pumpjack': 'oil',
    'distillation': 'refining', 'fireboxes_and_gases': 'refining',
    'engines': 'engines', 'engine_upgrades': 'engines',
    'power': 'power', 'electric_machines': 'power', 'winding_and_magnets': 'power',
    'electricity_basics': 'electricity', 'network_control': 'electricity',
}


def build_blueprints(chapters):
    """One blueprint per schematic, for the Factory Blueprint's in-world
    layer-by-layer projection. Layers stay bottom first."""
    shutil.rmtree(BLUEPRINTS, ignore_errors=True)
    os.makedirs(BLUEPRINTS, exist_ok=True)
    count = 0
    per_line = {}
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
            line = page.get('blueprint_line') or LINE_OF_CHAPTER.get(cid)
            if not line:
                sys.exit('chapter %s has a schematic but no blueprint line' % cid)
            if line not in LINES:
                sys.exit('%s: unknown blueprint line %s' % (bid, line))
            per_line[line] = per_line.get(line, 0) + 1
            write(os.path.join(BLUEPRINTS, bid + '.json'), {
                'line': line,
                'order': order * 100 + index,
                'name': name,
                'layers': [[row.replace('.', ' ') for row in layer] for layer in schematic['layers']],
                'key': schematic['key'],
            })
            count += 1
    print('blueprints: %d, per line: %s' % (count, ', '.join('%s %d' % (l, per_line.get(l, 0)) for l in LINES)))


def write(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    build()
