package com.kaleidoscope.kitchenware.client;

import com.kaleidoscope.kitchenware.block.SpiceJarBlock;
import com.kaleidoscope.kitchenware.blockentity.SpiceJarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a small floating icon of whatever each jar holds, so four jars in one block are
 * told apart at a glance.
 */
public class SpiceJarRenderer implements BlockEntityRenderer<SpiceJarBlockEntity> {
    public SpiceJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SpiceJarBlockEntity jar, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        BlockState state = jar.getBlockState();
        for (int index = 0; index < SpiceJarBlockEntity.JAR_COUNT; index++) {
            if (!state.getValue(SpiceJarBlock.JARS[index])) {
                continue;
            }
            ItemStack display = jar.displayItem(index);
            if (display.isEmpty()) {
                continue;
            }
            double[] offset = cornerOffset(index);
            pose.pushPose();
            // sit the icon inside the glass, low enough to read as "contents in a jar"
            pose.translate(offset[0], 0.10D, offset[1]);
            pose.scale(0.32F, 0.32F, 0.32F);
            Minecraft.getInstance().getItemRenderer().renderStatic(display, ItemDisplayContext.GROUND,
                    packedLight, packedOverlay, pose, buffer, jar.getLevel(), 0);
            pose.popPose();
        }
    }

    private static double[] cornerOffset(int index) {
        return switch (index) {
            case 0 -> new double[]{0.28D, 0.28D};   // north-west
            case 1 -> new double[]{0.72D, 0.28D};   // north-east
            case 2 -> new double[]{0.28D, 0.72D};   // south-west
            default -> new double[]{0.72D, 0.72D};  // south-east
        };
    }
}
