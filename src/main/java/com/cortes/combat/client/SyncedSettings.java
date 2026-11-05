package com.cortes.combat.client;

import com.cortes.combat.attack.WarhammerTuning;
import com.cortes.combat.config.ServerConfig;
import com.cortes.combat.net.ServerSettings;

/**
 * <b>La puerta</b> por la que el cliente pregunta los valores de la config del server.
 *
 * <h2>La regla, y es una sola</h2>
 *
 * <p><b>Si el server me mando el valor, ese; si no, mi copia local de la config.</b> Ni mas ni
 * menos. La copia local es lo que el cliente hacia siempre (Forge carga la spec de SERVER en el
 * cliente, desde {@code config/}), asi que el fallback <b>no cambia nada</b> para quien no entra a
 * un server o entra antes de que llegue el mensaje.</p>
 *
 * <h2>Por que la puerta y no "leer la config"</h2>
 *
 * <p>Porque <b>habia dos copias de un numero que deberia tener un solo dueno</b>: la del server y
 * la del cliente. Y el cliente no solo la leia: la <em>usa</em>. Los gates de input leen
 * {@code combatRework} (con el server apagado, el cliente mandaba swings que el server ignoraba) y
 * el tooltip leia los numeros del martillo (con el server con un tope, el tooltip prometia un
 * dano que el golpe no hacia).</p>
 *
 * <p>Un lector que pregunta "cual es el valor" a la puerta, y no a la config, es lo que hace que
 * la respuesta sea la misma en las dos patas y la unica que cuenta.</p>
 *
 * <h2>Y por que el valor guardado es un {@code volatile}</h2>
 *
 * <p>Lo escribe el manejador del paquete (en el hilo principal, dentro de
 * {@code enqueueWork}) y lo leen el render y el input. Un {@code volatile} sobre la referencia
 * alcanza: lo que se publica es la referencia a un record <b>completo</b>, no un record a medio
 * construir. No es estado de gameplay -- es lo que el server dijo -- asi que no va en un
 * attachment ni en una capability.</p>
 */
public final class SyncedSettings {

    /**
     * Lo que el server mando, o {@code null} si todavia no mandado nada.
     *
     * <p>{@code null} es "no se", y no "todo en default": la diferencia importa, porque el
     * fallback de la puerta es la copia local del cliente, que puede tener valores distintos de
     * los del server.</p>
     */
    private static volatile ServerSettings synced;

    private SyncedSettings() {
    }

    /** El server mando su config: a partir de aca, la puerta responde con esto. */
    public static void accept(ServerSettings settings) {
        synced = settings;
        // **La traza que dice si el mensaje llego**, porque sin ella el unico sintoma de un
        // sync que no funciona es "el tooltip dice otra cosa" y no se sabe de que lado esta la
        // diferencia. Apagada por defecto (`debug` de la config del cliente), como todas.
        if (com.cortes.combat.config.ClientConfig.SPEC.isLoaded()
                && com.cortes.combat.config.ClientConfig.DEBUG.get()) {
            com.cortes.combat.CortesCombat.LOGGER.info(
                    "[config] server: tope={} penetracion={}% combat={} particulas={}",
                    settings.warhammer().damageCap(),
                    Math.round(settings.warhammer().armourPenetration() * 100.0F),
                    settings.combatRework(), settings.particles());
        }
    }

    /**
     * Se termino el mundo: la puerta vuelve a su copia local.
     *
     * <p><b>Sin esto, el inventario creativo mostraria el numero del ultimo server</b> al que
     * entraste, y el siguiente server tendria que escribir el suyo antes de que el tooltip
     * dejara de mentir. La limpieza va con la de las otras tres cosas de pantalla (la camara, el
     * reactor, las poses remotas) -- ver {@code ClientWorldCleanup}.</p>
     */
    public static void clear() {
        synced = null;
    }

    /** Si el combate del mod esta encendido, segun el server. */
    public static boolean combatRework() {
        ServerSettings settings = synced;
        return settings != null ? settings.combatRework() : ServerConfig.combatRework();
    }

    /** Si las particulas estan encendidas, segun el server. */
    public static boolean particles() {
        ServerSettings settings = synced;
        return settings != null ? settings.particles() : ServerConfig.particles();
    }

    /**
     * Los dos numeros del martillo, segun el server.
     *
     * <p>Es lo que lee el tooltip, y por eso importa que el numero que ve el jugador sea el
     * numero que el server va a cobrar.</p>
     */
    public static WarhammerTuning warhammerTuning() {
        ServerSettings settings = synced;
        return settings != null ? settings.warhammer() : ServerConfig.warhammerTuning();
    }
}
