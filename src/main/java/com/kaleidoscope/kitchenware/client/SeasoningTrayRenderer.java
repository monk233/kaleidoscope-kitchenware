package com.kaleidoscope.kitchenware.client;

import com.kaleidoscope.kitchenware.block.SeasoningTrayBlock;
import com.kaleidoscope.kitchenware.blockentity.SeasoningTrayBlockEntity;
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
 * Draws the seasoning in a compartment as a stack of flat layers.
 *
 * Each of a compartment's sixteen stacks is one layer, lying flat, lifted slightly and turned a
 * few degrees from the one below so the pile reads as a pile. Beyond sixteen layers the picture
 * would no longer change, so that is the cap.
 */
public class SeasoningTrayRenderer implements BlockEntityRenderer<SeasoningTrayBlockEntity> {
    /** Height of the first layer inside the dish. */
    private static final float BASE_HEIGHT = 0.055F;
    /** How much each further layer rises. Sixteen layers stay inside the dish walls. */
    private static final float LAYER_STEP = 0.0075F;
    /** Twist per layer, which is what makes the pile look like a pile. */
    private static final float LAYER_TWIST = 9.0F;
    private static final float SCALE = 0.26F;
    /** The pile is drawn at most this many layers deep. */
    private static final int MAX_LAYERS = 16;

    public SeasoningTrayRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SeasoningTrayBlockEntity tray, float partialTick, PoseStack pose, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        Direction facing = tray.getBlockState().getValue(SeasoningTrayBlock.FACING);
        Direction view = facing.getOpposite();
        Direction right = view.getClockWise();
        for (int slot = 0; slot < SeasoningTrayBlockEntity.COMPARTMENTS; slot++) {
            int layers = Math.min(tray.storedStacks(slot), MAX_LAYERS);
            ItemStack stack = tray.stored(slot);
            if (layers == 0 || stack.isEmpty()) {
                continue;
            }
            // same left/right and near/far axes the click uses, so the pile sits in the
            // compartment the player is aiming at
            double left = (slot % 2 == 0) ? -0.25D : 0.25D;
            double far = (slot < 2) ? 0.25D : -0.25D;
            double x = 0.5D + right.getStepX() * left + view.getStepX() * far;
            double z = 0.5D + right.getStepZ() * left + view.getStepZ() * far;
            for (int layer = 0; layer < layers; layer++) {
                pose.pushPose();
                pose.translate(x, BASE_HEIGHT + layer * LAYER_STEP, z);
                pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(layer * LAYER_TWIST));
                pose.scale(SCALE, SCALE, SCALE);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE,
                        packedLight, packedOverlay, pose, buffer, tray.getLevel(), 0);
                pose.popPose();
            }
        }
    }
}
