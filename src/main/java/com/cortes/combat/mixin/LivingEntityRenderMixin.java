package com.cortes.combat.mixin;

import com.cortes.combat.client.DamageFlash;
import com.cortes.combat.client.HealthFlashBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
 * <h2>Y el metodo del target tiene DOS descriptores, uno de ellos un bridge vacio</h2>
 *
 * <p><b>Esto costo el primer arranque del cliente</b>, con {@code Scanned 0 target(s)} -- que se
 * lee como "el {@code @At} esta mal" y no lo estaba. La causa es que
 * {@code LivingEntityRenderer} <b>declara dos</b> metodos llamados {@code render}:</p>
 *
 * <pre>public void render(T, ...)                                  descriptor (Lnet/minecraft/world/entity/LivingEntity;FF...)V
 * public void render(net.minecraft.world.entity.Entity, ...)  descriptor (Lnet/minecraft/world/entity/Entity;FF...)V</pre>
 *
 * <p>El segundo es un <b>bridge method</b> que genera {@code javac}: {@code EntityRenderer<T extends
 * Entity>} declara {@code render(T, ...)} y esta clase lo sobrescribe con un bound <b>mas
 * estrecho</b> ({@code LivingEntity}), asi que el compilador emite un puente con el descriptor del
 * padre que <b>solo castea y delega</b>. Un puente no tiene el cuerpo: <b>no tiene el
 * {@code invokevirtual} adentro</b>, asi que apuntarle da cero puntos de inyeccion.</p>
 *
 * <p><b>La regla, que vale para cualquier {@code @Inject} sobre un metodo generico:</b> el
 * descriptor del <i>target</i> es el <b>del bound declarado</b> -- {@code LivingEntity} -- y
 * <b>no</b> el de la clase padre.</p>
 *
 * <h2>Por que {@code @Redirect} y no {@code @ModifyArg}</h2>
 *
 * <p><b>Segundo error de la misma ronda, y el que eligio el injector.</b> Con {@code @ModifyArg} el
 * cliente murio con:</p>
 *
 * <pre>@ModifyArg ... targets a method with an invalid signature
 * (Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III),
 * expected (Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/LivingEntity;FF...;I)</pre>
 *
 * <p>La segunda firma es <b>el handler entero</b> -- un argumento del invoke mas los seis del
 * contenedor -- asi que el problema no era el descriptor ni la captura: <b>{@code @ModifyArg}
 * valida el handler contra la firma del metodo invocado</b> y con argumentos de contexto no cierra.
 * {@code @Redirect} es el injector que el proyecto <b>ya tiene probado</b> para esto
 * ({@code OverlayTextureMixin} redirige {@code setPixelRGBA} y recibe el receptor mas sus tres
 * argumentos), y es el correcto acá porque el reemplazo necesita <b>la entidad del contenedor</b>
 * para saber cuánta vida le queda.</p>
 *
 * <p><b>Y se toma s\u00f3lo el primer argumento del contenedor</b> ({@code LivingEntity}): Mixin
 * permite declarar un prefijo de los argumentos del target, y pedir los seis sería declarar cinco
 * que nadie usa.</p>
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
 * pagar un objeto y seis delegaciones por vértice para no cambiar nada. Se deja pasar el buffer
 * original y el costo es cero.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRenderMixin {

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void cortescombat$healthBar(EntityModel<?> model, PoseStack poseStack,
                                        VertexConsumer vertexConsumer, int packedLight,
                                        int packedOverlay, int colour, LivingEntity living,
                                        float entityYaw, float partialTicks) {
        VertexConsumer toDraw = vertexConsumer;
        // **El frame del impacto va entero, y por eso se saltea.** A vida llena el umbral vale cero
        // -- o sea que no hay nada que borrar -- asi que envolver no cambiaria nada salvo perder el
        // negro del impacto. Un tick de cuerpo completo es el golpe llegando.
        boolean wholeBody = living.hurtTime > DamageFlash.FLASH_TICKS - DamageFlash.IMPACT_TICKS;
        if (living.hurtTime > 0 && !wholeBody) {
            float threshold = DamageFlash.barThreshold(living.getHealth(), living.getMaxHealth(),
                    living.getBbHeight() * DamageFlash.MODEL_UNITS_PER_BLOCK);
            if (threshold > 0.0F) {
                // **La Y base, y sin esto el efecto no existe.** El poseStack que llega al modelo ya
                // esta trasladado a la posicion del MUNDO de la entidad (EntityRenderDispatcher hace
                // translate antes de llamar a render), asi que la Y de un vertice vale `altura en el
                // mundo + altura local`. El umbral es local, y hay que sumarle la base o no corta
                // nunca: eso era "no baja por vida". Interpolada, porque el dispatcher tambien lo
                // esta y si no la linea tiembla al caminar.
                float baseY = (float) Mth.lerp((double) partialTicks, living.yOld, living.getY());
                toDraw = new HealthFlashBuffer(vertexConsumer, baseY + threshold,
                        OverlayTexture.NO_OVERLAY);
            }
        }
        model.renderToBuffer(poseStack, toDraw, packedLight, packedOverlay, colour);
    }
}
