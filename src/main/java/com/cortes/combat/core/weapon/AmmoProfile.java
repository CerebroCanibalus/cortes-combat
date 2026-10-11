package com.cortes.combat.core.weapon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Qué gasta un arma disparada, y cuánto.
 *
 * <h2>Por qué esto es un componente y no una constante en Arcabuz</h2>
 *
 * <p>Porque <b>la munición era la última decisión de gameplay que quedaba escrita a mano en
 * Java.</b> Alcance, cono, carga, especial, empuje y alcance de disparo ya son datos -- el dueño
 * y los packs los cambian sin tocar una linea -- y el ítem que gasta el arcabuz estaba clavado
 * en {@code firestoneSlot} contra {@code ModItems.FIRESTONE}. Eso significa que un pack que
 * quiere un arco de carne podrida <em>no puede</em>: tiene que editar Java, que es exactamente lo
 * que los packs existen para no hacer.</p>
 *
 * <p>Ahora la pregunta es una sola y vive en un solo sitio: <b>¿qué item acepta esta arma como
 * munición?</b> El escaneo del inventario es el mismo para cualquier item, así que un arma nueva
 * que gaste otra cosa es un renglón en un YAML y nada más.</p>
 *
 * <h2>Por qué el id va como String y no como ResourceLocation</h2>
 *
 * <p>Porque <b>los tests no pueden cargar tipos de Minecraft</b>, y esa regla ya le costó a este
 * proyecto varios {@code NoClassDefFoundError} que parecían tests rotos. Un {@code String} es
 * Java puro, su {@code Codec} es trivial, y la traducción a {@code ResourceLocation} y de ahí a
 * un {@code Item} pasa <b>una vez</b>, en el sitio que ya tiene el mundo a mano. Un id que no
 * resuelve a un item real es un arma sin munición, y eso es un error de dato que se avisa -- no
 * un crash.</p>
 *
 * <h2>El coste no es negociable desde el YAML</h2>
 *
 * <p>Sólo se declaran <b>qué</b> y <b>cuántos</b>. El <em>cuándo</em> -- al encender la mecha o
 * al terminar la recarga -- es el ritmo del arma y sigue decidiéndolo el código, porque un pack
 * que pudiera hacer la munición gratis haría un arma que no cuesta nada, que es un bug de
 * balance con aspecto de feature.</p>
 *
 * @param itemId el id del ítem, sin el {@code #} de tag y con namespace explícito
 * @param count cuántos se gastan por disparo. Uno por defecto, y el piso es uno.
 */
public record AmmoProfile(String itemId, int count) {

    /**
     * Lo que gasta el arcabuz si nadie dice otra cosa, y lo que gasta cualquier arma que declare
     * munición sin declarar cuánta.
     *
     * <p><b>La Firestone por default, y no una decisión nueva:</b> es lo que el mod ya gastaba
     * antes de que esto existiera, así que todo arma que no declare nada se comporta exactamente
     * igual que ayer. Un default que cambiara el comportamiento de las 31 armas existentes sería
     * un regalo o una trampa, y no se puede saber cuál sin playtest.</p>
     */
    public static final String DEFAULT_ITEM = "cortescombat:firestone";

    /** Uno, y no cero: un arma que dispara sin gastar nada no tiene precio. */
    public static final int DEFAULT_COUNT = 1;

    public static final AmmoProfile FIRESTONE = new AmmoProfile(DEFAULT_ITEM, DEFAULT_COUNT);

    public static final Codec<AmmoProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("item").forGetter(AmmoProfile::itemId),
            Codec.INT.fieldOf("count").orElse(DEFAULT_COUNT).forGetter(AmmoProfile::count)
    ).apply(instance, AmmoProfile::new));

    /**
     * Normaliza lo que venga de un YAML o de un datapack.
     *
     * <p>Sin namespace se asume {@code minecraft:}, que es la convención del juego y lo que un
     * pack escrito a mano espera. Y <b>un costo nunca puede ser cero o negativo</b>: eso sería un
     * arma que dispara gratis, y el piso se fuerza en código en vez de confiarse al dato -- que
     * es la misma regla que la carga ya tiene.</p>
     */
    public static AmmoProfile of(String rawItemId, int rawCount) {
        String id = rawItemId == null || rawItemId.isBlank() ? DEFAULT_ITEM : rawItemId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        return new AmmoProfile(id, Math.max(DEFAULT_COUNT, rawCount));
    }

    /** El perfil por defecto, para el caso de "no se declaró nada". */
    public static AmmoProfile firestone() {
        return FIRESTONE;
    }
}
