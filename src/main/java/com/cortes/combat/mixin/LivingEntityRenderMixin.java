package com.cortes.combat.mixin;

import com.cortes.combat.client.DamageFlash;
import com.cortes.combat.client.HealthFlashBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Convierte el flash en <b>una barra de vida</b>: envuelve el buffer del modelo para que el rojo se
 * retire hacia abajo a medida que el mob se vacía.
 *
 * <h2>El seam, y el dueño verificado con {@code javap}</h2>
 *
 * <p>{@code LivingEntityRenderer.render} arma el buffer y se lo pasa al modelo:</p>
 *
 * <pre>VertexConsumer vertexconsumer = buffer.getBuffer(rendertype);
 * int i = getOverlayCoords(entity, ...);
 * this.model.renderToBuffer(poseStack, vertexconsumer, packedLight, i, ...);</pre>
 *
 * <p><b>Y la entidad está en scope justo ahí</b> -- que es lo que hace que la vida del mob esté a
 * mano sin ningún estado global ni un attachment: el umbral se calcula en el sitio de render y se
 * hornea en el envoltorio.</p>
 *
 * <p><b>El dueño del {@code invokevirtual} se leyó del bytecode, ⊘ se adivinó:</b> {@code javap -c}
 * sobre {@code LivingEntityRenderer} da
 * {@code Method net/minecraft/client/model/EntityModel.renderToBuffer:(...PoseStack;...VertexConsumer;III)V}.
 * Es <b>{@code EntityModel}</b> y no {@code Model}, que es la clase que lo <i>declara</i> -- y esa
 * diferencia es la que decide si el mixin aplica o falla en silencio.</p>
 *
 * <h2>Cuándo NO se envuelve</h2>
 *
 * <p>Dos casos, y los dos son <b>el cuerpo entero</b>:</p>
 *
 * <ul>
 *   <li><b>El frame del impacto.</b> El negro de los primeros ticks tiene que cubrir todo -- es el
 *       golpe llegando, no la vida que queda.</li>
 *   <li><b>La muerte.</b> El blanco es un hecho, no una barra.</li>
 * </ul>
 *
 * <p>Y en los dos el umbral es cero, o sea que <b>no habría nada que borrar</b>: envolver sería
 * pagar un objeto y seis delegaciones por vértice para no cambiar nada. Se devuelve el buffer
 * original y el costo es cero.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRenderMixin {

    @ModifyArg(
            method = "render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 1)
    private VertexConsumer cortescombat$healthBar(VertexConsumer original, Entity entity,
                                                  float entityYaw, float partialTicks,
                                                  PoseStack poseStack, MultiBufferSource buffer,
                                                  int packedLight) {
        // El tipo erasa a `Entity` porque el renderer es generico, pero `LivingEntityRenderer` solo
        // existe para `T extends LivingEntity`, asi que el cast no puede fallar.
        if (!(entity instanceof LivingEntity living)) {
            return original;
        }
        boolean wholeBody = living.deathTime > 0
                || living.hurtTime > DamageFlash.FLASH_TICKS - DamageFlash.IMPACT_TICKS;
        if (wholeBody) {
            return original;
        }
        float threshold = DamageFlash.barThreshold(living.getHealth(), living.getMaxHealth(),
                living.getBbHeight() * DamageFlash.MODEL_UNITS_PER_BLOCK);
        if (threshold <= 0.0F) {
            return original;
        }
        return new HealthFlashBuffer(original, threshold, OverlayTexture.NO_OVERLAY);
    }
}
