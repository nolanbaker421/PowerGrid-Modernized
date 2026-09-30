package com.nolanbaker.pgmodernized.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.inspector.ElectricalInspectorEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** A villager with a hi-vis vest and a hard hat: the plain villager skin, then our overlay on top. */
public class ElectricalInspectorRenderer extends MobRenderer<ElectricalInspectorEntity, VillagerModel<ElectricalInspectorEntity>> {
    private static final ResourceLocation BASE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation OVERLAY = PowerGridModernized.asResource("textures/entity/electrical_inspector.png");

    public ElectricalInspectorRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
        addLayer(new Overlay(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ElectricalInspectorEntity entity) {
        return BASE;
    }

    private static class Overlay extends RenderLayer<ElectricalInspectorEntity, VillagerModel<ElectricalInspectorEntity>> {
        Overlay(RenderLayerParent<ElectricalInspectorEntity, VillagerModel<ElectricalInspectorEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ElectricalInspectorEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if(entity.isInvisible())
                return;
            renderColoredCutoutModel(getParentModel(), OVERLAY, poseStack, buffer, packedLight, entity, -1);
        }
    }
}
