package com.drmangotea.tfmg.content.electricity.storage;

import com.drmangotea.tfmg.base.blocks.TFMGDirectionalBlock;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public class AccumulatorBlock extends TFMGDirectionalBlock implements IBE<AccumulatorBlockEntity> {

    public AccumulatorBlock(Properties p_49795_) {
        super(p_49795_);
    }


    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        withBlockEntityDo(level, pos, be -> be.setCapacity(stack));
    }

    /**
     * Carries the stored energy into the dropped item. This has to live here
     * rather than in onDestroyedByPlayer: that hook only fires when a player
     * mines the block, while every other removal path (the Create wrench,
     * explosions, pistons) reads Block#getDrops. IWrenchable#onSneakWrenched in
     * particular collects getDrops and then calls destroyBlock without dropping,
     * so an accumulator taken with a wrench used to vanish outright.
     */
    @Override
    public List<ItemStack> getDrops(BlockState p_287732_, LootParams.Builder p_287596_) {
        ItemStack stack = TFMGBlocks.ACCUMULATOR.asItem().getDefaultInstance();
        if (p_287596_.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AccumulatorBlockEntity be)
            // Before destroy() ran (drills, explosions, the wrench) the
            // charge is still the bank's; afterwards destroy() has left only
            // what the surviving banks could not take.
            stack.set(TFMGDataComponents.ACCUMULATOR_STORAGE, be.isRemoved() ? be.energy.getEnergyStored() : be.chargeKeptOnRemoval());
        return List.of(stack);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {


        withBlockEntityDo(level,pos,AccumulatorBlockEntity::refreshMultiblock);

        super.onNeighborChange(state, level, pos, neighbor);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState blockState1, boolean a) {


        withBlockEntityDo(level,pos, IElectric::onPlaced);
        withBlockEntityDo(level,pos,b->b.refreshNextTick =true);

        super.onPlace(state, level, pos, blockState1, a);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        for(Direction direction : Direction.values()){
            BlockPos neighborPos = pos.relative(direction);
            if(level.getBlockState(pos).is(TFMGBlocks.ACCUMULATOR.get()))
                withBlockEntityDo(level,neighborPos,AccumulatorBlockEntity::refreshMultiblock);

        }
        IBE.onRemove(state, level, pos, newState);
    }



    @Override
    public Class<AccumulatorBlockEntity> getBlockEntityClass() {
        return AccumulatorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AccumulatorBlockEntity> getBlockEntityType() {
        return TFMGBlockEntities.ACCUMULATOR.get();
    }
}

