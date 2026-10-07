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
    /** Height of the first layer inside the dish. Clear of the well floor, not buried in it. */
    private static final float BASE_HEIGHT = 0.12F;
    /** How much each further layer rises; sixteen layers end up just proud of the dish rim. */
    private static final float LAYER_STEP = 0.0125F;
    /** Twist of the first pair of layers; each pair opens a little wider than the last. */
    private static final float LAYER_TWIST = 16.0F;
    private static final float SCALE = 0.4F;
    /**
     * Distance from the dish centre to a compartment centre. The dish is a 16px model whose four
     * wells sit at 4.5px and 11.5px, which is 0.22 blocks from the middle: aiming at 0.25 put
     * every pile off toward the corner.
     */
    private static final double WELL_OFFSET = 0.22D;
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
            double left = (slot % 2 == 0) ? -WELL_OFFSET : WELL_OFFSET;
            double far = (slot < 2) ? WELL_OFFSET : -WELL_OFFSET;
            double x = 0.5D + right.getStepX() * left + view.getStepX() * far;
            double z = 0.5D + right.getStepZ() * left + view.getStepZ() * far;
            for (int layer = 0; layer < layers; layer++) {
                pose.pushPose();
                pose.translate(x, BASE_HEIGHT + layer * LAYER_STEP, z);
                // -90 puts the item's face up: +90 turned it face down, so a flat item showed its
                // back to the sky
                pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
                // every other layer is flipped end for end, so the pile looks interleaved rather
                // than like one item rotated a little
                if (layer % 2 == 1) {
                    pose.mulPose(Axis.YP.rotationDegrees(180.0F));
                }
                float twist = (layer % 2 == 0 ? -1.0F : 1.0F) * LAYER_TWIST * ((layer / 2) + 1);
                pose.mulPose(Axis.ZP.rotationDegrees(twist));
                pose.scale(SCALE, SCALE, SCALE);
                // FIXED rather than NONE: NONE is the "no context" model transform and the base mod
                // ships an item render replacer, which is not reliable under it
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                        packedLight, packedOverlay, pose, buffer, tray.getLevel(), 0);
                pose.popPose();
            }
        }
    }
}
