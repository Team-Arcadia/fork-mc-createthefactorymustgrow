package com.drmangotea.tfmg.content.electricity.network.transformer.large;

import com.drmangotea.tfmg.content.electricity.storage.AccumulatorBlockEntity;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Collections;
import java.util.List;

public class LargeCoilBlock extends Block implements IBE<LargeCoilBlockEntity>, IWrenchable {
    public LargeCoilBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        withBlockEntityDo(level, pos, be -> be.setCapacity(stack));
    }

    /**
     * Drops itself carrying its setting. The drop used to be spawned from
     * onDestroyedByPlayer only, so a wrench pickup, an explosion or a drill
     * deleted the block outright.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack item = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof LargeCoilBlockEntity be)
            item.set(TFMGDataComponents.COIL_TURNS, be.turns);
        return List.of(item);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if(stack.is(TFMGBlocks.LAMINATED_MAGNETIC_ALLOY_BLOCK.asItem())){
            if(level.getBlockEntity(pos) instanceof LargeCoilBlockEntity be)
                return be.createTransformer(player,player.getNearestViewDirection());

        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }


    @Override
    public Class<LargeCoilBlockEntity> getBlockEntityClass() {
        return LargeCoilBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LargeCoilBlockEntity> getBlockEntityType() {
        return TFMGBlockEntities.LARGE_COIL.get();
    }
}
