package com.nolanbaker.pgmodernized.client;

import com.nolanbaker.pgmodernized.mob.PushBroomEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.util.Mth;

/** A broom standing on its bristles: a long handle, a wooden head and a brush of straw. */
public class PushBroomModel extends HierarchicalModel<PushBroomEntity> {
    private final ModelPart root;
    private final ModelPart broom;

    public PushBroomModel(ModelPart root) {
        this.root = root;
        this.broom = root.getChild("broom");
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var parts = mesh.getRoot();
        var broom = parts.addOrReplaceChild("broom", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        broom.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -31, -1, 2, 24, 2), PartPose.ZERO);
        broom.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 30).addBox(-8, -7, -2, 16, 3, 4), PartPose.ZERO);
        broom.addOrReplaceChild("bristles", CubeListBuilder.create().texOffs(0, 40).addBox(-8, -4, -2, 16, 4, 4), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(PushBroomEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        // Wobbles as it hops, leans into its stride, and swings forward hard on a sweep.
        broom.zRot = Mth.cos(limbSwing * 0.7f) * 0.35f * limbSwingAmount;
        broom.xRot = 0.25f * limbSwingAmount + Mth.sin(ageInTicks * 0.1f) * 0.03f;
        broom.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.3f;
        float swing = entity.attackAnim;
        if(swing > 0)
            broom.xRot += Mth.sin(swing * Mth.PI) * 0.9f;
    }
}
