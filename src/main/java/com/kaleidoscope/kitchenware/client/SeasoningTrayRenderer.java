package com.kaleidoscope.kitchenware.client;

import com.kaleidoscope.kitchenware.block.SeasoningTrayBlock;
import com.kaleidoscope.kitchenware.blockentity.SeasoningTrayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws a small icon inside each compartment so the tray shows what it holds. */
public class SeasoningTrayRenderer implements BlockEntityRenderer<SeasoningTrayBlockEntity> {
    public SeasoningTrayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SeasoningTrayBlockEntity tray, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        Direction facing = tray.getBlockState().getValue(SeasoningTrayBlock.FACING);
        for (int slot = 0; slot < SeasoningTrayBlockEntity.COMPARTMENTS; slot++) {
            ItemStack stack = tray.stored(slot);
            if (stack.isEmpty()) {
                continue;
            }
            double[] local = compartmentOffset(slot);
            // rotate the local offset so the icons sit on the face the tray shows
            double x = local[0];
            double z = local[1];
            double rx = switch (facing) {
                case SOUTH -> -x;
                case EAST -> -z;
                case WEST -> z;
                default -> x;
            };
            double rz = switch (facing) {
                case SOUTH -> -z;
                case EAST -> x;
                case WEST -> -x;
                default -> z;
            };
            pose.pushPose();
            pose.translate(0.5D + rx, local[2], 0.5D + rz);
            pose.scale(0.35F, 0.35F, 0.35F);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                    packedLight, packedOverlay, pose, buffer, tray.getLevel(), 0);
            pose.popPose();
        }
    }

    /** x offset, z offset and height of each compartment, in block space from the centre. */
    private static double[] compartmentOffset(int slot) {
        double side = (slot % 2 == 0) ? -0.22D : 0.22D;
        double height = (slot < 2) ? 0.58D : 0.28D;
        return new double[]{side, -0.22D, height};
    }
}
