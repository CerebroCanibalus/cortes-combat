package com.cortes.combat.mixin;

import com.cortes.combat.client.DamageFlash;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hace que el flash de daño <b>diga cuánta vida le queda al cuerpo</b>, y que la muerte sea blanca.
 *
 * <h2>El seam, y por qué es una sola línea</h2>
 *
 * <p>Verificado en las fuentes de 21.1.250, el juego decide el flash entero acá:</p>
 *
 * <pre>public static int getOverlayCoords(LivingEntity e, float u) {
 *     return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime &gt; 0 || e.deathTime &gt; 0));
 * }</pre>
 *
 * <p>Dos cosas de ahí son las que habilitan todo esto. La primera: <b>el entero que devuelve lleva
 * una columna y una fila</b>, y la columna elige la intensidad del rojo (ver
 * {@link OverlayTextureMixin}). La segunda: <b>{@code deathTime} ya dispara el rojo</b> -- o sea que
 * vanilla ya flashea al morir, y lo que falta es que sea blanco.</p>
 *
 * <h2>Las dos ramas, y por qué la muerte gana</h2>
 *
 * <p>Cuando un cuerpo se está muriendo puede tener {@code hurtTime} y {@code deathTime} a la vez, y
 * las dos piden un flash distinto. <b>La muerte gana</b> porque son dos hechos distintos: herido es
 * "te pegué", muriendo es "se terminó" -- y el segundo es más importante y más corto (veinte
 * ticks). Sin la prioridad, un remate mostraría el rojo del golpe y el blanco no se vería nunca, que
 * es exactamente el bug de "algo puesto y nadie dibujándolo" que este proyecto ya pagó varias
 * veces.</p>
 *
 * <h2>La vida, no el daño</h2>
 *
 * <p>La columna sale de <b>{@code health / maxHealth}</b> y no del daño que acaba de entrar. Un mob
 * con armadura completa que recibe un golpe chico apenas se entera; el mismo golpe sobre uno que ya
 * está en las últimas lo pinta casi entero. <b>La misma cantidad de daño tiene que verse distinta
 * según lo que le queda</b>, y eso es lo que la vida sabe y el daño no.</p>
 *
 * <p>Y se lee {@code getHealth()} <b>en el momento de dibujar</b>, no en el del golpe: el overlay se
 * recalcula cada frame mientras {@code hurtTime} dure, así que el rojo <b>se intensifica solo</b>
 * durante los diez ticks del flash si el cuerpo sigue perdiendo vida. Sin estado y sin una segunda
 * regla.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityOverlayMixin {

    /** La animación de muerte de vanilla: {@code deathTime} va de 0 a 20. */
    private static final int DEATH_TICKS = 20;

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void cortescombat$flashByHealth(LivingEntity entity, float vanillaU,
                                                   CallbackInfoReturnable<Integer> cir) {
        // Muriendo: blanco, y el rojo se apaga solo porque esta rama lo reemplaza.
        if (entity.deathTime > 0) {
            float white = DamageFlash.whiteForDeath(entity.deathTime, DEATH_TICKS);
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(white), false));
            return;
        }
        // Herido: el rojo de siempre, con la intensidad que le toca por la vida que le queda.
        if (entity.hurtTime > 0) {
            float hurt = DamageFlash.uForHealth(entity.getHealth(), entity.getMaxHealth());
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(hurt), true));
        }
        // Y si no está ni herido ni muriendo se deja pasar el valor de vanilla, que es el overlay
        // "sin nada". No hay rama para eso a propósito: el caso por defecto no se toca.
    }
}
