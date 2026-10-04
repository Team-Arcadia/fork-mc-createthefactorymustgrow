package com.drmangotea.tfmg.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helpers for the handheld item tests: a server player that clicks through
 * the real server paths (the same {@code ServerPlayerGameMode} calls a
 * connected player's packets reach), aiming, holding the use button, and
 * reading what landed in the world.
 *
 * @author vyrriox
 */
final class TFMGHandheldKit {

    private TFMGHandheldKit() {
    }

    // ---------------------------------------------------------- the player

    /**
     * A server player that never logs in (see ERROR_LOG: a logged-in mock
     * player crashes the test server) and keeps every chat and action bar
     * line it is sent, so a test can read what the player would have seen.
     */
    static final class Tester extends FakePlayer {

        final List<Component> messages = new ArrayList<>();

        Tester(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "tfmg_tester"));
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message);
        }

        @Override
        public void sendSystemMessage(Component message, boolean actionBar) {
            messages.add(message);
        }

        String said() {
            List<String> lines = new ArrayList<>();
            for (Component message : messages)
                lines.add(message.getString());
            return String.join(" | ", lines);
        }
    }

    /** A survival player standing at {@code rel} (feet), looking with the given angles. */
    static Tester player(GameTestHelper helper, Vec3 rel, float yaw, float pitch) {
        Tester player = new Tester(helper.getLevel());
        Vec3 abs = helper.absoluteVec(rel);
        player.moveTo(abs.x, abs.y, abs.z, yaw, pitch);
        player.setYHeadRot(yaw);
        return player;
    }

    /** Turns the player so that a ray from {@code from} (absolute) points at {@code target} (absolute). */
    static void lookFrom(Player player, Vec3 from, Vec3 target) {
        Vec3 d = target.subtract(from);
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
    }

    static void lookAt(Player player, Vec3 target) {
        lookFrom(player, player.getEyePosition(), target);
    }

    /**
     * Aims so that something leaving a hand-held barrel at
     * {@code barrelOffset} (right hand, as gadgets compute it) travels
     * towards {@code target}. The barrel moves with the aim, so a few passes
     * settle it.
     */
    static void aimBarrel(Player player, Vec3 target, Vec3 barrelOffset) {
        lookAt(player, target);
        for (int pass = 0; pass < 4; pass++)
            lookFrom(player, barrel(player, barrelOffset), target);
    }

    static Vec3 barrel(Player player, Vec3 rightHandForward) {
        Vec3 start = player.position().add(0, player.getEyeHeight(), 0);
        float yaw = (float) (player.getYRot() / -180 * Math.PI);
        float pitch = (float) (player.getXRot() / -180 * Math.PI);
        Vec3 offset = new Vec3(-rightHandForward.x, rightHandForward.y, rightHandForward.z);
        return start.add(offset.xRot(pitch).yRot(yaw));
    }

    // ------------------------------------------------------------- using

    // A tester never ticks, so its item cooldowns never run out on their own:
    // it waits each one out before acting. Tests check a cooldown right after
    // the action that sets it.

    /** Right-click in the air, through the server's own item use path. */
    static InteractionResult useInAir(GameTestHelper helper, Tester player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.getCooldowns().removeCooldown(stack.getItem());
        return player.gameMode.useItem(player, helper.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND);
    }

    /** Right-click on a face of a block, through the server's own path (item first-use hook, block, then item). */
    static InteractionResult click(GameTestHelper helper, Tester player, BlockPos rel, Direction face) {
        BlockPos abs = helper.absolutePos(rel);
        Vec3 hit = Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        BlockHitResult result = new BlockHitResult(hit, face, abs, false);
        player.getCooldowns().removeCooldown(held(player).getItem());
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(InteractionHand.MAIN_HAND),
                InteractionHand.MAIN_HAND, result);
    }

    static InteractionResult click(GameTestHelper helper, Tester player, BlockPos rel, Direction face, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return click(helper, player, rel, face);
    }

    static ItemStack held(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND);
    }

    /**
     * Keeps the use button held for {@code ticks} ticks, the way the living
     * entity tick drives a used item, then runs {@code after}. Stops early
     * (and still runs {@code after}) once the item lets go.
     */
    static void holdUse(GameTestHelper helper, Player player, int ticks, Runnable after) {
        holdUse(helper, player, ticks, 0, after);
    }

    private static void holdUse(GameTestHelper helper, Player player, int left, int used, Runnable after) {
        if (left <= 0 || !player.isUsingItem()) {
            after.run();
            return;
        }
        helper.runAfterDelay(1, () -> {
            if (player.isUsingItem())
                player.getUseItem().onUseTick(helper.getLevel(), player, player.getUseItemRemainingTicks());
            holdUse(helper, player, left - 1, used + 1, after);
        });
    }

    // ------------------------------------------------------------- world

    static <T extends Entity> List<T> entities(GameTestHelper helper, Class<T> type) {
        return helper.getLevel().getEntitiesOfClass(type, helper.getBounds().inflate(4));
    }

    static <T extends Entity> List<T> entities(GameTestHelper helper, EntityType<T> type) {
        List<T> out = new ArrayList<>();
        for (Entity entity : helper.getLevel().getEntities((Entity) null, helper.getBounds().inflate(4), e -> e.getType() == type))
            out.add(type.tryCast(entity));
        return out;
    }

    static int dropped(GameTestHelper helper, Item item) {
        int count = 0;
        for (ItemEntity entity : entities(helper, ItemEntity.class))
            if (entity.getItem().is(item))
                count += entity.getItem().getCount();
        return count;
    }

    /**
     * Items of a kind lying within a radius of a relative position. The test
     * bounds are inflated for {@link #dropped}, so on a client or server run,
     * where tests sit closer together, a neighbour's drops can be counted too.
     */
    static int droppedNear(GameTestHelper helper, Item item, BlockPos pos, double radius) {
        int count = 0;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(helper.absolutePos(pos)).inflate(radius);
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box))
            if (entity.getItem().is(item))
                count += entity.getItem().getCount();
        return count;
    }

    static int blocks(GameTestHelper helper, Block block) {
        return blocks(helper, block, 0);
    }

    /** Blocks of a kind within the test area grown by {@code margin} sideways (and a third of it up and down). */
    static int blocks(GameTestHelper helper, Block block, int margin) {
        int[] count = new int[1];
        BlockPos.betweenClosedStream(helper.getBounds().inflate(margin, margin / 3.0, margin)).forEach(pos -> {
            if (helper.getLevel().getBlockState(pos).is(block))
                count[0]++;
        });
        return count[0];
    }

    /** A pig that stands still where it is put. */
    static Mob target(GameTestHelper helper, Vec3 rel) {
        return helper.spawnWithNoFreeWill(EntityType.PIG, rel);
    }

    static boolean hurt(LivingEntity entity) {
        return !entity.isAlive() || entity.getHealth() < entity.getMaxHealth();
    }

    static Vec3 centre(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }
}
