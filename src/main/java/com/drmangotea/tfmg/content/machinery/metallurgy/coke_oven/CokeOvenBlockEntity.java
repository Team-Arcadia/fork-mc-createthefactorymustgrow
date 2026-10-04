package com.drmangotea.tfmg.content.machinery.metallurgy.coke_oven;

import com.drmangotea.tfmg.base.ThrottledSync;
import com.drmangotea.tfmg.base.capability.DrainOnlyFluidHandler;
import com.drmangotea.tfmg.base.capability.InsertOnlyItemHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import com.drmangotea.tfmg.base.TFMGUtils;
import com.drmangotea.tfmg.base.lang.TFMGLang;
import com.drmangotea.tfmg.base.lang.TFMGTexts;
import com.drmangotea.tfmg.config.TFMGConfigs;
import com.drmangotea.tfmg.content.items.inspector.IInspectable;
import com.drmangotea.tfmg.content.items.inspector.InspectionReport;
import com.drmangotea.tfmg.recipes.CokingRecipe;
import com.drmangotea.tfmg.registry.TFMGBlockEntities;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGRecipeTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.SmartInventory;


import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Optional;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

public class CokeOvenBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation, IInspectable {

    // Per-tick fluid and progress changes sync at most every few ticks.
    private final ThrottledSync throttledSync = new ThrottledSync();

