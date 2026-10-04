# Error Log

Session-discovered errors, root causes, and prevention rules for TFMG (Arcadia fork).

---

## [2026-08-17 15:10] — 1.2.7 server startup crash: duplicate steel_encased_shaft in the creative search tab

**Context:** Modpack report: every dedicated server start on tfmg-1.2.7-arcadia-fix dies with `ModLoadingException` before any player connects. Reproduced in dev by forcing `CreativeModeTabs.tryRebuildTabContents` on `ServerStartedEvent` (pack mods request tab contents server-side; a bare dev server never builds tabs, which is why 1.2.7 QA missed it).
**Error:** `java.lang.IllegalArgumentException: Itemstack 1 tfmg:steel_encased_shaft already exists in the tab's list` while dispatching `BuildCreativeModeTabContentsEvent`.
**Root cause:** Two independent adders collide in the *vanilla search tab*, not in tfmg's own tabs. (1) `customAdditions()` (new in 1.2.7, d095d2d5) listed the two encased shafts as component-less stacks with default `PARENT_AND_SEARCH_TABS` visibility, so they enter tfmg_main's search entries; the vanilla search tab aggregates every tab's search entries when it builds (after all category tabs). (2) Registrate's `AbstractRegistrate.item()` auto-tabs *every* item into `CreativeModeTabs.SEARCH` (`defaultCreativeModeTab`), and its own event listener then accepts a plain stack of each item into the search tab — the second plain `steel_encased_shaft` throws. Every other `customAdditions` stack carries data components and never equals Registrate's plain stack, which is why 1.2.6 was safe. A second, load-order-dependent duplicate also existed: the encased blocks register under whatever tab `CreateRegistrate`'s mutable `currentTab` global holds at class-load time, so a pack mod loading tfmg classes early lands them in TFMG_MAIN and the main-tab loop re-adds what customAdditions already listed.
**Fix:** In `TFMGCreativeTabs.addCreative`: component-less customAdditions stacks are accepted `PARENT_TAB_ONLY` (Registrate already lists every plain item in the search tab once); component-carrying stacks keep search visibility; both tab loops now filter the encased shaft and cogwheel families so their placement no longer depends on class-load order; customAdditions skips stacks another mod already listed.
**Prevention:** Never `accept` a component-less ItemStack with search visibility from a Registrate-managed mod — Registrate's default-tab listener will add the plain stack again and the search tab build throws on duplicates. When adding items to creative tabs manually, remember the search tab is built *last* from every tab's search entries plus Registrate's auto-additions. Creative-tab placement must never rely on `setCreativeTab` ordering across classes: any block a pack mod can class-load early needs an explicit filter or an explicit tab.

---

## [2026-07-01 12:00] — Stale datagen output silently overriding 1.2.3 fixes

**Context:** Session start audit of the working tree (3355 modified files).
**Error:** `src/generated/resources` contained an uncommitted `runData` output dated 2026-05-23 (pre-1.2.3 code). It deleted the hand-authored compressor/freezer crafting recipes, the zh_cn lang file and hundreds of `mineable/pickaxe` tag entries, and left stale *untracked* copies of the four vat recipes (`compressed_lpg`, `cooling_fluid`, `liquid_air`, `liquid_asphalt`) that the 1.2.3 commit had moved to `src/main/resources`. Since both resource roots are merged into the jar, the stale copies could override the fixed recipes at runtime.
**Root cause:** Hand-authored files were placed inside `src/generated/resources`, which is owned by datagen and fully rewritten by every `runData` run; plus a datagen run from an old code state was left half-applied in the working tree.
**Fix:** Restored `src/generated` to HEAD, deleted stale untracked datagen leftovers, moved hand-authored files (compressor/freezer recipes + advancements) to `src/main/resources`, merged the duplicated zh_cn.json (1172-key legacy + 436-key port → single 1298-key file in `src/main/resources`).
**Prevention:** NEVER hand-edit or hand-add files under `src/generated/resources` — put manual resources in `src/main/resources`. After changing datagen-relevant code, run `gradlew runData` and commit its full output in the same commit. Before committing datagen output, `git diff --stat src/generated` and investigate any *deletion*.

---

## [2026-07-01 12:05] — Build artifacts (`bin/`) tracked in git

**Context:** Same audit.
**Error:** 6033 compiled `.class`/resource files under `bin/` (Eclipse/VSCode compiler output) were tracked in git despite `/bin/` being in `.gitignore` (ignored only applies to untracked files).
**Root cause:** `bin/` was committed before the `.gitignore` entry was added; git keeps tracking already-tracked files.
**Fix:** `git rm -r --cached bin` (commit d3611d09).
**Prevention:** After adding a path to `.gitignore`, always check `git ls-files <path>` and untrack leftovers.

---

## [2026-07-02 05:30] — Session-limit interruption of the verification workflow

