package com.nolanbaker.pgmodernized.ac.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nolanbaker.pgmodernized.ac.sync.SynchroscopeBlock;
import com.nolanbaker.pgmodernized.ac.sync.SynchroscopeBlockEntity;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import static com.nolanbaker.pgmodernized.PowerGridModernized.asResource;

/** Turns the synchroscope's needle: straight up when the incoming is in phase with the bus, round at the slip. */
public class SynchroscopeRenderer extends SafeBlockEntityRenderer<SynchroscopeBlockEntity> {
    public static final ModelResourceLocation NEEDLE = ModelResourceLocation.standalone(asResource("block/synchroscope/needle"));

    public SynchroscopeRenderer(BlockEntityRendererProvider.Context context) {}

    /** Registered on the mod bus. */
    public static final class Models {
        private Models() {}

        @SubscribeEvent
        public static void registerModels(ModelEvent.RegisterAdditional event) {
            event.register(NEEDLE);
        }
    }

    @Override
    protected void renderSafe(SynchroscopeBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        var state = be.getBlockState();
        if(!(state.getBlock() instanceof SynchroscopeBlock))
            return;
        var facing = SynchroscopeBlock.facing(state);
        var model = Minecraft.getInstance().getModelManager().getModel(NEEDLE);
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var consumer = buffer.getBuffer(RenderType.cutout());

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);
        ms.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180)));
        ms.translate(-0.5, -0.5, -0.5);
        // Turn about the dial centre; the needle model points up from it. A leading incoming turns clockwise as the viewer sees it.
        ms.translate(SynchroscopeBlock.DIAL_X / 16, SynchroscopeBlock.DIAL_Y / 16, SynchroscopeBlock.DIAL_Z / 16);
        ms.mulPose(Axis.ZP.rotationDegrees(be.angleNow(partialTicks)));
        ms.translate(-SynchroscopeBlock.DIAL_X / 16, -SynchroscopeBlock.DIAL_Y / 16, -SynchroscopeBlock.DIAL_Z / 16);
        renderer.renderModel(ms.last(), consumer, null, model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        ms.popPose();
    }
}
