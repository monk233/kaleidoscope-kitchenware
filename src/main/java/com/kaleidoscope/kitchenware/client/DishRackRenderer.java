package com.kaleidoscope.kitchenware.client;

import com.kaleidoscope.kitchenware.block.DishRackBlock;
import com.kaleidoscope.kitchenware.blockentity.DishRackBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws what is on the rack: bowls and pots standing upright, facing out, lined up along the shelf
 * the way plates stand in a real drying rack.
 *
 * Items are drawn upright as they are — no tilt. A tilt about the horizontal axis lays an item
 * flat, which is what the first version did and why the bowls looked like plates lying down.
 */
public class DishRackRenderer implements BlockEntityRenderer<DishRackBlockEntity> {
    /** How many pieces are drawn on a shelf, whatever the count above that. */
    private static final int SHOWN_PER_SHELF = 4;
    /** How far from the centre the outermost piece sits. */
    private static final double PLACE_SPAN = 0.34D;
    /** Distance between neighbouring pieces. */
    private static final double PLACE_STEP = 0.2267D;
    private static final double UPPER_SHELF = 0.62D;
    private static final double LOWER_SHELF = 0.2D;
    private static final float SCALE = 0.5F;

    public DishRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DishRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        Direction facing = rack.getBlockState().getValue(DishRackBlock.FACING);
        Direction view = facing.getOpposite();
        Direction right = view.getClockWise();

        for (int shelf = 0; shelf < DishRackBlockEntity.SHELVES; shelf++) {
            ItemStack stack = rack.stored(shelf);
            if (stack.isEmpty()) {
                continue;
            }
            int shown = Math.min(stack.getCount(), SHOWN_PER_SHELF);
            double height = shelf == 0 ? UPPER_SHELF : LOWER_SHELF;
            for (int place = 0; place < shown; place++) {
                double along = -PLACE_SPAN + place * PLACE_STEP;
                double x = 0.5D + right.getStepX() * along;
                double z = 0.5D + right.getStepZ() * along;
                pose.pushPose();
                pose.translate(x, height, z);
                // face the rack's way; leave the item upright. The extra quarter turn lines the
                // opening up with the front of the rack rather than along it
                pose.mulPose(Axis.YP.rotationDegrees(facing.toYRot() + 90.0F));
                pose.scale(SCALE, SCALE, SCALE);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                        packedLight, packedOverlay, pose, buffer, rack.getLevel(), 0);
                pose.popPose();
            }
        }
    }
}
