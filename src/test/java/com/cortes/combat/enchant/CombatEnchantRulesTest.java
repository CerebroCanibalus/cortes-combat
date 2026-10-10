package com.cortes.combat.enchant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
