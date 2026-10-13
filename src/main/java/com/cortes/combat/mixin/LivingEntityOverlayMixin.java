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
 * Hace que la muerte sea <b>blanca</b>.
 *
 * <h2>El seam, y por qué es una sola línea</h2>
 *
 * <p>Verificado en las fuentes de 21.1.250, el juego decide el flash entero acá:</p>
 *
 * <pre>public static int getOverlayCoords(LivingEntity e, float u) {
 *     return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime &gt; 0 || e.deathTime &gt; 0));
 * }</pre>
 *
 * <p><b>Y ahí está la sorpresa: {@code deathTime > 0} YA dispara el rojo.</b> Un cuerpo que se muere
 * ya flashea en vanilla -- lo que faltaba era que fuera <b>blanco</b>, y el segundo argumento
 * {@code u}, que elige la intensidad del blanco, ya viaja por acá.</p>
 *
 * <h2>Y lo que este mixin YA NO hace</h2>
 *
 * <p>La ronda anterior también usaba esta columna para escalar el rojo según la vida que le queda al
 * cuerpo. <b>El playtest lo desmintió</b>: los mobs salían negros. La historia completa está en
 * {@link DamageFlash} -- lo que queda acá es sólo la muerte, que usa la <b>fila blanca</b> y por eso
 * no toca nada de lo que se rompió.</p>
 *
 * <h2>La muerte gana sobre el herido</h2>
 *
 * <p>Cuando un cuerpo se está muriendo puede tener {@code hurtTime} y {@code deathTime} a la vez, y
 * las dos piden un flash distinto. <b>La muerte gana</b> porque son dos hechos distintos: herido es
 * "te pegué", muriendo es "se terminó" -- y el segundo es más importante y más corto (veinte ticks).
 * Sin la prioridad, un remate mostraría el rojo del golpe y el blanco no se vería nunca, que es
 * exactamente el bug de "algo puesto y nadie dibujándolo" que este proyecto ya pagó varias veces.</p>
 *
 * <p>Y si no está ni herido ni muriendo, el valor de vanilla pasa sin tocarse: el caso por defecto no
 * tiene rama a propósito.</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LivingEntityOverlayMixin {

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void cortescombat$whiteOnDeath(LivingEntity entity, float vanillaU,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (entity.deathTime > 0) {
            float white = DamageFlash.whiteForDeath(entity.deathTime);
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(white), false));
        }
    }
}
