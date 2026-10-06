package com.kaleidoscope.kitchenware.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The jar in item form. Contents ride along in the block entity data component, so a
 * scooped-up jar keeps its seasoning and can be put down again.
 */
public class SpiceJarItem extends BlockItem {
    public SpiceJarItem(Block block, Item.Properties properties) {
        super(block, properties);
    }
}
