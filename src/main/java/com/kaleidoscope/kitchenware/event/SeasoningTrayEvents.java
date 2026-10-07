package com.kaleidoscope.kitchenware.event;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot;
import com.github.ysbbbbbb.kaleidoscopecookery.item.KitchenShovelItem;
import com.kaleidoscope.kitchenware.block.SeasoningTrayBlock;
import com.kaleidoscope.kitchenware.blockentity.SeasoningTrayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * The kitchen shovel scoops seasoning out of a tray compartment, and pours it back.
 *
 * It follows the base mod's oil basin exactly, which means two paths:
 * <ul>
 *   <li>oil: the shovel takes the base mod's own "has oil" flag, so right-clicking a wok
 *       afterwards runs the base mod's oil code, identical to scooping from the basin</li>
 *   <li>anything else: the shovel carries the actual item, and right-clicking a wok feeds it
 *       in through the same call a held seasoning item would use</li>
 * </ul>
 *
 * Registered explicitly from the mod class: a silently missing listener here is invisible, and
 * the shovel's own useOn would otherwise swallow the click.
 */
public final class SeasoningTrayEvents {
    private static final ResourceLocation OIL_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "oil");
    private static final TagKey<Item> KITCHEN_SHOVEL = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "kitchen_shovel"));
    /** Key inside the shovel's custom data holding what it currently carries. */
    private static final String SCOOPED = "kaleidoscope_kitchenware:scooped_seasoning";

    private SeasoningTrayEvents() {
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

        // a dish rack: the shovel lifts a bowl or pot off the shelf it points at
        if (state.getBlock() instanceof com.kaleidoscope.kitchenware.block.DishRackBlock) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide
                    || !(level.getBlockEntity(pos) instanceof com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity rack)) {
                return;
            }
            int shelf = com.kaleidoscope.kitchenware.block.DishRackBlock.shelfAt(event.getHitVec(), pos);
            ItemStack lifted = rack.takeLast(shelf);
            if (lifted.isEmpty()) {
                tell(player, "state.kaleidoscope_kitchenware.rack_shelf_empty");
                return;
            }
            player.getInventory().placeItemBackInInventory(lifted);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.1F);
            tell(player, "state.kaleidoscope_kitchenware.rack_lifted", lifted.getHoverName());
            return;
        }

        if (state.getBlock() instanceof SeasoningTrayBlock) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SeasoningTrayBlockEntity tray)) {
                return;
            }
            int slot = SeasoningTrayBlock.compartmentAt(event.getHitVec(), pos,
                    state.getValue(SeasoningTrayBlock.FACING));

            // the shovel is loaded with oil: put one portion back, exactly like the basin
            if (KitchenShovelItem.hasOil(held)) {
                ItemStack oil = new ItemStack(BuiltInRegistries.ITEM.get(OIL_ID));
                if (oil.isEmpty() || tray.insert(slot, oil, false) == 0) {
                    tell(player, "state.kaleidoscope_kitchenware.tray_full");
                    return;
                }
                KitchenShovelItem.setHasOil(held, false);
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
                tell(player, "state.kaleidoscope_kitchenware.tray_returned", oil.getHoverName());
                return;
            }

            // the shovel carries some other seasoning: tip it back in
            ItemStack carried = scooped(held, level.registryAccess());
            if (!carried.isEmpty()) {
                if (tray.insert(slot, carried, false) > 0) {
                    clearScooped(held);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
                    tell(player, "state.kaleidoscope_kitchenware.tray_returned", carried.getHoverName());
                } else {
                    tell(player, "state.kaleidoscope_kitchenware.tray_full");
                }
                return;
            }

            // scoop one portion out
            ItemStack portion = tray.extractOne(slot);
            if (portion.isEmpty()) {
                tell(player, "state.kaleidoscope_kitchenware.tray_empty");
                return;
            }
            if (isOil(portion)) {
                KitchenShovelItem.setHasOil(held, true);
            } else {
                setScooped(held, portion, level.registryAccess());
            }
            level.playSound(null, pos, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
            tell(player, "state.kaleidoscope_kitchenware.tray_scooped", portion.getHoverName());
            return;
        }

        // a wok: only seasoning we carry ourselves is handled here. Oil on the shovel is left to
        // the base mod, which already knows what to do with it.
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
            tell(player, "state.kaleidoscope_kitchenware.tray_poured", carried.getHoverName());
        } else {
            // the wok only takes seasoning once it has oil and is cooking
            tell(player, "state.kaleidoscope_kitchenware.tray_wok_refused", carried.getHoverName());
        }
    }

    private static boolean isShovel(ItemStack stack) {
        return stack.is(KITCHEN_SHOVEL) || stack.is(ItemTags.SHOVELS);
    }

    private static boolean isOil(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(OIL_ID);
    }

    /** What the shovel is carrying, empty when it carries nothing. */
    public static ItemStack scooped(ItemStack shovel, RegistryAccess access) {
        CustomData data = shovel.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(SCOOPED) ? ItemStack.parseOptional(access, tag.getCompound(SCOOPED))
                : ItemStack.EMPTY;
    }

    private static void setScooped(ItemStack shovel, ItemStack seasoning, RegistryAccess access) {
        CompoundTag tag = new CompoundTag();
        tag.put(SCOOPED, seasoning.save(access));
        shovel.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        shovel.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    private static void clearScooped(ItemStack shovel) {
        shovel.remove(DataComponents.CUSTOM_DATA);
        shovel.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
