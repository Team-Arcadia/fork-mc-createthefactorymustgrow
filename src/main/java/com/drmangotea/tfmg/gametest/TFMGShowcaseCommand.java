package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dev command {@code /tfmgtest showcase}: builds a test map in front of the
 * player with every handbook multiblock side by side (each on a stone pad
 * with its name on a sign), a row of every TFMG block, and chests holding
 * every TFMG item. Used for hands-on testing in a client or on a server.
 *
 * Dev only: this package is excluded from the release jar.
 *
 * @author vyrriox
 */
@EventBusSubscriber(modid = TFMG.MOD_ID)
public final class TFMGShowcaseCommand {

    private static final int SPACING = 10;

    private TFMGShowcaseCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tfmgtest")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("showcase").executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    BlockPos origin = BlockPos.containing(source.getPosition()).offset(4, 0, 4);
                    int built = build(source.getLevel(), origin);
                    source.sendSuccess(() -> Component.literal("TFMG showcase built: " + built + " structures, every block and every item, from " + origin.toShortString()), true);
                    return built;
                })));
    }

    private static int build(ServerLevel level, BlockPos origin) {
        // Multiblocks, one per pad along +X.
        int x = 0;
        int built = 0;
        for (Map.Entry<String, JsonObject> blueprint : TFMGStructureTests.readBlueprints().entrySet()) {
            Map<BlockPos, BlockState> blocks = TFMGStructureTests.blocksOf(blueprint.getValue());
            BlockPos base = origin.offset(x, 0, 0);
            for (int dx = -1; dx < SPACING - 1; dx++)
                for (int dz = -1; dz < 8; dz++)
                    level.setBlock(base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
                level.setBlock(base.offset(e.getKey()), e.getValue(), 3);
            level.setBlock(base.offset(-1, 0, -1), Blocks.OAK_SIGN.defaultBlockState(), 3);
            if (level.getBlockEntity(base.offset(-1, 0, -1)) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign)
                sign.updateText(text -> text.setMessage(0, Component.literal(blueprint.getKey())), true);
            x += SPACING;
            built++;
        }

        // Every TFMG block in a row behind the multiblocks, two blocks apart.
        BlockPos row = origin.offset(0, 0, 12);
        int i = 0;
        for (Block block : TFMGGameTestUtil.tfmgBlocks()) {
            if (block instanceof LiquidBlock)
                continue;
            BlockPos pos = row.offset((i % 40) * 2, 0, (i / 40) * 2);
            level.setBlock(pos.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
            level.setBlock(pos, block.defaultBlockState(), 3);
            i++;
        }

        // Every TFMG item, in chests.
        List<ItemStack> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM)
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(TFMG.MOD_ID))
                items.add(new ItemStack(item, Math.min(64, item.getDefaultMaxStackSize())));
        BlockPos chests = origin.offset(-4, 0, 0);
        int slot = 0;
        ChestBlockEntity chest = null;
        int chestIndex = 0;
        for (ItemStack stack : items) {
            if (chest == null || slot >= chest.getContainerSize()) {
                BlockPos pos = chests.offset(0, 0, chestIndex++);
                level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
                chest = level.getBlockEntity(pos) instanceof ChestBlockEntity c ? c : null;
                slot = 0;
                if (chest == null)
                    break;
            }
            chest.setItem(slot++, stack);
        }
        return built;
    }
}
