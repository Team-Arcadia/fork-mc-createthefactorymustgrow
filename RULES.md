# Project Rules & AI/IDE Instructions

Single source of truth for working on this repository. Read fully before changing anything.

## 1. Project Identity

| Field | Value |
|---|---|
| Project name | Create: The Factory Must Grow (TFMG) — Arcadia fork |
| Mod ID | `tfmg` |
| Package | `com.drmangotea.tfmg` |
| Tech stack | Java 21, NeoForge 21.1.213 (MC 1.21.1), Create 6.0.10, Ponder 1.0.82, Flywheel 1.0.6, Registrate MC1.21-1.3.0, JEI 19.25 |
| Build | Gradle (`net.neoforged.moddev` 2.0.89), jar classifier `arcadia-fix` |
| Version | 1.3.1 (in `gradle.properties` → `mod_version`; NEVER bump without explicit instruction) |
| Authors | DrMangoTea, Pepa, Luna (upstream) — Arcadia fork maintained by Team-Arcadia |
| License | MIT |

## 2. Git Workflow

- Branches: `Arcadia-fix` (active work, pushed to `origin`), `Arcadia-fix-dev` (dev), `upstream/*` (read-only upstream remotes; default upstream branch is `1.21.1`).
- Commits: conventional style — `fix(scope): message`, `feat:`, `chore:`, `build:`, `test:`. No AI attribution, ever.
- Push directly to `origin/Arcadia-fix` after committing. Never force-push.
- Never commit: `bin/`, `build/`, `run/`, `test-procedures/`, `TEST_PROCEDURE_*.html`, `.claude/`, `CLAUDE.md` (all gitignored).

## 3. Code Conventions

- All code, comments, logs in English. Naming: standard Java (`PascalCase` classes, `camelCase` members, `SCREAMING_SNAKE` constants).
- Create idioms are law: `SmartBlockEntity.write/read(CompoundTag, boolean clientPacket)`, `sendData()` for BE sync, behaviours (`ScrollValueBehaviour`, `SmartFluidTankBehaviour`), Registrate builders in `registry/`.
- Side safety: server logic in `tick()`/`lazyTick()` MUST be guarded with `level.isClientSide` checks. Client-only classes (`net.minecraft.client.*`, Flywheel visuals, screens) may only be referenced through lazy suppliers (`.renderer(() -> X::new)`), `@OnlyIn`-stripped members, or `Dist.CLIENT` event subscribers.
- NBT: anything a machine needs to survive a chunk unload/reload MUST be persisted in `write()` and restored in `read()`. Live `BlockEntity` references cached in fields must be revalidated with `isRemoved()` before use.
- Do NOT hand-edit anything under `src/generated/resources` — it is owned by datagen and wiped by every `runData`. Hand-authored assets/data go in `src/main/resources`.

## 4. Project Structure

```
src/main/java/com/drmangotea/tfmg/
├── TFMG.java                 # Common @Mod entrypoint: registrate, registries init order, packets, configs
├── TFMGClient.java           # @Mod(dist=CLIENT): ponder plugin, particle factories, gadget render handlers
├── base/                     # Shared: builder transformers, tiers, utils, TFMGCommonEvents/TFMGClientEvents
├── config/                   # catnip ConfigBase SERVER (stress/resistivity) + COMMON (machines/deposits) specs
├── content/
│   ├── electricity/          # Voltage network sim: base/ (IElectric, ElectricalNetwork, manager),
│   │                         # cables, generators (incl. large rotor/stator), storage (accumulator),
│   │                         # network/ (switches, transformers), utilities (converter, motor, pump, lights)
│   ├── machinery/            # Multiblocks: metallurgy/ (blast furnace, coke oven, blast stove, casting),
│   │                         # vat/ (chemical vats + IVatMachines), oil_processing/ (distillation, pumpjack),
│   │                         # misc/ (firebox, air intake, winding machine, smokestacks)
│   ├── engines/              # Fuel engines: types/ (regular/large), engine_controller/ (drivable), upgrades/, fuels/
│   ├── decoration/           # Pipes, tanks (steel multiblock), doors, concrete/rebar, cogs/flywheels
│   └── items/                # Weapons (cannons, flamethrower, grenades, lithium blade), tools
├── datagen/                  # GatherDataEvent providers (recipes, entries, damage types)
├── mixin/                    # 14 mixins into Create/vanilla (+ accessors); config: src/main/resources/tfmg.mixins.json
├── ponder/                   # Ponder scenes (client-only via TFMGClient)
├── recipes/                  # 8 custom ProcessingRecipe types + JEI categories (recipes/jei/)
├── registry/                 # All Registrate/DeferredRegister holders (TFMGBlocks, TFMGBlockEntities, TFMGPackets…)
└── worldgen/                 # Oil deposit/well features + datagen bootstraps
src/main/resources/           # Hand-authored assets/data (lang files incl. community translations, manual recipes)
src/main/templates/           # neoforge.mods.toml template (gradle expands ${} properties)
src/generated/resources/      # Datagen output ONLY — never hand-edit (committed, regenerate via runData)
```

