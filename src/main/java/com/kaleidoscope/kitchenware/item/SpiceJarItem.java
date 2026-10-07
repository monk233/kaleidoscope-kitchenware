package com.kaleidoscope.kitchenware.item;

import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The jar in item form. Contents ride along in the block entity data component, so a picked-up
 * jar keeps its seasoning and can be put down again anywhere.
 *
 * Vanilla applies the block entity data <em>after</em> {@code setPlacedBy} and loads it into the
 * first slot range, so the corner flags cannot be rebuilt there and the contents do not line up
 * with the corner the player aimed at. Both are settled here, after {@code super.place} has put
 * the data in place. The same settling code runs in the startup self test.
 */
public class SpiceJarItem extends BlockItem {
    public SpiceJarItem(Block block, Item.Properties properties) {
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
        BlockState state = level.getBlockState(pos);
        int placedCorner = 0;
        for (int corner = 0; corner < SpiceJarBlock.JAR_COUNT; corner++) {
            if (state.getValue(SpiceJarBlock.JARS[corner])) {
                placedCorner = corner;
                break;
            }
        }
        SpiceJarBlock.settlePlacement(level, pos, placedCorner);
        return result;
    }
}
