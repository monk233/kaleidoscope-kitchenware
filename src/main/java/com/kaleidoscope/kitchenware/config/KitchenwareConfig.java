package com.kaleidoscope.kitchenware.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** All tunables in one place. Defaults match the behaviour described in the design doc. */
public final class KitchenwareConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue FUEL_BURN_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue HOOD_TIER_BONUS;
    public static final ModConfigSpec.BooleanValue ENABLE_SMOKE_COUGH;
    public static final ModConfigSpec.IntValue CHIMNEY_EFFICIENCY;
    public static final ModConfigSpec.IntValue NO_CHIMNEY_EFFICIENCY;
    public static final ModConfigSpec.BooleanValue VAT_CONSUMES_LEVEL;
    public static final ModConfigSpec.IntValue CUPBOARD_CAPACITY;
    public static final ModConfigSpec.IntValue SPICE_RACK_CAPACITY;

    static {
        BUILDER.push("stove");
        FUEL_BURN_MULTIPLIER = BUILDER
                .comment("Multiplier on how long one fuel item keeps the firewood stove lit.")
                .defineInRange("fuelBurnMultiplier", 1.0D, 0.1D, 10.0D);
        HOOD_TIER_BONUS = BUILDER
                .comment("Whether a working range hood pushes the stove one fire tier higher.")
                .define("hoodTierBonus", true);
        BUILDER.pop();

        BUILDER.push("rangeHood");
        ENABLE_SMOKE_COUGH = BUILDER
                .comment("Whether standing by an unvented lit stove gives the smoke cough effect.")
                .define("enableSmokeCough", true);
        CHIMNEY_EFFICIENCY = BUILDER
                .comment("Venting efficiency percent when a chimney sits above the range hood.")
                .defineInRange("chimneyEfficiency", 100, 0, 100);
        NO_CHIMNEY_EFFICIENCY = BUILDER
                .comment("Venting efficiency percent when there is no chimney.")
                .defineInRange("noChimneyEfficiency", 50, 0, 100);
        BUILDER.pop();

        BUILDER.push("waterVat");
        VAT_CONSUMES_LEVEL = BUILDER
                .comment("Whether taking water lowers the vat level. Off means an endless supply.")
                .define("consumesLevel", false);
        BUILDER.pop();

        BUILDER.push("storage");
        CUPBOARD_CAPACITY = BUILDER
                .comment("Cupboard slots. Values above 27 are clamped to 27.")
                .defineInRange("cupboardCapacity", 16, 1, 27);
        SPICE_RACK_CAPACITY = BUILDER
                .comment("Spice rack slots. Values above 16 are clamped to 16.")
                .defineInRange("spiceRackCapacity", 8, 1, 16);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private KitchenwareConfig() {
    }
}
