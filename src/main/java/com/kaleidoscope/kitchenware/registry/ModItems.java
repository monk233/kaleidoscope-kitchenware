package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.item.SeasoningTrayItem;
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

    /** The tray carries its contents in its own custom data, so it holds one stack at a time. */
    public static final DeferredHolder<Item, SeasoningTrayItem> SEASONING_TRAY = ITEMS.register("seasoning_tray",
            () -> new SeasoningTrayItem(ModBlocks.SEASONING_TRAY.get(), new Item.Properties().stacksTo(1)));

    static {
        ALL.add(SEASONING_TRAY);
    }

    static void blockItem(DeferredHolder<Block, ? extends Block> block) {
        DeferredHolder<Item, BlockItem> holder = ITEMS.register(block.getId().getPath(),
                () -> new BlockItem(block.get(), new Item.Properties()));
        ALL.add(holder);
    }
}
