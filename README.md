<div align="center">
  <img src="https://cdn.modrinth.com/data/USgVjXsk/a8150331e2257d66e03e09478f17e121fcd3fdea_96.webp">
  <h1>Create: The Factory Must Grow</h1>
  <a href="https://www.curseforge.com/minecraft/mc-mods/create-industry"><picture><source srcset="https://img.shields.io/badge/CurseForge-202830?style=for-the-badge&logo=curseforge" media="(prefers-color-scheme: dark)"><img src="https://img.shields.io/badge/CurseForge-white?style=for-the-badge&logo=curseforge" alt="CurseForge"></picture></a>
  <a href="https://modrinth.com/mod/create-tfmg"><picture><source srcset="https://img.shields.io/badge/Modrinth-202830?style=for-the-badge&logo=modrinth" media="(prefers-color-scheme: dark)"><img src="https://img.shields.io/badge/Modrinth-white?style=for-the-badge&logo=modrinth" alt="Modrinth"></picture></a>
  <a href="https://discord.gg/HCRF9PYdSy"><picture><source srcset="https://img.shields.io/badge/Discord-202830?style=for-the-badge&logo=discord" media="(prefers-color-scheme: dark)"><img src="https://img.shields.io/badge/Discord-white?style=for-the-badge&logo=discord" alt="Discord"></picture></a>
  <br>
  <a>Heavy Engineering & Oil For The Create Mod</a>
</div>

<br>

## Arcadia Fork

This repository is the **Team-Arcadia maintenance fork** (branch `Arcadia-fix`, jar classifier `arcadia-fix`). On top of upstream TFMG 1.2.x for Minecraft 1.21.1 / NeoForge, it focuses on stability: machines surviving chunk unload/reload, full client/server separation (dedicated-server friendly), multiplayer hardening, crash fixes and performance work on the electrical network. See [CHANGELOG.md](CHANGELOG.md) for the full list.

*Version française : ce dépôt est le fork de maintenance Team-Arcadia (branche `Arcadia-fix`). Il se concentre sur la stabilité : machines qui survivent au déchargement de chunk, séparation client/serveur complète (compatible serveur dédié), durcissement multijoueur, correction de crashs et optimisation du réseau électrique. Voir le [CHANGELOG.md](CHANGELOG.md).*

### Since 1.3.0

