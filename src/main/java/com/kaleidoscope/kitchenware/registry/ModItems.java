package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.item.SpiceJarItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(KaleidoscopeKitchenware.MOD_ID);

    /** Every block item, in registration order, used to fill the creative tab. */
    public static final List<Supplier<? extends Item>> ALL = new ArrayList<>();

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    /** The spice jar is a BlockItem subclass, so it registers itself rather than via ModBlocks. */
    public static final DeferredHolder<Item, SpiceJarItem> SPICE_JAR = ITEMS.register("spice_jar",
            () -> new SpiceJarItem(ModBlocks.SPICE_JAR.get(), new Item.Properties().stacksTo(16)));

    static {
        ALL.add(SPICE_JAR);
    }

    static void blockItem(DeferredHolder<Block, ? extends Block> block) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(block.getId().getPath(),
                () -> new BlockItem(block.get(), new Item.Properties()));
        ALL.add(holder);
    }
}
