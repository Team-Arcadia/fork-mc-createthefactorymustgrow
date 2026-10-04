package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.content.decoration.tanks.steel.SteelTankBlockEntity;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.base.VoltageAlteringBlockEntity;
import com.drmangotea.tfmg.content.electricity.generators.large_generator.RotorBlockEntity;
import com.drmangotea.tfmg.content.electricity.generators.large_generator.StatorBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.large_switch.LargeSwitchBlock;
import com.drmangotea.tfmg.content.electricity.network.large_switch.LargeSwitchBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeCoilBlockEntity;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlock;
import com.drmangotea.tfmg.content.electricity.network.transformer.large.LargeTransformerBlockEntity;
import com.drmangotea.tfmg.content.electricity.storage.AccumulatorBlockEntity;
import com.drmangotea.tfmg.content.engines.types.AbstractSmallEngineBlockEntity;
import com.drmangotea.tfmg.content.engines.types.large_engine.LargeEngineBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_furnace.BlastFurnaceOutputBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.blast_stove.BlastStoveBlockEntity;
import com.drmangotea.tfmg.content.machinery.metallurgy.coke_oven.CokeOvenBlock;
import com.drmangotea.tfmg.content.machinery.metallurgy.coke_oven.CokeOvenBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.air_intake.AirIntakeBlockEntity;
import com.drmangotea.tfmg.content.machinery.misc.firebox.FireboxBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.distillation_tower.controller.DistillationControllerBlockEntity;
import com.drmangotea.tfmg.content.machinery.oil_processing.pumpjack.hammer.PumpjackBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.base.IVatMachine;
import com.drmangotea.tfmg.content.machinery.vat.base.VatBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.electrode_holder.ElectrodeHolderBlockEntity;
import com.drmangotea.tfmg.content.machinery.vat.industrial_mixer.IndustrialMixerBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.drmangotea.tfmg.registry.TFMGTags;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-structure half of the blueprint tests: what a player does by hand
 * once a structure stands (a blade in the mixer, electrodes in the holders,
 * glue on the pumpjack beam, a laminated block on the large coils), and then
 * whether every machine of the structure really formed: each block counted by
 * a controller of the expected size, every vat machine registered, every
 * tower output found, every engine row joined, every bridge feeding a network
 * of its own. Only the blueprint's own blocks are looked at, so the checks
 * hold for any new schematic.
 *
 * @author vyrriox
 */
final class TFMGFormationChecks {

    private TFMGFormationChecks() {
    }