    public SmartInventory inventory;
    public FluidTank primaryTank;
    public FluidTank secondaryTank;
    protected IFluidHandler primaryFluidCapability;
    protected IFluidHandler secondaryFluidCapability;
    public IItemHandlerModifiable itemCapability;
    int timer = -1;
    private RecipeHolder<CokingRecipe> cachedRecipe;
    public LerpedFloat doorAngle = LerpedFloat.angular();
    public boolean createNextTick;
    public BlockPos controller = getBlockPos();
    public int size = 1;
    public boolean forceOpen = false;
    public CokeOvenBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(10);
        inventory = new SmartInventory(1, this)
                .withMaxStackSize(64)
                .whenContentsChanged(i->this.onContentsChanged());
        primaryTank = new SmartFluidTank(8000, this::onFluidChanged);
        secondaryTank = new SmartFluidTank(8000, this::onFluidChanged);
        itemCapability = inventory;
        primaryFluidCapability =  primaryTank;
        secondaryFluidCapability = secondaryTank;
        createNextTick = true;
    }

    public void onContentsChanged(){
        if(!inventory.isEmpty()&& timer == -1){
            executeRecipe();
        }
        if(inventory.isEmpty())
            timer = -1;
    }

    public void executeRecipe(){

        Optional<RecipeHolder<CokingRecipe>> optional = findRecipe();
        if(optional.isEmpty())
            return;

        CokingRecipe recipe = optional.get().value();

        if(recipe.getIngredients().get(0).test(inventory.getItem(0)))
            timer = (int) (recipe.getProcessingDuration() / Math.max(size / 2f, 1f));
    }

    // Reuses the last matched recipe until the input no longer matches it,
    // instead of scanning the recipe manager every tick.
    private Optional<RecipeHolder<CokingRecipe>> findRecipe(){
        if(cachedRecipe != null && cachedRecipe.value().getIngredients().get(0).test(inventory.getItem(0)))
            return Optional.of(cachedRecipe);
        Optional<RecipeHolder<CokingRecipe>> optional = TFMGRecipeTypes.COKING.find(new RecipeWrapper(inventory), level);
        cachedRecipe = optional.orElse(null);
        return optional;
    }

    private void onFluidChanged(FluidStack stack) {
        if (!hasLevel())
            return;
        if (!level.isClientSide) {
            setChanged();
            throttledSync.request(this);
        }
    }



    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide)
            throttledSync.tick(this);

        tickRecipe();

        if(level.isClientSide){

            doorAngle.chase((timer > 0 && timer < 50) || forceOpen ? 90 : 0, 0.1f, LerpedFloat.Chaser.EXP);
            doorAngle.tickChaser();
            if(!forceOpen)
                manageDoors(timer > 0 && timer < 50);
        }
        if(createNextTick){
            createMultiblock();
            createNextTick = false;
        }
    }

    public void tickRecipe(){
        // Recipe progress is server logic: the client used to decrement the
        // timer, fill tanks, shrink the input and spawn a ghost ItemEntity.
        // Timer reaches the client through SmartBlockEntity data sync.
        if(level == null || (level.isClientSide && !isVirtual()))
            return;
        // Only the controller cooks; a member block's own slot is not reachable.
        if(!isController())
            return;
        if(inventory.isEmpty()||timer == -1)
            return;

        Optional<RecipeHolder<CokingRecipe>> optional = findRecipe();

        if(optional.isEmpty()) {
            timer = -1;
            return;
        }
        CokingRecipe recipe = optional.get().value();

        if(timer ==0){
            timer = -1;
            inventory.getItem(0).shrink(recipe.getIngredients().get(0).getItems()[0].getCount());

            Direction direction =  getBlockState().getValue(FACING);

            Vec3 dropVec = VecHelper.getCenterOf(worldPosition.relative(direction))
                    .add(0,0.4,0);
            ItemEntity dropped = new ItemEntity(level, dropVec.x, dropVec.y, dropVec.z, recipe.getResultItem(level.registryAccess()).copy());
            dropped.setDefaultPickUpDelay();
            dropped.setDeltaMovement(direction.getAxis() == Direction.Axis.X ? direction == Direction.WEST ? -.01f : .01f : 0, 0.05f, direction.getAxis() == Direction.Axis.Z ? direction == Direction.NORTH ? -.01f : .01f : 0);
            level.addFreshEntity(dropped);

            if (!level.isClientSide) {

                setChanged();
                sendData();
            }
            onContentsChanged();
        }

        // Soft-lock when a byproduct gas tank is full: pause the whole recipe
        // (timer frozen, input item kept, no gas voided) until the tanks are
        // drained. This forces players to store the creosote / coal gas rather
        // than letting the oven silently waste it. tickRecipe runs every tick
        // while the timer is active, so draining a tank resumes the recipe on
        // its own — no notification needed.
        if(timer > 0){
            FluidStack primaryResult = recipe.getPrimaryResult();
            FluidStack secondaryResult = recipe.getSecondaryResult();
            if(primaryTank.fill(primaryResult, IFluidHandler.FluidAction.SIMULATE) < primaryResult.getAmount()
                    || secondaryTank.fill(secondaryResult, IFluidHandler.FluidAction.SIMULATE) < secondaryResult.getAmount())
                return;
            primaryTank.fill(primaryResult, IFluidHandler.FluidAction.EXECUTE);
            secondaryTank.fill(secondaryResult, IFluidHandler.FluidAction.EXECUTE);
            timer--;
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        onContentsChanged();



    }



    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {

        TFMGTexts.header("coke_oven")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if(level.getBlockEntity(controller) instanceof CokeOvenBlockEntity controller) {
            if (controller.timer > 0)
                TFMGTexts.progress((double) controller.timer / 20)
                        .style(ChatFormatting.GOLD)
                        .forGoggles(tooltip);
            // A full by-product tank pauses the oven; it used to say nothing.
            if (controller.secondaryTank.getSpace() < 100)
                TFMGTexts.problem("coke_oven.co2_full").forGoggles(tooltip);
            if (controller.primaryTank.getSpace() < 100)
                TFMGTexts.problem("coke_oven.creosote_full").forGoggles(tooltip);
        }

        createFluidTooltip(this,tooltip);
        TFMGUtils.createItemTooltip(this, tooltip);
        return true;
    }
    public static boolean createFluidTooltip(CokeOvenBlockEntity be, List<Component> tooltip) {
        LangBuilder mb = CreateLang.translate("generic.unit.millibuckets");

        /////////

        if(be.level.getBlockEntity(be.controller) instanceof CokeOvenBlockEntity controller) {


            IFluidHandler tank = new CombinedTankWrapper(controller.primaryTank, controller.secondaryTank);


            if (tank.getTanks() == 0) return false;

            TFMGLang.translate("goggles.fluid_storage").style(ChatFormatting.GRAY).forGoggles(tooltip);


            boolean isEmpty = true;
            for (int i = 0; i < tank.getTanks(); i++) {
                FluidStack fluidStack = tank.getFluidInTank(i);
                if (fluidStack.isEmpty()) continue;
                TFMGLang.fluidName(fluidStack).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
                TFMGLang.builder().add(TFMGLang.number(fluidStack.getAmount()).add(mb).style(ChatFormatting.DARK_GREEN)).text(ChatFormatting.GRAY, " / ").add(TFMGLang.number(tank.getTankCapacity(i)).add(mb).style(ChatFormatting.DARK_GRAY)).forGoggles(tooltip, 1);
                isEmpty = false;
            }
            if (tank.getTanks() > 1) {
                if (isEmpty) tooltip.remove(tooltip.size() - 1);
                return true;
            }
            if (!isEmpty) return true;

            CreateLang.translate("gui.goggles.fluid_container.capacity").add(TFMGLang.number(tank.getTankCapacity(0)).add(mb).style(ChatFormatting.DARK_GREEN)).style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);

        }
        return true;
    }
    @Override
    public void inspect(InspectionReport report) {
        if (level == null)
            return;
        if (!isController()) {
            if (!level.isLoaded(controller) || !(level.getBlockEntity(controller) instanceof CokeOvenBlockEntity owner)) {
                report.problem("coke_oven.no_controller");
                report.fix("coke_oven.no_controller.fix");
                return;
            }
            report.info("coke_oven.member", controller.getX(), controller.getY(), controller.getZ());
            owner.inspect(report);
            return;
        }

        int maxSize = maxFormableSize();
        float speed = Math.max(size / 2f, 1f);
        if (size > 1)
            report.ok("coke_oven.formed", size, size, maxSize, String.format("%.1f", speed));
        else
            report.info("coke_oven.single", maxSize);

        ItemStack input = inventory.getItem(0);
        CokingRecipe recipe = null;
        if (input.isEmpty()) {
            report.problem("coke_oven.no_input");
            report.fix("coke_oven.no_input.fix");
        } else {
            Optional<RecipeHolder<CokingRecipe>> found = TFMGRecipeTypes.COKING.find(new RecipeWrapper(inventory), level);
            if (found.isEmpty()) {
                report.problem("coke_oven.no_recipe", input.getHoverName());
                report.fix("coke_oven.no_recipe.fix", input.getHoverName());
            } else {
                recipe = found.get().value();
                report.ok("coke_oven.input", input.getCount(), input.getHoverName(),
                        recipe.getResultItem(level.registryAccess()).getHoverName());
            }
        }

        if (recipe != null) {
            FluidStack creosote = recipe.getPrimaryResult();
            FluidStack gas = recipe.getSecondaryResult();
            if (primaryTank.fill(creosote, IFluidHandler.FluidAction.SIMULATE) < creosote.getAmount()) {
                report.problem("coke_oven.creosote_full", primaryTank.getFluidAmount(), primaryTank.getCapacity());
                report.fix("coke_oven.creosote_full.fix", creosote.getHoverName());
            } else {
                report.ok("coke_oven.creosote_space", creosote.getHoverName(), primaryTank.getFluidAmount(), primaryTank.getCapacity());
            }
            if (secondaryTank.fill(gas, IFluidHandler.FluidAction.SIMULATE) < gas.getAmount()) {
                report.problem("coke_oven.co2_full", secondaryTank.getFluidAmount(), secondaryTank.getCapacity());
                report.fix("coke_oven.co2_full.fix", gas.getHoverName());
            } else {
                report.ok("coke_oven.co2_space", gas.getHoverName(), secondaryTank.getFluidAmount(), secondaryTank.getCapacity());
            }
            report.info("coke_oven.byproducts", creosote.getAmount(), creosote.getHoverName(), gas.getAmount(), gas.getHoverName());
        }

        if (timer > 0)
            report.info("coke_oven.progress", String.format("%.1f", timer / 20f));
        BlockPos drop = worldPosition.relative(getBlockState().getValue(FACING));
        report.info("coke_oven.output", drop.getX(), drop.getY(), drop.getZ());
    }

    public void manageDoors(boolean open){

        for(int i =0; i< size;i++){
            BlockPos pos = getBlockPos().above(i);

            if(level.getBlockEntity(pos) instanceof CokeOvenBlockEntity be && !pos.equals(getBlockPos())){
                be.forceOpen = open;
            }
        }
    }
    public boolean isController(){
        return controller == null || controller.equals(getBlockPos());
    }
    /**
     * Joins this oven block to the structure run by {@code newController}. A
     * block that ran its own oven until now hands its coal and its tanks to
     * the new controller: growing an oven used to strand them in a block
     * nothing could reach, which went on coking by itself inside the wall.
     */
    private void assignController(BlockPos newController) {
        if (isController() && !newController.equals(getBlockPos())
                && level.getBlockEntity(newController) instanceof CokeOvenBlockEntity owner && owner != this) {
            ItemStack held = inventory.getItem(0);
            if (!held.isEmpty()) {
                ItemStack left = owner.inventory.insertItem(0, held.copy(), false);
                inventory.setStackInSlot(0, ItemStack.EMPTY);
                if (!left.isEmpty())
                    Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), left);
            }
            TFMGUtils.moveFluid(primaryTank, owner.primaryTank);
            TFMGUtils.moveFluid(secondaryTank, owner.secondaryTank);
            timer = -1;
        }
        controller = newController;
    }

    public void createMultiblock(){

        // Assembly mutates blockstates and controller links; server only.
        // Clients used to re-run this on chunk load with a partially loaded
        // view and locally rewrote CONTROLLER_TYPE to CASUAL, visually
        // breaking the oven until the chunk was reloaded.
        if(level == null || (level.isClientSide && !isVirtual()))
            return;
        int maxSize = TFMGConfigs.common().machines.cokeOvenMaxSize.get();
        Direction facing = getBlockState().getValue(FACING);
        if(level.getBlockState(getBlockPos().relative(facing)).is(TFMGBlocks.COKE_OVEN.get())||level.getBlockState(getBlockPos().below()).is(TFMGBlocks.COKE_OVEN.get()))
            return;

        int size = 1;
            for(int i = 1;i<=maxSize;i++){
                boolean cantBuildMultiblock = false;
                for(BlockPos pos : BlockPos.betweenClosed(getBlockPos(),getBlockPos().above(i).relative(facing.getOpposite(),i))) {
                    if(!level.getBlockState(pos).is(TFMGBlocks.COKE_OVEN.get())){
                        cantBuildMultiblock = true;
                    }else
                        if(level.getBlockState(pos).is(TFMGBlocks.COKE_OVEN.get()) &&level.getBlockState(pos).getValue(FACING) != facing){
                            cantBuildMultiblock = true;
                        }
                }
                if(cantBuildMultiblock)
                    break;
                size++;
            }

        // Over-size guard: when the wall keeps going past the largest
        // formable square (an extra layer added BELOW or IN FRONT of a
        // full-size oven), this freshly placed corner used to steal
        // controllership and re-form the square shifted by one block,
        // orphaning the far row. Keep the existing structure anchored on
        // its current controller instead and leave the extra layer as
        // standalone oven blocks.
        if (hasOvenBeyond(size, facing)) {
            CokeOvenBlockEntity anchor = findIntactAnchor(facing);
            if (anchor != null) {
                Direction back = facing.getOpposite();
                // Heal the whole CONNECTED component: partial extra layers can
                // spawn rogue corners anywhere along the wall (e.g. under the
                // middle of the bottom row) whose small scans used to steal a
                // chunk out of the intact structure. Walk the component from
                // this corner; members of the anchored square rejoin it,
                // everything else becomes a standalone oven again. A bounded
                // flood fill cannot touch disconnected neighbouring ovens.
                int reach = (maxSize + 2) * 2;
                java.util.ArrayDeque<BlockPos> stack = new java.util.ArrayDeque<>();
                java.util.HashSet<BlockPos> seen = new java.util.HashSet<>();
                stack.push(getBlockPos());
                while (!stack.isEmpty()) {
                    BlockPos pos = stack.pop();
                    if (!seen.add(pos))
                        continue;
                    if (pos.distManhattan(getBlockPos()) > reach)
                        continue;
                    if (!isMatchingOven(pos, facing))
                        continue;
                    if (level.getBlockEntity(pos) instanceof CokeOvenBlockEntity be) {
                        if (isInPlane(anchor.getBlockPos(), back, anchor.size, pos)) {
                            boolean controllerChanged = !anchor.getBlockPos().equals(be.controller);
                            be.assignController(anchor.getBlockPos());
                            be.refreshCapability();
                            if (controllerChanged)
                                be.notifyUpdate();
                        } else if (be.controller == null || !be.controller.equals(be.getBlockPos()) || be.size != 1) {
                            be.controller = be.getBlockPos();
                            be.size = 1;
                            be.refreshCapability();
                            be.forceOpen = false;
                            be.doorAngle.setValue(0);
                            be.notifyUpdate();
                            level.setBlock(be.getBlockPos(), level.getBlockState(be.getBlockPos())
                                    .setValue(CokeOvenBlock.CONTROLLER_TYPE, CokeOvenBlock.ControllerType.CASUAL), 2);
                        }
                    }
                    stack.push(pos.above());
                    stack.push(pos.below());
                    stack.push(pos.relative(facing));
                    stack.push(pos.relative(back));
                }
                anchor.setBlockStates(anchor.size);
                return;
            }
        }

        for(BlockPos pos : BlockPos.betweenClosed(getBlockPos(),getBlockPos().above(size-1).relative(facing.getOpposite(),size-1))) {
            if(level.getBlockEntity(pos) instanceof CokeOvenBlockEntity be&&(!level.getBlockState(getBlockPos().relative(facing)).is(TFMGBlocks.COKE_OVEN.get())&&!level.getBlockState(getBlockPos().below()).is(TFMGBlocks.COKE_OVEN.get()))){

                boolean controllerChanged = !getBlockPos().equals(be.controller);
                be.assignController(getBlockPos());
                be.refreshCapability();
                if(controllerChanged)
                    be.notifyUpdate();
            }
        }
        if(!level.getBlockState(getBlockPos().relative(facing)).is(TFMGBlocks.COKE_OVEN.get())&&!level.getBlockState(getBlockPos().below()).is(TFMGBlocks.COKE_OVEN.get()))
            setBlockStates(size);
        // Cover BOTH the previous and the new footprint, otherwise members
        // of a larger previous structure were never reset to standalone.
        int detachRange = Math.max(this.size, size);
        for(BlockPos pos : BlockPos.betweenClosed(getBlockPos(), getBlockPos().above(detachRange-1).relative(facing.getOpposite(),detachRange-1))){
            if(level.getBlockEntity(pos) instanceof CokeOvenBlockEntity be){
                if(Math.abs(getBlockPos().getX()-be.getBlockPos().getX())>=size || Math.abs(getBlockPos().getY()-be.getBlockPos().getY())>=size || Math.abs(getBlockPos().getZ()-be.getBlockPos().getZ())>=size)
                    if (be.controller != null && (be.controller.equals(getBlockPos()) || !be.controller.equals(be.getBlockPos()))) {
                        be.controller = be.getBlockPos();
                        be.refreshCapability();
                        be.forceOpen = false;
                        be.doorAngle.setValue(0);
                        be.notifyUpdate();
                        level.setBlock(be.getBlockPos(), getBlockState().setValue(CokeOvenBlock.CONTROLLER_TYPE ,CokeOvenBlock.ControllerType.CASUAL), 2);
                    }
            }
        }
        if(this.size != size){
            this.size = size;
            notifyUpdate();
        }
    }
    private boolean isMatchingOven(BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(TFMGBlocks.COKE_OVEN.get()) && state.getValue(FACING) == facing;
    }

    /**
     * True when the oven wall continues past the scanned square — i.e. a
     * row above it or a column behind it still holds matching oven blocks.
     */
    private boolean hasOvenBeyond(int size, Direction facing) {
        Direction back = facing.getOpposite();
        for (int i = 0; i < size; i++) {
            if (isMatchingOven(getBlockPos().above(size).relative(back, i), facing))
                return true;
            if (isMatchingOven(getBlockPos().relative(back, size).above(i), facing))
                return true;
        }
        return false;
    }

    /**
     * Finds an already-formed controller near this corner whose square is
     * still complete; the over-size guard re-anchors on it instead of
     * letting the new corner shift or tear the structure. The search covers
     * a FIXED reach up, back AND forward of this corner: a rogue corner
     * created by a partial bottom layer sits below the middle of the wall,
     * with the real controller diagonally FORWARD of it — a search bounded
     * by the rogue's own scan size (and only up/back) never found it.
     */
    private CokeOvenBlockEntity findIntactAnchor(Direction facing) {
        Direction back = facing.getOpposite();
        int reach = TFMGConfigs.common().machines.cokeOvenMaxSize.get() + 1;
        for (BlockPos pos : BlockPos.betweenClosed(getBlockPos().relative(facing, reach),
                getBlockPos().above(reach).relative(back, reach))) {
            if (pos.equals(getBlockPos()))
                continue;
            if (!(level.getBlockEntity(pos) instanceof CokeOvenBlockEntity be))
                continue;
            if (!be.isController() || be.size <= 1)
                continue;
            if (!isMatchingOven(pos, facing))
                continue;
            boolean intact = true;
            for (BlockPos part : BlockPos.betweenClosed(be.getBlockPos(),
                    be.getBlockPos().above(be.size - 1).relative(back, be.size - 1))) {
                if (!isMatchingOven(part, facing)) {
                    intact = false;
                    break;
                }
            }
            if (intact)
                return be;
        }
        return null;
    }

    private static boolean isInPlane(BlockPos corner, Direction back, int size, BlockPos pos) {
        int dx = pos.getX() - corner.getX();
        int dy = pos.getY() - corner.getY();
        int dz = pos.getZ() - corner.getZ();
        int along = dx * back.getStepX() + dz * back.getStepZ();
        int perpendicular = back.getStepX() == 0 ? dx : dz;
        return dy >= 0 && dy < size && along >= 0 && along < size && perpendicular == 0;
    }

    /**
     * The largest square an oven forms. The scan in {@link #createMultiblock}
     * starts at size 1 and grows once per step up to cokeOvenMaxSize, so the
     * default setting of 5 forms ovens up to 6x6, as the handbook says. The
     * inspector used to report the setting itself as the maximum.
     */
    public static int maxFormableSize() {
        return TFMGConfigs.common().machines.cokeOvenMaxSize.get() + 1;
    }

    public void setBlockStates(int size){

        // Only the front column shows doors. A smaller oven formed further
        // back before this one grew over it kept its own door states, so
        // doors and a second "top" were drawn in the middle of the wall.
        Direction back = getBlockState().getValue(FACING).getOpposite();
        for(int depth = 1; depth < size; depth++)
            for(int i = 0; i < size; i++) {
                BlockPos pos = getBlockPos().relative(back, depth).above(i);
                BlockState state = level.getBlockState(pos);
                if(state.is(TFMGBlocks.COKE_OVEN.get()) && state.getValue(CokeOvenBlock.CONTROLLER_TYPE) != CokeOvenBlock.ControllerType.CASUAL)
                    level.setBlock(pos, state.setValue(CokeOvenBlock.CONTROLLER_TYPE, CokeOvenBlock.ControllerType.CASUAL), 2);
            }

        if(size>1){
            level.setBlock(getBlockPos(), getBlockState().setValue(CokeOvenBlock.CONTROLLER_TYPE ,CokeOvenBlock.ControllerType.BOTTOM_ON), 2);
            level.setBlock(getBlockPos().above(size-1), getBlockState().setValue(CokeOvenBlock.CONTROLLER_TYPE ,CokeOvenBlock.ControllerType.TOP_ON), 2);
        } else
            level.setBlock(getBlockPos(), getBlockState().setValue(CokeOvenBlock.CONTROLLER_TYPE ,CokeOvenBlock.ControllerType.CASUAL), 2);

        for(int i = 0; i < size; i++) {
            BlockPos pos = getBlockPos().above(i);

            if (i > 0&&i != size-1) {
                level.setBlock(pos, getBlockState().setValue(CokeOvenBlock.CONTROLLER_TYPE, CokeOvenBlock.ControllerType.MIDDLE_ON), 2);
            }
        }
    }
    public void onPlaced(){
        createNextTick = true;
        updateOvenBlocks();
        if (level instanceof ServerLevel serverLevel)
           CatnipServices.NETWORK.sendToClientsTrackingChunk(serverLevel, new ChunkPos(getBlockPos()),new CokeOvenPacket(getBlockPos()));
    }
    @Override
    public void remove() {
        super.remove();
        updateOvenBlocks();
    }

    @Override
    public void destroy() {
        super.destroy();
        // Every block owns its slot; a member's is normally empty, but
        // anything left there is better dropped than deleted.
        ItemHelper.dropContents(level, worldPosition, inventory);
    }

    public void updateOvenBlocks(){
        int maxSize = TFMGConfigs.common().machines.cokeOvenMaxSize.get();
        Direction facing = getBlockState().getValue(FACING);

        for(BlockPos pos : BlockPos.betweenClosed(getBlockPos(), getBlockPos().below(maxSize).relative(facing,maxSize))){
           //
            if(level.getBlockEntity(pos) instanceof CokeOvenBlockEntity be){
                be.createMultiblock();

            }
        }
    }
    private void refreshCapability() {
        IFluidHandler oldPrimaryFluidCap = primaryFluidCapability;
        IFluidHandler oldSecondaryFluidCap = secondaryFluidCapability;
        IItemHandlerModifiable oldItemCap = itemCapability;

        CokeOvenBlockEntity be;
        if(level.getBlockEntity(controller) instanceof CokeOvenBlockEntity be1){
            be = be1;
        } else {
            controller = getBlockPos();
            be = (CokeOvenBlockEntity) level.getBlockEntity(getBlockPos());
        }
        primaryFluidCapability = be.primaryTank;
        secondaryFluidCapability = be.secondaryTank;
        itemCapability = be.inventory;
        invalidateCapabilities();
    }
    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
    //@Override
    //public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
