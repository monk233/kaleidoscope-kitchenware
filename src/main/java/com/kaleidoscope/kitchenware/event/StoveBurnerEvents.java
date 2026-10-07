package com.kaleidoscope.kitchenware.event;

import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.PotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.StockpotBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.TeapotBlock;
import com.kaleidoscope.kitchenware.block.FirewoodStoveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * A stove burner takes the added iron wok and nothing else.
 *
 * The base mod's own pans and pots are free-standing blocks that can be dropped anywhere, so the
 * only place to say no is the placement event. Other blocks - a torch, a lantern - are left alone;
 * refusing those would make the stove a protected zone for no reason.
 */
public final class StoveBurnerEvents {
    private StoveBurnerEvents() {
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockState(pos.below()).getBlock() instanceof FirewoodStoveBlock)) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        if (!(placed.getBlock() instanceof PotBlock
                || placed.getBlock() instanceof StockpotBlock
                || placed.getBlock() instanceof SteamerBlock
                || placed.getBlock() instanceof TeapotBlock)) {
            return;
        }
        event.setCanceled(true);
        if (level instanceof ServerLevel && event.getEntity() instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable("state.kaleidoscope_kitchenware.burner_wok_only"), true);
        }
    }
}
