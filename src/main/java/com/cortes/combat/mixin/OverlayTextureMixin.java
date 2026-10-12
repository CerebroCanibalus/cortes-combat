package com.cortes.combat.mixin;

import com.cortes.combat.client.DamageFlash;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hace <b>más intenso</b> el rojo del flash de daño de vanilla.
 *
 * <h2>Por qué acá y no en {@code getOverlayCoords}</h2>
 *
 * <p>El seam obvio era {@code LivingEntityRenderer.getOverlayCoords}, que es {@code static} y
 * devuelve el entero del overlay. <b>Pero ese entero elige la FILA, y la fila no puede cambiar la
 * intensidad</b>: las ocho filas rojas de la textura son idénticas, así que el overlay no sabe decir
 * "medio rojo" -- sólo sabe decir rojo o no rojo. El número que hay que mover es el <b>alpha del
 * píxel</b>, y eso vive en el constructor de la textura.</p>
 *
 * <h2>Por qué {@code @ModifyArg} y no un {@code @Inject} al final</h2>
 *
 * <p>El constructor llena la imagen, la sube a la GPU y vuelve a atar la unidad 0. Un {@code @Inject}
 * en TAIL tendría que reescribir los píxeles <b>y volver a subirlos</b>, y ese segundo
 * {@code upload()} ata la textura a la unidad que esté activa en ese momento -- que ya no es la 1.
 * Modificar el argumento <b>antes</b> de que se escriba deja que la subida del propio constructor
 * lleve el cambio, sin una segunda subida y sin tocar el estado de las unidades de textura.</p>
 *
 * <p><b>Y el método es estático y puro</b>: {@code DamageFlash.strengthen} recibe el color que el
 * juego iba a escribir y devuelve el que tiene que escribir. El único número de esta ronda queda
 * así donde un test lo alcanza, porque <b>un mixin no se puede testear</b> -- no arranca en una JVM
 * pelada.</p>
 *
 * <p><b>La llamada aparece una sola vez en el método</b> (adentro de un doble bucle, pero una sola
 * instrucción), así que no hace falta discriminar por fila: el rojo de vanilla es un valor único y
 * las filas blancas se reconocen por no serlo.</p>
 */
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"),
            index = 2)
    private int cortescombat$strongerRedFlash(int color) {
        return DamageFlash.strengthen(color);
    }
}
