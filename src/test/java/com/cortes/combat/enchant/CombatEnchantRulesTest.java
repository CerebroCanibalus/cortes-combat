package com.cortes.combat.enchant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los números de Reflejo de Acero y Audacia, en JVM pelada.
 *
 * <p>Lo que se afirma acá no son "los tres valores" sino la <b>forma</b>: que el nivel cero devuelve
 * el cooldown de siempre (un encantamiento que cambiara algo sin estar es una tabla con un default
 * mentiroso), que subir de nivel siempre baja el número, y que ningún nivel puede llevarlo a cero --
 * el piso es lo que impide que un nivel fuera de rango convierta un botón en gratis.</p>
 */
class CombatEnchantRulesTest {

    @Test
    @DisplayName("nivel cero devuelve el cooldown de siempre: sin encantamiento no cambia nada")
    void zeroIsTheBase() {
        assertEquals(100, CombatEnchantRules.parryCooldown(100, 0),
                "el parry sin Reflejo de Acero son los 100 ticks de ParryState");
        assertEquals(80, CombatEnchantRules.lungeCooldown(80, 0),
                "y el lunge sin Audacia, los cuatro golpes de siempre");
    }

    @Test
    @DisplayName("Reflejo de Acero: 100 -> 80 -> 60 ticks, un segundo por nivel")
    void steelReflexLevels() {
        assertEquals(80, CombatEnchantRules.parryCooldown(100, 1));
        assertEquals(60, CombatEnchantRules.parryCooldown(100, 2));
    }

    @Test
    @DisplayName("Audacia: 80 -> 64 -> 48 ticks, un quinto por nivel")
    void audacityLevels() {
        assertEquals(64, CombatEnchantRules.lungeCooldown(80, 1));
        assertEquals(48, CombatEnchantRules.lungeCooldown(80, 2));
    }

    @Test
    @DisplayName("**y subir de nivel siempre baja el numero**, en los dos")
    void everyLevelIsCheaper() {
        for (int level = 1; level <= CombatEnchantRules.STEEL_REFLEX_MAX_LEVEL; level++) {
            assertTrue(CombatEnchantRules.parryCooldown(100, level)
                            < CombatEnchantRules.parryCooldown(100, level - 1),
                    "Reflejo de Acero " + level + ": el cooldown baja");
        }
        for (int level = 1; level <= CombatEnchantRules.AUDACITY_MAX_LEVEL; level++) {
            assertTrue(CombatEnchantRules.lungeCooldown(80, level)
                            < CombatEnchantRules.lungeCooldown(80, level - 1),
                    "Audacia " + level + ": el cooldown baja");
        }
    }

    @Test
    @DisplayName("**Audacia es proporcional y Reflejo de Acero no**, y esa es la diferencia")
    void audacityScalesWithTheWeapon() {
        // La misma fraccion sobre otro tempo: el encantamiento tiene que escalar con lo que el arma
        // ya cuesta. Un descuento fijo de ticks seria casi gratis en un arma lenta y seria todo en
        // una rapida, y el cooldown del lunge se deriva del intervalo por diseno.
        assertEquals(40, CombatEnchantRules.lungeCooldown(50, 1),
                "la mitad de base, la misma fraccion");
        assertEquals(100 - CombatEnchantRules.STEEL_REFLEX_TICKS_PER_LEVEL,
                CombatEnchantRules.parryCooldown(100, 1),
                "y el parry, en cambio, descuenta ticks: su base no depende del arma");
    }

    @Test
    @DisplayName("ningun nivel puede llevar el cooldown a cero: hay piso")
    void thereIsAFloor() {
        // El piso se prueba con una base chica, que es donde una resta sin guarda lo cruza: con la
        // base real de 100 hacen falta seis niveles, y el tope son dos. La pregunta util no es "el
        // nivel dos da 60" sino "que pasa si el numero entra mal".
        for (int level = 0; level <= 10; level++) {
            assertTrue(CombatEnchantRules.parryCooldown(5, level) >= 1,
                    "parry con base 5 y nivel " + level + ": nunca cero");
            assertTrue(CombatEnchantRules.lungeCooldown(1, level) >= 1,
                    "lunge con base 1 y nivel " + level + ": nunca cero");
        }
    }

    @Test
    @DisplayName("Filo Fugaz: cero sin el encantamiento, 40 -> 60 ticks con el")
    void fleetingEdgeLevels() {
        assertEquals(0, CombatEnchantRules.fleetingEdgeTicks(0),
                "sin el encantamiento la duracion es cero, y el handler usa ese cero para no "
                        + "aplicar nada: un efecto de duracion cero existe y no hace nada, que es "
                        + "peor que no ponerlo");
        assertEquals(40, CombatEnchantRules.fleetingEdgeTicks(1));
        assertEquals(60, CombatEnchantRules.fleetingEdgeTicks(2));
    }

