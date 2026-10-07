package com.kaleidoscope.kitchenware.event;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot;
import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The kitchen shovel works the jars the way it works the base mod's oil basin: it picks the
 * seasoning up onto the shovel, carries it, and drops it into a wok.
 *
 * The base mod's shovel returns SUCCESS from its own useOn, so the block never gets a chance
 * to react; the click has to be intercepted before that. Registered explicitly from the mod
 * class rather than by annotation, because a silently missing listener here is invisible.
 */
public final class SpiceJarEvents {
    private static final TagKey<Item> KITCHEN_SHOVEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "kitchen_shovel"));
    /** Key inside the shovel's custom data holding what it currently carries. */
    private static final String SCOOPED = "kaleidoscope_kitchenware:scooped_seasoning";

    private SpiceJarEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = event.getItemStack();
        if (!isShovel(held)) {
            return;
        }
        Player player = event.getEntity();

        if (state.getBlock() instanceof SpiceJarBlock) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
                return;
            }
            int index = SpiceJarBlock.resolveJarIndex(state, event.getHitVec().getLocation(), pos);
            if (index < 0) {
                tell(player, "state.kaleidoscope_kitchenware.jar_missing");
                return;
            }
            ItemStack carried = scooped(held, level.registryAccess());
            if (!carried.isEmpty()) {
                // shovel already loaded: tip it back into the jar
                if (jar.insert(index, carried, false) > 0) {
                    clearScooped(held);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
                    tell(player, "state.kaleidoscope_kitchenware.jar_returned", carried.getHoverName());
                } else {
                    tell(player, "state.kaleidoscope_kitchenware.storage_full");
                }
                return;
            }
            ItemStack scooped = jar.extract(index, player.isShiftKeyDown());
            if (scooped.isEmpty()) {
                tell(player, "state.kaleidoscope_kitchenware.storage_empty");
                return;
            }
            setScooped(held, scooped, level.registryAccess());
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.3F);
            tell(player, "state.kaleidoscope_kitchenware.jar_scooped_on_shovel", scooped.getHoverName());
            return;
        }

        // anything else: if the shovel carries seasoning and we clicked a wok, tip it in
        ItemStack carried = scooped(held, level.registryAccess());
        if (carried.isEmpty() || !(level.getBlockEntity(pos) instanceof IPot pot)) {
            return;
        }
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        if (level.isClientSide) {
            return;
        }
        if (pot.addIngredient(level, player, carried.copy())) {
            clearScooped(held);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.1F);
            tell(player, "state.kaleidoscope_kitchenware.jar_poured", carried.getHoverName());
        }
    }

    private static boolean isShovel(ItemStack stack) {
        return stack.is(KITCHEN_SHOVEL) || stack.is(ItemTags.SHOVELS);
    }

    /** What the shovel is carrying, empty when it carries nothing. */
    public static ItemStack scooped(ItemStack shovel, RegistryAccess access) {
        CustomData data = shovel.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(SCOOPED) ? ItemStack.parseOptional(access, tag.getCompound(SCOOPED)) : ItemStack.EMPTY;
    }

    private static void setScooped(ItemStack shovel, ItemStack seasoning, RegistryAccess access) {
        CompoundTag tag = new CompoundTag();
        tag.put(SCOOPED, seasoning.save(access));
        shovel.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearScooped(ItemStack shovel) {
        shovel.remove(DataComponents.CUSTOM_DATA);
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
