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

    /**
     * Vanilla hands the water tint to {@code Blocks.WATER}, bubble columns and the water cauldron,
     * which is the whole reason a cauldron's water looks blue while its texture is greyscale. The
     * vat is not on that list, so it registers its own: without this the surface stays grey however
     * the model is written.
     */
    @SubscribeEvent
    public static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, index) -> level != null && pos != null
                        ? net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, pos)
                        : 0x3F76E4,
                com.kaleidoscope.kitchenware.registry.ModBlocks.WATER_VAT.get());
    }
}