    /**
     * Fits the items a player would add by hand. Returns the items put into
     * the world that way, so the dismantling check does not count them as
     * duplicates when they drop back.
     */
    static Map<Item, Integer> prepare(GameTestHelper helper, BlockPos origin, Map<BlockPos, BlockState> blocks) {
        ServerLevel level = helper.getLevel();
        Map<Item, Integer> added = new HashMap<>();
        Set<BlockPos> converted = new HashSet<>();
        for (BlockPos rel : blocks.keySet()) {
            BlockPos abs = helper.absolutePos(origin.offset(rel));
            BlockEntity be = level.getBlockEntity(abs);
            if (be instanceof IndustrialMixerBlockEntity mixer) {
                // Both setters only report success when simulating.
                if (mixer.setMixerMode(TFMGItems.MIXER_BLADE.asStack(), true)) {
                    mixer.setMixerMode(TFMGItems.MIXER_BLADE.asStack(), false);
                    added.merge(TFMGItems.MIXER_BLADE.get(), 1, Integer::sum);
                }
            } else if (be instanceof ElectrodeHolderBlockEntity holder) {
                // Graphite in an arc furnace (fireproof vat), copper elsewhere.
                boolean fireproof = level.getBlockState(abs.below()).is(TFMGBlocks.FIREPROOF_CHEMICAL_VAT.get());
                ItemStack electrode = fireproof ? TFMGItems.GRAPHITE_ELECTRODE.asStack() : TFMGItems.COPPER_ELECTRODE.asStack();
                if (holder.setElectrode(electrode, true)) {
                    holder.setElectrode(electrode, false);
                    added.merge(electrode.getItem(), 1, Integer::sum);
                }
            } else if (be instanceof LargeCoilBlockEntity coil && !converted.contains(abs)) {
                // Two wound coils side by side, joined with a laminated block.
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (level.getBlockEntity(abs.relative(side)) instanceof LargeCoilBlockEntity other) {
                        ItemStack wound = new ItemStack(TFMGBlocks.LARGE_COIL.get());
                        wound.set(TFMGDataComponents.COIL_TURNS, 100);
                        coil.setCapacity(wound);
                        other.setCapacity(wound);
                        coil.createTransformer(null, side);
                        converted.add(abs);
                        converted.add(abs.relative(side));
                        break;
                    }
                }
            } else if (be instanceof PumpjackBlockEntity) {
                // Super Glue over the whole beam, the row on top of the holder.
                BlockPos min = null, max = null;
                for (BlockPos other : blocks.keySet()) {
                    BlockState s = blocks.get(other);
                    if (other.getY() != rel.getY() + 1 || !(s.is(TFMGTags.TFMGBlockTags.PUMPJACK_PART.tag)
                            || s.is(TFMGTags.TFMGBlockTags.PUMPJACK_HEAD.tag) || s.is(TFMGTags.TFMGBlockTags.PUMPJACK_CONNECTOR.tag)))
                        continue;
                    if (other.getX() != rel.getX() && other.getZ() != rel.getZ())
                        continue;
                    min = min == null ? other : new BlockPos(Math.min(min.getX(), other.getX()), other.getY(), Math.min(min.getZ(), other.getZ()));
                    max = max == null ? other : new BlockPos(Math.max(max.getX(), other.getX()), other.getY(), Math.max(max.getZ(), other.getZ()));
                }
                if (min != null)
                    level.addFreshEntity(new SuperGlueEntity(level, SuperGlueEntity.span(
                            helper.absolutePos(origin.offset(min)), helper.absolutePos(origin.offset(max)))));
            }
        }
        return added;
    }

    /** Everything that did not form as the blueprint promises; empty when all is well. */
    static List<String> problems(GameTestHelper helper, BlockPos origin, Map<BlockPos, BlockState> blocks) {
        ServerLevel level = helper.getLevel();
        List<String> out = new ArrayList<>();
        Map<Class<?>, List<BlockEntity>> byType = new HashMap<>();
        for (BlockPos rel : blocks.keySet()) {
            BlockEntity be = level.getBlockEntity(helper.absolutePos(origin.offset(rel)));
            if (be != null)
                byType.computeIfAbsent(kind(be), k -> new ArrayList<>()).add(be);
        }

        // Coke ovens: every block in a formed square of at least 2x2, and
        // only the front column showing doors.
        List<BlockEntity> ovens = byType.getOrDefault(CokeOvenBlockEntity.class, List.of());
        int ovenBlocks = 0;
        for (BlockEntity be : ovens) {
            CokeOvenBlockEntity oven = (CokeOvenBlockEntity) be;
            if (oven.isController()) {
                if (oven.size < 2)
                    out.add("coke oven at " + rel(helper, oven) + " did not join any oven");
                ovenBlocks += oven.size * oven.size;
            } else if (!(level.getBlockEntity(oven.controller) instanceof CokeOvenBlockEntity)) {
                out.add("coke oven at " + rel(helper, oven) + " points at a missing controller");
            }
            Direction facing = oven.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            boolean front = !(level.getBlockEntity(oven.getBlockPos().relative(facing)) instanceof CokeOvenBlockEntity);
            if (!front && oven.getBlockState().getValue(CokeOvenBlock.CONTROLLER_TYPE) != CokeOvenBlock.ControllerType.CASUAL)
                out.add("coke oven at " + rel(helper, oven) + " shows doors inside the wall");
        }
        if (!ovens.isEmpty() && ovenBlocks != ovens.size())
            out.add("coke ovens: " + ovenBlocks + " blocks in formed ovens out of " + ovens.size());

        // Air intakes: one fan of 2x2 or 3x3 per square, read from the saved data.
        List<BlockEntity> intakes = byType.getOrDefault(AirIntakeBlockEntity.class, List.of());
        int intakeBlocks = 0;
        for (BlockEntity be : intakes) {
            var tag = be.saveWithoutMetadata(level.registryAccess());
            if (tag.getBoolean("IsController"))
                intakeBlocks += tag.getInt("Diameter") * tag.getInt("Diameter");
        }
        if (!intakes.isEmpty() && intakeBlocks != intakes.size())
            out.add("air intakes: " + intakeBlocks + " blocks in formed fans out of " + intakes.size());

        // Tank-like multiblocks: every block counted by one controller.
        countTanks(out, "blast stove", byType.get(BlastStoveBlockEntity.class),
                be -> ((BlastStoveBlockEntity) be).isController(), be -> ((BlastStoveBlockEntity) be).getTotalTankSize());
        countTanks(out, "firebox", byType.get(FireboxBlockEntity.class),
                be -> ((FireboxBlockEntity) be).isController(), be -> ((FireboxBlockEntity) be).getTotalTankSize());
        countTanks(out, "steel tank", byType.get(SteelTankBlockEntity.class),
                be -> ((SteelTankBlockEntity) be).isController(), be -> ((SteelTankBlockEntity) be).getTotalTankSize());
        countTanks(out, "chemical vat", byType.get(VatBlockEntity.class),
                be -> ((VatBlockEntity) be).isController(), be -> ((VatBlockEntity) be).getTotalTankSize());

        // Vat machines: every machine sitting on or under a vat is registered.
        for (BlockEntity be : byType.getOrDefault(IVatMachine.class, List.of())) {
            BlockPos pos = be.getBlockPos();
            BlockEntity vat = level.getBlockEntity(pos.below());
            if (!(vat instanceof VatBlockEntity))
                vat = level.getBlockEntity(pos.above());
            if (!(vat instanceof VatBlockEntity v))
                continue;
            VatBlockEntity controller = v.getControllerBE();
            if (controller == null || !controller.machineMap.containsKey(pos))
                out.add("vat machine at " + rel(helper, be) + " is not registered by its vat");
        }

        // Blast furnaces: the height and the reinforced status the blueprint shows.
        for (BlockEntity be : byType.getOrDefault(BlastFurnaceOutputBlockEntity.class, List.of())) {
            BlastFurnaceOutputBlockEntity output = (BlastFurnaceOutputBlockEntity) be;
            Direction facing = output.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos middle = output.getBlockPos().relative(facing.getOpposite());
            int height = 0;
            boolean reinforced = true;
            while (true) {
                BlockPos centre = middle.above(height);
                boolean complete = true;
                for (int dx = -1; dx <= 1 && complete; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0)
                            continue;
                        BlockPos cell = centre.offset(dx, 0, dz);
                        BlockState s = level.getBlockState(cell);
                        if (cell.equals(output.getBlockPos()))
                            continue;
                        boolean corner = dx != 0 && dz != 0;
                        TFMGTags.TFMGBlockTags normalTag = corner ? TFMGTags.TFMGBlockTags.BLAST_FURNACE_SUPPORT : TFMGTags.TFMGBlockTags.BLAST_FURNACE_WALL;
                        TFMGTags.TFMGBlockTags strongTag = corner ? TFMGTags.TFMGBlockTags.REINFORCED_BLAST_FURNACE_SUPPORT : TFMGTags.TFMGBlockTags.REINFORCED_BLAST_FURNACE_WALL;
                        if (!s.is(normalTag.tag) && !s.is(strongTag.tag)) {
                            complete = false;
                            break;
                        }
                    }
                if (!complete)
                    break;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos cell = centre.offset(dx, 0, dz);
                        if ((dx == 0 && dz == 0) || cell.equals(output.getBlockPos()))
                            continue;
                        boolean corner = dx != 0 && dz != 0;
                        if (!level.getBlockState(cell).is((corner ? TFMGTags.TFMGBlockTags.REINFORCED_BLAST_FURNACE_SUPPORT
                                : TFMGTags.TFMGBlockTags.REINFORCED_BLAST_FURNACE_WALL).tag))
                            reinforced = false;
                    }
                height++;
            }
            if (height < 3)
                out.add("blast furnace at " + rel(helper, output) + " is drawn only " + height + " layers high");
            if (output.getCachedSize() != height)
                out.add("blast furnace at " + rel(helper, output) + " counts " + output.getCachedSize() + " layers, " + height + " built");
            boolean isReinforced = output.saveWithoutMetadata(level.registryAccess()).getBoolean("IsReinforce");
            if (isReinforced != reinforced)
                out.add("blast furnace at " + rel(helper, output) + (reinforced ? " is not reinforced" : " is wrongly reinforced"));
        }

        // Distillation towers: every output stacked on the controller found,
        // and the tank behind it turned into a tower big enough for them.
        for (BlockEntity be : byType.getOrDefault(DistillationControllerBlockEntity.class, List.of())) {
            DistillationControllerBlockEntity controller = (DistillationControllerBlockEntity) be;
            int expected = 0;
            for (BlockPos pos = controller.getBlockPos().above(); level.getBlockState(pos).is(TFMGBlocks.STEEL_DISTILLATION_OUTPUT.get());
                 pos = pos.above(2))
                expected++;
            int found = controller.getOutputs().size();
            if (found != expected || found == 0)
                out.add("distillation controller at " + rel(helper, controller) + " found " + found + " outputs, " + expected + " built");
            Direction facing = controller.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
            if (!(level.getBlockEntity(controller.getBlockPos().relative(facing.getOpposite())) instanceof SteelTankBlockEntity part)
                    || part.getControllerBE() == null) {
                out.add("distillation controller at " + rel(helper, controller) + " has no steel tank behind it");
                continue;
            }
            SteelTankBlockEntity tank = part.getControllerBE();
            if (!tank.isDistillationTower)
                out.add("the tank of the tower at " + rel(helper, controller) + " is not a distillation tower");
            if (tank.getHeight() < 2 * found || (found > 3 && tank.getWidth() < 2))
                out.add("the tank of the tower at " + rel(helper, controller) + " is too small for " + found + " outputs");
        }

        // Pumpjacks: beam, crank and base found, and the beam swinging.
        for (BlockEntity be : byType.getOrDefault(PumpjackBlockEntity.class, List.of())) {
            PumpjackBlockEntity hammer = (PumpjackBlockEntity) be;
            if (!hammer.isComplete())
                out.add("pumpjack at " + rel(helper, hammer) + " did not find its beam, crank and base");
            else if (!hammer.isRunning())
                out.add("pumpjack at " + rel(helper, hammer) + " did not assemble its beam");
        }

        // Small engines: every block in a row run by its front block.
        List<BlockEntity> engines = byType.getOrDefault(AbstractSmallEngineBlockEntity.class, List.of());
        int engineBlocks = 0;
        for (BlockEntity be : engines) {
            AbstractSmallEngineBlockEntity engine = (AbstractSmallEngineBlockEntity) be;
            if (engine.isController())
                engineBlocks += engine.engineLength() + 1;
            else if (engine.getControllerBE() == null)
                out.add("engine at " + rel(helper, engine) + " lost its controller");
        }
        if (!engines.isEmpty() && engineBlocks != engines.size())
            out.add("engines: " + engineBlocks + " blocks in engine rows out of " + engines.size());

        for (BlockEntity be : byType.getOrDefault(LargeEngineBlockEntity.class, List.of()))
            if (((LargeEngineBlockEntity) be).getShaft() == null)
                out.add("large engine at " + rel(helper, be) + " has no powered shaft");

        // Large generators: a full ring of eight stators on every rotor.
        Map<BlockPos, Integer> ringSizes = new HashMap<>();
        for (BlockEntity be : byType.getOrDefault(StatorBlockEntity.class, List.of())) {
            BlockPos rotor = ((StatorBlockEntity) be).rotor;
            if (rotor == null || !(level.getBlockEntity(rotor) instanceof RotorBlockEntity))
                out.add("stator at " + rel(helper, be) + " has no rotor");
            else
                ringSizes.merge(rotor, 1, Integer::sum);
        }
        for (BlockEntity be : byType.getOrDefault(RotorBlockEntity.class, List.of()))
            if (ringSizes.getOrDefault(be.getBlockPos(), 0) != 8)
                out.add("rotor at " + rel(helper, be) + " has " + ringSizes.getOrDefault(be.getBlockPos(), 0) + " stators");

        // Accumulators: every block in a chain.
        List<BlockEntity> accumulators = byType.getOrDefault(AccumulatorBlockEntity.class, List.of());
        int chained = 0;
        for (BlockEntity be : accumulators)
            if (((AccumulatorBlockEntity) be).isController())
                chained += ((AccumulatorBlockEntity) be).length;
        if (!accumulators.isEmpty() && chained != accumulators.size())
            out.add("accumulators: " + chained + " blocks in chains out of " + accumulators.size());

        // Bridges: each one feeds a network of its own.
        for (BlockEntity be : byType.getOrDefault(VoltageAlteringBlockEntity.class, List.of()))
            checkBridge(helper, out, be, ((VoltageAlteringBlockEntity) be).getControlledBlock());
        for (BlockEntity be : byType.getOrDefault(LargeTransformerBlockEntity.class, List.of()))
            if (be.getBlockState().getValue(LargeTransformerBlock.IS_MAIN_PART))
                checkBridge(helper, out, be, ((LargeTransformerBlockEntity) be).getControlledBlock());
        for (BlockEntity be : byType.getOrDefault(LargeSwitchBlockEntity.class, List.of()))
            if (be.getBlockState().getValue(LargeSwitchBlock.IS_MAIN_PART))
                checkBridge(helper, out, be, ((LargeSwitchBlockEntity) be).getControlledBlock());
        if (!byType.getOrDefault(LargeCoilBlockEntity.class, List.of()).isEmpty())
            out.add("large coils were not joined into a large transformer");
        return out;
    }

    private static void checkBridge(GameTestHelper helper, List<String> out, BlockEntity be, IElectric controlled) {
        if (controlled == null)
            out.add("bridge at " + rel(helper, be) + " has nothing on its output side");
        else if (controlled.getData().getId() == ((IElectric) be).getData().getId())
            out.add("bridge at " + rel(helper, be) + " feeds its own network");
    }

    private static void countTanks(List<String> out, String name, List<BlockEntity> parts,
                                   java.util.function.Predicate<BlockEntity> isController,
                                   java.util.function.ToIntFunction<BlockEntity> size) {
        if (parts == null || parts.isEmpty())
            return;
        int counted = 0;
        Set<BlockPos> controllers = new LinkedHashSet<>();
        for (BlockEntity be : parts)
            if (isController.test(be) && controllers.add(be.getBlockPos()))
                counted += size.applyAsInt(be);
        if (counted != parts.size())
            out.add(name + ": " + counted + " blocks counted by " + controllers.size() + " controllers out of " + parts.size());
    }

    /** Groups block entities by the multiblock family the checks know about. */
    private static Class<?> kind(BlockEntity be) {
        if (be instanceof AbstractSmallEngineBlockEntity)
            return AbstractSmallEngineBlockEntity.class;
        if (be instanceof VoltageAlteringBlockEntity)
            return VoltageAlteringBlockEntity.class;
        if (be instanceof IVatMachine)
            return IVatMachine.class;
        if (be instanceof LargeEngineBlockEntity)
            return LargeEngineBlockEntity.class;
        return be.getClass();
    }

    private static String rel(GameTestHelper helper, BlockEntity be) {
        return helper.relativePos(be.getBlockPos()).toShortString();
    }
}
