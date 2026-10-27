package com.cortes.combat.client;

import com.cortes.combat.platform.ResourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * El sprite plano del arcabuz: <b>solo el registro del modelo adicional</b>, porque en 1.20.1
 * hay una sola puerta y es el mixin.
 *
 * <h2>Lo que cambió, y por qué esta clase se quedó corta</h2>
 *
 * <p>Hasta la ronda del 2026-10-26 esta clase tenía dos mitades: registraba el icono como
 * modelo adicional y después <b>envolvia el modelo del arcabuz en el mapa del bakery</b>
 * ({@code ModifyBakingResult}) para que la ranura lo dibujara plano. La segunda mitad se cayó
 * por dos razones distintas, y las dos se verificaron:</p>
 *
 * <ol>
 *   <li><b>El wrapper no decidía la GUI, porque los overrides resuelven DESPUÉS.</b>
 *       {@code ItemRenderer#getModel} termina en {@code getOverrides().resolve(...)}, y los
 *       frames de la mecha son exactamente overrides del modelo del arma. Llamar al wrapper
 *       "el modelo del inventario" era confiar en que {@code resolve} lo devolviera a él, y
 *       {@code resolve} devuelve el override que matchea.</li>
 *   <li><b>Y cuando ningún override matchea (arma vacía, mecha apagada), {@code resolve}
 *       devuelve el wrapper... que dibuja los quads del icono. La pistola en la MANO se
 *       volvía un sprite plano.</b> Un mecanismo que sólo es correcto en el contexto equivocado
 *       no es un mecanismo.</li>
 * </ol>
 *
 * <p>La decisión vive donde el context es un dato: {@code ArcabuzGuiIconMixin}, en
 * {@code ItemRenderer#render} -- la misma técnica del tridente, que vanilla resuelve en esa
 * línea. Lo único que queda acá es lo que el mixin necesita del bakery: el icono horneado.</p>
 *
 * <h2>La llave, que es el bug del playtest</h2>
 *
 * <p><b>{@code ResourceLocation} pelado y no {@code ModelResourceLocation}.</b> El parche de
 * Forge a {@code ModelBakery} (47.4.28, leído de las fuentes) archiva el modelo adicional así:
 * {@code f_119214_.put(rl, unbakedmodel)} con el id que entregó
 * {@code ModelEvent.RegisterAdditional}, sin variante. Pedirlo después con un
 * {@code ModelResourceLocation} -- aunque sea {@code standalone}, que es lo que NeoForge pide
 * en 1.21.1 -- <b>falla en silencio</b>: {@code ModelResourceLocation.hashCode()} suma la
 * variante, el {@code HashMap} busca en otro balde, y {@code ModelManager} devuelve el
 * <b>missing model</b> por su {@code getOrDefault}. El sintoma fue exactamente el reportado:
 * "el arcabuz sigue sin texturas en la GUI" -- el checkerboard es el missing model.
 * ({@code FlatInventoryIcon} v120, la llave pelada.) (¬‿¬)</p>
 */
public final class FlatInventoryIcon {

    private FlatInventoryIcon() {
    }

    /** La llave con la que el bakery archiva el icono en esta pata: <b>pelada</b>. */
    static final ResourceLocation ICON = ResourceIds.rl("arcabuz_icon");

    /**
     * Pide el icono, porque nada más lo referencia.
     *
     * <p>Un modelo sólo se hornea si algo lo pide. El JSON existe en
     * {@code models/arcabuz_icon.json} y la textura en {@code textures/item/arcabuz_icon.png},
     * pero el juego no los lee a menos que este evento los nombre -- la lección ya pagada de
     * "un archivo que existe no es un archivo que el juego lee".</p>
     */
    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(ICON);
    }
}