    @Test
    @DisplayName("y no es lineal: el segundo nivel suma veinte ticks, no duplica")
    void fleetingEdgeIsNotLinear() {
        assertTrue(CombatEnchantRules.fleetingEdgeTicks(2)
                        < CombatEnchantRules.fleetingEdgeTicks(1) * 2,
                "60 no es el doble de 40: el nivel uno es salir del rango de lo que apunalaste y el "
                        + "dos es reposicionarse de verdad, y un buff de cuatro segundos se pisaria "
                        + "con el gate de 'ya lo tengo' casi siempre");
        assertEquals(CombatEnchantRules.fleetingEdgeTicks(2),
                CombatEnchantRules.fleetingEdgeTicks(9),
                "y un nivel de mas vale lo que el maximo");
        assertEquals(0, CombatEnchantRules.fleetingEdgeTicks(-1),
                "mientras que uno negativo no da nada");
    }

    @Test
    @DisplayName("Ritmo de Hierro: 80 -> 100 -> 120 ticks, un segundo por nivel")
    void rhythmOfIronLevels() {
        assertEquals(80, CombatEnchantRules.streakWindow(0),
                "sin el encantamiento son los cuatro segundos del diseno");
        assertEquals(100, CombatEnchantRules.streakWindow(1));
        assertEquals(120, CombatEnchantRules.streakWindow(2));
        assertEquals(120, CombatEnchantRules.streakWindow(9),
                "un nivel de mas vale lo que el maximo");
        assertEquals(80, CombatEnchantRules.streakWindow(-1),
                "y uno negativo, lo de siempre");
    }

    @Test
    @DisplayName("Contragolpe: un cuarto de armadura menos, y la mitad en el nivel dos")
    void riposteLevels() {
        assertEquals(0.0F, CombatEnchantRules.riposteArmourIgnored(0), 1.0E-6F,
                "sin el encantamiento no ignora nada: la ventana se abre igual pero no cobra");
        assertEquals(0.25F, CombatEnchantRules.riposteArmourIgnored(1), 1.0E-6F);
        assertEquals(0.50F, CombatEnchantRules.riposteArmourIgnored(2), 1.0E-6F);
        assertEquals(0.50F, CombatEnchantRules.riposteArmourIgnored(9), 1.0E-6F,
                "un nivel de mas vale lo que el maximo");
        assertEquals(0.0F, CombatEnchantRules.riposteArmourIgnored(-1), 1.0E-6F);
    }

    @Test
    @DisplayName("y nunca es el 100%: el martillo ya es el que ignora la armadura del todo")
    void riposteIsNeverComplete() {
        for (int level = 0; level <= CombatEnchantRules.RIPOSTE_MAX_LEVEL; level++) {
            assertTrue(CombatEnchantRules.riposteArmourIgnored(level) < 1.0F,
                    "nivel " + level + ": Contragolpe baja la armadura, no la saltea. El 'ignora "
                            + "todo' del martillo arrastra bypasses_shield, y un premio de guardia "
                            + "que atraviesa la guardia es el chiste contado al reves");
        }
    }

    @Test
    @DisplayName("Polvora Seca: 50 -> 40 -> 30 ticks, medio segundo por nivel")
    void dryPowderLevels() {
        assertEquals(50, CombatEnchantRules.reloadTicks(50, 0),
                "sin el encantamiento son los dos segundos y medio del diseno");
        assertEquals(40, CombatEnchantRules.reloadTicks(50, 1),
                "**y esto es lo que Quick Charge de vanilla NO daba**: su efecto es -0,25 s por "
                        + "nivel (absoluto), que sobre nuestra base de 2,5 s es la mitad de fraccion "
                        + "que sobre la ballesta. El diseno pedia -10 ticks por nivel");
        assertEquals(30, CombatEnchantRules.reloadTicks(50, 2));
        assertEquals(30, CombatEnchantRules.reloadTicks(50, 9), "un nivel de mas vale el maximo");
        assertEquals(50, CombatEnchantRules.reloadTicks(50, -1), "y uno negativo, lo de siempre");
    }

    @Test
    @DisplayName("y una recarga nunca cuesta cero: el piso se fuerza en codigo")
    void theReloadHasAFloor() {
        // El piso se prueba con una base chica, que es donde una resta sin guarda lo cruza: con la
        // base real de 50 harian falta cinco niveles y el tope son dos.
        for (int level = 0; level <= 10; level++) {
            assertTrue(CombatEnchantRules.reloadTicks(25, level)
                            >= com.cortes.combat.state.ArcabuzState.MIN_RELOAD_TICKS,
                    "recarga con base 25 y nivel " + level + ": nunca por debajo de un segundo. Un "
                            + "encantamiento que reste mas de lo que hay regalaria un disparo "
                            + "instantaneo");
        }
    }

