package com.cortes.combat.net;

import com.cortes.combat.attack.WarhammerTuning;

import io.netty.buffer.ByteBuf;

/**
 * Lo que el server le manda al cliente de la <b>config del server</b>.
 *
 * <h2>Por que existe, y que estaba roto</h2>
 *
 * <p>En 1.20.1 (Forge) la config de tipo SERVER se carga <b>tambien en el cliente</b>, pero desde
 * <b>otro archivo</b>: el server la tiene en {@code world/serverconfig/} y el cliente en
 * {@code config/}. Son dos copias y nada las mantiene juntas, asi que un administrador que
 * cambia un numero del server deja al cliente con el numero viejo. Y el cliente no solo la
 * <em>lee</em>: la <b>usa</b> -- los gates de input leen {@code combatRework} y el tooltip lee los
 * numeros del martillo.</p>
 *
 * <p>El efecto eran dos bugs de la misma familia: un tooltip que promise un dano que el golpe no
 * hace, y un cliente que manda swings a un server que los ignora. <b>Los dos son el mismo
 * problema</b>: dos copias de un numero que deberia tener un solo dueño.</p>
 *
 * <p>En 1.21.1 (NeoForge) esto ya lo resuelve la sincronizacion de configs del loader
 * ({@code ConfigSync} -&gt; {@code ConfigFilePayload}), pero la pata de 1.20.1 no la tiene. <b>El
 * mecanismo de este paquete es el mismo en las dos patas</b> a proposito: que la respuesta no
 * dependa de una feature del loader es lo que hace que las dos patas se comporten igual, y lo que
 * deja un solo guard que verifique las dos.</p>
 *
 * <h2>Y por que es una clase pura</h2>
 *
 * <p>Nada de Minecraft entra aqui: solo cuatro valores y el orden en que viajan. El orden lo
 * escribe <b>esta</b> clase y las dos patas lo leen de aca, asi que no hay dos opiniones sobre en
 * que campo va primero. Y como es pura, un test en una JVM pelada puede hacer el viaje de ida y
 * vuelta y comprobar que vuelve lo mismo.</p>
 */
public record ServerSettings(boolean combatRework, boolean particles, WarhammerTuning warhammer) {

    /**
     * Los valores que traveling, en un orden que no cambia.
     *
     * <p><b>Un byte por booleano y cuatro por float</b>: diez bytes en total. El tamaño no es el
     * problema (el mensaje viaja una vez, al entrar), pero el orden si: por eso la escritura y la
     * lectura estan en el mismo archivo y en el mismo metodo, y no en el payload de cada pata.</p>
     */
    public static void encode(ByteBuf buf, ServerSettings settings) {
        buf.writeBoolean(settings.combatRework());
        buf.writeBoolean(settings.particles());
        buf.writeFloat(settings.warhammer().damageCap());
        buf.writeFloat(settings.warhammer().armourPenetration());
    }

    /** El viaje de vuelta, en el orden inverso y con los mismos cuatro campos. */
    public static ServerSettings decode(ByteBuf buf) {
        boolean combatRework = buf.readBoolean();
        boolean particles = buf.readBoolean();
        float damageCap = buf.readFloat();
        float penetration = buf.readFloat();
        // **El record se construye, no se copia:** el constructor de WarhammerTuning deja los
        // dos numeros en rango, asi que un valor raro en el cable no llega ni al tooltip ni al
        // motor como un numero fuera de rango.
        return new ServerSettings(combatRework, particles,
                new WarhammerTuning(damageCap, penetration));
    }
}
