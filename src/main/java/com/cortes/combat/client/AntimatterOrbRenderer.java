package com.cortes.combat.client;

import com.cortes.combat.platform.ResourceIds;

import com.cortes.combat.entity.AntimatterOrb;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * El renderer del orbe de antimateria, y <b>dibuja exactamente nada</b>.
 *
 * <p>Por la misma razon que {@link MobCloudRenderer}, que la tiene escrita entera: un
 * {@code EntityType} sin renderer registrado no es invisible, es un <b>NPE en el cliente</b> --
 * {@code EntityRenderDispatcher} dereferencia lo que le devuelve el mapa sin guarda de nulo, en
 * {@code shouldRender} y en {@code render}. Asi que el renderer esta y su {@code render} esta
 * vacio.</p>
 *
 * <p>Lo que se ve del orbe son sus <b>particulas</b>: la cola de {@code REVERSE_PORTAL} mientras
 * viaja, los {@code PORTAL} que salen de cada cuerpo que tira, y el estallido del final. Eso lo
 * manda el servidor, como todo lo visible de este mod.</p>
 */
public class AntimatterOrbRenderer extends EntityRenderer<AntimatterOrb> {

    private static final ResourceLocation TEXTURE = ResourceIds.rl("textures/particle/purified_effect.png");

    public AntimatterOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** Nada. Ver la nota de la clase. */
    @Override
    public void render(AntimatterOrb entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // Vacio a proposito.
    }

    @Override
    public ResourceLocation getTextureLocation(AntimatterOrb entity) {
        return TEXTURE;
    }
}
