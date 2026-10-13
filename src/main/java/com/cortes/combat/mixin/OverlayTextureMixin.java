package com.cortes.combat.mixin;

import com.cortes.combat.client.DamageFlash;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Escribe <b>nuestra paleta</b> en la textura del overlay.
 *
 * <h2>Las filas 0-2 y 4-7 son nuestras</h2>
 *
 * <p>Vanilla llena las ocho filas rojas con el mismo valor y después usa <b>una sola</b>: la 3, la
 * de herir. La 10 es la blanca del creeper. O sea que hay <b>siete filas escritas que nadie lee</b>
 * -- y ahí entra el frame negro, sin pisar nada de vanilla.</p>
 *
 * <h2>Por qué {@code @Redirect} y no {@code @ModifyArg}</h2>
 *
 * <p>Porque el efecto elige la <b>fila</b>, y para escribir una fila distinta hay que saber en cuál
 * se está. {@code @ModifyArg} sólo ve el color, y las ocho filas rojas tienen el mismo. La versión
 * anterior pasó a {@code @Redirect} por la <b>columna</b> y salió mal -- ver {@link DamageFlash} --
 * pero la fila es el eje que vanilla mismo usa para elegir rojo o blanco, así que es el que está
 * explicado.</p>
 *
 * <p><b>La columna se pasa intacta</b>: no se toca, y por eso el gradiente que rompió el efecto no
 * puede volver por acá.</p>
 *
 * <p>Y no hay {@code @Inject} al final del constructor: el constructor sube la imagen a la GPU y
 * vuelve a atar la unidad 0, así que reescribir los píxeles después obligaría a una segunda subida
 * que ataría la textura a la unidad equivocada.</p>
 */
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void cortescombat$ourPalette(NativeImage image, int column, int row, int colour) {
        image.setPixelRGBA(column, row, DamageFlash.texturePixel(row, colour));
    }
}