Architecture facts that matter (learned the hard way):

- **Electricity is a mirror simulation.** Voltage/current/network id are NEVER persisted nor synced; server and client each rebuild networks from scratch via `connectNextTick` flood-fill on every load. `ElectricNetworkManager` holds a static per-`LevelAccessor` map (client and server levels coexist in singleplayer). Nudge packets (`NetworkUpdatePacket` etc.) keep the client sim roughly aligned.
- **Three multiblock styles coexist:** Create `ConnectivityHandler` (vats, blast stove, firebox, steel tanks), custom controller propagation (coke oven, air intake), and stateless world rescans every tick/lazyTick (blast furnace, distillation, pumpjack).
- **Chunk unload does NOT call `destroy()`** (Create's `SmartBlockEntity` sets `chunkUnloaded` so `remove()` runs without network/multiblock teardown). Anything not in NBT is lost; anything holding a live BE reference goes stale.
- **Packets** are registered through catnip's `CatnipPacketRegistry` in `registry/TFMGPackets.java`; handling side is defined by the payload interface (Clientbound vs Serverbound), not the registration.

## 5. Adding a New Feature (Step by Step)

1. Branch from `Arcadia-fix` (`feat/<name>`), or work directly on `Arcadia-fix` for small fixes (fork convention).
2. Register content in `registry/` (block + item + BE type + renderer supplier), reusing `TFMGBuilderTransformers` where possible.
3. Implement the BE extending the right base (`ElectricBlockEntity`, `KineticElectricBlockEntity`, or Create `SmartBlockEntity`); persist ALL reload-critical state in `write()/read()`.
4. Guard every world-mutating path with `level.isClientSide`; sync via `sendData()` on change only.
5. Add lang keys via datagen (`en_us`) — community translations live in `src/main/resources/assets/tfmg/lang/`.
6. Run `gradlew runData` and commit the FULL datagen output together with the code.
7. Test in-game (client) AND on a dedicated server (`gradlew runServer`).
8. Update `CHANGELOG.md` (bilingual EN/FR, strict format), commit, push.

## 6. Testing Checklist

- [ ] `gradlew compileJava` green.
- [ ] `gradlew runData` produces no unexpected deletions in `src/generated` (`git diff --stat src/generated`).
- [ ] Dedicated server boots (`gradlew runServer`) — no `NoClassDefFoundError`/dist crashes.
- [ ] Machines survive a chunk unload/reload (move >10 chunks away and back, or relog) with state intact.
- [ ] No client/server desync: test each changed machine in multiplayer or LAN.
- [ ] JEI pages open for changed recipe types; ponder scenes still load.
- [ ] `CHANGELOG.md` updated (EN + FR).

## 7. Environment Setup

- Clone, then `./gradlew build` (Java 21 toolchain auto-provisioned; Windows: `gradlew.bat`).
- Run configs: `runClient`, `runServer` (add `--nogui`), `runData` (datagen), `gameTestServer`.
- IDE: VSCode or IntelliJ; Gradle sync generates run configs via ModDevGradle. Do not commit IDE compiler output (`bin/` is gitignored).
- Dependencies resolve from createmod.net / devos.one / blamejared / cursemaven mavens (see `build.gradle`).

## 8. AI Assistant Instructions

1. Read this file and `ERROR_LOG.md` before any change; apply documented prevention rules.
2. NEVER bump `mod_version` or any version reference unless explicitly told to.
3. NEVER hand-edit `src/generated/resources`; put manual resources in `src/main/resources` and re-run datagen for generated ones.
4. Every reload-critical BE field you add must be persisted in `write()/read()`; every cached BE reference must be `isRemoved()`-checked.
5. Keep fixes minimal and Create-idiomatic; do not redesign gameplay without an explicit request.
6. After code changes: `gradlew compileJava` minimum; full `build` before pushing.
7. Commit + push directly (conventional messages, no AI attribution); update `CHANGELOG.md` bilingually for user-visible changes.
8. Test procedures are LOCAL-ONLY HTML files in `test-procedures/` — never commit them.
9. The coke-oven over-sizing issue (CHANGELOG 1.2.3) is FIXED: the formation scan anchors on the existing controller when the wall exceeds the max formable square (`hasOvenBeyond`/`findIntactAnchor` in `CokeOvenBlockEntity`). Preserve that anchoring behavior when touching oven formation.
10. When touching electricity/multiblock lifecycle, re-read §4 architecture facts first; chunk-border behavior is the #1 regression source.
