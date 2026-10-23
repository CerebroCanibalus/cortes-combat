package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.entity.MobCloud;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * El renderer de la nube de moscas, y <b>dibuja exactamente nada</b>.
 *
 * <h2>Por qué existe si no dibuja</h2>
 *
 * <p>Porque <b>un tipo sin renderer no es invisible: es un NPE en el cliente.</b> Verificado en las
 * fuentes de 1.21.1 -- {@code EntityRenderDispatcher} tiene
 * {@code shouldRender(entity, ...) = getRenderer(entity).shouldRender(...)} y lo mismo en
 * {@code render}, <b>dos lineas y sin guarda de nulo</b>. El mapa devuelve null para lo que nadie
 * registro, y {@code null.shouldRender(...)} no es "no se dibuja", es una excepcion en el render
 * thread la primera vez que el jugador mira hacia ahi.</p>
 *
 * <p>Asi que el renderer esta, con el cuerpo vacío, y <b>eso es correcto</b>: la nube se ve por las
 * {@link com.cortes.combat.particle.MoscaParticles} que el servidor le suelta adentro, que es donde
 * esta el arte y el comportamiento. Este archivo no dibuja porque no hay nada que dibujar, no por
 * faltarle ganas.</p>
 *
 * <p>La textura que devuelve <em>si</em> es honesta: es la del sprite de la mosca. Un renderer
 * obligado a nombrar algo tiene que nombrar lo que la entidad es, y esta nube es un monton de
 * esos sprites.</p>
 */
public class MobCloudRenderer extends EntityRenderer<MobCloud> {

    private static final ResourceLocation TEXTURE = CortesCombat.rl("textures/particle/mosca.png");

    public MobCloudRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** Nada. Ver la nota de la clase: la nube la dibujan las particulas, no este renderer. */
    @Override
    public void render(MobCloud entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // Vacío a proposito.
    }

    /** El sprite de la mosca, porque es lo que esta nube es. */
    @Override
    public ResourceLocation getTextureLocation(MobCloud entity) {
        return TEXTURE;
    }
}
