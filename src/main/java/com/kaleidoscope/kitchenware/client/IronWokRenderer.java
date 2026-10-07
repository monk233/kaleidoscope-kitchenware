package com.kaleidoscope.kitchenware.client;

import com.kaleidoscope.kitchenware.blockentity.IronWokBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Draws what is in the wok: the ingredients while they cook, and the finished dish once it is done.
 *
 * Everything is tiny and sits just above the liquid, because the bowl is only ten pixels across at
 * the surface. Positions come from the block position rather than a running tick, so a stack of
 * ingredients does not jitter as the wok ticks.
 */
public class IronWokRenderer implements BlockEntityRenderer<IronWokBlockEntity> {
    private static final float SCALE = 0.25F;
    private static final double RADIUS = 0.08D;
    private static final float SURFACE = -0.22F;
    private final ItemRenderer itemRenderer;

    public IronWokRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = Minecraft.getInstance().getItemRenderer();
    }

    @Override
    public void render(IronWokBlockEntity wok, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        List<ItemStack> shown = wok.displayedStacks();
        if (shown.isEmpty()) {
            return;
        }
        int seed = (int) wok.getBlockPos().asLong();
        for (int index = 0; index < shown.size(); index++) {
            ItemStack stack = shown.get(index);
            if (stack.isEmpty()) {
                continue;
            }
            double angle = (index * 2.4D) + (seed % 360) * 0.01D;
            float x = (float) (RADIUS * Mth.cos((float) angle));
            float z = (float) (RADIUS * Mth.sin((float) angle));
            pose.pushPose();
            pose.translate(0.5D + x, SURFACE + (index % 3) * 0.012D, 0.5D + z);
            pose.mulPose(Axis.YP.rotationDegrees((float) (angle * Mth.RAD_TO_DEG)));
            pose.mulPose(Axis.XP.rotationDegrees(70.0F));
            pose.scale(SCALE, SCALE, SCALE);
            itemRenderer.renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, wok.getLevel(), seed + index);
            pose.popPose();
        }
    }
}
