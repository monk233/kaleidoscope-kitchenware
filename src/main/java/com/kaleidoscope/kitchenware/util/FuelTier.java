package com.kaleidoscope.kitchenware.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Firepower of the firewood stove, decided by what the player burns.
 * The base mod reads only a single boolean heat source, so the tier changes how long
 * the fuel lasts rather than how fast the pot cooks.
 */
public enum FuelTier {
    LOW("low", 1.0F),
    MID("mid", 0.75F),
    HIGH("high", 0.5F);

    public final String key;
    private final float burnFactor;

    FuelTier(String key, float burnFactor) {
        this.key = key;
        this.burnFactor = burnFactor;
    }

    /** Ticks the stack keeps the stove lit, after the tier modifier and the config multiplier. */
    public int burnTicks(ItemStack stack) {
        int base = stack.getBurnTime(null);
        double multiplier = com.kaleidoscope.kitchenware.config.KitchenwareConfig.FUEL_BURN_MULTIPLIER.get();
        return Math.max(40, (int) (base * burnFactor * multiplier));
    }

    public static FuelTier of(ItemStack stack) {
        if (stack.is(Items.BLAZE_POWDER) || stack.is(Items.BLAZE_ROD) || stack.is(Items.LAVA_BUCKET)
                || stack.is(Items.COAL_BLOCK) || stack.is(Items.DRIED_KELP_BLOCK)) {
            return HIGH;
        }
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL) || stack.is(Items.BLAZE_ROD)) {
            return MID;
        }
        return LOW;
    }
}
