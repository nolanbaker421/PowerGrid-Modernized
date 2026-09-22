package com.nolanbaker.pgmodernized.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** For entities that exist only for the simulation, such as the conductors inside conduit. */
public class NoopEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public NoopEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
        return false;
    }
}
