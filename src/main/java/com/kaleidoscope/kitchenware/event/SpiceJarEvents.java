package com.kaleidoscope.kitchenware.event;

import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The base mod's kitchen shovel returns SUCCESS from its own useOn, so the block never gets
 * a chance to react. Intercepting the click first is what makes scooping work.
 *
 * Registered explicitly from the mod class: relying on the annotation alone is one silent
 * failure mode too many for an interaction this fiddly.
 */
public final class SpiceJarEvents {
    private static final TagKey<Item> KITCHEN_SHOVEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "kitchen_shovel"));

    private SpiceJarEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SpiceJarBlock)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(KITCHEN_SHOVEL) && !held.is(ItemTags.SHOVELS)) {
            return;
        }
        // stop the shovel's own useOn from swallowing the click, on both sides
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        if (level.isClientSide) {
            return;
        }
        // removing the block is enough: onRemove drops one jar with its contents plus the empties
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 1.2F);
    }
}
