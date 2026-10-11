package com.cortes.combat.attack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El techo del empuje, que subio dos veces y por dos motivos distintos.
 *
 * <p>La primera vez fue por un arma (la pala) y la segunda por dos encantamientos (Paliza e Inercia).
 * Este test existe porque la segunda subida toca un numero que <b>todas</b> las armas del juego
 * comparten, y la unica forma de que siga siendo un techo es que nada del catalogo lo alcance.</p>
 */
@DisplayName("el techo del empuje")
class KnockbackCeilingTest {

    @Test
    @DisplayName("**subio a 2,5/0,70 por Paliza e Inercia**")
    void theCeilingIsForTwoEnchantments() {
        assertEquals(2.5F, KnockbackProfile.MAX_HORIZONTAL, 1.0E-6);
        assertEquals(0.70F, KnockbackProfile.MAX_VERTICAL, 1.0E-6);
        assertTrue(KnockbackProfile.MAX_VERTICAL < KnockbackProfile.MAX_HORIZONTAL,
                "el vertical sigue siendo mucho mas chico que el horizontal, que es la decision de "
                        + "diseno que el tipo documenta");
    }

    @Test
    @DisplayName("**y ninguna arma del catalogo lo toca sin encantamiento**")
    void noWeaponReachesTheCeiling() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            KnockbackProfile knock = WeaponTables.knockback(profile);
            assertTrue(knock.horizontal() < KnockbackProfile.MAX_HORIZONTAL,
                    profile + " toca el techo horizontal sin encantamiento. El techo es para un "
                            + "override de config y para dos encantamientos con nombre, no un "
                            + "objetivo: un arma que lo alcanza de fabrica es un arma que ya no tiene "
                            + "lugar para ser tuneada");
            assertTrue(knock.vertical() < KnockbackProfile.MAX_VERTICAL,
                    profile + " toca el techo vertical sin encantamiento");
        }
    }
}
