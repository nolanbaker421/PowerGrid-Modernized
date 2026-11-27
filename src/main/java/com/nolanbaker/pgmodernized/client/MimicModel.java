package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.conduit.ChasewayBlockEntity;
import com.nolanbaker.pgmodernized.conduit.gang.GangBoxBlock;
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
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A block set into a wall, drawn as the wall it replaced with its own model on top: the inset gang
 * box. The wall comes from the block entity's model data (the same property the chaseway uses),
 * with its tints and render layers; the box's own baked model, the plate flush on the face, is
 * drawn as it is. Wraps the inset states' models when baking finishes; registered on the mod bus.
 */
public class MimicModel extends BakedModelWrapper<BakedModel> {
    public MimicModel(BakedModel base) {
        super(base);
    }

    @SubscribeEvent
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for(var block : List.of(ModBlocks.GANG_BOX_1, ModBlocks.GANG_BOX_2, ModBlocks.GANG_BOX_3, ModBlocks.GANG_BOX_4)) {
            for(var state : block.get().getStateDefinition().getPossibleStates()) {
                if(!GangBoxBlock.inset(state))
                    continue;
                var key = BlockModelShaper.stateToModelLocation(state);
                var model = models.get(key);
                if(model != null)
                    models.put(key, new MimicModel(model));
            }
        }
    }

    /** Grass, leaves and the like keep their tint: the colour comes from the wall that was there. */
    @SubscribeEvent
    public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if(level == null || pos == null)
                return -1;
            var material = GangBoxBlock.material(level, pos);
            return material == null ? -1 : Minecraft.getInstance().getBlockColors().getColor(material, level, pos, tint);
        }, ModBlocks.GANG_BOX_1.get(), ModBlocks.GANG_BOX_2.get(), ModBlocks.GANG_BOX_3.get(), ModBlocks.GANG_BOX_4.get());
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
        var quads = new ArrayList<BakedQuad>();
        var material = material(data);
        if(material != null) {
            var model = modelOf(material);
            if(renderType == null || model.getRenderTypes(material, rand, ModelData.EMPTY).contains(renderType))
                quads.addAll(model.getQuads(material, side, rand, ModelData.EMPTY, renderType));
        }
        if(renderType == null || originalModel.getRenderTypes(state, rand, data).contains(renderType))
            quads.addAll(originalModel.getQuads(state, side, rand, data, renderType));
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        var own = originalModel.getRenderTypes(state, rand, data);
        var material = material(data);
        return material == null ? own : ChunkRenderTypeSet.union(own, modelOf(material).getRenderTypes(material, rand, ModelData.EMPTY));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        var material = material(data);
        return material == null ? super.getParticleIcon(data) : modelOf(material).getParticleIcon(ModelData.EMPTY);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        var material = material(data);
        return material == null ? super.useAmbientOcclusion(state, data, renderType) : modelOf(material).useAmbientOcclusion(material, ModelData.EMPTY, renderType);
    }
}
