package com.kaleidoscope.kitchenware.event;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.kaleidoscope.kitchenware.registry.ModItems;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The base mod's kitchen shovel returns SUCCESS from its own useOn, so the block never
 * gets a chance to react. Intercepting the click event first is what makes scooping work.
 */
@EventBusSubscriber(modid = KaleidoscopeKitchenware.MOD_ID)
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
        event.setCanceled(true);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
            return;
        }
        int count = Math.max(1, SpiceJarBlock.jarCount(state));
        Block.popResource(level, pos, SpiceJarBlock.jarStack(jar, level.registryAccess()));
        for (int i = 1; i < count; i++) {
            Block.popResource(level, pos, new ItemStack(ModItems.SPICE_JAR.get()));
        }
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7F, 1.2F);
    }
}
