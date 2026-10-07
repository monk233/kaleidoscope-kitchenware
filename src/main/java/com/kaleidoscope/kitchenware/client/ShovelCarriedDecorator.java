package com.kaleidoscope.kitchenware.client;

import com.kaleidoscope.kitchenware.event.SeasoningTrayEvents;
import com.kaleidoscope.kitchenware.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * Draws what the kitchen shovel is carrying in the top left of its inventory slot: the seasoning,
 * bowl or flower pot itself, and a water bucket for the water scooped out of a vat.
 *
 * The shovel's own glint is not used for this any more — an enchantment shimmer says "something is
 * on the shovel" without saying what.
 */
@EventBusSubscriber(modid = com.kaleidoscope.kitchenware.KaleidoscopeKitchenware.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShovelCarriedDecorator implements IItemDecorator {
    private static final ResourceLocation KITCHEN_SHOVEL =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "kitchen_shovel");

    private ShovelCarriedDecorator() {
    }

    @SubscribeEvent
    public static void onRegisterDecorators(RegisterItemDecorationsEvent event) {
        // Item tags are not loaded while registrations run, so tagging would match nothing here.
        // Match on the registry names instead: every shovel the base mod ships.
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (id.getNamespace().equals("kaleidoscope_cookery") && id.getPath().contains("shovel")) {
                event.register(entry.getValue(), new ShovelCarriedDecorator());
            }
        }
    }

    @Override
    public boolean render(GuiGraphics gui, Font font, ItemStack stack, int x, int y) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        ItemStack icon = SeasoningTrayEvents.carried(stack, level.registryAccess());
        if (icon.isEmpty() && SeasoningTrayEvents.hasWater(stack)) {
            icon = new ItemStack(Items.WATER_BUCKET);
        }
        if (icon.isEmpty()) {
            return false;
        }
        gui.pose().pushPose();
        gui.pose().translate(x, y, 200.0F);
        gui.pose().scale(0.5F, 0.5F, 1.0F);
        gui.renderItem(icon, 0, 0);
        gui.pose().popPose();
        return true;
    }
}
