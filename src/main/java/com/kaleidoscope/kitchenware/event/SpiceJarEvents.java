package com.kaleidoscope.kitchenware.event;

import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Scooping seasoning out of a jar with the kitchen shovel.
 *
 * The base mod's shovel returns SUCCESS from its own useOn, so the block never gets a chance
 * to react; the click has to be intercepted before that. Registered explicitly from the mod
 * class rather than by annotation, because a silently missing listener here is invisible.
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
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
            return;
        }

        Player player = event.getEntity();
        int index = SpiceJarBlock.jarIndexAt(event.getHitVec().getLocation(), pos);
        if (!state.getValue(SpiceJarBlock.JARS[index])) {
            player.displayClientMessage(
                    Component.translatable("state.kaleidoscope_kitchenware.jar_missing"), true);
            return;
        }
        ItemStack scooped = jar.extract(index, player.isShiftKeyDown());
        if (scooped.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("state.kaleidoscope_kitchenware.storage_empty"), true);
            return;
        }
        player.getInventory().placeItemBackInInventory(scooped);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.3F);
        player.displayClientMessage(Component.translatable(
                "state.kaleidoscope_kitchenware.jar_scooped", scooped.getHoverName()), true);
    }
}