//
    //    if(cap == ForgeCapabilities.FLUID_HANDLER){
    //        return side == Direction.UP ? secondaryFluidCapability.cast() : primaryFluidCapability.cast();
    //    }
    //    if(cap == ForgeCapabilities.ITEM_HANDLER)
    //        return itemCapability.cast();
//
    //    return super.getCapability(cap, side);
    //}
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                TFMGBlockEntities.COKE_OVEN.get(),
                // Both tanks only ever hold by-products: piped water used to
                // fill one and pause the oven with no way to tell why.
                (be, context) -> new DrainOnlyFluidHandler(context == Direction.UP ? be.secondaryFluidCapability : be.primaryFluidCapability)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                TFMGBlockEntities.COKE_OVEN.get(),
                // The slot only holds raw input (coke drops as an entity), so
                // a hopper under the oven was pulling the coal back out.
                (be, context) -> be.itemCapability == null ? null : new InsertOnlyItemHandler(be.itemCapability)
        );
    }
    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound,registries , clientPacket);
        compound.putInt("Timer", timer);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.put("PrimaryTankContent", primaryTank.writeToNBT(registries,new CompoundTag()));
        compound.put("SecondaryTankContent", secondaryTank.writeToNBT(registries,new CompoundTag()));
        compound.putLong("Controller", controller.asLong());
        compound.putInt("Size", size);
    }
    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound,registries , clientPacket);
        timer = compound.getInt("Timer");
        inventory.deserializeNBT(registries,compound.getCompound("Inventory"));
        primaryTank.readFromNBT(registries,compound.getCompound("PrimaryTankContent"));
        secondaryTank.readFromNBT(registries,compound.getCompound("SecondaryTankContent"));
        // Guarded like Size just below: getLong returns 0 for a missing key,
        // which would point every oven at (0,0,0) and make isController false
        // for all of them. The field already defaults to this block's position.
        if (compound.contains("Controller"))
            controller = BlockPos.of(compound.getLong("Controller"));
        if (compound.contains("Size"))
            size = Math.max(1, compound.getInt("Size"));
    }
}
