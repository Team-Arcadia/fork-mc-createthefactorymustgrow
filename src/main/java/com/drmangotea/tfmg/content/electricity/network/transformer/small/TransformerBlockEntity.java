package com.drmangotea.tfmg.content.electricity.network.transformer.small;

import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import net.minecraft.ChatFormatting;
import com.drmangotea.tfmg.content.electricity.base.IElectric;
import com.drmangotea.tfmg.content.electricity.base.VoltageAlteringBlockEntity;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;

import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class TransformerBlockEntity extends VoltageAlteringBlockEntity {
    boolean updateInFront = false;

    public ItemStack primaryCoil = ItemStack.EMPTY;
    public ItemStack secondaryCoil = ItemStack.EMPTY;

    public float coilRatio = 0;

    public TransformerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public int getOutputVoltage() {
        return (int) (getData().getVoltage()*coilRatio);
    }

    @Override
    public int getOutputPower() {
        return coilRatio == 0 ? 0 : getPowerUsage();
    }

    @Override
    public void tick() {
        super.tick();
        if(updateInFront) {
            updateInFront();
            updateInFront = false;
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        BlockPos pos = this.getBlockPos();
        // Spawning the coils is server logic, as in the mixer and the electrode
        // holder. Unguarded it ran on the client too, which briefly showed item
        // entities the server never created. updateInFront stays outside the
        // guard: the electrical simulation is mirrored on both sides and the
        // client network still has to learn this transformer is gone.
        if (level != null && !level.isClientSide) {
            if(!primaryCoil.isEmpty()){
                ItemEntity item = new ItemEntity(level, pos.getX()+.5f,pos.getY()+.5f,pos.getZ()+.5f,primaryCoil);
                level.addFreshEntity(item);
            }
            if(!secondaryCoil.isEmpty()){
                ItemEntity item = new ItemEntity(level, pos.getX()+.5f,pos.getY()+.5f,pos.getZ()+.5f,secondaryCoil);
                level.addFreshEntity(item);
            }
        }
        updateInFront();
    }

    @Override
    public int getPowerUsage() {
        // Same feedback-loop guard as VoltageAlteringBlockEntity: a ring of
        // bridge blocks recursed through getNetworkPowerUsage until
        // StackOverflowError.
        if (computingPowerUsage)
            return 0;
        Direction facing = getDirection();

        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            if (be.hasElectricitySlot(facing.getOpposite())) {
                computingPowerUsage = true;
                try {
                    return Math.max(be.getNetworkPowerUsage(this), 0);
                } finally {
                    computingPowerUsage = false;
                }
            }
        }

        return 0;
    }



    public void updateCoils(){
        if(primaryCoil.get(TFMGDataComponents.COIL_TURNS)==null||secondaryCoil.get(TFMGDataComponents.COIL_TURNS)==null) {
            coilRatio = 0;
            updateNextTick();
            updateInFront();
            return;
        }
        int primaryTurns = primaryCoil.get(TFMGDataComponents.COIL_TURNS);
        int secondaryTurns = secondaryCoil.get(TFMGDataComponents.COIL_TURNS);

        if(primaryCoil.isEmpty()||secondaryCoil.isEmpty()||primaryTurns<50||secondaryTurns<50){

            coilRatio = 0;
            updateNextTick();
            updateInFront();
            return;
        }

        coilRatio = (float) (float)secondaryTurns/(float) primaryTurns;

        updateNextTick();
        updateInFront();

    }

    @Override
    public boolean makeMultimeterTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.makeMultimeterTooltip(tooltip, isPlayerSneaking);

        TFMGTexts.Multimeter.separator().forGoggles(tooltip);
        if(coilRatio!=0) {
            TFMGTexts.Multimeter.transformerRatio(coilRatio).forGoggles(tooltip, 1);
            // Name both coils. The ratio alone does not tell a player which
            // side they wired, and the whole mechanism is "output turns divided
            // by input turns" — testers reported having to read the wiki to get
            // that far.
            TFMGLang.text("Input coil: " + primaryCoil.getOrDefault(TFMGDataComponents.COIL_TURNS, 0) + " turns")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
            TFMGLang.text("Output coil: " + secondaryCoil.getOrDefault(TFMGDataComponents.COIL_TURNS, 0) + " turns")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip, 1);
        } else {
            // A dead transformer used to show NOTHING — surface why.
            TFMGLang.text("Needs two coils with at least 50 turns each")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }
        return true;
    }

    @Override
    public void inspect(InspectionReport report) {
        checkCoil(report, primaryCoil, "transformer.coil.input");
        checkCoil(report, secondaryCoil, "transformer.coil.output");
        if (coilRatio != 0)
            report.ok("transformer.ratio", String.format("%.2f", coilRatio), getData().getVoltage(), getOutputVoltage());
        inspectFaces(report);
    }

    private static void checkCoil(InspectionReport report, ItemStack coil, String coilKey) {
        Component name = Component.translatable("tfmg.inspector." + coilKey);
        if (coil.isEmpty()) {
            report.problem("transformer.coil_missing", name);
            report.fix("transformer.coil_missing.fix");
            return;
        }
        int turns = coil.getOrDefault(TFMGDataComponents.COIL_TURNS, 0);
        report.check(turns >= 50, "transformer.coil_ok", "transformer.coil_few_turns", "transformer.coil_few_turns.fix", name, turns, 50);
    }

    @Override
    public float resistance() {
        Direction facing = getBlockState().getValue(FACING).getCounterClockWise();
        if (level.getBlockEntity(getBlockPos().relative(facing)) instanceof IElectric be && be.getData().getId() != data.getId()) {
            if (be.hasElectricitySlot(facing.getOpposite())) {
                int count = getBlocksConnectedToNetworkCount(getControlledBlock().getData().getId());
                if(count!=0)
                    return Math.max(be.getNetworkResistance()*count, 0);
            }
        }
        return 0;
    }

    @Override
    public boolean hasElectricitySlot(Direction direction) {
        return direction == getBlockState().getValue(FACING).getClockWise();
    }
    @Override
    public void onNetworkChanged(int oldVoltage, int oldPower) {
        super.onNetworkChanged(oldVoltage, oldPower);
        if (oldVoltage != getData().getVoltage() || oldPower != getPowerUsage()) {
            updateInFront = true;
        }
        sendStuff();
        setChanged();
    }

    @Override
    public void updateNetwork() {
        super.updateNetwork();
        updateInFront();
    }

    @Override
    public void remove() {
        super.remove();
    }

    @Override
    public void onPlaced() {

        super.onPlaced();
        updateInFront = true;
    }



    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        if(!primaryCoil.isEmpty())
            compound.put("PrimaryCoil", primaryCoil.saveOptional(registries));
        if(!secondaryCoil.isEmpty())
            compound.put("SecondaryCoil", secondaryCoil.save(registries));

        compound.putFloat("CoilRation", coilRatio);

    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);

        // Absent keys mean an empty coil slot: clear it, or a removed coil
        // stayed visible on other clients.
        primaryCoil = compound.contains("PrimaryCoil")
                ? ItemStack.parseOptional(registries, compound.getCompound("PrimaryCoil")) : ItemStack.EMPTY;
        secondaryCoil = compound.contains("SecondaryCoil")
                ? ItemStack.parseOptional(registries, compound.getCompound("SecondaryCoil")) : ItemStack.EMPTY;
