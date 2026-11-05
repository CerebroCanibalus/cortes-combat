package com.cortes.combat.net;

import com.cortes.combat.attack.WarhammerTuning;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El viaje de los numeros de la config del server, de ida y de vuelta.
 *
 * <p><b>Puro a proposito.</b> El mensaje son cuatro valores y un orden, y las dos patas los leen
 * de la misma clase: si el orden se escribiera dos veces, un campo movido seria un numero
 * leido como otro. Este test hace el viaje completo en una JVM pelada.</p>
 */
class ServerSettingsTest {

    private static ServerSettings roundTrip(ServerSettings settings) {
        ByteBuf buf = Unpooled.buffer();
        ServerSettings.encode(buf, settings);
        return ServerSettings.decode(buf);
    }

    @Test
    @DisplayName("lo que sale del cable es lo que entro")
    void theRoundTripKeepsEverything() {
        ServerSettings original = new ServerSettings(true, false,
                new WarhammerTuning(20.0F, 0.5F));

        ServerSettings decoded = roundTrip(original);

        assertEquals(original.combatRework(), decoded.combatRework(),
                "el gate de input");
        assertEquals(original.particles(), decoded.particles(),
                "las particulas");
        assertEquals(original.warhammer().damageCap(), decoded.warhammer().damageCap(), 1.0E-6,
                "el tope del martillo");
        assertEquals(original.warhammer().armourPenetration(),
                decoded.warhammer().armourPenetration(), 1.0E-6,
                "la penetracion de armadura");
    }

    @Test
    @DisplayName("el orden de los campos es el de la clase y no el de un dia")
    void theOrderIsTheClassOne() {
        ByteBuf buf = Unpooled.buffer();
        ServerSettings.encode(buf, new ServerSettings(true, false,
                new WarhammerTuning(20.0F, 0.5F)));

        // 1 byte por booleano y 4 por float: diez bytes, en el orden de la clase.
        assertEquals(10, buf.readableBytes(),
                "**el tamaño del mensaje es parte del formato.** Si cambia, el otro extremo esta "
                        + "leyendo otra cosa");

        assertTrue(buf.readBoolean(), "el primer booleano");
        assertTrue(!buf.readBoolean(), "el segundo booleano");
        assertEquals(20.0F, buf.readFloat(), 1.0E-6, "el tope");
        assertEquals(0.5F, buf.readFloat(), 1.0E-6, "la penetracion");
    }

    @Test
    @DisplayName("un numero raro en el cable no llega como un numero raro")
    void aWildNumberIsClampedOnTheWayIn() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        buf.writeFloat(-5.0F);
        buf.writeFloat(4.0F);

        ServerSettings decoded = ServerSettings.decode(buf);

        assertEquals(0.0F, decoded.warhammer().damageCap(), 1.0E-6,
                "un tope negativo no es un tope: es cero, que es 'sin tope'");
        assertEquals(1.0F, decoded.warhammer().armourPenetration(), 1.0E-6,
                "una penetracion de 400 % es 100 %: la armadura no puede ignorar mas que toda");
    }
}