- **The Factory Handbook**: an in-game Patchouli guide to the whole mod (craft it from a book and coal, new players get one on first join). Chapters follow the order you build things in, with a 3D view of every multiblock that can also be projected as a ghost in the world. Requires Patchouli.
- **Machines say what is wrong**: red goggle lines when a structure is incomplete, fuel, flux or hot air is missing, or an output tank is full; JEI info pages for every multiblock.
- **The Factory Inspector**: right-click a machine to learn what works, what stops it and what to do; right-click the air to see your progress along the production chain and what the next step needs.
- **Factory Blueprints**: rare plans, one per assembly line, found in village workshop chests or sold by master cartographers. Nine lines to collect and trade, 62 structures in all: coke (ovens of every size), steel (air intakes, blast stoves, blast furnaces, casting), aluminium (electrolysis), chemistry (vats with every machine, arc furnace), oil (surface scanner, pumpjacks), refining (distillation towers, fireboxes, chimney, flarestack), engines (every engine type, gearbox, large engines), power (generators, large generators, accumulators, converter) and electricity (cables, transformers, switches). Right-click a block to project the line's multiblocks layer by layer; the next layer appears once one is built.
- **No more dead end**: new bootstrap recipes give the first magnet and the first aluminium, which survival could not reach before.
- **Game tests**: one test per block, data checks, a formation check for every blueprint structure, and functional tests where every machine runs for real with its power, fuel and supply, up to full production chains (coal to steel ingot, oil field to press, generator to accumulators to a millstone), every variant and setting (valves and smart pipes of every material, every tank size, every fluid and its bucket, every block dial), every hand-held item and weapon used by a server player with its effect checked in the world, and world checks (every ore, layer and oil feature generated through its real placed feature and biome modifiers against the handbook's figures, the surface scanner on generated oil fields, blueprints in the real village chest loot and the cartographer trade, the first-join handbook, block loot, and a progression check that every TFMG item can be obtained from vanilla and Create resources), run on a dedicated test server (`gradlew runGameTestServer`), on a singleplayer client (`gradlew runClientGameTest`) and on a regular server (`gradlew runServerGameTest`).

### Depuis la 1.3.0

- **Le Manuel de l'Usine** : un guide Patchouli en jeu de tout le mod (fabriqué avec un livre et du charbon, offert aux nouveaux joueurs à leur première connexion). Les chapitres suivent l'ordre de construction, avec une vue 3D de chaque multibloc qu'on peut aussi projeter en fantôme dans le monde. Nécessite Patchouli.
- **Les machines disent ce qui ne va pas** : lignes rouges aux lunettes quand une structure est incomplète, qu'il manque combustible, fondant ou air chaud, ou qu'un réservoir de sortie est plein ; pages d'information JEI pour chaque multibloc.
- **L'Inspecteur d'usine** : clic droit sur une machine pour savoir ce qui fonctionne, ce qui la bloque et quoi faire ; clic droit dans le vide pour voir votre avancée dans la chaîne de production et ce qu'il faut pour l'étape suivante.
- **Plans d'usine** : des plans rares, un par ligne d'assemblage, trouvés dans les coffres des ateliers de village ou vendus par les cartographes maîtres. Neuf lignes à collectionner et à échanger, 62 structures en tout : coke (fours de toutes tailles), acier (prises d'air, fourneaux à air chaud, hauts fourneaux, coulée), aluminium (électrolyse), chimie (cuves avec chaque machine, four à arc), pétrole (scanner de surface, chevalets de pompage), raffinage (tours de distillation, foyers, cheminée, torche d'évacuation), moteurs (chaque type de moteur, boîte de vitesses, grands moteurs), énergie (générateurs, grands générateurs, accumulateurs, convertisseur) et électricité (câbles, transformateurs, interrupteurs). Clic droit sur un bloc pour projeter les multiblocs de la ligne couche par couche ; la couche suivante apparaît dès qu'une couche est construite.
- **Plus d'impasse** : de nouvelles recettes d'amorçage donnent le premier aimant et le premier aluminium, impossibles à obtenir en survie auparavant.
- **Tests en jeu** : un test par bloc, des vérifications de données, une vérification que chaque structure des plans se forme, et des tests fonctionnels où chaque machine tourne pour de vrai avec son énergie, son carburant et son approvisionnement, jusqu'aux chaînes de production complètes (du charbon au lingot d'acier, du champ de pétrole à la presse, du générateur aux accumulateurs jusqu'à une meule), chaque variante et réglage (vannes et tuyaux intelligents de chaque matériau, chaque taille de réservoir, chaque fluide et son seau, chaque réglage de bloc), chaque objet ou arme tenu en main utilisé par un joueur serveur avec son effet vérifié dans le monde, et des vérifications du monde (chaque minerai, couche et gisement de pétrole généré par sa vraie configuration et ses modificateurs de biome, comparé aux chiffres du manuel, le scanner de surface sur des champs de pétrole générés, les plans dans le vrai butin des coffres de village et l'échange du cartographe, le manuel offert à la première connexion, le butin des blocs, et une vérification que chaque objet TFMG s'obtient à partir des ressources vanilla et Create), lancés sur un serveur de test dédié (`gradlew runGameTestServer`), sur un client solo (`gradlew runClientGameTest`) et sur un serveur classique (`gradlew runServerGameTest`).

## Info

Create is by default a steam/clockpunk mod and most addons aim to expand this part of Create and do that pretty well,
we thought the next natural expansion would be moving on from steampunk to dieselpunk.
We believe that create could be later used not just as a single steampunk tech mod,
but due to its modularity and polishedness, it is a perfect base for other tech mods aiming to Create (get it) something new with it,
essentially using it as a library.
We wanna be the first ones to try and prove this concept.

<br>

## Features

* Large Distilleries
* Realistic Electricity
* Steel Mills
* Concrete
* Electrolyzers
* Steel
* Aluminum
* Cast Iron
* Lead
* Sulfur
* OIL!!!
* Quad Potato Cannon
* Flamethrowers
* And more..

<br>

![refinery image](https://cdn.modrinth.com/data/USgVjXsk/images/16f8c83fbec919fdc571236d62434b2d8050cf11.png)

