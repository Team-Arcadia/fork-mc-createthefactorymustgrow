package com.drmangotea.tfmg.content.machinery.metallurgy.casting_basin;

import com.drmangotea.tfmg.base.blocks.TFMGHorizontalDirectionalBlock;
import com.drmangotea.tfmg.base.TFMGShapes;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CastingBasinBlock extends TFMGHorizontalDirectionalBlock implements IBE<CastingBasinBlockEntity> {
    public CastingBasinBlock(Properties p_54120_) {
        super(p_54120_);
    }

    @Override
    public VoxelShape getShape(BlockState p_60555_, BlockGetter p_60556_, BlockPos p_60557_, CollisionContext p_60558_) {
        return TFMGShapes.CASTING_BASIN.get(p_60555_.getValue(FACING));
    }
    /**
     * An empty hand takes the cast item. The slot used to be reachable only
     * by a funnel or hopper, so a player without one had no way to get the
     * ingot out.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CastingBasinBlockEntity be))
            return InteractionResult.PASS;
        ItemStack cast = be.inventory.getStackInSlot(0);
        if (cast.isEmpty())
            return InteractionResult.PASS;
        if (!level.isClientSide) {
            player.getInventory().placeItemBackInInventory(cast.copy());
            be.inventory.setStackInSlot(0, ItemStack.EMPTY);
            be.notifyUpdate();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }
    @Override
    public Class<CastingBasinBlockEntity> getBlockEntityClass() {
        return CastingBasinBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CastingBasinBlockEntity> getBlockEntityType() {
        return TFMGBlockEntities.CASTING_BASIN.get();
    }
}