    @Test
    @DisplayName("Punta Fina: pegado paga, lejos no, y el borde cuenta")
    void puntaFina() {
        assertEquals(0.0F, CombatEnchantRules.puntaFina(0, 1.0), 1.0E-6,
                "sin el encantamiento no paga nada");
        assertEquals(0.15F, CombatEnchantRules.puntaFina(1, 1.0), 1.0E-6);
        assertEquals(0.25F, CombatEnchantRules.puntaFina(2, 1.0), 1.0E-6);
        assertEquals(0.25F, CombatEnchantRules.puntaFina(2, CombatEnchantRules.PUNTA_FINA_RANGE),
                1.0E-6, "**el borde paga**: la condicion es `hasta 1.5`, no `menos de 1.5`");
        assertEquals(0.0F, CombatEnchantRules.puntaFina(2, CombatEnchantRules.PUNTA_FINA_RANGE + 0.01),
                1.0E-6, "un pelo mas alla ya no: la ropera tiene 3,6 de alcance, y a media distancia "
                        + "el encantamiento no premia nada");
    }

    @Test
    @DisplayName("Filo Astillado: por pieza, y **sin armadura no paga**")
    void filoAstillado() {
        assertEquals(0.0F, CombatEnchantRules.filoAstillado(2, 0), 1.0E-6,
                "**sin armadura no paga**: es la inversa exacta del martillo, que premia al blindado");
        assertEquals(0.06F, CombatEnchantRules.filoAstillado(1, 1), 1.0E-6);
        assertEquals(0.24F, CombatEnchantRules.filoAstillado(1, 4), 1.0E-6);
        assertEquals(0.40F, CombatEnchantRules.filoAstillado(2, 4), 1.0E-6,
                "cuatro piezas al nivel 2");
        assertEquals(0.40F, CombatEnchantRules.filoAstillado(2, 9), 1.0E-6,
                "**y mas de cuatro no suma**: no se pueden llevar mas piezas que cuatro");
    }

    @Test
    @DisplayName("Apunalar: la espalda paga y el frente no")
    void apunalar() {
        assertEquals(0.0F, CombatEnchantRules.apunalar(2, false), 1.0E-6, "de frente no paga");
        assertEquals(0.0F, CombatEnchantRules.apunalar(0, true), 1.0E-6,
                "ni desde atras sin el encantamiento");
        assertEquals(0.60F, CombatEnchantRules.apunalar(1, true), 1.0E-6);
        assertEquals(0.90F, CombatEnchantRules.apunalar(2, true), 1.0E-6);
    }

    @Test
    @DisplayName("**la convencion del yaw: 0 mira a +Z, 90 mira a -X**")
    void theYawConvention() {
        // El objetivo mira a +Z (yaw 0). Un atacante en +Z respecto de el esta DE FRENTE.
        assertFalse(CombatEnchantRules.fromBehind(0.0, 0.0, 1.0),
                "yaw 0 y atacante en +Z: de frente");
        assertTrue(CombatEnchantRules.fromBehind(0.0, 0.0, -1.0),
                "yaw 0 y atacante en -Z: **detras**");
        // El objetivo mira a -X (yaw 90). Un atacante en -X esta DE FRENTE.
        assertFalse(CombatEnchantRules.fromBehind(90.0, -1.0, 0.0),
                "**y este es el caso que caza el signo**: con `atan2(dx, dz)` en vez de "
                        + "`atan2(-dx, dz)` el 'detras' sale espejado y el golpe por la espalda "
                        + "paga cuando pegas de frente");
        assertTrue(CombatEnchantRules.fromBehind(90.0, 1.0, 0.0), "yaw 90 y atacante en +X: detras");
    }

    @Test
    @DisplayName("el angulo entre dos direcciones es el MAS CORTO, no la resta")
    void angleBetween() {
        assertEquals(0.0, CombatEnchantRules.angleBetween(0, 0), 1.0E-6);
        assertEquals(90.0, CombatEnchantRules.angleBetween(0, 90), 1.0E-6);
        assertEquals(180.0, CombatEnchantRules.angleBetween(0, 180), 1.0E-6);
        assertEquals(10.0, CombatEnchantRules.angleBetween(350, 0), 1.0E-6,
                "**el caso que rompe la resta simple**: 350 y 0 estan a diez grados, no a 350. "
                        + "Sin el wrap, un enemigo al que le pegas por la espalda pasando por el "
                        + "norte dejaria de pagar");
        assertEquals(170.0, CombatEnchantRules.angleBetween(0, 190), 1.0E-6);
    }

    @Test
    @DisplayName("los niveles fuera de rango se clampean")    void levelsAreClamped() {
        assertEquals(CombatEnchantRules.parryCooldown(100, 2),
                CombatEnchantRules.parryCooldown(100, 9),
                "un nivel de mas vale lo que el maximo, no un descuento inventado");
        assertEquals(CombatEnchantRules.parryCooldown(100, 0),
                CombatEnchantRules.parryCooldown(100, -3),
                "y uno negativo vale el de siempre, no un cooldown mas largo");
        assertEquals(CombatEnchantRules.lungeCooldown(80, 2),
                CombatEnchantRules.lungeCooldown(80, 7));
    }
}
