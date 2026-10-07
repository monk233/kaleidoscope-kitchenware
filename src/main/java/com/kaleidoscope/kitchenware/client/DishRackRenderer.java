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
 * Draws what is on the rack: each bowl or pot stands on edge, facing out, lined up along the shelf
 * the way plates stand in a real drying rack.
 */
public class DishRackRenderer implements BlockEntityRenderer<DishRackBlockEntity> {
    /** How far from the centre the outermost place sits. */
    private static final double PLACE_SPAN = 0.36D;
    /** Distance between neighbouring places. */
    private static final double PLACE_STEP = 0.24D;
    private static final double UPPER_SHELF = 0.72D;
    private static final double LOWER_SHELF = 0.28D;
    private static final float SCALE = 0.45F;

    public DishRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DishRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        Direction facing = rack.getBlockState().getValue(DishRackBlock.FACING);
        Direction view = facing.getOpposite();
        Direction right = view.getClockWise();
        // tilt about the axis running across the rack, so a bowl tips onto its side facing out
        Axis tilt = facing.getAxis() == Direction.Axis.X ? Axis.ZP : Axis.XP;

        for (int slot = 0; slot < DishRackBlockEntity.SLOTS; slot++) {
            ItemStack stack = rack.stored(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int shelf = slot / DishRackBlockEntity.SHELF_SLOTS;
            int place = slot % DishRackBlockEntity.SHELF_SLOTS;
            double along = -PLACE_SPAN + place * PLACE_STEP;
            double height = shelf == 0 ? UPPER_SHELF : LOWER_SHELF;
            double x = 0.5D + right.getStepX() * along;
            double z = 0.5D + right.getStepZ() * along;

            pose.pushPose();
            pose.translate(x, height, z);
            pose.mulPose(tilt.rotationDegrees(90.0F));
            pose.scale(SCALE, SCALE, SCALE);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                    packedLight, packedOverlay, pose, buffer, rack.getLevel(), 0);
            pose.popPose();
        }
    }
}
