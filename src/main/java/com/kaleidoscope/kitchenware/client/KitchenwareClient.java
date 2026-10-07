package com.kaleidoscope.kitchenware.client;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = KaleidoscopeKitchenware.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KitchenwareClient {
    private KitchenwareClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SEASONING_TRAY.get(), SeasoningTrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.DISH_RACK.get(), DishRackRenderer::new);
    }
}