**Context:** 17-agent adversarial verification workflow over the 121 mapped findings.
**Error:** All agents failed twice with "You've hit your session limit"; failed agents have no checkpoint and restart from scratch on resume (only COMPLETED agents replay from cache).
**Root cause:** Subagent fan-out burned the API quota; a killed agent's partial work is lost except for files it already wrote to disk.
**Fix:** Reused the 10 patch-plan files the finished agents had written to the scratchpad; verified and fixed the remaining 7 groups inline (single-context reads) instead of re-running agents.
**Prevention:** Have verification agents WRITE their outputs to disk incrementally (patch files survive the agent's death). When quota is tight, prefer inline verification over agent re-runs — resuming a workflow only saves tokens for agents that fully completed.

## [2026-07-02 06:30] — Accumulator energy nearly truncated by a "safety" clamp

**Context:** Adding a capacity clamp to `TFMGForgeEnergyStorage.setEnergy` (verified finding AC2).
**Error:** The clamp alone would have deleted energy: `AccumulatorBlockEntity.read` restores `ForgeEnergy` into the storage built by the field initializer (capacity = length 1) BEFORE the chain rebuild resizes it — a full 5-block chain would have been clamped to 1 block's capacity on every load.
**Root cause:** `EnergyStorage.capacity` is fixed at construction; `getMaxCapacity()` is dynamic (config × length). The NBT read order (length before storage rebuild) was invisible in the finding itself.
**Fix:** `read()` now rebuilds the storage at the persisted length before `setEnergy`, then the clamp is safe.
**Prevention:** Before clamping any restore path, trace WHEN the receiving container gets its final size — clamps applied to half-initialized state destroy data.

---

## [2026-07-02 15:15] — Upstream datagen drift: block tags regress on every runData

**Context:** Re-running `gradlew runData` for the new fluid tags (hydrogen/butane/propane).
**Error:** A fresh datagen from CURRENT code deletes entries that are committed and needed at runtime: `c:ores` (lead/nickel/lithium), `c:storage_blocks/*`, `minecraft:doors`/`climbable`/`beacon_base_blocks`/`needs_iron_tool`, `create:casing`/`fan_transparent`, plus heavy churn in `mineable/pickaxe`. Identical deletions appeared in the stale 2026-05-23 run — the committed tag data comes from an older code state (pre-Create-6/1.21-port) that the current builder transforms no longer reproduce.
**Root cause:** Tagging code was lost upstream during the 1.21/Create 6 migration; the shipped jar only stayed correct because nobody re-ran datagen and committed blindly.
**Fix:** Selective accept: staged only the additive outputs (new c: fluid tags, firebox_fuel, vanilla tool/enchantable item tags, extinguisher filling default-component change, regenerated compressed_lpg) and `git checkout`-restored every drifted file.
**Prevention:** NEVER commit a full runData output on this repo without reviewing `git diff --stat src/generated` — deletions in tag files are regressions until the lost tagging transforms are restored in code (tracked as future work). Also: `runData` requires NeoForge ≥ 21.1.219 since Create 6.0.10.

## [2026-07-05 18:10] — 'git add -A' silently committed a working-tree deletion of CHANGELOG.md

**Context:** Committing the regular-engine renderer hardening (66c63730).
**Error:** CHANGELOG.md had been deleted from the working tree (outside this session); `git add -A` staged the deletion and the commit recorded it. Discovered only when a later edit failed with "File does not exist".
**Root cause:** Committed without reviewing `git status --porcelain` first; `add -A` stages deletions too.
**Fix:** Restored the file from the parent commit (`git checkout 58067f75 -- CHANGELOG.md`) and re-applied the new entries (f4ead0c1).
**Prevention:** Always review `git status --porcelain` before `git add -A`; treat unexpected ` D` entries as red flags to investigate, never to commit.

## [2026-08-01 00:55] — Blast Stove capability refresh silently dead across a package boundary

**Context:** Ticket #227, the heat recuperator (Blast Stove) losing its piping after every relog until a block was broken and replaced.
**Error:** After a chunk reload, every non-controller stove block served the empty tanks built by its own constructor instead of delegating to the controller, so pumping heated air out of the top silently did nothing.
**Root cause:** `BlastStoveBlockEntity` declares `public void refreshCapability()` believing it overrides `FluidTankBlockEntity#refreshCapability`. That parent method is **package-private** in `com.simibubi.create.content.fluids.tank`, and a package-private method cannot be overridden from another package: the child method is a new, unrelated one. The parent's own `updateCapability` hook (set in `read()`, consumed in `tick()`) therefore only ever rebuilt the parent's `fluidCapability`, never the stove's `primaryCapability`/`secondaryCapability`. The stove's constructor had built them while `controller` was still null, i.e. pointing at its own tanks.
**Fix:** Added a dedicated `refreshStoveCapability` flag set at the end of `read()` and consumed right after `super.tick()`. Deliberately NOT named `updateCapability`, which would shadow the inherited field and reintroduce the shadowing bug already documented in that class.
**Prevention:** Before relying on an override of a Create method, check the parent's access modifier: no modifier means package-private and no cross-package override, silently. TFMG already handles this correctly elsewhere via `@Invoker("refreshCapability")` in `FluidTankBlockEntityAccessor` — the existence of an `@Invoker` for a method is itself the signal that it is not directly reachable. Defer any post-`read()` capability refresh to the next tick: at read time neighbouring block entities may not be loaded, and `handlerForCapability()` silently falls back to local tanks when `getControllerBE()` returns null.

## [2026-08-10 11:30] — Untracked duplicates of purged files reappeared in the working tree

**Context:** Ticket #244 session. `git status` showed nine untracked files that predated the session and had all been purged in 1.2.3.
**Error:** A stray `ru_ru.json` at the repository root (byte-identical duplicate of the tracked `assets/tfmg/lang/ru_ru.json`), the four dead mixins 1.2.3 removed (`TFMGMixinPlugin`, `UtilMixin`, `ProcessingRecipeAccessor`, `SequencedAssemblyRecipeAccessor`), and hand-authored copies of `compressed_lpg` / `cooling_fluid` / `liquid_air` / `liquid_asphalt` under `src/main/resources` shadowing the datagen versions in `src/generated`.
**Root cause:** Not re-created by any build step — leftovers of an earlier checkout or IDE state that were never cleaned after the 1.2.3 purge. The vat recipes are now produced by `TFMGVatRecipeGen`, so the manual copies became redundant the moment datagen took them over; both resource roots merge into the jar, so a stale copy can override the generated one at runtime.
**Fix:** Deleted the nine files after verifying each: the recipes differed from the generated output only by a trailing newline (and by ingredient order for `liquid_asphalt`), the mixins appear nowhere in `tfmg.mixins.json` and have zero external references. Jar verified afterwards: 16 vat recipes with no duplicate, no orphan mixin class, `ru_ru.json` only under `assets/tfmg/lang/`.
**Prevention:** `hydrogen.json` and `lpg_separation.json` live in `src/main/resources` legitimately — they are NOT produced by datagen and must never be deleted in such a cleanup. Before removing anything from `src/main/resources/data`, check whether `src/generated` has the same path: same path means a duplicate to drop, no counterpart means hand-authored content to keep. Untracked files cannot be recovered from git, so back them up before deleting.

## [2026-08-21 16:20] — PowerShell regex rewrite mangled a source file's encoding

**Context:** Replacing `fluid.getSource()` with a helper call across `FluidContainingItem.java` during the 1.2.9 fixes, using `Get-Content -Raw | -replace | Set-Content -Encoding utf8`.
**Error:** Two silent corruptions in one command: an em dash inside a code comment became `â€”` mojibake, and `Set-Content -Encoding utf8` wrote a UTF-8 BOM at the head of the file. Neither breaks `compileJava`, so both would have been committed unnoticed.
**Root cause:** Windows PowerShell 5.1's `utf8` encoding always emits a BOM (there is no `utf8NoBOM` in 5.1), and `Get-Content -Raw` decoded the existing file as the ANSI codepage rather than UTF-8, so every non-ASCII byte was re-encoded wrong on the way back out.
**Fix:** Restored the comment text by hand and stripped the three BOM bytes with `[System.IO.File]::ReadAllBytes` / `WriteAllBytes`.
**Prevention:** Never rewrite a source file through a PowerShell text pipeline. Use the Edit tool for targeted replacements; if a scripted pass is unavoidable, verify the first three bytes are not `239 187 191` afterwards and re-read any line containing non-ASCII characters.

## [2026-09-01 16:05] - Scripted edit silently duplicated a whole source file

**Context:** Removing two now-unused generator methods from `TFMGBuilderTransformers.java` after folding
the coloured rebar floors and pillars into the colour loop (bug K2).
**Error:** A throwaway python snippet searched for the method start with `src.find(name)` but computed the
cut point from an unrelated earlier `find`, so instead of deleting a range it re-inserted one. The file went
from 533 to 1966 lines with the class body duplicated; `git diff --stat` showed "1433 insertions" and the
duplicated methods still matched a grep for their own name, which is what gave it away.
**Root cause:** Index arithmetic across several independent `find` calls with no assertion that the computed
end came after the computed start, and no check that the result was shorter than the input.
**Fix:** `git checkout --` the file, then redo the edit with the exact old/new replacement helper used for
every other change in the session, and delete the dead methods with a single anchored slice verified by a
line count and a grep for the removed names.
**Prevention:** Never delete a code range by composing several `find` offsets. Use one exact-string
replacement, or slice between two anchors verified in the same expression, and always check the file got
SHORTER (`wc -l`) plus `git diff --stat` before compiling. A compile alone would not have caught this:
duplicated private methods in the same class do fail, but duplicating a whole class body can still compile
in other shapes, and the file was only saved by the grep count.

## [2026-10-04 08:52] - Singleplayer game test pass froze: the integrated server was paused

**Context:** First run of the new `runClientGameTest` config, which boots the client into a world and runs every TFMG game test on the integrated server.
**Error:** The log stopped after "running 466 TFMG tests on the integrated server" and nothing else happened for minutes; no test passed or failed.
**Root cause:** A singleplayer world pauses its integrated server whenever the game window loses focus (`pauseOnLostFocus:true` in `run/options.txt`), and the window opened behind the IDE. A paused server never ticks, so no test could advance.
**Fix:** Set `pauseOnLostFocus:false` (and `soundCategory_master:0.0` at the user's request) in `run/options.txt`, killed the stuck client and reran: 462/466, then 466/466 after the test fix below.
**Prevention:** Before any automated client run, check `run/options.txt` for `pauseOnLostFocus:false` and the master volume at 0. A client test run that logs its start and then goes silent is paused, not slow.

## [2026-10-04 09:00] - Round-trip test flagged Create's "Force" sync flag only on the integrated server

**Context:** The per-block save/load round-trip game test passed on the dedicated gameTestServer and failed on the integrated server for the three vats and the concrete hose.
**Error:** `save/load round trip changed: [InputTanks ... -> ...{Level:{Force:1b,...}}]`.
**Root cause:** Create's `LerpedFloat` / tank level writes `Force:1b` after a load to force the next client sync. It is transport state, not game data, and it only differs where a client is attached. The diff also compared list tags as a whole, so the flag inside a list element failed the whole list.
**Fix:** The diff now recurses into lists of compounds and ignores `Force` alongside `NeedsSpeedUpdate`.
**Prevention:** A round-trip difference that appears on only one side (integrated vs dedicated) is almost always sync state; check the key against Create's behaviours before treating it as data loss.

## [2026-10-04 09:55] - A mock server player crashed the game test server

**Context:** Testing the Factory Inspector's progression checklist, which needs a ServerPlayer.
**Error:** `GameTestHelper.makeMockServerPlayerInLevel()` failed with "Payload configured:session_data may not be sent to the client!" and the whole gameTestServer run crashed instead of failing one test.
**Root cause:** The helper logs the player in through `PlayerList.placeNewPlayer`, and NeoForge's configuration phase tries to send a configuration payload over the embedded test connection, which is not allowed in that state.
**Fix:** Build a bare `new ServerPlayer(server, level, profile, ClientInformation.createDefault())` instead: no connection, no login, inventory and stats still work.
**Prevention:** In TFMG game tests, never log a mock player in. Use `makeMockPlayer` for item use and a bare ServerPlayer when server-side player data is needed.

## [2026-10-04 10:07] - Last screenshot of a dev run was an empty file

**Context:** The clientShowcaseShots run halts the client once its last screenshot is taken.
**Error:** `showcase_blueprint_projection.png` existed but was empty.
**Root cause:** `Screenshot.grab` hands the PNG write to a worker thread; `Runtime.halt` right after killed the JVM before the write finished.
**Fix:** Wait 60 ticks after the last screenshot before halting.
**Prevention:** Any dev tool that exits the game must leave time for asynchronous writes (screenshots, logs, saves) after its last action.

## [2026-10-04 10:30] - Factory Blueprint never advanced to the next layer
**Context:** Building the first layer of a projected multiblock with the Factory Blueprint.
**Error:** Once the shown blocks were placed, the next layer never appeared and the projection vanished.
**Root cause:** Patchouli draws an anchored ghost through `simulate(..., forView = true)`, which adds one block on Y, while `IMultiblock.validate` uses `forView = false`. The projector checked one block below the drawn ghost, so the layer never matched; Patchouli then reported it complete and the projector read that as the player closing it. On top of that, `strictBlockMatcher` compares against the block's default state, so any block placed with another facing (coke oven, hatches) never matched.
**Fix:** The projector checks the layer through the same view simulation the visualizer renders, and anchors one block lower so the ghost sits on the clicked face. Each position matches on the block only (`predicateMatcher` with the drawn state for display). The client showcase now places each layer where it is drawn and fails if the projector does not move on.
**Prevention:** When checking a Patchouli multiblock shown with `showMultiblock`, use `simulate(level, anchor, rotation, true)`, never `validate`.

## [2026-10-04 18:20] - Handbook pages showing "Format error"
**Context:** Reviewing every handbook page in French through the screenshot run.
**Error:** 13 entries per language displayed "Format error: ..." instead of their text.
**Root cause:** book.json sets `i18n: true`, so Patchouli passes every string through `I18n.get`, which formats it with `String.format`. A lone `%` ("50 % de chance", "25% chance") is an invalid format specifier and the whole string is replaced.
**Fix:** The converter escapes `%` as `%%` in every book string; TFMGGuideTests runs `String.format` on every string of every entry.
**Prevention:** Any text that reaches an i18n Patchouli book must be format safe. Check new literal strings with the guide test.

## [2026-10-04 18:20] - Handbook landing page and titles overflowing the book
**Context:** User report "les 3d dans le book déborde parfois", with a screenshot of the landing page.
**Error:** The landing page showed a sixth row of category icons below the page; some 3D views and many entry titles ran past the frame or the spine.
**Root cause:** 24 top-level categories need six rows where five fit. Patchouli scales a multiblock by `90 / max(diagonal, height)` but draws it tilted 30 degrees, so tall narrow structures exceed the frame. Titles are drawn on one line with no wrapping or scaling. A group named like a chapter (`engines`) was overwritten by it and became its own parent.
**Fix:** Seven top-level groups with chapters as subcategories; symmetric empty padding around tall multiblocks; short titles from `tools/handbook/short_titles.json`, enforced by the converter; group ids asserted distinct from chapter ids; the guide test checks parents, loops and the number of top-level categories.
**Prevention:** After changing the handbook, run `runClientGuideShots` in both languages and scan the bottom margin of every page.

## [2026-10-04 18:20] - A charging accumulator counted as a power source
**Context:** Power game test: a 64 RPM generator (1320 W) feeding 1361 W of resistors, with a charged accumulator on the line.
**Error:** The network was not flagged as undersupplied and the bank still gained 100 FE per tick: 1461 W consumed on 1320 W generated.
**Root cause:** `AccumulatorBlockEntity.powerGeneration()` returned its full output whenever it held charge, and `ElectricalNetwork.updateNetwork` summed it with the generators. Driven above its own voltage the bank charges and never discharges (`tick`), so it was counted as a source and as a load at the same time.
**Fix:** New `IElectric.powerGeneration(int networkVoltage)`; the network sums power after the network voltage is known (and `getNetworkPowerGeneration` passes the member's voltage). The accumulator supplies nothing while the network voltage is above its own.
**Prevention:** A storage block may only count as a source when it is actually the one setting the voltage. Any new storage or conversion block must answer `powerGeneration(int)` accordingly.

## [2026-10-04 18:27] - Breaking a block of a charged accumulator bank voided charge
**Context:** Power game test breaking the middle of a full five block bank.
**Error:** 500000 FE became 200000 in the lower half, 0 in the upper half and 0 in the dropped item.
**Root cause:** Create's `IBE.onRemove` calls `destroy()` while the block entity is still in the chunk, so the chain rebuild in `destroy()` walked through the broken block and kept the old chain whole; the real split only happened on the next neighbour refresh, which summed the controller's charge into the shorter half and clamped it. A non-controller also donated only its own (always empty) storage. On paths that roll drops before `destroy()` (drills, explosions, the wrench) the item read the raw storage while `destroy()` handed the same charge to the surviving banks.
**Fix:** `destroy()` flags the block as removing (chain scans skip it), takes the bank's charge out of the controller, fills the controller side, hands the rest to the far side and keeps only what fits nowhere; `getDrops` uses `chargeKeptOnRemoval()` while the block entity is not removed yet.
**Prevention:** In a Create block entity's `destroy()`, the block entity is still returned by `level.getBlockEntity(pos)`. Multiblock scans run from `destroy()` must exclude it explicitly. Item drops must agree with what `destroy()` hands out, whichever runs first.

## [2026-10-04 18:22] - Converter and accumulator traded FE back and forth
**Context:** Power game test: converter in TFMG to FE mode with an accumulator on its FE side.
**Error:** The accumulator never filled; the converter's own storage kept everything.
**Root cause:** The converter exposed its raw storage as the FE capability in both modes. The accumulator pushes FE into any neighbour, so it sent the converted FE straight back every tick.
**Fix:** The converter's FE port is one way: extract only in TFMG to FE mode, receive only in FE to TFMG mode.
**Prevention:** An energy capability on a block with a direction of conversion must refuse the wrong direction, or pushing neighbours loop the energy.

## [2026-10-04 18:22] - Electric pump moved nothing through pipes when powered after placement
**Context:** Power game test: tank, pipe, electric pump, pipe, tank, generator added 40 ticks later.
**Error:** Pump against the tanks worked; the piped one never moved fluid.
**Root cause:** Create spreads pump pressure into pipes only from `updatePressureChange`, run on a speed change. The electric pump never changes speed, so its pipes kept the zero pressure computed at placement.
**Fix:** `ElectricPumpBlockEntity.onNetworkChanged` calls `updatePressureChange()` on the server whenever its voltage changes.
**Prevention:** Electric versions of kinetic machines must replay whatever Create triggers from `onSpeedChanged` when their voltage changes.

## [2026-10-04 18:22] - Large transformer input part reported a 1.00 ratio
**Context:** Power game test assembling a 100/300 turn large transformer.
**Error:** The output was right (300 V from 100 V) but the inspector on the input part read a ratio of 1.00.
**Root cause:** `LargeCoilBlockEntity.createTransformer` set the turn ratio on the output part only; the input part kept its default until a steel block copied it over.
**Fix:** The ratio is set on both parts at assembly.
**Prevention:** When a multiblock is assembled from two block entities, every value either part displays must be written to both.

## [2026-10-04 18:24] - Neon tube shone on an overloaded network
**Context:** Power game test: a 1 ohm load on a generator too weak for it, with a bulb and a neon tube.
**Error:** The bulb went dark (network flagged not enough power), the neon tube kept shining at full voltage.
**Root cause:** `NeonTubeBlockEntity.tick` set its light from the voltage alone, without the `canWork()` check the light bulb has.
**Fix:** The neon tube lights only when `canWork()`.
**Prevention:** Every consumer must gate its effect on `canWork()`, not on the voltage alone.

## [2026-10-04 11:00] - Every healthy fuel engine reported "no voltage" in the Factory Inspector
**Context:** New functional engine game tests assert that the inspector shows no problem line on a running engine.
**Error:** Regular, radial, turbine and large engines all reported `No voltage reaches this block` with advice to wire a generator, while turning at full speed.
**Root cause:** Engines extend `KineticElectricBlockEntity` only so the generator upgrade can make them a voltage source. `FactoryInspectorItem` runs `GenericInspections.electric` on every `IElectric`, which flags zero voltage on anything that is not a generator.
**Fix:** `IInspectable.wantsElectricCheck()` (default true), honoured by the inspector; small engines answer true only with a generator upgrade mounted, the large engine always false.
**Prevention:** A block that implements `IElectric` for an optional feature must opt out of the generic electric check, the same way `wantsRotationCheck` lets non-shaft kinetic blocks opt out of "not turning".

## [2026-10-04 11:05] - Radial and turbine engines claimed to have no output shaft
**Context:** Same engine game tests, radial and turbine engines.
**Error:** Both engines turned their shaft at the expected speed, yet the inspector reported `engine.no_shaft`, the goggles printed "no shaft", and `outputStress()` (goggle stress capacity) read 0.
**Root cause:** `AbstractSmallEngineBlockEntity.hasOutputShaft` only recognised the `SHAFT` engine state, which only regular engines reach by having a shaft inserted. Radial (`SINGLE`/`SHAFT` ends) and turbine (`SINGLE`/`BACK`) engines export rotation through built-in shaft faces declared by their block's `hasShaftTowards`.
**Fix:** `hasOutputShaft` now asks the engine block's own `hasShaftTowards` for each horizontal face and counts a face only if it does not lead into another engine block, which keeps the old answer for regular engines.
**Prevention:** Derive "does this export rotation" from the block's kinetic contract (`hasShaftTowards`), never from one subclass's blockstate value.

## [2026-10-04 18:35] - A firebox placed lit heated forever without fuel
**Context:** New game test building the handbook distillation blueprint (fireboxes stored as `blaze=fading`) with crude oil but no firebox fuel.
**Error:** After 200 ticks every firebox still showed a flame and the steel tank kept heat level 8, so the tower would distil on empty fireboxes.
**Root cause:** Heat is read from the firebox blockstate (`TFMGBoilerHeaters`), but `FireboxBlockEntity.lazyTick` only wrote `HEAT_LEVEL = NONE` when its `running` flag had been true. A firebox whose blockstate arrived lit (blueprint, schematic, `/setblock`, a moved structure) never had `running` set, so the "stop burning" branch never touched it.
**Fix:** When the firebox cannot burn, the blockstate is put out whenever it is not already `NONE`, whatever `running` says.
**Prevention:** When a blockstate is the source of truth for other machines, reconcile it against the real condition on every update; never gate the write on a flag that only tracks the block's own past writes.

## [2026-10-04 18:40] - Electric pump powered after its pipes were laid never pumped
**Context:** New game test: tank, pipe, electric pump, pipe, tank, then a creative generator placed next to the pump.
**Error:** The pump read 500 V, 2500 W and pressure 1000 in the inspector, yet moved 0 mB.
**Root cause:** It extends Create's `PumpBlockEntity`, which pushes pressure into the pipe network only when its speed or the pipes change (`updatePressureChange` / `updatePipesOnSide`). The electric pump has no speed, so the pressure it computed while unpowered (0) stayed in the pipes when power arrived, and after any later voltage change.
**Fix:** `lazyTick` recomputes the pressure from the power state and calls `updatePressureChange()` when it differs from the last value pushed.
**Prevention:** A Create pump subclass driven by anything but rotation must trigger the pressure redistribution itself whenever its driving input changes.

## [2026-10-04 18:43] - An engine refuelled by pipe after running dry never restarted
**Context:** New end-to-end game test: distillation tower diesel piped into a regular engine that already had its redstone signal.
**Error:** The engine held 4000 mB of diesel, the inspector found nothing wrong, and its shaft stayed at 0 RPM.
**Root cause:** Emptying the fuel tank set `rpm = 0` through `tankUpdated`, but nothing recomputed the rotation when fuel arrived again; only a redstone signal change or an item interaction called `updateRotation`. Any engine switched on before its first fuel, or that ran out and was refilled by a pipe or the piping upgrade, stayed still until the lever was toggled.
**Fix:** `AbstractEngineBlockEntity.tankUpdated` calls `updateRotation` when the fuel tank goes from empty to holding fuel.
**Prevention:** Every state that zeroes a machine's output (empty tank, missing input) needs the matching transition back; test machines in the order a pipe feeds them, not only in the order a player clicks.

## [2026-10-04 18:45] - The inspector told players a complete pumpjack assembles on its own
**Context:** New pumpjack game tests built from the handbook blueprint.
**Error:** With beam, crank, base and deposit all found and the crank turning, the hammer never moved, and the inspector advised "Complete the beam, the crank and the base: it assembles on its own once all three are found".
**Root cause:** The hammer is a Create bearing contraption, which only lifts blocks super-glued to the block above the holder. The ponder scene says so; the blueprint and the inspector did not, and the inspector had no line for an unglued beam.
**Fix:** When everything is found but the hammer is idle, the inspector walks the beam from the holder to the head and to the connector with `SuperGlueEntity.isGlued` and reports the first loose block (`pumpjack.not_glued`, English and French). The game tests glue the beam as players must.
**Prevention:** Inspector advice for a contraption-based machine must cover Super Glue; when a "does nothing" report is reproduced, check the contraption's block list before suspecting the machine.

## [2026-10-04 18:46] - Large engine never powered a shaft placed after it
**Context:** New structure tests that assert every blueprint forms; the large engine blueprints place the engine (bottom layer) before its shaft (two blocks up).
**Error:** `engine_upgrades_6: did not form: [large engine at ... has no powered shaft]`.
**Root cause:** `LargeEngineBlock.onPlace` turns an existing `create:shaft` into a powered shaft, and nothing else ever does: Create's own `ShaftBlock.pickCorrectShaftType` only knows `SteamEngineBlock`. A shaft placed after the engine, as any bottom-up build or blueprint does, stayed a plain shaft and the engine never ran.
**Fix:** `LargeEngineBlockEntity.lazyTick` converts a valid plain shaft at the shaft position when the engine has none. Verified by disabling the fix: both large engine structures fail; with it every structure passes.
**Prevention:** A multiblock that binds a neighbour only in `onPlace` breaks for every other placement order. Re-check the binding from the block entity's lazy tick, and test structures in bottom-up order.

## [2026-10-04 18:46] - Firebox placed or reloaded lit burned forever without fuel
**Context:** Writing distillation and vat schematics, checking what heats them.
**Error:** A firebox with `blaze=fading` and an empty tank kept heating vats and towers indefinitely.
**Root cause:** `lazyTick` only put the flame out `if (wasRunning)`, and `running` is not saved: after a chunk reload, or when placed lit, `running` starts false, so the flame was never cleared.
**Fix:** The flame goes out when the firebox cannot burn and either it was running or its block state still shows a flame.
**Prevention:** When a runtime flag gates a block state change, base the check on the block state itself, or persist the flag; unsaved flags lie after every reload.

## [2026-10-04 18:46] - Coke oven kept doors inside the wall after growing over a smaller oven
**Context:** Reviewing coke oven formation for the 2x2 to 6x6 blueprints.
**Error:** Building the back of a large oven first formed a smaller oven there; once the front column was added, the old controller and its top kept `bottom_on` / `top_on` in the middle of the wall.
**Root cause:** `setBlockStates` only wrote the front column; the detach loop only resets blocks outside the new square.
**Fix:** `setBlockStates` now also resets every non-front block of the square to `casual`. The structure tests assert that no block behind the front shows doors. The inspector's maximum size now reports `cokeOvenMaxSize + 1`, the size ovens really reach.
**Prevention:** When a multiblock grows, rewrite the visual state of every member, not just the ones that change role in the new shape.

## [2026-10-04 18:40] - Machine setters report success only when simulating
**Context:** The structure test fits a mixer blade and electrodes before checking the vats, and counts the inserted items so their drops are not taken for duplicates.
**Error:** `aluminium_2: dismantling duplicated items: [copper_electrode 2 dropped for 0 expected]`.
**Root cause:** `IndustrialMixerBlockEntity.setMixerMode` and `ElectrodeHolderBlockEntity.setElectrode` return true only in simulate mode; the real call always returns false, so nothing was counted.
**Fix:** Simulate first, then apply and count.
**Prevention:** Check what a Create-style `(stack, simulate)` setter returns in each mode before trusting its result.

## [2026-10-04 18:25] - Hoppers pulled the workpiece out of the polarizer and the winding machine
**Context:** Functional game tests feeding each machine from a chest through a hopper and emptying it with a hopper into a chest.
**Error:** The output hopper took the magnetic alloy ingot out of the polarizer, and the unfinished coil out of the winding machine, on its first pass; nothing was ever polarised or wound.
**Root cause:** Both machines keep the workpiece and the product in the same single slot, and both published that slot to automation with extraction open (the winding machine only kept wire in).
**Fix:** The polarizer's item capability only lets out an item no polarizing recipe accepts; the winding machine only lets out a workpiece it has finished (`isWorkpieceDone`: resistor or coil at its target, or no winding recipe left for it).
**Prevention:** A machine whose one slot holds both input and output must gate extraction on "is this the product", like the vat, coke oven and casting basin already do.

## [2026-10-04 18:28] - A hopper could not deliver coke dust to a loaded blast furnace
**Context:** Same game tests, loading a reinforced blast furnace through a hopper on its output block.
**Error:** The furnace held ore and flux but no fuel; the coke dust stayed in the hopper.
**Root cause:** The output block's item handler showed two slots (ore, flux). A hopper only inserts into an empty slot or onto a matching stack, so once both held other items it never offered the dust, although the router would have accepted it as fuel.
**Fix:** The handler shows a third slot holding the fuel counter as a stack of coal coke dust (still extraction-proof), so hoppers see where fuel goes.
**Prevention:** A routing item handler must expose a slot a hopper can match for every kind of item it accepts; test automation with a real hopper, not only with direct `insertItem` calls.

## [2026-10-04 18:36] - The chemical vat ran recipes on dead attachments
**Context:** Game test of a hydrogen vat whose two electrode holders had no generator yet.
**Error:** The inspector listed both holders as "attached but not working", yet the vat showed "Recipe found: Hydrogen, Processing: 33%" and would have made hydrogen with no power. An instant mixing recipe could likewise complete with a stopped mixer.
**Root cause:** `areMachinesValid` was only refreshed by `revalidateMachines()` on the lazy tick, after `evaluate()` had already put the new machines in `machineMap`; a fresh vat starts with it `true`. A recipe matched in that window, and `handleRecipe` never looked at the machines again, so it ran to completion.
**Fix:** `evaluate()` revalidates the machines it just found, and `handleRecipe` pauses while any attachment cannot operate.
**Prevention:** A cached "can run" flag must be refreshed in the same step that changes what it describes, and the processing loop must check it every tick, not only the matcher.

## [2026-10-04 18:44] - A lone chemical vat had half its tank capacity until reloaded
**Context:** Game test making liquid concrete (32000 mB) in a single cast iron vat.
**Error:** "The output has no room for the result" with an empty vat; the inspector showed 4000 mB segments.
**Root cause:** The tank behaviours are built with 4000 mB segments. Only multiblock formation (`applyVatSize`) and `read()` size them to the configured capacity per block (8000 mB by default), so a vat placed alone kept 4000 mB until its chunk reloaded and could never fit a concrete batch.
**Fix:** `initialize()` sizes the controller's segments the same way `read()` does (shared `sizeTanks`).
**Prevention:** Capacity that depends on the multiblock size must be set on every path that creates a controller, not only on formation and load.

## [2026-10-04 20:30] - Refuelling a flamethrower from a tank it emptied voided the fuel
**Context:** New handheld game test: a flamethrower clicked on a Create tank holding 2000 mB of each fuel.
**Error:** The tank went to 0 mB, the click answered SUCCESS, and the flamethrower held the empty fallback fuel. Partial drains (fuel left in the tank) worked, which is why the 1.3.0 engine fix did not show it.
**Root cause:** `FlamethrowerItem.useOn` built the new fuel from `fluidStack.getFluid()` after the drain, and `fluidStack` was the tank's live stack (`getFluidInTank` returns it uncopied). Once the drain took everything, the stack read as empty, `createForType` found no fuel type and returned EMPTY.
**Fix:** The new fuel is built from the fuel type resolved before the drain.
**Prevention:** Never read a `getFluidInTank` result after draining the same handler; copy it or keep the values first. Test a refill that empties the source exactly, not only one that leaves some behind.

## [2026-10-04 20:38] - A flamethrower that ran dry kept its trigger held
**Context:** Handheld game test firing 30 mB of gasoline until empty.
**Error:** The fuel reached empty but the player stayed in use (`isUsingItem`) until the button was released, slowed as when using any item.
**Root cause:** `onUseTick` returned early when the component was the `FlamethrowerFuel.EMPTY` constant, and `decrement` returns that very constant when the last drop is fired, so the `stopUsingItem` branch below was never reached on the server.
**Fix:** Any empty fuel, whatever the instance, stops the use.
**Prevention:** Never decide behaviour by comparing records by identity; use their own `isEmpty()`.

## [2026-10-04 20:31] - Quad potato cannon duplicated ammo with Potato Recovery
**Context:** New handheld game test firing a quad cannon enchanted with Potato Recovery III.
**Error:** All 4 projectiles of one shot carried a recovery chance ("Recovery" saved as 0.5), so one potato could come back up to four times.
**Root cause:** `QuadPotatoCannonItem.use` copies Create's split-shot loop but dropped its `if (i != 0) projectile.recoveryChance = 0;`. The field is protected in Create's package, so the copy could not write it and the line was lost.
**Fix:** A new accessor mixin (`PotatoProjectileEntityAccessor`) clears the recovery chance of every projectile but the first.
**Prevention:** When copying a Create method into TFMG, check every statement that touches a non-public member: it is the one that silently disappears. One ammo spent must never be recoverable more than once.

## [2026-10-04 20:32] - Thermite and zinc grenades turned blue when reloaded
**Context:** New game test saving a thrown grenade of each kind and loading it back, as a chunk unload does.
**Error:** A thermite or zinc grenade came back as a blue (copper) grenade.
**Root cause:** The colour is a final field set by the thrower's constructor and never saved; the entity type constructor, used for every loaded or client-side grenade, hard-coded BLUE.
**Fix:** The type constructor derives the colour from the entity type (`ThermiteGrenade.colorOf`).
**Prevention:** State that is not saved must be derivable from what is: the entity type is always known when an entity is rebuilt.

## [2026-10-04 20:48] - Copper grenade sparks set no fire
**Context:** New game test dropping each kind of spark on the floor.
**Error:** Plain and green sparks set their fire; blue sparks set nothing, so copper grenades burnt nothing.
**Root cause:** In the 1.21 refactor of the sparks into one `Spark` class, `BlueSpark.getFireState` returned `Optional.empty()` (as `LithiumSpark` does) instead of the blue fire 1.20.1 placed.
**Fix:** `BlueSpark` returns `BlueFireBlock.getState` again, and its blue particle trail is back.
**Prevention:** When subclasses are folded into a template method, test each subclass's override, not only the base.

## [2026-10-04 20:49] - Napalm blasts one block off on negative coordinates
**Context:** Reading the napalm potato and napalm bomb while writing their game tests.
**Error:** The fire explosion was centred on `new BlockPos((int) x, (int) y, (int) z)`.
**Root cause:** An `(int)` cast rounds towards zero, so on negative coordinates the blast moved one block towards the origin.
**Fix:** Both use `BlockPos.containing`, which floors.
**Prevention:** Never build a BlockPos from casts of entity coordinates; use `blockPosition()` or `BlockPos.containing`.

## [2026-10-04 20:33] - Lead weapons and the lit lithium blade wore twice as fast
**Context:** New handheld game tests counting durability per hit.
**Error:** A lead sword lost 3 per hit, a lead axe 4, the lit lithium blade 3, where 1.20.1 took 2 each.
**Root cause:** Since 1.21 vanilla wears weapons in `postHurtEnemy` (sword 1, digger 2) and only calls `hurtEnemy` for effects. The ported overrides still wore the item in `hurtEnemy`, so both ran; the lead sword also passed the target's hand slot.
**Fix:** `hurtEnemy` only applies the effects; the lead sword and lit blade wear 2 in `postHurtEnemy`, the lead axe keeps the axe's own 2.
**Prevention:** When porting a 1.20 `hurtEnemy` that wears the item, move the wear to `postHurtEnemy`.

## [2026-10-04 20:34] - TFMG pickaxes, shovels and hoes attacked five times a second
**Context:** New tool tier game tests reading each tool's attribute modifiers.
**Error:** Steel, aluminum and lead pickaxes, shovels and hoes had an attack speed modifier of +1.0 (vanilla tools: -2.8 to -3.0); lead tools even used the axe's helper.
**Root cause:** The 1.21 port replaced the 1.20.1 constructors with `createAttributes(tier, 1, 1)` placeholders.
**Fix:** The 1.20.1 values are back: pickaxe (1, -2.8), shovel (1.5, -3.0), hoe (0, -3.0), each with its own helper.
**Prevention:** A tool's attack speed modifier is always negative; the tier test now checks every TFMG tool's attributes.

## [2026-10-04 20:35] - The oil hammer said nothing on a dedicated server
**Context:** New handheld game test: a server player knocks the ground above a registered oil deposit.
**Error:** No message reached the player.
**Root cause:** The reserves message was sent only on the client (`level.isClientSide`), reading `TFMG.DEPOSITS`, which is filled only on the server. In singleplayer both sides share that static, so it worked there and nowhere else.
**Fix:** The server sends the line (`displayClientMessage` on the server player).
**Prevention:** Data held by a server-side manager must be read and reported on the server; a static shared in singleplayer hides the bug from local testing.

## [2026-10-04 20:36] - Sneak-clicking an engine with an oil can filled its fuel tank
**Context:** New handheld game test sneak-clicking a regular engine with a full oil can and a full cooling fluid bottle.
**Error:** 4000 mB of lubrication oil or cooling fluid went into the engine's fuel tank, which refuses extraction, so the engine was jammed with a fluid it cannot burn.
**Root cause:** The 1.3.0 "sneak pours into any fluid handler" change runs in `onItemUseFirst`, before the engine's own handling, and an engine's capability is its fuel tank, which accepts any fluid.
**Fix:** On an engine the sneak-click empties the can as its tooltip says; oil and coolant still go in with a plain click.
**Prevention:** A generic "pour into any fluid handler" must exclude blocks whose handler is not a general container; check what a capability is before filling it.

## [2026-10-04 20:37] - A block of laminated magnetic alloy burned in furnaces
**Context:** New game test reading the furnace burn time of TFMG items.
**Error:** The laminated magnetic alloy block burned for 28800 ticks.
**Root cause:** Its registration copied the coal coke block's `.item(CoalCokeBlockItem::new)`.
**Fix:** It uses a plain block item.
**Prevention:** When copying a block registration, check the item factory: it carries behaviour (burn time) that a model or tag review does not show.

## [2026-10-04 20:24] - Game test platform floor is at helper y=1, not y=0
**Context:** First run of the handheld tests: torches placed on the floor, shovels, hoes and fired sparks all misbehaved.
**Error:** Clicking the floor at helper y=0 placed nothing or hit the ground below the platform; a pig spawned at y=1 dropped to y=0.
**Root cause:** `StructureUtils.prepareTestStructure` puts the structure block one block below the structure, and `GameTestHelper` coordinates start at the structure block. Template layer 0 (the floor) is therefore helper y=1; `TFMGGameTestUtil` said y=0.
**Fix:** Floor blocks are at y=1, things standing on the floor at y=2; the util comment now says so.
**Prevention:** Before relying on a template's layout, check one known block with `helper.getBlockState` instead of the comment.

## [2026-10-04 20:23] - Calling the multimeter tooltip crashed the game test server
**Context:** Handheld test asking a powered resistor for its multimeter tooltip on the server.
**Error:** `Attempted to load class net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER` and the whole run crashed.
**Root cause:** `IElectric.makeMultimeterTooltip` builds goggle lines (`forGoggles`), which reach client classes; it is only called from client overlays.
**Fix:** The test reads the values the overlay shows (voltage, resistance, current, power) instead.
**Prevention:** Never call a tooltip or goggle builder from server-side test code.
