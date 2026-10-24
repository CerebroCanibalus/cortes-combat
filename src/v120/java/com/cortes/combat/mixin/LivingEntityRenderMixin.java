package com.cortes.combat.mixin;

import com.cortes.combat.client.DamageFlash;
import com.cortes.combat.client.HealthFlashBuffer;
import com.cortes.combat.client.VertexBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Convierte el flash en <b>una barra de vida</b>: envuelve el buffer del modelo para que el rojo
 * cubra sólo la parte del cuerpo que todavía tiene vida, y se retire hacia abajo a medida que el mob
 * se vacía.
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
 * <p><b>El dueño del {@code invokevirtual} se leyó del bytecode, igual que en el
 * gemelo:</b> {@code javap -c} sobre {@code LivingEntityRenderer} da
 * {@code Method net/minecraft/client/model/EntityModel.renderToBuffer:(...PoseStack;...VertexConsumer;IIFFFF)V}.
 * Mismo dueño, ocho argumentos: el color viaja como cuatro floats en vez de un int.</p>
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
 *   <li><b>El frame del impacto.</b> El negro del primer tick tiene que cubrir todo -- es el golpe
 *       llegando.</li>
 *   <li><b>La muerte.</b> El blanco es un hecho, no una barra.</li>
 * </ul>
 *
 * <p>Y cuando sí se envuelve, el corte sale del <b>rango medido</b> del modelo, no de la caja de
 * colisión: ver {@link DamageFlash#barCut} para las dos rondas que costó deducirlo.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRenderMixin {

    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"))
    private void cortescombat$healthBar(EntityModel<?> model, PoseStack poseStack,
                                        VertexConsumer vertexConsumer, int packedLight,
                                        int packedOverlay, float red, float green, float blue,
                                        float alpha, LivingEntity living,
                                        float entityYaw, float partialTicks) {
        VertexConsumer toDraw = vertexConsumer;
        // **El frame del impacto va entero, y ahora eso encaja solo.** El impacto es el cuerpo
        // completo porque un golpe que llega cubre todo; y con la barra, vida llena tambien es el
        // cuerpo completo. El unico tick que se saltea es el del impacto, que ya lo pinta la fila.
        boolean wholeBody = living.hurtTime > DamageFlash.FLASH_TICKS - DamageFlash.IMPACT_TICKS;
        if (living.hurtTime > 0 && !wholeBody) {
            // **El rango vertical se MIDE, no se deduce.** Ver DamageFlash#barCut: las dos versiones
            // anteriores lo calcularon desde `getBbHeight()` y las dos fallaron -- una por unidades
            // (el vertice llega en bloques y el umbral venia en dieciseisavos) y la otra por el
            // `translate(0, -1.501, 0)` del renderer. Un pase con `VertexBounds` da los dos extremos
            // reales, en el mismo espacio en que se dibuja.
            VertexBounds bounds = new VertexBounds();
            model.renderToBuffer(poseStack, bounds, packedLight, packedOverlay,
                    red, green, blue, alpha);
            if (bounds.maxY() > bounds.minY()) {
                float cut = DamageFlash.barCut(bounds.minY(), bounds.maxY(),
                        living.getHealth(), living.getMaxHealth());
                if (living.hurtTime == DamageFlash.FLASH_TICKS - DamageFlash.IMPACT_TICKS) {
                    com.cortes.combat.CortesCombat.LOGGER.info(
                            "[flash/bar] vida={}/{} Y=[{},{}] corte={}",
                            living.getHealth(), living.getMaxHealth(), bounds.minY(), bounds.maxY(),
                            cut);
                }
                toDraw = new HealthFlashBuffer(vertexConsumer, cut, OverlayTexture.NO_OVERLAY);
            }
        }
        model.renderToBuffer(poseStack, toDraw, packedLight, packedOverlay,
                red, green, blue, alpha);
    }
}
