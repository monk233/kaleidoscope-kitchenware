package com.kaleidoscope.kitchenware;

import com.kaleidoscope.kitchenware.config.KitchenwareConfig;
import com.kaleidoscope.kitchenware.event.SeasoningTrayEvents;
import com.kaleidoscope.kitchenware.event.StoveBurnerEvents;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import com.kaleidoscope.kitchenware.registry.ModBlocks;
import com.kaleidoscope.kitchenware.registry.ModCreativeTabs;
import com.kaleidoscope.kitchenware.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(KaleidoscopeKitchenware.MOD_ID)
public class KaleidoscopeKitchenware {
    public static final String MOD_ID = "kaleidoscope_kitchenware";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KaleidoscopeKitchenware(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, KitchenwareConfig.SPEC);

        // game bus: the shovel's scoop has to intercept the click before its own useOn eats it
        NeoForge.EVENT_BUS.register(SeasoningTrayEvents.class);
        // ... and the burners have to refuse the base mod's pans at placement time
        NeoForge.EVENT_BUS.register(StoveBurnerEvents.class);

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
    }
}