;

        coilRatio = compound.getFloat("CoilRation");
    }

    public static List<Direction> getCoilDirections(Level level, BlockPos pos, BlockHitResult result){
        Direction direction = level.getBlockState(pos).getValue(FACING);
        Collection<Direction> validDirections = new ArrayList<>();
        validDirections.add(direction.getClockWise());
        validDirections.add(direction.getCounterClockWise());


        return IPlacementHelper.orderedByDistance(pos, result.getLocation(), validDirections);

    }

    @OnlyIn(Dist.CLIENT)
    public static void tickOutliner() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult result))
            return;

        ClientLevel level = mc.level;
        BlockPos pos = result.getBlockPos();
        Player player = mc.player;
        ItemStack heldItem = player.getMainHandItem();



        if (!TFMGBlocks.TRANSFORMER.has(level.getBlockState(pos)))
            return;

        if (!(TFMGItems.ELECTROMAGNETIC_COIL.isIn(heldItem)|| heldItem.is(Items.AIR)))
            return;

        Direction direction = level.getBlockState(pos).getValue(FACING);


        Direction coilDirection = getCoilDirections(level,pos,result).get(0);
        /////////

        Vec3 center = VecHelper.getCenterOf(pos);

        Vec3 corner1 = center.relative(coilDirection,7/16f).relative(direction,3/16f).relative(Direction.UP,5.75/16f);
        Vec3 corner2 = center.relative(coilDirection,1/16f).relative(direction.getOpposite(),3/16f).relative(Direction.DOWN,1.75/16f);

        TFMGUtils.createOutline(corner1,corner2,"CoilOutline", Color.rainbowColor(AnimationTickHolder.getTicks() * 5));
    }
}
