package com.drmangotea.tfmg.gametest;

import com.drmangotea.tfmg.TFMG;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * One test per TFMG item: the stack survives a save (NBT codec) and a network
 * sync (stream codec) unchanged, and using it in the air and on a block, the
 * way a player would, throws nothing, then the world runs a few ticks with
 * whatever it spawned or placed.
 *
 * @author vyrriox
 */
@GameTestHolder(TFMG.MOD_ID)
@PrefixGameTestTemplate(false)
public class TFMGItemTests {

    @GameTestGenerator
    public static List<TestFunction> items() {
        List<TestFunction> tests = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(TFMG.MOD_ID))
                continue;
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            tests.add(TFMGGameTestUtil.test("tfmg_items", "item." + path, TFMGGameTestUtil.PLATFORM, 100,
                    helper -> codecsAndUse(helper, item)));
        }
        return tests;
    }

    private static void codecsAndUse(GameTestHelper helper, Item item) {
        var registries = helper.getLevel().registryAccess();
        ItemStack stack = new ItemStack(item, Math.max(1, item.getDefaultMaxStackSize()));

        // Saved to disk.
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow(msg -> new IllegalStateException("encode: " + msg));
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow(msg -> new IllegalStateException("decode: " + msg));
        TFMGGameTestUtil.check(helper, ItemStack.matches(stack, loaded), "the stack changed through a save and load");

        // Sent over the network.
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        ItemStack.STREAM_CODEC.encode(buf, stack);
        ItemStack received = ItemStack.STREAM_CODEC.decode(buf);
        buf.release();
        TFMGGameTestUtil.check(helper, ItemStack.matches(stack, received), "the stack changed through a network sync");

        // Used by a player, in the air and on the floor.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 standing = helper.absoluteVec(new Vec3(2.5, 1, 0.5));
        player.moveTo(standing.x, standing.y, standing.z, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        held.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        player.releaseUsingItem();

        player.setItemInHand(InteractionHand.MAIN_HAND, stack.copyWithCount(1));
        BlockPos floor = helper.absolutePos(new BlockPos(2, 0, 2));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));

        // Let anything placed or spawned run for a moment.
        helper.runAfterDelay(20, helper::succeed);
    }
}
