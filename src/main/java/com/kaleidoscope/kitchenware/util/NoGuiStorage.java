package com.kaleidoscope.kitchenware.util;

import com.kaleidoscope.kitchenware.blockentity.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The shared no-GUI storage interaction used by the cupboard and the spice rack.
 *
 * Hand holding a storable item -> store one. Empty hand, or Shift to force it -> take one.
 * Nothing opens; feedback goes through the action bar and sounds.
 */
public final class NoGuiStorage {
    private NoGuiStorage() {
    }

    public static ItemInteractionResult interact(ItemStack stack, Level level, BlockPos pos, Player player,
                                                 InteractionHand hand, StorageBlockEntity storage) {
        if (hand == InteractionHand.OFF_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean forceTake = player.isShiftKeyDown();

        if (!forceTake && !stack.isEmpty() && storage.accepts(stack)) {
            if (!storage.insertOne(stack)) {
                tell(player, "state.kaleidoscope_kitchenware.storage_full");
                return ItemInteractionResult.FAIL;
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.4F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (stack.isEmpty() || forceTake) {
            ItemStack taken = storage.extractOne();
            if (taken.isEmpty()) {
                tell(player, "state.kaleidoscope_kitchenware.storage_empty");
                return ItemInteractionResult.FAIL;
            }
            player.getInventory().placeItemBackInInventory(taken);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5F, 0.9F);
            return ItemInteractionResult.SUCCESS;
        }

        if (!storage.accepts(stack)) {
            tell(player, "state.kaleidoscope_kitchenware.storage_rejects");
            return ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
