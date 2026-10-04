package com.drmangotea.tfmg.base;

import com.drmangotea.tfmg.content.decoration.kinetics.encased.TFMGEncasedCogwheelBlock;
import com.drmangotea.tfmg.content.decoration.kinetics.encased.TFMGEncasedShaftBlock;
import com.drmangotea.tfmg.registry.TFMGEncasedBlocks;
import com.drmangotea.tfmg.content.machinery.misc.winding_machine.SpoolItem;
import com.drmangotea.tfmg.registry.TFMGBlocks;
import com.drmangotea.tfmg.registry.TFMGDataComponents;
import com.drmangotea.tfmg.registry.TFMGItems;
import com.simibubi.create.*;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;


import java.util.*;

import static com.drmangotea.tfmg.TFMG.MOD_ID;
import static com.drmangotea.tfmg.TFMG.REGISTRATE;

public class TFMGCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> REGISTER =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);


    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TFMG_MAIN = REGISTER.register("tfmg_main", () -> CreativeModeTab.builder()
            .withTabsBefore(AllCreativeModeTabs.BASE_CREATIVE_TAB.getId())
            .title(Component.translatable("creative_tab.tfmg_main"))
            .icon(()-> TFMGItems.STEEL_MECHANISM.get().asItem().getDefaultInstance())
           // .displayItems(new RegistrateDisplayItemsGenerator(true, TFMGCreativeTabs.TFMG_MAIN))
            .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TFMG_DECORATION = REGISTER.register("tfmg_decoration", () -> CreativeModeTab.builder()
            .withTabsBefore(TFMG_MAIN.getId())
            .title(Component.translatable("creative_tab.tfmg_decoration"))
            .icon(()-> TFMGBlocks.CONCRETE.block.get().asItem().getDefaultInstance())
           // .displayItems(new RegistrateDisplayItemsGenerator(true, TFMGCreativeTabs.TFMG_DECORATION))
            .build());
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {


        if(event.getTab() == TFMGCreativeTabs.TFMG_MAIN.get()){
            for(ItemStack stack : customAdditions()){
                // A component-less stack must stay out of the search entries.
                // The vanilla search tab copies every tab's search entries and
                // registrate's own listener then adds a plain stack of every
                // registered item on top, so a plain stack listed here with
                // search visibility is accepted twice over there, and that
                // duplicate is an IllegalArgumentException that kills server
                // startup. Stacks with components never equal the plain stack
                // registrate adds, so they keep their search entry.
                acceptOnce(event, stack, stack.getComponentsPatch().isEmpty()
                        ? CreativeModeTab.TabVisibility.PARENT_TAB_ONLY
                        : CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
            for(RegistryEntry<Item, ?> item : REGISTRATE.getAll(Registries.ITEM)){

                if(!CreateRegistrate.isInCreativeTab(item,TFMG_MAIN))
                    continue;
                if(blacklist().contains(item))
                    continue;
                // The encased blocks register under whichever creative tab the
                // registrate's mutable tab global holds when their class loads,
                // and a pack mod touching tfmg classes early moves that moment
                // before the pipes restore the decoration tab. The shafts are
                // already listed once through customAdditions above, so letting
                // this loop see them again crashed servers at startup with
                // "already exists in the tab's list". Both loops filter both
                // encased families, so their placement no longer depends on
                // class load order: shafts through customAdditions, cogwheels
                // hidden, matching upstream.
                if(item.get() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TFMGEncasedCogwheelBlock)
                    continue;
                if(item.get() instanceof BlockItem blockItem2 && blockItem2.getBlock() instanceof TFMGEncasedShaftBlock)
                    continue;
                if(item.get() instanceof SequencedAssemblyItem)
                    continue;
                if(item.get() instanceof SpoolItem&&!item.is(TFMGItems.EMPTY_SPOOL.get())){
                    continue;
                }
                acceptOnce(event, new ItemStack(item.get()), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
            }

        }

        if (event.getTab() == TFMG_DECORATION.get()){
            for(RegistryEntry<Item, Item> item : REGISTRATE.getAll(Registries.ITEM)){
                if(!CreateRegistrate.isInCreativeTab(item, TFMG_DECORATION))
                    continue;
                if(blacklist().contains(item))
                    continue;
                if(item.get() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TFMGEncasedCogwheelBlock)
                    continue;
                // Encased shafts are kinetics, and the encased cogwheels right
                // next to them were already filtered out of this tab. They are
                // listed in the main tab through customAdditions instead.
                if(item.get() instanceof BlockItem blockItem2 && blockItem2.getBlock() instanceof TFMGEncasedShaftBlock)
                    continue;
                if(item.get() instanceof SequencedAssemblyItem)
                    continue;

                acceptOnce(event, new ItemStack(item.get()), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);

            }

        }
    }

    // Accepts the stack only into the entry lists it is not already in,
    // checked per list because a stack can sit in one and not the other.
    // Another mod's listener can run before this one and list the same
    // stack, and accepting it again is a hard IllegalArgumentException
    // that kills the whole tab build - on a dedicated server that is a
    // startup crash - so every accept in this class goes through here.
    private static void acceptOnce(BuildCreativeModeTabContentsEvent event, ItemStack stack,
                                   CreativeModeTab.TabVisibility visibility) {
        boolean parent = visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY
                && !event.getParentEntries().contains(stack);
        boolean search = visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY
                && !event.getSearchEntries().contains(stack);
        if (parent && search)
            event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        else if (parent)
            event.accept(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        else if (search)
            event.accept(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
    }


    public static List<RegistryEntry<Item, ? extends Item>> blacklist(){
        List<RegistryEntry<Item, ? extends Item>> list = new ArrayList<>();

        list.add(TFMGItems.LIT_LITHIUM_BLADE);
        // Listed per line through customAdditions; a blank one shows nothing.
        list.add(TFMGItems.FACTORY_BLUEPRINT);
        list.add(TFMGItems.GOLDEN_TURBO);
        list.add(TFMGItems.ALUMINUM_SPOOL);
        list.add(TFMGItems.COPPER_SPOOL);
        list.add(TFMGItems.CONSTANTAN_SPOOL);
        // Debug-only item: it has no recipe and no JEI page, and it has no
        // business being browsable next to the real cinder blocks.
        list.add(TFMGItems.DEBUG_CINDERBLOCK);


        return list;
    }
    public static List<ItemStack> customAdditions(){
        List<ItemStack> list = new ArrayList<>();

        // Kinetics, listed here because the decoration tab filters them out.
        list.add(TFMGEncasedBlocks.STEEL_ENCASED_SHAFT.asStack());
        list.add(TFMGEncasedBlocks.HEAVY_CASING_ENCASED_SHAFT.asStack());

        // One Factory Blueprint per assembly line; the blank item stays hidden.
        for (String line : com.drmangotea.tfmg.content.items.blueprint.BlueprintLines.LINES)
            list.add(com.drmangotea.tfmg.content.items.blueprint.BlueprintLines.stack(line));

        ItemStack copperSpool = TFMGItems.COPPER_SPOOL.asStack();
        copperSpool.set(TFMGDataComponents.SPOOL_AMOUNT,1000);
        list.add(copperSpool);

        ItemStack aluminumSpool = TFMGItems.ALUMINUM_SPOOL.asStack();
        aluminumSpool.set(TFMGDataComponents.SPOOL_AMOUNT,1000);
        list.add(aluminumSpool);

        ItemStack constantanSpool = TFMGItems.CONSTANTAN_SPOOL.asStack();
        constantanSpool.set(TFMGDataComponents.SPOOL_AMOUNT,1000);
        list.add(constantanSpool);

        CompoundTag gasolineTag = new CompoundTag();
        gasolineTag.putString("gasoline", "c:gasoline");
        gasolineTag.putString("kerosene", "c:kerosene");
        gasolineTag.putString("naphtha", "c:naphtha");
        CompoundTag gasolineTagName = new CompoundTag();
        gasolineTagName.putString("gasoline", "fluid.tfmg.gasoline");
        gasolineTagName.putString("kerosene", "fluid.tfmg.kerosene");
        gasolineTagName.putString("naphtha", "fluid.tfmg.naphtha");
        //
        CompoundTag creosoteTag = new CompoundTag();
        creosoteTag.putString("creosote", "c:creosote");
        creosoteTag.putString("furnace_gas", "c:furnace_gas");
        CompoundTag creosoteTagName = new CompoundTag();
        creosoteTagName.putString("creosote", "fluid.tfmg.creosote");
        creosoteTagName.putString("furnace_gas", "fluid.tfmg.furnace_gas");
        //
        CompoundTag dieselTag = new CompoundTag();
        dieselTag.putString("diesel", "c:diesel");
        CompoundTag dieselTagName = new CompoundTag();
        dieselTagName.putString("diesel", "fluid.tfmg.diesel");
        //
        CompoundTag lpgTag = new CompoundTag();
        lpgTag.putString("lpg", "c:lpg");
        CompoundTag lpgTagName = new CompoundTag();
        lpgTagName.putString("lpg", "fluid.tfmg.lpg");
        //
        CompoundTag keroseneTag = new CompoundTag();
        keroseneTag.putString("kerosene", "c:kerosene");
        CompoundTag keroseneTagName = new CompoundTag();
        keroseneTagName.putString("kerosene", "fluid.tfmg.kerosene");
        //


        ItemStack gasoline = TFMGItems.ENGINE_CYLINDER.asStack();
        gasoline.set(TFMGDataComponents.FUELS, gasolineTagName);
        gasoline.set(TFMGDataComponents.FUEL_TAGS, gasolineTag);
        list.add(gasoline);
        ItemStack diesel = TFMGItems.DIESEL_ENGINE_CYLINDER.asStack();
        diesel.set(TFMGDataComponents.FUELS,  dieselTagName);
        diesel.set(TFMGDataComponents.FUEL_TAGS, dieselTag);
        list.add(diesel);
        ItemStack lpg = TFMGItems.ENGINE_CYLINDER.asStack();
        lpg.set(TFMGDataComponents.FUELS, lpgTagName);
        lpg.set(TFMGDataComponents.FUEL_TAGS, lpgTag);
        list.add(lpg);

        // Gas cylinder variants for the LPG separation products and for
        // electrolysis hydrogen (survival path: spout-fill an engine
        // cylinder with the matching gas).
        CompoundTag butaneTag = new CompoundTag();
        butaneTag.putString("butane", "c:butane");
        CompoundTag butaneTagName = new CompoundTag();
        butaneTagName.putString("butane", "fluid.tfmg.butane");
        ItemStack butane = TFMGItems.ENGINE_CYLINDER.asStack();
        butane.set(TFMGDataComponents.FUELS, butaneTagName);
        butane.set(TFMGDataComponents.FUEL_TAGS, butaneTag);
        list.add(butane);

        CompoundTag propaneTag = new CompoundTag();
        propaneTag.putString("propane", "c:propane");
        CompoundTag propaneTagName = new CompoundTag();
        propaneTagName.putString("propane", "fluid.tfmg.propane");
        ItemStack propane = TFMGItems.ENGINE_CYLINDER.asStack();
        propane.set(TFMGDataComponents.FUELS, propaneTagName);
        propane.set(TFMGDataComponents.FUEL_TAGS, propaneTag);
        list.add(propane);

        CompoundTag hydrogenTag = new CompoundTag();
        hydrogenTag.putString("hydrogen", "c:hydrogen");
        CompoundTag hydrogenTagName = new CompoundTag();
        hydrogenTagName.putString("hydrogen", "fluid.tfmg.hydrogen");
        ItemStack hydrogen = TFMGItems.ENGINE_CYLINDER.asStack();
        hydrogen.set(TFMGDataComponents.FUELS, hydrogenTagName);
        hydrogen.set(TFMGDataComponents.FUEL_TAGS, hydrogenTag);
        list.add(hydrogen);
        ItemStack creosote = TFMGItems.SIMPLE_ENGINE_CYLINDER.asStack();
        creosote.set(TFMGDataComponents.FUELS, creosoteTagName);
        creosote.set(TFMGDataComponents.FUEL_TAGS, creosoteTag);
        list.add(creosote);


        ItemStack kerosene = TFMGItems.TURBINE_BLADE.asStack();
        kerosene.set(TFMGDataComponents.FUELS, keroseneTagName);
        kerosene.set(TFMGDataComponents.FUEL_TAGS, keroseneTag);
        list.add(kerosene);

        return list;
    }

    @ApiStatus.Internal
    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }


}


