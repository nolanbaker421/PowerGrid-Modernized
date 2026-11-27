package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ChasewayBlock;
import com.nolanbaker.pgmodernized.conduit.ChasewayBlockEntity;
import com.nolanbaker.pgmodernized.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The chaseway's model: the block it went through, drawn from that block's own model with its
 * own tints and render layers, and the sleeve ends from the chaseway's model on top. Wraps the
 * baked chaseway models when baking finishes; registered on the mod bus.
 */
public class ChasewayModel extends BakedModelWrapper<BakedModel> {
    public ChasewayModel(BakedModel sleeve) {
        super(sleeve);
    }

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for(var state : ModBlocks.CHASEWAY.get().getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            var model = models.get(key);
            if(model != null)
                models.put(key, new ChasewayModel(model));
        }
    }

    /** Grass, leaves and the like keep their tint when chased: the colour comes from the block that was there. */
    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if(level == null || pos == null)
                return -1;
            var material = ChasewayBlock.material(level, pos);
            return material == null ? -1 : Minecraft.getInstance().getBlockColors().getColor(material, level, pos, tint);
        }, ModBlocks.CHASEWAY.get());
    }

    @Nullable
    private static BlockState material(ModelData data) {
        return data.get(ChasewayBlockEntity.MATERIAL);
    }

    private static BakedModel modelOf(BlockState material) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        var material = material(data);
        if(material == null)
            return super.getQuads(state, side, rand, data, renderType);
        var quads = new ArrayList<BakedQuad>();
        var model = modelOf(material);
        if(renderType == null || model.getRenderTypes(material, rand, ModelData.EMPTY).contains(renderType))
            quads.addAll(model.getQuads(material, side, rand, ModelData.EMPTY, renderType));
        if(renderType == null || super.getRenderTypes(state, rand, data).contains(renderType))
            quads.addAll(super.getQuads(state, side, rand, data, renderType));
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        var own = super.getRenderTypes(state, rand, data);
        var material = material(data);
        return material == null ? own : ChunkRenderTypeSet.union(own, modelOf(material).getRenderTypes(material, rand, ModelData.EMPTY));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        var material = material(data);
        return material == null ? super.getParticleIcon(data) : modelOf(material).getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        var material = material(data);
        return material == null ? super.useAmbientOcclusion(state, data, renderType) : modelOf(material).useAmbientOcclusion(material, ModelData.EMPTY, renderType);
    }
}
