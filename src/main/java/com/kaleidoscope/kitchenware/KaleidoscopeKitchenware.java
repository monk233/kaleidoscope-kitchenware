package com.kaleidoscope.kitchenware;

import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.registry.ModBlocks;
import com.kaleidoscope.kitchenware.registry.ModCreativeTabs;
import com.kaleidoscope.kitchenware.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(KaleidoscopeKitchenware.MOD_ID)
public class KaleidoscopeKitchenware {
    public static final String MOD_ID = "kaleidoscope_kitchenware";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KaleidoscopeKitchenware(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
    }
}
