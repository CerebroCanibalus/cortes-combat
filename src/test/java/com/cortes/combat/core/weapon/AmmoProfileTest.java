package com.cortes.combat.core.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La munición declarada, y sobre todo <b>lo que no se le puede pedir al dato</b>.
 *
 * <p>Estos tests son puros porque {@code AmmoProfile} es Java puro: el id va como {@code String}
 * justamente para que se pueda probar sin cargar un tipo de Minecraft. El traducirlo a un
 * {@code Item} pasa una vez, en {@code Arcabuz}, y ahi lo que verifica es el juego.</p>
 */
class AmmoProfileTest {

    @Test
    @DisplayName("sin namespace se asume minecraft, y con namespace se respeta")
    void namespacesAreResolvedTheWayTheGameDoes() {
        assertEquals("minecraft:rotten_flesh", AmmoProfile.of("rotten_flesh", 1).itemId(),
                "Un pack que escribe `rotten_flesh` espera el de vanilla, igual que en cualquier "
                        + "otro archivo del juego");
        assertEquals("cortescombat:firestone", AmmoProfile.of("cortescombat:firestone", 1).itemId(),
                "Y uno que declara su namespace no lo pierde");
        assertEquals("jugadorsito:balon", AmmoProfile.of("jugadorsito:balon", 1).itemId(),
                "Y los de otros mods tambien -- que es la mitad del motivo de existir de esto");
    }

    @Test
    @DisplayName("el costo no puede ser cero: el piso se fuerza en codigo")
    void theCostHasAFloorAndItIsOne() {
        // **Esta es la regla del proyecto aplicada a un dato nuevo**: la carga nunca resta y el
        // piso se fuerza en codigo en vez de confiarse al archivo. Un arma que dispara sin gastar
        // nada no tiene precio, y un pack que lo declare no deberia poder romper esa cuenta.
        assertEquals(1, AmmoProfile.of("minecraft:arrow", 0).count(),
                "Cero es un arma gratis");
        assertEquals(1, AmmoProfile.of("minecraft:arrow", -7).count(),
                "Y negativo todavia peor");
        assertEquals(3, AmmoProfile.of("minecraft:arrow", 3).count(),
                "Pero arriba de uno si se puede: asi un arma gasta de a tres sin tocar Java");
    }

    @Test
    @DisplayName("lo vacio cae al default, que es la Firestone de siempre")
    void blankFallsBackToTheDefault() {
        assertEquals(AmmoProfile.DEFAULT_ITEM, AmmoProfile.of(null, 1).itemId());
        assertEquals(AmmoProfile.DEFAULT_ITEM, AmmoProfile.of("   ", 1).itemId());
        // **Y el default tiene que ser exactamente lo que el mod ya gastaba.** Un default que
        // cambiara el precio de las 31 armas que ya existen seria un regalo o una trampa, y las
        // dos se ven igual hasta el playtest.
        assertEquals("cortescombat:firestone", AmmoProfile.FIRESTONE.itemId(),
                "El default dejo de ser la Firestone y nadie lo decidio");
        assertEquals(1, AmmoProfile.FIRESTONE.count());
    }

    @Test
    @DisplayName("el costo siempre trae el suyo aunque el campo venga sin el")
    void countDefaultsWhenAbsent() {
        // El codec tiene `.orElse(1)` para `count`; este lo afirma desde la otra punta, con el
        // constructor directo, para que el dia que se quite el `orElse` el default deje de ser
        // una costumbre y pase a ser una asercion.
        assertEquals(1, new AmmoProfile("minecraft:arrow", 1).count());
        assertTrue(AmmoProfile.FIRESTONE.count() >= AmmoProfile.DEFAULT_COUNT,
                "El piso es uno y el default lo respeta");
    }
}
