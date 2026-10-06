package com.kaleidoscope.kitchenware.registry;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    /** Items the cupboard will take: bowls and flower pots by default, extendable by datapack. */
    public static final TagKey<Item> CUPBOARD_STORABLE =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(
                    KaleidoscopeKitchenware.MOD_ID, "cupboard_storable"));

    private ModTags() {
    }
}
