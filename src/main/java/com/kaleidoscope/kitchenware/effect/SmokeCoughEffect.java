package com.kaleidoscope.kitchenware.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Smoke cough: picked up by standing next to a lit stove that nobody is venting.
 * Install a powered range hood over the stove to stop getting it.
 */
public class SmokeCoughEffect extends MobEffect {
    public SmokeCoughEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A4A4A);
    }
}
