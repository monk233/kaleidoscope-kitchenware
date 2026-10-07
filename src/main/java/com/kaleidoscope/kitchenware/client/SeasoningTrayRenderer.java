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
        Direction view = facing.getOpposite();
        Direction right = view.getClockWise();
        for (int slot = 0; slot < SeasoningTrayBlockEntity.COMPARTMENTS; slot++) {
            ItemStack stack = tray.stored(slot);
            if (stack.isEmpty()) {
                continue;
            }
            // same left/right and near/far axes the click uses, so the icon sits in the
            // compartment the player is aiming at
            double left = (slot % 2 == 0) ? -0.25D : 0.25D;
            double far = (slot < 2) ? 0.25D : -0.25D;
            double x = right.getStepX() * left + view.getStepX() * far;
            double z = right.getStepZ() * left + view.getStepZ() * far;
            pose.pushPose();
            pose.translate(0.5D + x, 0.12D, 0.5D + z);
            pose.scale(0.30F, 0.30F, 0.30F);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                    packedLight, packedOverlay, pose, buffer, tray.getLevel(), 0);
            pose.popPose();
        }
    }
}
