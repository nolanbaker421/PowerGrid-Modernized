package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.PowerGridModernized;
import com.nolanbaker.pgmodernized.mob.PushBroomEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class PushBroomRenderer extends MobRenderer<PushBroomEntity, PushBroomModel> {
    private static final ResourceLocation TEXTURE = PowerGridModernized.asResource("textures/entity/push_broom.png");

    public PushBroomRenderer(EntityRendererProvider.Context context) {
        // Baked here rather than through a registered layer: nothing else shares the mesh.
        super(context, new PushBroomModel(PushBroomModel.createBodyLayer().bakeRoot()), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(PushBroomEntity entity) {
        return TEXTURE;
    }
}
