package com.kaleidoscope.kitchenware.item;

import com.kaleidoscope.kitchenware.blockentity.SeasoningTrayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The tray in item form. Its contents ride in the stack's own custom data, so a picked-up tray
 * keeps its seasoning and can be put down anywhere.
 */
public class SeasoningTrayItem extends BlockItem {
    public SeasoningTrayItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (!result.consumesAction() || context.getLevel().isClientSide()) {
            return result;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof SeasoningTrayBlockEntity tray) {
            tray.loadFromItem(context.getItemInHand(), level.registryAccess());
        }
        return result;
    }
}
