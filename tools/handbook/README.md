# The Factory Handbook

The in-game guide (a Patchouli book) is written once, in English and French, in
`src/guide/chapters/*.json`, one file per chapter. `build_handbook.py` turns
those files into Patchouli's layout under
`src/main/resources/assets/tfmg/patchouli_books/handbook/<lang>/`.

```
python tools/handbook/build_handbook.py
```

Run it after editing a chapter and commit both the chapter and the generated
files. The `handbookIsValid` game test checks the generated book (items,
block states, categories, both languages).

## Chapter format

```json
{
  "order": 30,
  "icon": "tfmg:coke_oven",
  "title": {"en_us": "Coke Oven", "fr_fr": "Four à coke"},
  "pages": [{
    "title": {"en_us": "...", "fr_fr": "..."},
    "text": {"en_us": ["line", "", "- bullet", "## heading", "**highlight**"], "fr_fr": ["..."]},
    "items": ["tfmg:coke_oven", "minecraft:coal"],
    "schematic": {
      "layers": [["CCC"], ["CCC"]],
      "key": {"C": "tfmg:coke_oven[facing=south]"}
    }
  }]
}
```

Each chapter page becomes one book entry: its text split over as many book
pages as needed, then a 3D multiblock page (with Patchouli's Visualize button)
when it has a schematic, then a page cycling through its items.

Schematic layers go from the bottom up; in a layer, each string is a row from
north to south and each character a block from west to east. Space and `.` are
air.

## Le Manuel de l'Usine

Le guide en jeu (un livre Patchouli) s'écrit une seule fois, en anglais et en
français, dans `src/guide/chapters/*.json`. `build_handbook.py` le convertit
au format Patchouli dans
`src/main/resources/assets/tfmg/patchouli_books/handbook/<langue>/`. Lancez le
script après chaque modification d'un chapitre et commitez le chapitre et les
fichiers générés. Le test en jeu `handbookIsValid` vérifie le livre généré.
