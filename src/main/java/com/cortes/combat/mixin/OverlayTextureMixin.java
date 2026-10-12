package com.cortes.combat.mixin;

import com.cortes.combat.client.DamageFlash;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Convierte el rojo <b>plano</b> del flash de daño en un <b>gradiente por columna</b>.
 *
 * <h2>Por qué {@code @Redirect} y no {@code @ModifyArg}</h2>
 *
 * <p>La ronda anterior usaba {@code @ModifyArg} sobre el tercer argumento, que alcanzaba porque el
 * rojo era un valor único: todas las columnas del rojo eran el mismo número. <b>Ahora la columna
 * significa algo</b> -- es la intensidad -- así que el mixin necesita saber <em>en qué columna
 * está</em>, y eso sólo lo da interceptar la llamada entera.</p>
 *
 * <p>El método se llama 256 veces, una por píxel, y las 256 pasan por acá. No hay forma de
 * discriminar por fila sin leer el color, y <b>leerlo es lo correcto</b>: el rojo se reconoce por su
 * valor, no por su índice, así que si Mojang mueve las filas el mixin sigue funcionando.</p>
 *
 * <p><b>Y no hay {@code @Inject} al final del constructor</b> por la razón que la ronda anterior ya
 * documentó: el constructor sube la imagen a la GPU y vuelve a atar la unidad 0, así que reescribir
 * los píxeles después obligaría a una segunda subida que ataría la textura a la unidad equivocada.
 * Modificar el argumento antes de que se escriba deja que la subida del propio constructor lleve el
 * cambio.</p>
 */
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void cortescombat$redGradient(NativeImage image, int column, int row, int colour) {
        image.setPixelRGBA(column, row, DamageFlash.texturePixel(column, colour));
    }
}
