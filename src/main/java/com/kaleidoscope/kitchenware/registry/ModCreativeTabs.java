package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KaleidoscopeKitchenware.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> KITCHEN = TABS.register("kitchen",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.kaleidoscope_kitchenware.kitchen"))
                    .icon(() -> new ItemStack(ModBlocks.FIREWOOD_STOVE.get()))
                    .displayItems((parameters, output) -> ModItems.ALL.forEach(item -> output.accept(item.get())))
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
