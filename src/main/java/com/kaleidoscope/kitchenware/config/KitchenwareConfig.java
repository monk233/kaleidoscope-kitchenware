package com.kaleidoscope.kitchenware.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** All tunables in one place. Defaults match the behaviour described in the design doc. */
public final class KitchenwareConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue FUEL_BURN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue VAT_CONSUMES_LEVEL;

    static {
        BUILDER.push("stove");
        FUEL_BURN_MULTIPLIER = BUILDER
                .comment("Multiplier on how long one fuel item keeps the firewood stove lit.")
                .defineInRange("fuelBurnMultiplier", 1.0D, 0.1D, 10.0D);
        BUILDER.pop();

        BUILDER.push("waterVat");
        VAT_CONSUMES_LEVEL = BUILDER
                .comment("Whether taking water lowers the vat level. Off means an endless supply.")
                .define("consumesLevel", false);
        BUILDER.pop();

        BUILDER.push("storage");
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private KitchenwareConfig() {
    }
}
