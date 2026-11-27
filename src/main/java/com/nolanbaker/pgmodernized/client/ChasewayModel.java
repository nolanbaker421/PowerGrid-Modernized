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
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
 * The chaseway's model: the block it went through, drawn from that block's own model with its
 * own tints and render layers, and on top the sleeve ends of every slot in use, each a small
 * standalone model. Wraps the baked chaseway model when baking finishes; registered on the mod bus.
 */
public class ChasewayModel extends BakedModelWrapper<BakedModel> {
    private static final ModelResourceLocation[] SLOTS = new ModelResourceLocation[ChasewayBlock.SLOT_COUNT];

    static {
        for(int s = 0; s < SLOTS.length; ++s)
            SLOTS[s] = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("powergrid_modernized", "block/chaseway_slot_" + s));
    }

    public ChasewayModel(BakedModel base) {
        super(base);
    }

    @SubscribeEvent
    public static void registerSlots(ModelEvent.RegisterAdditional event) {
        for(var slot : SLOTS)
            event.register(slot);
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

    private static int sleeves(ModelData data) {
        var bits = data.get(ChasewayBlockEntity.SLEEVES);
        return bits == null ? 0 : bits;
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
        if(renderType == null || renderType == RenderType.solid()) {
            int bits = sleeves(data);
            var manager = Minecraft.getInstance().getModelManager();
            for(int s = 0; s < SLOTS.length; ++s)
                if((bits & (1 << s)) != 0)
                    quads.addAll(manager.getModel(SLOTS[s]).getQuads(state, side, rand, ModelData.EMPTY, renderType));
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        var own = ChunkRenderTypeSet.of(RenderType.solid());
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
