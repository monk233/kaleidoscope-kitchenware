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
    /** Key marking the shovel as carrying water scooped from a vat. */
    private static final String WATER = "kaleidoscope_kitchenware:scooped_water";

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

        // a dish rack: put the carried bowl back, or lift one off the shelf being pointed at
        if (state.getBlock() instanceof com.kaleidoscope.kitchenware.block.DishRackBlock) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide
                    || !(level.getBlockEntity(pos) instanceof com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity rack)) {
                return;
            }
            int shelf = com.kaleidoscope.kitchenware.block.DishRackBlock.shelfAt(event.getHitVec(), pos);
            ItemStack holding = carried(held, level.registryAccess());
            if (!holding.isEmpty()
                    && com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity.accepts(holding)) {
                int moved = rack.insert(shelf, holding);
                if (moved > 0) {
                    clearCarried(held);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
                    tell(player, "state.kaleidoscope_kitchenware.rack_returned", holding.getHoverName());
                } else {
                    tell(player, "state.kaleidoscope_kitchenware.rack_shelf_full");
                }
                return;
            }
            if (!holding.isEmpty() || KitchenShovelItem.hasOil(held)) {
                tell(player, "state.kaleidoscope_kitchenware.shovel_busy");
                return;
            }
            ItemStack lifted = rack.takeOne(shelf);
            if (lifted.isEmpty()) {
                tell(player, "state.kaleidoscope_kitchenware.rack_shelf_empty");
                return;
            }
            setCarried(held, lifted, level.registryAccess());
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.1F);
            tell(player, "state.kaleidoscope_kitchenware.rack_lifted", lifted.getHoverName());
            return;
        }

        // a stockpot: water on the shovel becomes its soup base
        if (state.getBlock() instanceof com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock
                && hasWater(held)
                && level.getBlockEntity(pos) instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot stockpot) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide) {
                return;
            }
            if (stockpot.addSoupBase(level, player, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET))) {
                clearCarried(held);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
                tell(player, "state.kaleidoscope_kitchenware.shovel_poured_water");
            } else {
                tell(player, "state.kaleidoscope_kitchenware.shovel_water_refused");
            }
            return;
        }

        // Any water source: a bucket's worth onto the shovel, or tipped back. A source block, a
        // waterlogged block and a full cauldron all count, and scooping does not drain them, the
        // same way the vat behaves.
        boolean waterSource = state.is(net.minecraft.world.level.block.Blocks.WATER)
                || state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)
                || (state.hasProperty(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                    && state.getValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED));
        if (waterSource) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide) {
                return;
            }
            if (hasWater(held)) {
                clearCarried(held);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
                tell(player, "state.kaleidoscope_kitchenware.shovel_returned_water");
                return;
            }
            if (!carried(held, level.registryAccess()).isEmpty() || KitchenShovelItem.hasOil(held)) {
                tell(player, "state.kaleidoscope_kitchenware.shovel_busy");
                return;
            }
            setWater(held);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            tell(player, "state.kaleidoscope_kitchenware.shovel_scooped_water");
            return;
        }

        // a water vat: scoop water onto the shovel, or tip it back in
        if (state.getBlock() instanceof com.kaleidoscope.kitchenware.block.WaterVatBlock) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide) {
                return;
            }
            if (hasWater(held)) {
                clearCarried(held);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
                tell(player, "state.kaleidoscope_kitchenware.shovel_returned_water");
                return;
            }
            if (!carried(held, level.registryAccess()).isEmpty() || KitchenShovelItem.hasOil(held)) {
                tell(player, "state.kaleidoscope_kitchenware.shovel_busy");
                return;
            }
            boolean consumes = com.kaleidoscope.kitchenware.config.KitchenwareConfig.VAT_CONSUMES_LEVEL.get();
            int waterLevel = state.getValue(com.kaleidoscope.kitchenware.block.WaterVatBlock.LEVEL);
            if (consumes && waterLevel <= 0) {
                tell(player, "state.kaleidoscope_kitchenware.vat_empty");
                return;
            }
            if (consumes) {
                level.setBlockAndUpdate(pos, state.setValue(com.kaleidoscope.kitchenware.block.WaterVatBlock.LEVEL,
                        waterLevel - 1));
            }
            setWater(held);
            level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
            tell(player, "state.kaleidoscope_kitchenware.shovel_scooped_water");
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
            ItemStack carried = carried(held, level.registryAccess());
            if (!carried.isEmpty()) {
                if (tray.insert(slot, carried, false) > 0) {
                    clearCarried(held);
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
                setCarried(held, portion, level.registryAccess());
            }
            level.playSound(null, pos, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
            tell(player, "state.kaleidoscope_kitchenware.tray_scooped", portion.getHoverName());
            return;
        }

        // A bowl or pot on the shovel, pointing at a pot: serve the dish out with that bowl.
        //
        // The base mod checks the carrier against whatever item it is handed, and its own click path
        // only ever hands it the main hand item - which a shovel is not, so a shovel can never pass
        // that test when the pot has a carrier. Handing it the bowl from the shovel satisfies the
        // check, and the pot consumes it from there.
        ItemStack payload = carried(held, level.registryAccess());
        if (!payload.isEmpty()
                && com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity.accepts(payload)
                && level.getBlockEntity(pos) instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IPot pot) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide) {
                return;
            }
            if (pot.takeOutProduct(level, player, payload)) {
                clearCarried(held);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
                tell(player, "state.kaleidoscope_kitchenware.shovel_served");
            } else {
                tell(player, "state.kaleidoscope_kitchenware.shovel_nothing_to_serve");
            }
            return;
        }
        if (!payload.isEmpty()
                && com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity.accepts(payload)
                && level.getBlockEntity(pos) instanceof com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot stockpot) {
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            if (level.isClientSide) {
                return;
            }
            if (stockpot.takeOutProduct(level, player, payload)) {
                clearCarried(held);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.0F);
                tell(player, "state.kaleidoscope_kitchenware.shovel_served");
            } else {
                tell(player, "state.kaleidoscope_kitchenware.shovel_nothing_to_serve");
            }
            return;
        }
        if (!payload.isEmpty()
                && com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity.accepts(payload)) {
            return;
        }

        // a wok: only seasoning we carry ourselves is handled here. Oil on the shovel is left to
        // the base mod, which already knows what to do with it.
        ItemStack carried = carried(held, level.registryAccess());
        if (carried.isEmpty() || !(level.getBlockEntity(pos) instanceof IPot pot)) {
            return;
        }
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        if (level.isClientSide) {
            return;
        }
        if (pot.addIngredient(level, player, carried.copy())) {
            clearCarried(held);
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
    public static ItemStack carried(ItemStack shovel, RegistryAccess access) {
        CustomData data = shovel.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(SCOOPED) ? ItemStack.parseOptional(access, tag.getCompound(SCOOPED))
                : ItemStack.EMPTY;
    }

    private static void setCarried(ItemStack shovel, ItemStack seasoning, RegistryAccess access) {
        CompoundTag tag = new CompoundTag();
        tag.put(SCOOPED, seasoning.save(access));
        shovel.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearCarried(ItemStack shovel) {
        shovel.remove(DataComponents.CUSTOM_DATA);
    }

    /** Marks the shovel as carrying water. */
    private static void setWater(ItemStack shovel) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(WATER, true);
        shovel.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean hasWater(ItemStack shovel) {
        CustomData data = shovel.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(WATER);
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }
}
