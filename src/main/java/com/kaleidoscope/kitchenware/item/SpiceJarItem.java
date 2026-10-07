package com.kaleidoscope.kitchenware.item;

import com.kaleidoscope.kitchenware.KaleidoscopeKitchenware;
import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
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
 * Vanilla applies the block entity data <em>after</em> calling {@code setPlacedBy}, so the
 * corner flags cannot be rebuilt there — they would be rebuilt from an empty entity. The
 * rebuild therefore happens here, once {@code super.place} has finished applying the data.
 */
public class SpiceJarItem extends BlockItem {
    public SpiceJarItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (!result.consumesAction()) {
            return result;
        }
        Level level = context.getLevel();
        if (level.isClientSide) {
            return result;
        }
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof SpiceJarBlockEntity jar)) {
            return result;
        }
        BlockState state = level.getBlockState(pos);
        BlockState restored = state;
        boolean anyFilled = false;
        for (int corner = 0; corner < SpiceJarBlock.JAR_COUNT; corner++) {
            boolean filled = !jar.isJarEmpty(corner);
            anyFilled |= filled;
            restored = restored.setValue(SpiceJarBlock.JARS[corner], filled);
        }
        KaleidoscopeKitchenware.LOGGER.info("[jar] placed, stored={} cornersFilled={}{}{}{}",
                jar.carryingTotal(), anyFilled && restored.getValue(SpiceJarBlock.JARS[0]),
                restored.getValue(SpiceJarBlock.JARS[1]), restored.getValue(SpiceJarBlock.JARS[2]),
                restored.getValue(SpiceJarBlock.JARS[3]));
        if (anyFilled && restored != state) {
            level.setBlockAndUpdate(pos, restored);
        }
        return result;
    }
}
