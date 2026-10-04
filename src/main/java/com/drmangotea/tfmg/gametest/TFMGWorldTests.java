package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.items.blueprint.BlueprintLines;
import com.drmangotea.tfmg.content.items.guide.GuideEvents;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.base.FluidReservoir;
import com.drmangotea.tfmg.content.machinery.oil_processing.surface_scanner.SurfaceScannerBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlock;
import com.drmangotea.tfmg.content.machinery.vat.electrode_holder.electrode.Electrode;
import com.drmangotea.tfmg.recipes.VatMachineRecipe;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGElectrodes;
import com.drmangotea.tfmg.registry.TFMGFluids;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGPaletteStoneTypes;
import com.simibubi.create.content.decoration.palettes.AllPaletteStoneTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The mod in a real world rather than on a test platform: world generation
 * through the registered features and biome modifiers, oil found by the
 * scanner, Factory Blueprints through the real village loot path and the
 * cartographer trade, the first-join handbook, block loot, and a progression
 * check that every TFMG item can be obtained from vanilla and Create
 * resources.
 *
 * World generation runs in prepared areas far from the test grid. Each area
 * is filled with the host rock of the dimension (stone over deepslate, or
 * netherrack), then the registered placed features run through
 * {@link PlacedFeature#place}, the same call chunk decoration makes, so the
 * placement modifiers and the feature code are the real ones. The areas are
 * rebuilt by every run and never meet the test structures.
 *
 * Every figure checked against "the handbook" comes from
 * src/guide/chapters/01_resources.json.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGWorldTests {

    private static final String BATCH = "tfmg_world";
    private static final String PLATFORM = "gametest/platform";
    private static final int MAX_LISTED = 40;

    /** First chunk of the prepared areas: 320000 blocks out, far from spawn and the test grid. */
    private static final int AREA_CHUNK = 20_000;
    private static final int AREA_ORES = 0, AREA_LAYERS = 1, AREA_NETHER = 2, AREA_OIL = 3, AREA_WELL = 4;

    // ================================================================ worldgen

    private record Generation(String name, GenerationStep.Decoration step, TagKey<Biome> biomes) {
    }

    /** Every TFMG feature, where the handbook says it generates, at the step its biome modifier uses. */
    private static final List<Generation> GENERATION = List.of(
            new Generation("lead_ore", GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_OVERWORLD),
            new Generation("nickel_ore", GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_OVERWORLD),
            new Generation("lithium_ore", GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_OVERWORLD),
            new Generation("tfmg_striated_ores_overworld", GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_OVERWORLD),
            new Generation("tfmg_striated_ores_nether", GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_NETHER),
            new Generation("oil_deposit", GenerationStep.Decoration.UNDERGROUND_ORES, BiomeTags.IS_OVERWORLD),
            new Generation("oil_well", GenerationStep.Decoration.FLUID_SPRINGS, BiomeTags.HAS_DESERT_PYRAMID));

    /**
     * Each TFMG placed feature, configured feature and biome modifier is
     * registered and known to this class, and the biome modifiers put each
     * feature in exactly the biomes the handbook names (every Overworld biome,
     * every Nether biome, or the desert pyramid biomes) and nowhere else.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void featuresAreAttachedToTheirBiomes(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        Registry<PlacedFeature> placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        Registry<ConfiguredFeature<?, ?>> configured = access.registryOrThrow(Registries.CONFIGURED_FEATURE);
        Registry<BiomeModifier> modifiers = access.registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
        List<String> problems = new ArrayList<>();

        Set<String> known = new TreeSet<>();
        GENERATION.forEach(g -> known.add(g.name()));
        for (Registry<?> registry : List.of(placed, configured, modifiers))
            for (ResourceLocation id : registry.keySet())
                if (id.getNamespace().equals(TFMG.MOD_ID) && !known.contains(id.getPath()))
                    problems.add(registry.key().location().getPath() + " " + id + " is not covered by this test");

        for (Generation generation : GENERATION) {
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, TFMG.asResource(generation.name()));
            if (!placed.containsKey(key)) {
                problems.add("placed feature " + generation.name() + " is not registered");
                continue;
            }
            if (!configured.containsKey(TFMG.asResource(generation.name())))
                problems.add("configured feature " + generation.name() + " is not registered");
            if (!modifiers.containsKey(TFMG.asResource(generation.name())))
                problems.add("biome modifier " + generation.name() + " is not registered");
            int targets = 0;
            for (Holder.Reference<Biome> biome : biomes.holders().toList()) {
                boolean wanted = biome.is(generation.biomes());
                List<HolderSet<PlacedFeature>> steps = biome.value().getGenerationSettings().features();
                int step = generation.step().ordinal();
                boolean atStep = steps.size() > step && steps.get(step).stream().anyMatch(h -> h.is(key));
                boolean anywhere = steps.stream().anyMatch(set -> set.stream().anyMatch(h -> h.is(key)));
                if (wanted) {
                    targets++;
                    if (!atStep)
                        problems.add(generation.name() + " missing from " + biome.key().location() + " at step " + generation.step());
                } else if (anywhere) {
                    problems.add(generation.name() + " also generates in " + biome.key().location());
                }
            }
            if (targets == 0)
                problems.add(generation.name() + " has no biome to generate in");
        }
        // The handbook: oil wells in deserts, never in plains; the ores in the
        // Overworld only.
        check(problems, hasFeature(biomes, "desert", "oil_well"), "the desert has no oil well");
        check(problems, !hasFeature(biomes, "plains", "oil_well"), "plains have oil wells");
        check(problems, !hasFeature(biomes, "nether_wastes", "lead_ore"), "the Nether has lead ore");
        check(problems, !hasFeature(biomes, "plains", "tfmg_striated_ores_nether"), "the Overworld has Nether layers");
        report(helper, problems, "world generation registration problems");
    }

    private record Ore(String feature, Supplier<Block> stone, Supplier<Block> deepslate, int size, int perChunk, int minY, int maxY) {
    }

    /** Handbook: vein size, attempts per chunk and height range of each ore. */
    private static final List<Ore> ORES = List.of(
            new Ore("lead_ore", TFMGBlocks.LEAD_ORE::get, TFMGBlocks.DEEPSLATE_LEAD_ORE::get, 12, 5, -15, 80),
            new Ore("nickel_ore", TFMGBlocks.NICKEL_ORE::get, TFMGBlocks.DEEPSLATE_NICKEL_ORE::get, 10, 5, -63, 20),
            new Ore("lithium_ore", TFMGBlocks.LITHIUM_ORE::get, TFMGBlocks.DEEPSLATE_LITHIUM_ORE::get, 7, 3, -63, -5));

    /**
     * Lead, nickel and lithium: the configured vein size, the attempts per
     * chunk and the height range match the handbook, and the real placement
     * in sixteen chunks of stone over deepslate fills a sane number of ore
     * blocks, in the right variant for the host rock (deepslate lithium most
     * of the time, as the handbook says).
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void oreVeinsMatchTheHandbook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        List<String> problems = new ArrayList<>();
        ChunkPos2 min = area(AREA_ORES);
        int size = 6;
        for (Ore ore : ORES) {
            PlacedFeature feature = placed(level, ore.feature()).value();
            if (feature.feature().value().config() instanceof OreConfiguration config) {
                check(problems, config.size == ore.size(), ore.feature() + " veins are " + config.size + " blocks, the handbook says " + ore.size());
            } else {
                problems.add(ore.feature() + " is not an ore feature");
            }

            RandomSource random = RandomSource.create(ore.feature().hashCode());
            int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
            boolean wrongCount = false;
            for (int c = 0; c < 1000; c++) {
                List<BlockPos> positions = positions(level, feature, random, new BlockPos(c * 16, 0, 0));
                if (positions.size() != ore.perChunk())
                    wrongCount = true;
                for (BlockPos pos : positions) {
                    lowest = Math.min(lowest, pos.getY());
                    highest = Math.max(highest, pos.getY());
                    if (pos.getX() < c * 16 || pos.getX() >= c * 16 + 16)
                        problems.add(ore.feature() + " placed a vein outside its chunk");
                }
            }
            check(problems, !wrongCount, ore.feature() + " does not try " + ore.perChunk() + " veins per chunk");
            check(problems, lowest >= ore.minY() && highest <= ore.maxY(),
                    ore.feature() + " veins start between Y " + lowest + " and " + highest + ", the handbook says " + ore.minY() + " to " + ore.maxY());
            check(problems, lowest <= ore.minY() + 3 && highest >= ore.maxY() - 3,
                    ore.feature() + " veins only span Y " + lowest + " to " + highest + " of " + ore.minY() + " to " + ore.maxY());

            prepare(level, min, size, 100, y -> y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState());
            RandomSource veins = RandomSource.create(ore.feature().hashCode());
            for (int dx = 1; dx < size - 1; dx++)
                for (int dz = 1; dz < size - 1; dz++)
                    feature.place(level, generator, veins, new BlockPos((min.x() + dx) * 16, 0, (min.z() + dz) * 16));
            Block stone = ore.stone().get(), deepslate = ore.deepslate().get();
            Map<Block, Found> found = census(level, min, size, level.getMinBuildHeight(), 100, s -> s.is(stone) || s.is(deepslate));
            Found inStone = found.getOrDefault(stone, new Found());
            Found inDeepslate = found.getOrDefault(deepslate, new Found());
            int total = inStone.count + inDeepslate.count;
            TFMG.LOGGER.info("[gametest] {}: starts Y {}..{}, {}", ore.feature(), lowest, highest, describe(found));
            int attempts = (size - 2) * (size - 2) * ore.perChunk();
            check(problems, total >= attempts * 2 && total <= attempts * ore.size() * 2,
                    ore.feature() + " placed " + total + " ore blocks for " + attempts + " veins of up to " + ore.size());
            int lowestBlock = Math.min(inStone.minY, inDeepslate.minY);
            int highestBlock = Math.max(inStone.maxY, inDeepslate.maxY);
            check(problems, total == 0 || (lowestBlock >= ore.minY() - 4 && highestBlock <= ore.maxY() + 4),
                    ore.feature() + " blocks between Y " + lowestBlock + " and " + highestBlock + ", outside " + ore.minY() + " to " + ore.maxY());
            check(problems, inStone.count == 0 || inStone.minY >= 0, ore.feature() + " put its stone variant in deepslate at Y " + inStone.minY);
            check(problems, inDeepslate.count == 0 || inDeepslate.maxY < 0, ore.feature() + " put its deepslate variant in stone at Y " + inDeepslate.maxY);
            if (ore.feature().equals("lithium_ore"))
                check(problems, inDeepslate.count >= total * 0.9, "lithium is deepslate lithium ore only " + inDeepslate.count + " times in " + total);
        }
        report(helper, problems, "ore generation problems");
    }

    /**
     * Overworld striated layers: about one chunk in 18, between Y -30 and 70,
     * and the four patterns of the handbook (bauxite, galena, lignite and
     * fireclay with their companion stones) all appear in the clusters.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void overworldLayersMatchTheHandbook(GameTestHelper helper) {
        layers(helper, "tfmg_striated_ores_overworld", -30, 70, AREA_LAYERS, false,
                List.of(TFMGPaletteStoneTypes.BAUXITE.getBaseBlock().get(), TFMGPaletteStoneTypes.GALENA.getBaseBlock().get(),
                        TFMGBlocks.LIGNITE.get(), TFMGBlocks.FIRECLAY.get()),
                List.of(Blocks.SMOOTH_BASALT, Blocks.GRANITE, AllPaletteStoneTypes.ANDESITE.getBaseBlock().get(), Blocks.DRIPSTONE_BLOCK,
                        Blocks.TUFF, AllPaletteStoneTypes.SCORIA.getBaseBlock().get(), AllPaletteStoneTypes.CRIMSITE.getBaseBlock().get(),
                        Blocks.SAND, Blocks.GRAVEL));
    }

    /**
     * Nether layers: about one chunk in 18, between Y 40 and 90, with the
     * sulfur and the fireclay patterns of the handbook.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void netherLayersMatchTheHandbook(GameTestHelper helper) {
        layers(helper, "tfmg_striated_ores_nether", 40, 90, AREA_NETHER, true,
                List.of(TFMGBlocks.SULFUR.get(), TFMGBlocks.FIRECLAY.get()),
                List.of(AllPaletteStoneTypes.SCORCHIA.getBaseBlock().get(), Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.BASALT,
                        Blocks.SMOOTH_BASALT, Blocks.GRAVEL, Blocks.SOUL_SOIL, Blocks.SOUL_SAND));
    }

    private static void layers(GameTestHelper helper, String name, int minY, int maxY, int areaIndex, boolean nether,
                               List<Block> ores, List<Block> companions) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        PlacedFeature feature = placed(level, name).value();
        List<String> problems = new ArrayList<>();

        RandomSource random = RandomSource.create(name.hashCode());
        int chunks = 18_000, clustersPlanned = 0;
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        for (int c = 0; c < chunks; c++)
            for (BlockPos pos : positions(level, feature, random, new BlockPos(c * 16, 0, 0))) {
                clustersPlanned++;
                lowest = Math.min(lowest, pos.getY());
                highest = Math.max(highest, pos.getY());
            }
        // One in 18 of 18000 chunks is 1000 clusters, standard deviation 31.
        check(problems, clustersPlanned >= 850 && clustersPlanned <= 1150,
                name + " planned " + clustersPlanned + " clusters in " + chunks + " chunks, the handbook says one chunk in 18");
        check(problems, lowest >= minY && highest <= maxY && lowest <= minY + 3 && highest >= maxY - 3,
                name + " clusters centred between Y " + lowest + " and " + highest + ", the handbook says " + minY + " to " + maxY);

        ChunkPos2 min = area(areaIndex);
        int size = 8, top = nether ? 120 : 100;
        prepare(level, min, size, top, nether
                ? y -> Blocks.NETHERRACK.defaultBlockState()
                : y -> y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        int clusters = 0;
        for (int dx = 1; dx < size - 1; dx++)
            for (int dz = 1; dz < size - 1; dz++) {
                BlockPos origin = new BlockPos((min.x() + dx) * 16, 0, (min.z() + dz) * 16);
                // One random per chunk, drawn from until the rarity filter
                // passes: consecutive seeds give correlated first draws.
                RandomSource chunkRandom = RandomSource.create(dx * 31L + dz);
                for (int attempt = 0; attempt < 2000; attempt++)
                    if (feature.place(level, generator, chunkRandom, origin)) {
                        clusters++;
                        break;
                    }
            }
        check(problems, clusters == (size - 2) * (size - 2), name + " formed only " + clusters + " clusters in " + (size - 2) * (size - 2) + " chunks");
        Set<Block> wanted = new HashSet<>(ores);
        wanted.addAll(companions);
        Map<Block, Found> found = census(level, min, size, level.getMinBuildHeight(), top, s -> wanted.contains(s.getBlock()));
        int oreBlocks = 0;
        for (Block ore : ores) {
            Found f = found.getOrDefault(ore, new Found());
            oreBlocks += f.count;
            check(problems, f.count > 0, name + " never placed " + BuiltInRegistries.BLOCK.getKey(ore));
            check(problems, f.count == 0 || (f.minY >= minY - 16 && f.maxY <= maxY + 16),
                    name + " put " + BuiltInRegistries.BLOCK.getKey(ore) + " between Y " + f.minY + " and " + f.maxY);
        }
        for (Block companion : companions)
            check(problems, found.getOrDefault(companion, new Found()).count > 0,
                    name + " never placed its companion " + BuiltInRegistries.BLOCK.getKey(companion));
        check(problems, clusters == 0 || (oreBlocks / clusters >= 20 && oreBlocks / clusters <= 6000),
                name + " placed " + oreBlocks + " resource blocks in " + clusters + " clusters");
        TFMG.LOGGER.info("[gametest] {}: {} clusters, {} resource blocks, {}", name, clusters, oreBlocks, describe(found));
        report(helper, problems, "layer generation problems");
    }

    /**
     * Oil deposits through the real placed feature in 256 chunks of bedrock
     * and stone: about one field in 16 chunks, 1 to 6 deposits per field on
     * the bedrock floor at Y -64, crude oil columns at most 24 blocks high and
     * fossilstone beside their lower part. Then a surface scanner standing
     * above the fields lights exactly the chunks that hold oil, and the
     * deposits of one field share one reserve between 1000 and the server
     * maximum, as the handbook says.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void oilFieldsAreFoundByTheScanner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        PlacedFeature feature = placed(level, "oil_deposit").value();
        List<String> problems = new ArrayList<>();
        Block deposit = TFMGBlocks.OIL_DEPOSIT.get();
        Block crude = TFMGFluids.CRUDE_OIL.getSource().defaultFluidState().createLegacyBlock().getBlock();
        Block fossil = TFMGBlocks.FOSSILSTONE.get();
        int bottom = level.getMinBuildHeight();

        ChunkPos2 min = area(AREA_OIL);
        int size = 18;
        prepare(level, min, size, -30, y -> Blocks.STONE.defaultBlockState());
        int fields = 0;
        RandomSource fieldRandom = RandomSource.create(16);
        for (int dx = 1; dx < size - 1; dx++)
            for (int dz = 1; dz < size - 1; dz++) {
                BlockPos origin = new BlockPos((min.x() + dx) * 16, 0, (min.z() + dz) * 16);
                int before = depositsAround(level, origin, deposit);
                if (!feature.place(level, generator, fieldRandom, origin))
                    continue;
                fields++;
                int added = depositsAround(level, origin, deposit) - before;
                check(problems, added >= 1 && added <= 6, "an oil field has " + added + " deposits, the feature makes 1 to 6");
            }
        int chunks = (size - 2) * (size - 2);
        // One in 16 of 256 chunks is 16 fields.
        check(problems, fields >= 6 && fields <= 30, fields + " oil fields in " + chunks + " chunks, the handbook says about one chunk in sixteen");

        Map<Block, Found> found = census(level, min, size, bottom, 64, s -> s.is(deposit) || s.is(crude) || s.is(fossil));
        Found deposits = found.getOrDefault(deposit, new Found());
        Found oil = found.getOrDefault(crude, new Found());
        Found fossils = found.getOrDefault(fossil, new Found());
        TFMG.LOGGER.info("[gametest] oil_deposit: {} fields in {} chunks, {}", fields, chunks, describe(found));
        check(problems, deposits.count > 0 && deposits.minY == bottom && deposits.maxY == bottom,
                "oil deposits between Y " + deposits.minY + " and " + deposits.maxY + ", the handbook says the bedrock floor at Y -64");
        check(problems, oil.count > 0 && oil.minY > bottom && oil.maxY <= bottom + 24,
                "crude oil between Y " + oil.minY + " and " + oil.maxY + ", columns of at most 24 blocks above the deposit");
        check(problems, fossils.count > 0 && fossils.minY > bottom && fossils.maxY <= bottom + 4,
                "fossilstone between Y " + fossils.minY + " and " + fossils.maxY + ", expected beside the lower part of the columns");

        // The scanner, on a chunk whose 5x5 window holds a field.
        int scanFloor = TFMGConfigs.common().machines.surfaceScannerScanDepth.get();
        BlockPos scannerPos = null;
        for (int cx = min.x() + 3; cx < min.x() + size - 3 && scannerPos == null; cx++)
            for (int cz = min.z() + 3; cz < min.z() + size - 3 && scannerPos == null; cz++) {
                int lit = 0;
                for (int x = -2; x <= 2; x++)
                    for (int z = -2; z <= 2; z++)
                        if (holdsOil(level, cx + x, cz + z, deposit, scanFloor))
                            lit++;
                if (lit > 0 && lit < 25)
                    scannerPos = new BlockPos(cx * 16 + 8, 10, cz * 16 + 8);
            }
        if (scannerPos == null) {
            problems.add("no oil field to scan for");
        } else {
            level.setBlock(scannerPos, TFMGBlocks.SURFACE_SCANNER.getDefaultState(), 3);
            if (level.getBlockEntity(scannerPos) instanceof SurfaceScannerBlockEntity scanner) {
                scanner.findDeposits();
                int sx = scannerPos.getX() >> 4, sz = scannerPos.getZ() >> 4;
                for (int x = 0; x < 5; x++)
                    for (int z = 0; z < 5; z++) {
                        boolean expected = holdsOil(level, sx + x - 2, sz + z - 2, deposit, scanFloor);
                        Boolean shown = scanner.grid[x][z];
                        check(problems, shown != null && shown == expected, "the scanner shows chunk " + (sx + x - 2) + "," + (sz + z - 2)
                                + " as " + shown + " but it " + (expected ? "holds" : "does not hold") + " oil");
                    }
            } else {
                problems.add("the surface scanner has no block entity");
            }
            level.removeBlock(scannerPos, false);
        }

        // One field shares one reserve.
        List<BlockPos> field = new ArrayList<>();
        BlockPos first = null;
        for (BlockPos pos : depositPositions(level, min, size, deposit)) {
            if (first == null)
                first = pos;
            if (Math.abs(pos.getX() - first.getX()) < 32 && Math.abs(pos.getZ() - first.getZ()) < 32)
                field.add(pos);
        }
        if (first != null) {
            int max = TFMGConfigs.common().worldgen.depositMaxReserves.get();
            for (BlockPos pos : field)
                TFMG.DEPOSITS.addDeposit(level, pos.asLong());
            FluidReservoir reservoir = TFMG.DEPOSITS.getReservoirFor(first.asLong());
            if (reservoir == null) {
                problems.add("the first deposit has no reserve once a pumpjack registered it");
            } else {
                check(problems, reservoir.oilReserves >= 1000 && reservoir.oilReserves <= max,
                        "a deposit holds " + reservoir.oilReserves + " mB, the handbook says 1000 to " + max);
                for (BlockPos pos : field)
                    check(problems, TFMG.DEPOSITS.getReservoirFor(pos.asLong()) == reservoir,
                            "deposit " + pos.toShortString() + " within 32 blocks of " + first.toShortString() + " has its own reserve");
            }
            for (BlockPos pos : field)
                TFMG.DEPOSITS.removeDeposit(pos.asLong());
        }
        report(helper, problems, "oil field problems");
    }

    /**
     * A reserve is rolled between 1000 and depositMaxReserves, both included,
     * even when a server sets the maximum to its lowest allowed value.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void depositReservesAcceptEveryAllowedMaximum(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(AREA_CHUNK * 16 - 64, level.getMinBuildHeight(), AREA_CHUNK * 16 - 64);
        var setting = TFMGConfigs.common().worldgen.depositMaxReserves;
        int before = setting.get();
        BlockState previous = level.getBlockState(pos);
        List<String> problems = new ArrayList<>();
        try {
            level.setBlock(pos, TFMGBlocks.OIL_DEPOSIT.getDefaultState(), 2 | 16);
            setting.set(1000);
            try {
                TFMG.DEPOSITS.addDeposit(level, pos.asLong());
                FluidReservoir reservoir = TFMG.DEPOSITS.getReservoirFor(pos.asLong());
                check(problems, reservoir != null && reservoir.oilReserves == 1000,
                        "with depositMaxReserves = 1000 the reserve is " + (reservoir == null ? "missing" : reservoir.oilReserves));
            } catch (RuntimeException e) {
                problems.add("registering a deposit with depositMaxReserves = 1000 throws " + e);
            }
        } finally {
            setting.set(before);
            TFMG.DEPOSITS.removeDeposit(pos.asLong());
            level.setBlock(pos, previous, 2 | 16);
        }
        report(helper, problems, "deposit reserve problems");
    }

    /**
     * Oil wells: about one desert chunk in 500, and the real feature raises
     * one oil deposit with a crude oil column from the bedrock to about 70
     * blocks above the surface, with fossilstone in the rock around it.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void oilWellsRiseAboveTheDesert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        PlacedFeature feature = placed(level, "oil_well").value();
        List<String> problems = new ArrayList<>();
        int bottom = level.getMinBuildHeight();

        RandomSource random = RandomSource.create(7);
        int wells = 0, chunks = 100_000;
        for (int c = 0; c < chunks; c++)
            for (BlockPos pos : positions(level, feature, random, new BlockPos(c * 16, 0, 0))) {
                wells++;
                check(problems, pos.getY() == bottom, "an oil well starts at Y " + pos.getY());
            }
        // One in 500 of 100000 chunks is 200 wells, standard deviation 14.
        check(problems, wells >= 150 && wells <= 250, wells + " oil wells in " + chunks + " chunks, the handbook says one in 500");

        ChunkPos2 min = area(AREA_WELL);
        int surface = 64;
        prepare(level, min, 3, surface, y -> y > surface - 4 ? Blocks.SAND.defaultBlockState()
                : y < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState());
        BlockPos origin = new BlockPos((min.x() + 1) * 16, 0, (min.z() + 1) * 16);
        boolean placedWell = false;
        RandomSource wellRandom = RandomSource.create(500);
        for (int attempt = 0; attempt < 50_000 && !placedWell; attempt++)
            placedWell = feature.place(level, generator, wellRandom, origin);
        check(problems, placedWell, "the oil well never generated in 50000 tries");

        Block deposit = TFMGBlocks.OIL_DEPOSIT.get();
        Block crude = TFMGFluids.CRUDE_OIL.getSource().defaultFluidState().createLegacyBlock().getBlock();
        List<BlockPos> deposits = depositPositions(level, min, 3, deposit);
        check(problems, deposits.size() == 1, "the oil well made " + deposits.size() + " deposits");
        if (deposits.size() == 1) {
            BlockPos base = deposits.get(0);
            int y = bottom + 1;
            while (y < level.getMaxBuildHeight() && level.getBlockState(base.atY(y)).is(crude))
                y++;
            int topOfColumn = y - 1;
            int aboveSurface = topOfColumn - surface;
            check(problems, aboveSurface >= 65 && aboveSurface <= 85,
                    "the oil column tops out " + aboveSurface + " blocks above the surface, the handbook says about 70");
            for (int c = topOfColumn + 1; c < Math.min(level.getMaxBuildHeight(), topOfColumn + 20); c++)
                check(problems, !level.getBlockState(base.atY(c)).is(crude), "crude oil above the top of the column at Y " + c);
            Map<Block, Found> fossils = census(level, min, 3, bottom, surface, s -> s.is(TFMGBlocks.FOSSILSTONE.get()));
            check(problems, fossils.containsKey(TFMGBlocks.FOSSILSTONE.get()), "no fossilstone around the oil well");
            // Take the column down so it does not flow over the area later.
            for (int c = bottom + 1; c <= topOfColumn + 1; c++)
                if (level.getBlockState(base.atY(c)).is(crude))
                    level.setBlock(base.atY(c), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        report(helper, problems, "oil well problems");
    }

    // ============================================================= loot

    private static final String[] WORKSHOP_CHESTS = {"village_toolsmith", "village_weaponsmith", "village_armorer",
            "village_mason", "village_cartographer"};
    private static final String[] OTHER_CHESTS = {"village/village_plains_house", "village/village_desert_house",
            "village/village_savanna_house", "village/village_snowy_house", "village/village_taiga_house",
            "village/village_fisher", "village/village_butcher", "village/village_temple", "village/village_tannery",
            "village/village_shepherd", "village/village_fletcher", "simple_dungeon", "abandoned_mineshaft",
            "desert_pyramid", "shipwreck_supply"};

    /**
     * The real vanilla chest tables of the five village workshops, rolled
     * through the server's loot tables with the global loot modifiers applied,
     * each give a blueprint about once in 20 chests, of every line; houses,
     * other village chests and dungeons never do.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void blueprintsComeFromWorkshopChestsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int rolls = 2000, total = 0;
        for (String chest : WORKSHOP_CHESTS) {
            Set<String> lines = new TreeSet<>();
            int[] blank = new int[1];
            int found = blueprints(level, "chests/village/" + chest, rolls, lines, blank);
            total += found;
            // 1 in 20 of 2000 is 100, standard deviation 9.7.
            check(problems, found >= 65 && found <= 140, chest + " gave " + found + " blueprints in " + rolls + " chests, expected about 100");
            check(problems, lines.containsAll(BlueprintLines.LINES), chest + " never gave the lines " + missing(lines));
            check(problems, blank[0] == 0, chest + " gave " + blank[0] + " blank blueprints");
        }
        check(problems, total >= 430 && total <= 570, "the workshops gave " + total + " blueprints in " + rolls * WORKSHOP_CHESTS.length
                + " chests, the changelog says about one in twenty");
        for (String chest : OTHER_CHESTS) {
            int found = blueprints(level, "chests/" + chest, 1000, new HashSet<>(), new int[1]);
            check(problems, found == 0, chest + " gave " + found + " blueprints");
        }
        report(helper, problems, "village blueprint problems");
    }

    /**
     * The master cartographer trade registered through VillagerTradesEvent:
     * a level-5 cartographer offers one lined blueprint for 32 emeralds and a
     * compass, once, and buying it through the trading menu takes exactly the
     * price and hands over a valid blueprint.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void masterCartographerSellsOneBlueprint(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER).setLevel(5));
        MerchantOffer offer = null;
        RandomSource random = RandomSource.create(5);
        for (VillagerTrades.ItemListing listing : VillagerTrades.TRADES.get(VillagerProfession.CARTOGRAPHER).get(5)) {
            MerchantOffer candidate = listing.getOffer(villager, random);
            if (candidate != null && candidate.getResult().is(TFMGItems.FACTORY_BLUEPRINT.get()))
                offer = candidate;
        }
        if (offer == null) {
            villager.discard();
            report(helper, List.of("level 5 cartographers have no blueprint offer"), "trade problems");
            return;
        }
        check(problems, offer.getCostA().is(Items.EMERALD) && offer.getCostA().getCount() == 32,
                "the blueprint costs " + offer.getCostA() + ", expected 32 emeralds");
        check(problems, offer.getCostB().is(Items.COMPASS) && offer.getCostB().getCount() == 1,
                "the second price is " + offer.getCostB() + ", expected a compass");
        check(problems, offer.getMaxUses() == 1, "the blueprint trade has " + offer.getMaxUses() + " uses");
        check(problems, BlueprintLines.lineOf(offer.getResult()) != null, "the offered blueprint has no line");

        villager.getOffers().clear();
        villager.getOffers().add(offer);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MerchantMenu menu = new MerchantMenu(0, player.getInventory(), villager);
        menu.setSelectionHint(0);
        menu.getSlot(0).set(new ItemStack(Items.EMERALD, 40));
        menu.getSlot(1).set(new ItemStack(Items.COMPASS));
        Slot result = menu.getSlot(2);
        ItemStack bought = result.safeTake(1, Integer.MAX_VALUE, player);
        check(problems, bought.is(TFMGItems.FACTORY_BLUEPRINT.get()) && BlueprintLines.lineOf(bought) != null,
                "buying the offer gave " + bought);
        check(problems, menu.getSlot(0).getItem().getCount() == 8, "the trade left " + menu.getSlot(0).getItem() + " of 40 emeralds");
        check(problems, menu.getSlot(1).getItem().isEmpty(), "the trade did not take the compass");
        check(problems, offer.isOutOfStock(), "the blueprint trade can be used again");
        menu.getSlot(0).set(new ItemStack(Items.EMERALD, 32));
        menu.getSlot(1).set(new ItemStack(Items.COMPASS));
        check(problems, result.getItem().isEmpty(), "a second blueprint is on offer: " + result.getItem());
        menu.removed(player);
        villager.discard();
        report(helper, problems, "trade problems");
    }

    /**
     * The first-join gift: a new player gets exactly one handbook, a player
     * who comes back (saved and loaded as the server does on a relog) gets
     * no other one even after losing it, and with the config off a new
     * player gets none. GuideEvents is called directly, as the login event
     * does, with a mock player: logging a mock player in crashes the game test
     * server (see ERROR_LOG).
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void handbookIsGivenOnceOnFirstJoin(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var setting = TFMGConfigs.common().giveHandbookOnFirstJoin;
        boolean before = setting.get();
        Item guide = TFMGItems.FACTORY_GUIDE.get();
        try {
            setting.set(true);
            Player newcomer = helper.makeMockPlayer(GameType.SURVIVAL);
            GuideEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(newcomer));
            check(problems, newcomer.getInventory().countItem(guide) == 1,
                    "a new player has " + newcomer.getInventory().countItem(guide) + " handbooks after joining");
            GuideEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(newcomer));
            check(problems, newcomer.getInventory().countItem(guide) == 1, "a second login event gave another handbook");

            CompoundTag saved = newcomer.saveWithoutId(new CompoundTag());
            Player returning = helper.makeMockPlayer(GameType.SURVIVAL);
            returning.load(saved);
            check(problems, returning.getInventory().countItem(guide) == 1, "the handbook did not survive the relog");
            returning.getInventory().clearContent();
            GuideEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(returning));
            check(problems, returning.getInventory().countItem(guide) == 0, "a returning player who lost the handbook got another");

            setting.set(false);
            Player other = helper.makeMockPlayer(GameType.SURVIVAL);
            GuideEvents.onLogin(new PlayerEvent.PlayerLoggedInEvent(other));
            check(problems, other.getInventory().countItem(guide) == 0, "the handbook was given with giveHandbookOnFirstJoin off");
        } finally {
            setting.set(before);
        }
        report(helper, problems, "handbook gift problems");
    }

    private record OreLoot(Supplier<Block> ore, Supplier<Item> raw) {
    }

    private static final List<OreLoot> ORE_LOOT = List.of(
            new OreLoot(TFMGBlocks.LEAD_ORE::get, TFMGItems.RAW_LEAD::get),
            new OreLoot(TFMGBlocks.DEEPSLATE_LEAD_ORE::get, TFMGItems.RAW_LEAD::get),
            new OreLoot(TFMGBlocks.NICKEL_ORE::get, TFMGItems.RAW_NICKEL::get),
            new OreLoot(TFMGBlocks.DEEPSLATE_NICKEL_ORE::get, TFMGItems.RAW_NICKEL::get),
            new OreLoot(TFMGBlocks.LITHIUM_ORE::get, TFMGItems.RAW_LITHIUM::get),
            new OreLoot(TFMGBlocks.DEEPSLATE_LITHIUM_ORE::get, TFMGItems.RAW_LITHIUM::get));

    /**
     * The naturally generated blocks drop what the handbook says: each ore
     * its raw item with a pickaxe (more with Fortune) and itself with Silk
     * Touch, and needs at least a stone pickaxe; bauxite, galena, lignite,
     * sulfur and fossilstone drop themselves; fireclay one fireclay ball and
     * is dug with a shovel; fossilstone is twice as hard as obsidian; the oil
     * deposit cannot be mined, blown up or moved and drops nothing.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void worldgenBlocksDropWhatTheHandbookSays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        ItemStack pickaxe = new ItemStack(Items.NETHERITE_PICKAXE);
        ItemStack silk = enchanted(level, Items.NETHERITE_PICKAXE, Enchantments.SILK_TOUCH, 1);
        ItemStack fortune = enchanted(level, Items.NETHERITE_PICKAXE, Enchantments.FORTUNE, 3);
        int rolls = 300;
        for (OreLoot loot : ORE_LOOT) {
            Block ore = loot.ore().get();
            Item raw = loot.raw().get();
            String name = BuiltInRegistries.BLOCK.getKey(ore).getPath();
            BlockState state = ore.defaultBlockState();
            int plain = 0, rich = 0, most = 0;
            for (int i = 0; i < rolls; i++) {
                List<ItemStack> drops = drops(level, state, pickaxe);
                int raws = count(drops, raw);
                plain += raws;
                check(problems, raws == 1 && count(drops, ore.asItem()) == 0, name + " mined with a pickaxe dropped " + drops);
                List<ItemStack> silky = drops(level, state, silk);
                check(problems, count(silky, ore.asItem()) == 1 && count(silky, raw) == 0, name + " mined with Silk Touch dropped " + silky);
                int lucky = count(drops(level, state, fortune), raw);
                rich += lucky;
                most = Math.max(most, lucky);
            }
            // Fortune III with the ore_drops formula averages 2.2 per block, at most 4.
            check(problems, rich >= plain * 1.8 && most <= 4,
                    name + " with Fortune III gave " + rich + " raw items in " + rolls + " blocks against " + plain + " without, at most " + most);
            check(problems, state.is(BlockTags.MINEABLE_WITH_PICKAXE) && state.requiresCorrectToolForDrops()
                            && new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(state)
                            && !new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),
                    name + " does not need exactly a stone pickaxe or better");
        }
        for (Block block : List.of(TFMGPaletteStoneTypes.BAUXITE.getBaseBlock().get(), TFMGPaletteStoneTypes.GALENA.getBaseBlock().get(),
                TFMGBlocks.LIGNITE.get(), TFMGBlocks.SULFUR.get(), TFMGBlocks.FOSSILSTONE.get())) {
            List<ItemStack> drops = drops(level, block.defaultBlockState(), pickaxe);
            check(problems, drops.size() == 1 && drops.get(0).is(block.asItem()) && drops.get(0).getCount() == 1,
                    BuiltInRegistries.BLOCK.getKey(block).getPath() + " dropped " + drops + " instead of itself");
        }
        BlockState fireclay = TFMGBlocks.FIRECLAY.getDefaultState();
        for (ItemStack tool : List.of(new ItemStack(Items.NETHERITE_SHOVEL), enchanted(level, Items.NETHERITE_SHOVEL, Enchantments.SILK_TOUCH, 1))) {
            List<ItemStack> drops = drops(level, fireclay, tool);
            check(problems, drops.size() == 1 && drops.get(0).is(TFMGItems.FIRECLAY_BALL.get()) && drops.get(0).getCount() == 1,
                    "fireclay dug with " + tool + " dropped " + drops + ", the handbook says one fireclay ball");
        }
        check(problems, fireclay.is(BlockTags.MINEABLE_WITH_SHOVEL), "fireclay is not dug with a shovel");
        BlockState fossilstone = TFMGBlocks.FOSSILSTONE.getDefaultState();
        float obsidian = Blocks.OBSIDIAN.defaultBlockState().getDestroySpeed(level, BlockPos.ZERO);
        check(problems, fossilstone.getDestroySpeed(level, BlockPos.ZERO) >= obsidian * 2 && fossilstone.is(BlockTags.MINEABLE_WITH_PICKAXE),
                "fossilstone hardness " + fossilstone.getDestroySpeed(level, BlockPos.ZERO) + ", the handbook says twice obsidian's " + obsidian);
        BlockState deposit = TFMGBlocks.OIL_DEPOSIT.getDefaultState();
        // 69696969 hardness: a netherite pickaxe would take years of real time.
        float hardness = deposit.getDestroySpeed(level, BlockPos.ZERO);
        check(problems, hardness < 0 || hardness >= 1_000_000, "the oil deposit can be mined (hardness " + hardness + ")");
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        for (Direction direction : Direction.values())
            check(problems, !PistonBaseBlock.isPushable(deposit, level, at, direction, true, direction),
                    "a piston can push the oil deposit " + direction);
        check(problems, TFMGBlocks.OIL_DEPOSIT.get().getExplosionResistance() >= Blocks.BEDROCK.getExplosionResistance(),
                "the oil deposit can be blown up (resistance " + TFMGBlocks.OIL_DEPOSIT.get().getExplosionResistance() + ")");
        check(problems, drops(level, deposit, silk).isEmpty() && drops(level, deposit, pickaxe).isEmpty(), "the oil deposit drops something");
        report(helper, problems, "worldgen block loot problems");
    }

    /**
     * Every TFMG block with a loot table drops something when broken with its
     * tool (or with Silk Touch for blocks that need it), and rolling its
     * table never throws.
     */
    @GameTest(template = PLATFORM, batch = BATCH)
    public static void everyBlockDropsWithItsTool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        for (Block block : TFMGGameTestUtil.tfmgBlocks()) {
            if (block instanceof LiquidBlock || block instanceof BaseFireBlock || block == TFMGBlocks.OIL_DEPOSIT.get())
                continue; // fluids and fires drop nothing; the deposit is checked above
            if (block.getLootTable() == BuiltInLootTables.EMPTY)
                continue;
            BlockState state = block.defaultBlockState();
            String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
            try {
                boolean any = false;
                for (ItemStack tool : List.of(tool(state), enchanted(level, tool(state).getItem() == Items.AIR
                        ? Items.NETHERITE_PICKAXE : tool(state).getItem(), Enchantments.SILK_TOUCH, 1)))
                    for (int i = 0; i < 10 && !any; i++)
                        any = !drops(level, state, tool).isEmpty();
                check(problems, any, name + " drops nothing with its tool or Silk Touch");
            } catch (RuntimeException e) {
                problems.add(name + " loot throws " + e);
            }
        }
        report(helper, problems, "blocks with broken loot");
    }

    // ======================================================= progression

    /**
     * Starting from vanilla and Create items, the crude oil of the world and
     * what the naturally generated TFMG blocks drop, follow every recipe whose
     * inputs and machines are already obtainable until nothing new appears.
     * Every TFMG item must end up obtainable. A recipe made in a TFMG machine
     * also needs that machine, and an electric one needs a generator: this is
     * what catches the dead ends the 1.3.0 magnet and aluminium recipes fixed
     * (the polarizer needing a magnet that only the polarizer made).
     */
    @GameTest(template = PLATFORM, batch = BATCH, timeoutTicks = 200)
    public static void everyTfmgItemIsObtainable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        List<String> problems = new ArrayList<>();
        Set<Item> items = new HashSet<>();
        Set<Fluid> fluids = new HashSet<>();

        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals("minecraft") && vanillaBase(id.getPath()))
                items.add(item);
            if (id.getNamespace().equals("create") && createBase(id.getPath()))
                items.add(item);
        }
        for (Fluid fluid : BuiltInRegistries.FLUID)
            if (BuiltInRegistries.FLUID.getKey(fluid).getNamespace().equals("create"))
                fluids.add(still(fluid));
        fluids.add(Fluids.WATER);
        fluids.add(Fluids.LAVA);
        // Crude oil stands in the world above every deposit and in oil wells.
        fluids.add(still(TFMGFluids.CRUDE_OIL.getSource()));
        // Village workshop chests and master cartographers (tested above).
        items.add(TFMGItems.FACTORY_BLUEPRINT.get());

        Map<Item, Set<Item>> blockDrops = blockDrops(level);
        for (Block block : List.of(TFMGBlocks.LEAD_ORE.get(), TFMGBlocks.DEEPSLATE_LEAD_ORE.get(), TFMGBlocks.NICKEL_ORE.get(),
                TFMGBlocks.DEEPSLATE_NICKEL_ORE.get(), TFMGBlocks.LITHIUM_ORE.get(), TFMGBlocks.DEEPSLATE_LITHIUM_ORE.get(),
                TFMGPaletteStoneTypes.BAUXITE.getBaseBlock().get(), TFMGPaletteStoneTypes.GALENA.getBaseBlock().get(),
                TFMGBlocks.LIGNITE.get(), TFMGBlocks.FIRECLAY.get(), TFMGBlocks.SULFUR.get(), TFMGBlocks.FOSSILSTONE.get()))
            items.addAll(blockDrops.getOrDefault(block.asItem(), Set.of()));

        List<Step> steps = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getServer().getRecipeManager().getRecipes()) {
            Step step = step(holder, access, problems);
            if (step != null)
                steps.add(step);
        }

        Item generator = TFMGBlocks.GENERATOR.asItem(), rotor = TFMGBlocks.ROTOR.asItem(), stator = TFMGBlocks.STATOR.asItem();
        Fluid air = still(TFMGFluids.AIR.getSource()), exhaust = still(TFMGFluids.CARBON_DIOXIDE.getSource());
        Map<String, Step> firstMaker = new LinkedHashMap<>();
        int before;
        do {
            before = items.size() + fluids.size();
            boolean power = items.contains(generator) || (items.contains(rotor) && items.contains(stator));
            for (Step step : steps) {
                if (step.done || !step.ready(items, fluids, power))
                    continue;
                step.done = true;
                for (Item out : step.outItems)
                    if (items.add(out))
                        firstMaker.putIfAbsent(BuiltInRegistries.ITEM.getKey(out).toString(), step);
                fluids.addAll(step.outFluids);
            }
            for (Item item : List.copyOf(items)) {
                items.addAll(blockDrops.getOrDefault(item, Set.of()));
                FluidUtil.getFluidContained(new ItemStack(item)).ifPresent(f -> fluids.add(still(f.getFluid())));
            }
            for (Fluid fluid : List.copyOf(fluids)) {
                Item bucket = fluid.getBucket();
                if (bucket != Items.AIR)
                    items.add(bucket);
            }
            // Machines that make a fluid without a recipe.
            if (items.contains(TFMGBlocks.AIR_INTAKE.asItem()))
                fluids.add(air);
            if (items.contains(TFMGBlocks.FIREBOX.asItem()))
                fluids.add(exhaust);
            inWorld(items, fluids);
        } while (items.size() + fluids.size() != before);

        List<String> unreachable = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id.getNamespace().equals(TFMG.MOD_ID) && !items.contains(item) && !CREATIVE_ONLY.contains(id.getPath())
                    && !WORLD_ONLY.contains(id.getPath()))
                unreachable.add(id.getPath());
        }
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id.getNamespace().equals(TFMG.MOD_ID) && fluid.isSource(fluid.defaultFluidState()) && !fluids.contains(fluid))
                unreachable.add("fluid " + id.getPath());
        }
        if (!unreachable.isEmpty())
            problems.add(unreachable.size() + " TFMG items or fluids cannot be obtained in survival: " + unreachable);
        for (String path : CREATIVE_ONLY)
            if (items.contains(BuiltInRegistries.ITEM.get(TFMG.asResource(path))))
                problems.add(path + " is listed as creative only but can be made by " + firstMaker.get(TFMG.asResource(path).toString()));
        report(helper, problems, "progression problems");
    }

    /** Items that exist for creative mode and command use only. */
    private static final Set<String> CREATIVE_ONLY = Set.of("creative_generator", "debug_cinderblock");

    /** Blocks that only world generation places and that never drop (handbook: the oil deposit). */
    private static final Set<String> WORLD_ONLY = Set.of("oil_deposit");

    /** Rebar block, and the block it becomes once liquid concrete has set in it. */
    private static final Map<String, String> REBAR_SETS = Map.of(
            "rebar_block", "rebar_concrete", "rebar_floor", "rebar_concrete_floor", "rebar_pillar", "rebar_concrete_pillar",
            "rebar_stairs", "rebar_concrete_stairs", "rebar_wall", "rebar_concrete_wall");

    /** Shafts and cogwheels a TFMG casing can be put on, as the encased block's id suffix. */
    private static final Map<String, String> ENCASABLE = Map.of(
            "create:shaft", "shaft", "tfmg:steel_cogwheel", "steel_cogwheel", "tfmg:large_steel_cogwheel", "large_steel_cogwheel",
            "tfmg:aluminum_cogwheel", "aluminum_cogwheel", "tfmg:large_aluminum_cogwheel", "large_aluminum_cogwheel");

    /**
     * What players make in the world without a recipe: liquid concrete and
     * liquid asphalt set into blocks, concrete sets inside rebar, a casing
     * right-clicked on a shaft or cogwheel encases it, and a lithium blade
     * is lit by using it.
     */
    private static void inWorld(Set<Item> items, Set<Fluid> fluids) {
        if (fluids.contains(still(TFMGFluids.LIQUID_CONCRETE.getSource()))) {
            items.add(TFMGBlocks.CONCRETE.block.get().asItem());
            REBAR_SETS.forEach((rebar, set) -> {
                if (items.contains(item(TFMG.asResource(rebar))))
                    items.add(item(TFMG.asResource(set)));
            });
        }
        if (fluids.contains(still(TFMGFluids.LIQUID_ASPHALT.getSource())))
            items.add(TFMGBlocks.ASPHALT.get().asItem());
        for (Map.Entry<String, String> casing : Map.of("steel_casing", "steel_encased_", "heavy_machinery_casing", "heavy_casing_encased_").entrySet()) {
            if (!items.contains(item(TFMG.asResource(casing.getKey()))))
                continue;
            ENCASABLE.forEach((part, suffix) -> {
                if (items.contains(item(ResourceLocation.parse(part))))
                    items.add(item(TFMG.asResource(casing.getValue() + suffix)));
            });
        }
        if (items.contains(TFMGItems.LITHIUM_BLADE.get()))
            items.add(TFMGItems.LIT_LITHIUM_BLADE.get());
    }

    private static Item item(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR)
            throw new IllegalStateException("no item " + id);
        return item;
    }

    private static final class Step {
        final String id;
        final List<Set<Item>> items = new ArrayList<>();
        final List<Set<Fluid>> fluids = new ArrayList<>();
        final List<Set<Item>> machines = new ArrayList<>();
        boolean power;
        final List<Item> outItems = new ArrayList<>();
        final List<Fluid> outFluids = new ArrayList<>();
        boolean done;

        Step(String id) {
            this.id = id;
        }

        boolean ready(Set<Item> have, Set<Fluid> haveFluids, boolean powered) {
            if (power && !powered)
                return false;
            for (Set<Item> anyOf : items)
                if (anyOf.stream().noneMatch(have::contains))
                    return false;
            for (Set<Item> anyOf : machines)
                if (anyOf.stream().noneMatch(have::contains))
                    return false;
            for (Set<Fluid> anyOf : fluids)
                if (anyOf.stream().noneMatch(haveFluids::contains))
                    return false;
            return true;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private static Step step(RecipeHolder<?> holder, RegistryAccess access, List<String> problems) {
        Recipe<?> recipe = holder.value();
        // Create flags every processing recipe as "special" to keep it out of
        // the recipe book, so only skip the code-defined crafting recipes.
        if (recipe instanceof CustomRecipe || recipe instanceof SmithingRecipe)
            return null; // no TFMG item comes out of either
        Step step = new Step(holder.id().toString());
        if (recipe instanceof SequencedAssemblyRecipe sequenced) {
            step.items.add(items(sequenced.getIngredient()));
            Item transitional = sequenced.getTransitionalItem().getItem();
            for (SequencedRecipe<?> sequence : sequenced.getSequence()) {
                ProcessingRecipe<?, ?> part = sequence.getRecipe();
                for (Ingredient ingredient : part.getIngredients()) {
                    Set<Item> anyOf = items(ingredient);
                    if (!ingredient.isEmpty() && !anyOf.contains(transitional))
                        step.items.add(anyOf);
                }
                for (SizedFluidIngredient fluid : part.getFluidIngredients())
                    step.fluids.add(fluids(fluid));
            }
            step.outItems.add(sequenced.getResultItem(access).getItem());
            // The half-made item exists in the world between the steps.
            step.outItems.add(transitional);
            return step;
        }
        for (Ingredient ingredient : recipe.getIngredients())
            if (!ingredient.isEmpty())
                step.items.add(items(ingredient));
        if (recipe instanceof ProcessingRecipe<?, ?> processing) {
            for (SizedFluidIngredient fluid : processing.getFluidIngredients())
                step.fluids.add(fluids(fluid));
            for (ProcessingOutput output : processing.getRollableResults())
                step.outItems.add(output.getStack().getItem());
            for (FluidStack fluid : processing.getFluidResults())
                step.outFluids.add(still(fluid.getFluid()));
        } else {
            ItemStack result = recipe.getResultItem(access);
            if (!result.isEmpty())
                step.outItems.add(result.getItem());
        }
        machines(step, recipe, problems);
        step.outItems.remove(Items.AIR);
        return step;
    }

    /** The TFMG machine each TFMG recipe type runs in, as its JEI catalysts list it. */
    private static void machines(Step step, Recipe<?> recipe, List<String> problems) {
        ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (type == null || !type.getNamespace().equals(TFMG.MOD_ID))
            return; // vanilla and Create machines are part of the starting set
        switch (type.getPath()) {
            case "coking" -> step.machines.add(Set.of(TFMGBlocks.COKE_OVEN.asItem()));
            case "industrial_blasting" -> {
                step.machines.add(Set.of(TFMGBlocks.BLAST_FURNACE_OUTPUT.asItem()));
                step.machines.add(Set.of(TFMGBlocks.FIREPROOF_BRICKS.asItem()));
            }
            case "casting" -> step.machines.add(Set.of(TFMGBlocks.CASTING_BASIN.asItem()));
            case "distillation" -> {
                step.machines.add(Set.of(TFMGBlocks.STEEL_DISTILLATION_CONTROLLER.asItem()));
                step.machines.add(Set.of(TFMGBlocks.STEEL_DISTILLATION_OUTPUT.asItem()));
            }
            case "winding" -> step.machines.add(Set.of(TFMGBlocks.WINDING_MACHINE.asItem()));
            case "hot_blast" -> step.machines.add(Set.of(TFMGBlocks.BLAST_STOVE.asItem()));
            case "polarizing" -> {
                step.machines.add(Set.of(TFMGBlocks.POLARIZER.asItem()));
                step.power = true;
            }
            case "vat_machine_recipe" -> {
                VatMachineRecipe vat = (VatMachineRecipe) recipe;
                Set<Item> vats = new HashSet<>();
                for (Block block : TFMGGameTestUtil.tfmgBlocks())
                    if (block instanceof VatBlock vatBlock && vat.allowedVatTypes.contains(vatBlock.vatType))
                        vats.add(block.asItem());
                if (vats.isEmpty())
                    problems.add(step.id + " allows no existing vat: " + vat.allowedVatTypes);
                step.machines.add(vats);
                for (String machine : new LinkedHashSet<>(vat.machines)) {
                    switch (machine) {
                        case "tfmg:mixing" -> {
                            step.machines.add(Set.of(TFMGBlocks.INDUSTRIAL_MIXER.asItem()));
                            step.machines.add(Set.of(TFMGItems.MIXER_BLADE.get()));
                        }
                        case "tfmg:centrifuge" -> {
                            step.machines.add(Set.of(TFMGBlocks.INDUSTRIAL_MIXER.asItem()));
                            step.machines.add(Set.of(TFMGItems.CENTRIFUGE.get()));
                        }
                        case "tfmg:electrode", "tfmg:graphite_electrode" -> {
                            Set<Item> electrodes = new HashSet<>();
                            for (Electrode electrode : List.of(TFMGElectrodes.copper.get(), TFMGElectrodes.zinc.get(), TFMGElectrodes.graphite.get()))
                                if (machine.equals(electrode.getOperationId()) && electrode.getItem() != null)
                                    electrodes.add(electrode.getItem().get());
                            if (electrodes.isEmpty())
                                problems.add(step.id + " needs " + machine + " but no electrode provides it");
                            step.machines.add(Set.of(TFMGBlocks.ELECTRODE_HOLDER.asItem()));
                            step.machines.add(electrodes);
                            step.power = true;
                        }
                        case "tfmg:freezing" -> {
                            step.machines.add(Set.of(TFMGBlocks.FREEZER.asItem()));
                            step.power = true;
                        }
                        case "tfmg:pressurising" -> step.machines.add(Set.of(TFMGBlocks.COMPRESSOR.asItem()));
                        default -> problems.add(step.id + " needs an unknown vat machine " + machine);
                    }
                }
            }
            default -> problems.add(step.id + " is a TFMG recipe type this test does not know the machine of: " + type);
        }
    }

    /** What every TFMG block can drop, broken with each tool, with and without Silk Touch. */
    private static Map<Item, Set<Item>> blockDrops(ServerLevel level) {
        Map<Item, Set<Item>> drops = new HashMap<>();
        List<ItemStack> tools = List.of(ItemStack.EMPTY, new ItemStack(Items.NETHERITE_PICKAXE), new ItemStack(Items.NETHERITE_SHOVEL),
                new ItemStack(Items.NETHERITE_AXE), new ItemStack(Items.NETHERITE_HOE), new ItemStack(Items.SHEARS),
                enchanted(level, Items.NETHERITE_PICKAXE, Enchantments.SILK_TOUCH, 1),
                enchanted(level, Items.NETHERITE_SHOVEL, Enchantments.SILK_TOUCH, 1),
                enchanted(level, Items.NETHERITE_AXE, Enchantments.SILK_TOUCH, 1));
        for (Block block : TFMGGameTestUtil.tfmgBlocks()) {
            Item item = block.asItem();
            if (item == Items.AIR)
                continue;
            Set<Item> out = drops.computeIfAbsent(item, i -> new HashSet<>());
            for (ItemStack tool : tools)
                for (int i = 0; i < 5; i++) {
                    try {
                        for (ItemStack stack : drops(level, block.defaultBlockState(), tool))
                            out.add(stack.getItem());
                    } catch (RuntimeException e) {
                        break;
                    }
                }
        }
        return drops;
    }

    private static boolean vanillaBase(String path) {
        return !(path.equals("air") || path.equals("barrier") || path.contains("command_block") || path.startsWith("structure_")
                || path.equals("jigsaw") || path.equals("debug_stick") || path.equals("knowledge_book") || path.endsWith("_spawn_egg")
                || path.equals("light") || path.equals("bedrock") || path.equals("spawner") || path.equals("trial_spawner")
                || path.equals("vault") || path.equals("reinforced_deepslate") || path.equals("budding_amethyst")
                || path.equals("petrified_oak_slab") || path.equals("end_portal_frame"));
    }

    /** Create's own items, without its creative ones and the crushed ores it only lists for other mods. */
    private static boolean createBase(String path) {
        if (path.contains("creative") || path.equals("handheld_worldshaper"))
            return false;
        if (path.startsWith("crushed_raw_"))
            return Set.of("crushed_raw_iron", "crushed_raw_gold", "crushed_raw_copper", "crushed_raw_zinc").contains(path);
        return true;
    }

    private static Set<Item> items(Ingredient ingredient) {
        Set<Item> out = new HashSet<>();
        for (ItemStack stack : ingredient.getItems())
            if (!stack.is(Items.BARRIER))
                out.add(stack.getItem());
        return out;
    }

    private static Set<Fluid> fluids(SizedFluidIngredient ingredient) {
        Set<Fluid> out = new HashSet<>();
        for (FluidStack stack : ingredient.getFluids())
            out.add(still(stack.getFluid()));
        return out;
    }

    private static Fluid still(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    // ========================================================== helpers

    private record ChunkPos2(int x, int z) {
    }

    private static final class Found {
        int count;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;

        @Override
        public String toString() {
            return count + " (Y " + minY + ".." + maxY + ")";
        }
    }

    private static ChunkPos2 area(int index) {
        return new ChunkPos2(AREA_CHUNK + index * 32, AREA_CHUNK);
    }

    private static Holder<PlacedFeature> placed(ServerLevel level, String name) {
        return level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .getHolderOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, TFMG.asResource(name)));
    }

    private static boolean hasFeature(Registry<Biome> biomes, String biome, String feature) {
        Biome value = biomes.get(ResourceLocation.withDefaultNamespace(biome));
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, TFMG.asResource(feature));
        return value != null && value.getGenerationSettings().features().stream().anyMatch(set -> set.stream().anyMatch(h -> h.is(key)));
    }

    /** The positions a placed feature's modifiers pick for one chunk, without placing anything. */
    private static List<BlockPos> positions(ServerLevel level, PlacedFeature feature, RandomSource random, BlockPos origin) {
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(feature));
        List<BlockPos> current = List.of(origin);
        for (PlacementModifier modifier : feature.placement()) {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos pos : current)
                modifier.getPositions(context, random, pos).forEach(next::add);
            current = next;
        }
        return current;
    }

    /**
     * Rebuilds a square of chunks as fresh terrain: bedrock at the floor, the
     * given rock up to {@code top}, air for 48 blocks above. Sections are
     * written directly like world generation does (no light, no block
     * updates), and the heightmaps the features read are recomputed.
     */
    private static void prepare(ServerLevel level, ChunkPos2 min, int size, int top, IntFunction<BlockState> rock) {
        int bottom = level.getMinBuildHeight();
        int ceiling = Math.min(level.getMaxBuildHeight() - 1, top + 48);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = 0; dx < size; dx++)
            for (int dz = 0; dz < size; dz++) {
                LevelChunk chunk = level.getChunk(min.x() + dx, min.z() + dz);
                for (BlockPos pos : List.copyOf(chunk.getBlockEntities().keySet()))
                    chunk.removeBlockEntity(pos);
                for (int y = bottom; y <= ceiling; y++) {
                    BlockState state = y == bottom ? Blocks.BEDROCK.defaultBlockState() : y <= top ? rock.apply(y) : air;
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    if (state.isAir() && section.hasOnlyAir())
                        continue;
                    int local = SectionPos.sectionRelative(y);
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++)
                            section.setBlockState(x, local, z, state, false);
                }
                Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG,
                        Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.MOTION_BLOCKING,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
            }
    }

    private static Map<Block, Found> census(ServerLevel level, ChunkPos2 min, int size, int fromY, int toY, Predicate<BlockState> wanted) {
        Map<Block, Found> found = new HashMap<>();
        for (int dx = 0; dx < size; dx++)
            for (int dz = 0; dz < size; dz++) {
                LevelChunk chunk = level.getChunk(min.x() + dx, min.z() + dz);
                for (int y = fromY; y <= toY; y++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                    if (section.hasOnlyAir())
                        continue;
                    int local = SectionPos.sectionRelative(y);
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++) {
                            BlockState state = section.getBlockState(x, local, z);
                            if (!wanted.test(state))
                                continue;
                            Found f = found.computeIfAbsent(state.getBlock(), b -> new Found());
                            f.count++;
                            f.minY = Math.min(f.minY, y);
                            f.maxY = Math.max(f.maxY, y);
                        }
                }
            }
        return found;
    }

    private static String describe(Map<Block, Found> found) {
        Map<String, String> out = new TreeMap<>();
        found.forEach((block, f) -> out.put(BuiltInRegistries.BLOCK.getKey(block).getPath(), f.toString()));
        return out.toString();
    }

    private static int depositsAround(ServerLevel level, BlockPos origin, Block deposit) {
        int found = 0;
        int y = level.getMinBuildHeight();
        for (int x = origin.getX() - 16; x < origin.getX() + 32; x++)
            for (int z = origin.getZ() - 16; z < origin.getZ() + 32; z++)
                if (level.getBlockState(new BlockPos(x, y, z)).is(deposit))
                    found++;
        return found;
    }

    private static List<BlockPos> depositPositions(ServerLevel level, ChunkPos2 min, int size, Block deposit) {
        List<BlockPos> out = new ArrayList<>();
        int y = level.getMinBuildHeight();
        for (int x = min.x() * 16; x < (min.x() + size) * 16; x++)
            for (int z = min.z() * 16; z < (min.z() + size) * 16; z++)
                if (level.getBlockState(new BlockPos(x, y, z)).is(deposit))
                    out.add(new BlockPos(x, y, z));
        return out;
    }

    /** What the surface scanner should report for a chunk: an oil deposit or crude oil between its floor and Y 64. */
    private static boolean holdsOil(ServerLevel level, int chunkX, int chunkZ, Block deposit, int floor) {
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        for (int y = Math.max(floor, level.getMinBuildHeight()); y <= 64; y++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            if (section.hasOnlyAir())
                continue;
            int local = SectionPos.sectionRelative(y);
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    BlockState state = section.getBlockState(x, local, z);
                    if (state.is(deposit) || state.getFluidState().getType().isSame(TFMGFluids.CRUDE_OIL.getSource()))
                        return true;
                }
        }
        return false;
    }

    private static int blueprints(ServerLevel level, String table, int rolls, Set<String> lines, int[] blank) {
        LootTable loot = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace(table)));
        if (loot == LootTable.EMPTY)
            throw new IllegalStateException("no loot table " + table);
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        int count = 0;
        for (int i = 0; i < rolls; i++)
            for (ItemStack stack : loot.getRandomItems(params))
                if (stack.is(TFMGItems.FACTORY_BLUEPRINT.get())) {
                    count += stack.getCount();
                    String line = BlueprintLines.lineOf(stack);
                    if (line == null)
                        blank[0]++;
                    else
                        lines.add(line);
                }
        return count;
    }

    private static List<String> missing(Set<String> lines) {
        List<String> out = new ArrayList<>(BlueprintLines.LINES);
        out.removeAll(lines);
        return out;
    }

    private static List<ItemStack> drops(ServerLevel level, BlockState state, ItemStack tool) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.TOOL, tool);
        return state.getDrops(builder);
    }

    private static int count(List<ItemStack> stacks, Item item) {
        int n = 0;
        for (ItemStack stack : stacks)
            if (stack.is(item))
                n += stack.getCount();
        return n;
    }

    private static ItemStack tool(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE))
            return new ItemStack(Items.NETHERITE_PICKAXE);
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL))
            return new ItemStack(Items.NETHERITE_SHOVEL);
        if (state.is(BlockTags.MINEABLE_WITH_AXE))
            return new ItemStack(Items.NETHERITE_AXE);
        if (state.is(BlockTags.MINEABLE_WITH_HOE))
            return new ItemStack(Items.NETHERITE_HOE);
        return ItemStack.EMPTY;
    }

    private static ItemStack enchanted(ServerLevel level, Item item, ResourceKey<Enchantment> enchantment, int lvl) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment), lvl);
        return stack;
    }

    private static void check(List<String> problems, boolean condition, String message) {
        if (!condition)
            problems.add(message);
    }

    private static void report(GameTestHelper helper, List<String> problems, String what) {
        if (problems.isEmpty()) {
            helper.succeed();
            return;
        }
        TFMG.LOGGER.error("[gametest] {} {}: {}", problems.size(), what, problems);
        helper.fail(problems.size() + " " + what + ": " + problems.subList(0, Math.min(MAX_LISTED, problems.size())));
    }
}
