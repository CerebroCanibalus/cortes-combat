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
 * Elige <b>la fila</b> del flash: el impacto negro, después el rojo, y blanco al morir.
 *
 * <h2>El seam, y por qué es una sola línea</h2>
 *
 * <p>Verificado en las fuentes de 21.1.250, el juego decide el flash entero acá:</p>
 *
 * <pre>public static int getOverlayCoords(LivingEntity e, float u) {
 *     return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(e.hurtTime &gt; 0 || e.deathTime &gt; 0));
 * }</pre>
 *
 * <p>El entero que devuelve lleva una columna y una fila, y {@code pack(int, int)} acepta cualquier
 * fila -- no sólo las dos que {@code v(boolean)} sabe nombrar. <b>Eso es lo que permite tener una
 * paleta propia sin tocar nada de vanilla.</b></p>
 *
 * <h2>La columna es cero, y a propósito</h2>
 *
 * <p>Todos los valores de acá usan la <b>columna 0</b>, que es la que el playtest aprobó. La columna
 * es el eje que rompió el efecto cuando se la usó para escalar la intensidad, así que se queda
 * quieta: <b>el efecto entero se controla por fila.</b></p>
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
    private static void cortescombat$ourFlash(LivingEntity entity, float vanillaU,
                                              CallbackInfoReturnable<Integer> cir) {
        if (entity.hurtTime <= 0) {
            return;
        }
        // **El blanco de la muerte se decide con `getHealth()` y NO con `deathTime`.** Verificado con
        // javap: `deathTime` no esta en `defineSynchedData`, asi que el cliente vale cero siempre y el
        // blanco no se dibujo nunca. `hurtTime` si llega, por `ClientboundHurtAnimationPacket`.
        //
        // **Y entra al final, despues del rojo**: el golpe mortal es negro (un tick), despues rojo, y
        // el blanco es lo ultimo. Es el orden que se pidio. Compararlo contra DEATH_WHITE_TICKS y no
        // contra un reloj aparte es lo que hace que los dos usen el mismo contador.
        if (entity.getHealth() <= 0.0F && entity.hurtTime <= DamageFlash.DEATH_WHITE_TICKS) {
            float white = DamageFlash.whiteForDeath(entity.hurtTime);
            cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(white), false));
            return;
        }
        // La fila decide el color y el tiempo; la columna queda en cero. Ver el javadoc.
        cir.setReturnValue(OverlayTexture.pack(0, DamageFlash.rowFor(entity.hurtTime)));
    }
}
