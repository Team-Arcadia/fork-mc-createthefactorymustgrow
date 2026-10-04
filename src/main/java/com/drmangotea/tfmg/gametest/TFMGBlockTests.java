package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * One test per TFMG block: place it, let it tick through its first lazy tick,
 * check that its block entity survives a save/load round trip unchanged and
 * that its client sync tag builds, then break it with drops.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGBlockTests {

    private static final BlockPos POS = new BlockPos(2, 1, 2);

    @GameTestGenerator
    public static Collection<TestFunction> blockLifecycle() {
        List<TestFunction> tests = new ArrayList<>();
        for (Block block : TFMGGameTestUtil.tfmgBlocks()) {
            if (block instanceof LiquidBlock)
                continue;
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            tests.add(TFMGGameTestUtil.test("tfmg_blocks", "block." + path, TFMGGameTestUtil.PLATFORM, 100,
                    helper -> placeTickSaveBreak(helper, block)));
        }
        return tests;
    }

    private static void placeTickSaveBreak(GameTestHelper helper, Block block) {
        BlockState state = block.defaultBlockState();
        helper.setBlock(POS, state);
        // Create's SmartBlockEntity initialises on its first tick and runs its
        // first lazyTick after 10; 30 ticks covers both with margin.
        helper.runAfterDelay(30, () -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(POS);
            BlockState placed = level.getBlockState(abs);
            if (block instanceof EntityBlock && placed.is(block)) {
                BlockEntity be = level.getBlockEntity(abs);
                if (be != null)
                    roundTrip(helper, level, abs, placed, be);
            }
            List<String> inspection = TFMGGameTestUtil.inspectionProblems(helper, abs);
            TFMGGameTestUtil.check(helper, inspection.isEmpty(), "inspector report shows raw text: " + inspection);
            level.destroyBlock(abs, true);
            helper.succeed();
        });
    }

    private static void roundTrip(GameTestHelper helper, ServerLevel level, BlockPos abs, BlockState state, BlockEntity be) {
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag first = be.saveWithFullMetadata(registries);
        BlockEntity copy = BlockEntity.loadStatic(abs, state, first, registries);
        TFMGGameTestUtil.check(helper, copy != null, "block entity failed to load from its own save");
        copy.setLevel(level);
        CompoundTag second = copy.saveWithFullMetadata(registries);
        Set<String> diff = TFMGGameTestUtil.diffKeys(first, second);
        TFMGGameTestUtil.check(helper, diff.isEmpty(), "save/load round trip changed: " + diff);
        // The client sync tag must build without throwing on the server.
        be.getUpdateTag(registries);
        be.getUpdatePacket();
    }
}
