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
    @DisplayName("Sanguijuela: un porcentaje y no un flat, por el tamano de la daga")
    void sanguijuela() {
        assertEquals(0.0F, CombatEnchantRules.sanguijuela(0), 1.0E-6);
        assertEquals(0.08F, CombatEnchantRules.sanguijuela(1), 1.0E-6);
        assertEquals(0.14F, CombatEnchantRules.sanguijuela(2), 1.0E-6);
        assertEquals(0.14F, CombatEnchantRules.sanguijuela(9), 1.0E-6, "un nivel de mas vale el maximo");
    }

    @Test
    @DisplayName("Aventurero: veinte por ciento por nivel, y **al cinco el doble**")
    void aventurero() {
        assertEquals(0.0F, CombatEnchantRules.aventurero(0), 1.0E-6);
        assertEquals(0.20F, CombatEnchantRules.aventurero(1), 1.0E-6);
        assertEquals(1.00F, CombatEnchantRules.aventurero(5), 1.0E-6,
                "**al cinco el doble**: es el numero que el diseno eligio para un encantamiento de "
                        + "cinco niveles, y es el unico de la lista que llega al cien por ciento");
        assertEquals(1.00F, CombatEnchantRules.aventurero(9), 1.0E-6);
    }

    @Test
    @DisplayName("Culata: 25/50/75 %, con el nivel 3 como tope")
    void culata() {
        assertEquals(0.0F, CombatEnchantRules.culata(0), 1.0E-6);
        assertEquals(0.25F, CombatEnchantRules.culata(1), 1.0E-6);
        assertEquals(0.50F, CombatEnchantRules.culata(2), 1.0E-6);
        assertEquals(0.75F, CombatEnchantRules.culata(3), 1.0E-6);
        assertEquals(0.75F, CombatEnchantRules.culata(9), 1.0E-6);
    }

    @Test
    @DisplayName("Estocada: 15/30 %, y **subir de nivel siempre sube**")
    void estocada() {
        assertEquals(0.0F, CombatEnchantRules.estocada(0), 1.0E-6,
                "sin el encantamiento el lunge cobra la armadura entera");
        assertEquals(0.15F, CombatEnchantRules.estocada(1), 1.0E-6);
        assertEquals(0.30F, CombatEnchantRules.estocada(2), 1.0E-6);
        assertEquals(0.30F, CombatEnchantRules.estocada(9), 1.0E-6);
        assertTrue(CombatEnchantRules.estocada(2) > CombatEnchantRules.estocada(1),
                "**y el nivel 2 vale mas que el 1**: un nivel que no cambia nada no es un nivel");
    }

    @Test
    @DisplayName("Segundo Aliento: el premio por que te rompan la guardia")
    void segundoAliento() {
        assertEquals(0.0F, CombatEnchantRules.segundoAliento(0), 1.0E-6);
        assertEquals(0.15F, CombatEnchantRules.segundoAliento(1), 1.0E-6);
        assertEquals(0.25F, CombatEnchantRules.segundoAliento(2), 1.0E-6);
        assertEquals(0.25F, CombatEnchantRules.segundoAliento(9), 1.0E-6);
        assertTrue(CombatEnchantRules.SEGUNDO_ALIENTO_PUSH > 0.0F,
                "y empuja de mas: es la mitad del premio, y un cero la dejaria sin cobrar en "
                        + "silencio");
    }

    @Test
    @DisplayName("Guardia Firme: **un nivel, y el cero no lo tiene**")
    void guardiaFirme() {
        assertFalse(CombatEnchantRules.guardiaFirme(0), "sin el encantamiento todos los bloqueos cuestan");
        assertTrue(CombatEnchantRules.guardiaFirme(1));
        assertTrue(CombatEnchantRules.guardiaFirme(2),
                "**y mas de uno sigue siendo cierto**: es un nivel en el archivo, y clampear de mas "
                        + "no puede convertirlo en falso");
    }

    @Test
    @DisplayName("**el nivel del efecto es el del encantamiento menos uno, y no se pasa**")
    void amplifierFor() {
        assertEquals(0, CombatEnchantRules.amplifierFor(1), "nivel I del encantamiento = amplifier 0");
        assertEquals(1, CombatEnchantRules.amplifierFor(2));
        assertEquals(3, CombatEnchantRules.amplifierFor(4));
        assertEquals(3, CombatEnchantRules.amplifierFor(9),
                "**y de cuatro no pasa**: el tope de los cuatro encantamientos de la tanda es cuatro, "
                        + "y un amplifier sin techo es un efecto que nadie diseno");
        assertEquals(0, CombatEnchantRules.amplifierFor(0), "sin encantamiento no hay efecto");
    }

    @Test
    @DisplayName("Destripamiento: seis segundos al uno, diez al dos")
    void sangradoTicks() {
        assertEquals(0, CombatEnchantRules.sangradoTicks(0));
        assertEquals(120, CombatEnchantRules.sangradoTicks(1));
        assertEquals(200, CombatEnchantRules.sangradoTicks(2));
        assertEquals(200, CombatEnchantRules.sangradoTicks(9));
        assertTrue(CombatEnchantRules.sangradoTicks(2) > CombatEnchantRules.sangradoTicks(1),
                "**el nivel dos dura mas**: es lo unico que compra, porque el sangrado no se apila");
    }

    @Test
    @DisplayName("**Desvio Vampirico: el nivel es el TOPE del acumulado, no el robo de una vez**")
    void vampiricoAccumulates() {
        assertEquals(1, CombatEnchantRules.vampiricoStolen(0, 1), "primer parry al nivel I: un corazon");
        assertEquals(1, CombatEnchantRules.vampiricoStolen(1, 1),
                "**y al nivel I no sube mas**: el tope es el nivel, asi que un segundo parry solo "
                        + "refresca el timer");
        assertEquals(2, CombatEnchantRules.vampiricoStolen(1, 4), "al nivel IV, el segundo suma");
        assertEquals(4, CombatEnchantRules.vampiricoStolen(3, 4), "y el cuarto llega al tope");
        assertEquals(4, CombatEnchantRules.vampiricoStolen(4, 4),
                "**y ahi se queda**: cuatro corazones es el maximo que este encantamiento da");
        assertEquals(0, CombatEnchantRules.vampiricoStolen(0, 0), "sin encantamiento no roba nada");

        assertTrue(CombatEnchantRules.vampiricoStolen(2, 4) > CombatEnchantRules.vampiricoStolen(1, 4),
                "**y cada parry suma**: es la identidad del sable -- la lectura repetida -- la que "
                        + "desbloquea el tope, y un robo que no acumulara seria un premio de una vez");
    }

    @Test
    @DisplayName("**Mecha Corta: 20 → 15 → 10, y la base entra como argumento**")
    void mechaCorta() {
        assertEquals(20, CombatEnchantRules.fuseTicks(0, 20), "sin encantamiento, la de fabrica");
        assertEquals(15, CombatEnchantRules.fuseTicks(1, 20));
        assertEquals(10, CombatEnchantRules.fuseTicks(2, 20));
        assertEquals(10, CombatEnchantRules.fuseTicks(9, 20), "un nivel de mas vale el maximo");
        assertEquals(30, CombatEnchantRules.fuseTicks(0, 30),
                "**y la base es un argumento**: la tabla es pura y el numero de fabrica vive donde vive "
                        + "el arma, asi que un cambio ahi no puede dejar esta tabla mintiendo");
    }

    @Test
    @DisplayName("**Barredor: el cono crece 1,15 y 1,30, sobre la base que le pasen**")
    void barredor() {
        assertEquals(1.35F, CombatEnchantRules.sweepScale(0, 1.35F), 1.0E-6,
                "sin encantamiento, el barrido de fabrica");
        assertEquals(1.5525F, CombatEnchantRules.sweepScale(1, 1.35F), 1.0E-6);
        assertEquals(1.755F, CombatEnchantRules.sweepScale(2, 1.35F), 1.0E-6);
        assertEquals(1.755F, CombatEnchantRules.sweepScale(9, 1.35F), 1.0E-6,
                "un nivel de mas vale el maximo");
        assertTrue(CombatEnchantRules.sweepScale(1, 1.35F) < CombatEnchantRules.sweepScale(2, 1.35F),
                "y los dos niveles son distintos: un L2 que valiera lo mismo que el L1 es un nivel "
                        + "que el jugador paga y no recibe");
        assertEquals(2.0F, CombatEnchantRules.sweepScale(0, 2.0F), 1.0E-6,
                "**la base es un argumento**: el 1,35 de fabrica vive en HalberdSweep, y esta tabla "
                        + "no tiene por que saber de barridos");
    }

    @Test
    @DisplayName("**Astilladora: un golpe bloqueado cuesta dos, y sin encantamiento cuesta uno**")
    void astilladora() {
        assertEquals(1, CombatEnchantRules.astilladoraCost(0), "sin encantamiento, lo de siempre");
        assertEquals(2, CombatEnchantRules.astilladoraCost(1));
        assertEquals(2, CombatEnchantRules.astilladoraCost(9), "un nivel de mas vale el maximo");
        assertTrue(CombatEnchantRules.ASTILLADORA_COST > 1,
                "**y el gasto tiene que ser mayor que uno o el encantamiento no hace nada**: es un "
                        + "gasto y no un dano, y un gasto de uno es el golpe de cualquier arma");
    }

    @Test
    @DisplayName("**Mella: el desgaste sube 1,5 / 2,0 / 2,5 / 3,0, y el cuarto nivel se nota**")
    void mella() {
        assertEquals(1.0F, CombatEnchantRules.mellaWear(0), 1.0E-6);
        assertEquals(1.5F, CombatEnchantRules.mellaWear(1), 1.0E-6);
        assertEquals(2.0F, CombatEnchantRules.mellaWear(2), 1.0E-6);
        assertEquals(2.5F, CombatEnchantRules.mellaWear(3), 1.0E-6);
        assertEquals(3.0F, CombatEnchantRules.mellaWear(4), 1.0E-6);
        assertEquals(3.0F, CombatEnchantRules.mellaWear(9), 1.0E-6, "un nivel de mas vale el maximo");
        assertTrue(CombatEnchantRules.mellaWear(4) - CombatEnchantRules.mellaWear(3)
                        >= CombatEnchantRules.mellaWear(1) - 1.0F,
                "**y el ultimo nivel no puede ser el mas chico.** Un salto que se encoge hace que el "
                        + "cuarto nivel sea invisible, y un nivel que el jugador paga y no ve es peor "
                        + "que no tenerlo");
    }

    @Test
    @DisplayName("**Segunda Pasada: sin matar devuelve el 30 % / 50 %, y matando no devuelve nada**")
    void segundaPasada() {
        assertEquals(0.0F, CombatEnchantRules.segundaPasada(0), 1.0E-6,
                "sin encantamiento no devuelve nada");
        assertEquals(0.30F, CombatEnchantRules.segundaPasada(1), 1.0E-6);
        assertEquals(0.50F, CombatEnchantRules.segundaPasada(2), 1.0E-6);
        assertEquals(0.50F, CombatEnchantRules.segundaPasada(9), 1.0E-6);
        assertTrue(CombatEnchantRules.segundaPasada(2) < 1.0F,
                "**y no devuelve todo.** Un barrido gratis seria el barrido de siempre sin coste, y "
                        + "el coste de cuatro swings es lo unico que hace que el boton sea una "
                        + "decision en vez de la mejora del click que reemplaza");
    }

    @Test
    @DisplayName("**Inercia: el empuje sube 10 %/20 % por golpe de cadena, y el primero no sube nada**")
    void inercia() {
        assertEquals(0.0F, CombatEnchantRules.inerciaLift(0, 5), 1.0E-6,
                "sin encantamiento el empuje es el del arma");
        assertEquals(0.0F, CombatEnchantRules.inerciaLift(1, 0), 1.0E-6,
                "**y sin cadena tampoco sube nada**: el primer golpe de un martillo no es una cadena "
                        + "de uno, y un encantamiento que empuja mas en el primer golpe no es Inercia, "
                        + "es un empuje mas fuerte");
        assertEquals(0.10F, CombatEnchantRules.inerciaLift(1, 1), 1.0E-6);
        assertEquals(0.30F, CombatEnchantRules.inerciaLift(1, 3), 1.0E-6);
        assertEquals(0.60F, CombatEnchantRules.inerciaLift(2, 3), 1.0E-6);
        assertTrue(CombatEnchantRules.inerciaLift(2, 3) > CombatEnchantRules.inerciaLift(1, 3),
                "y el L2 sube mas que el L1 con la misma cadena");
        assertEquals(0.0F, CombatEnchantRules.inerciaLift(1, -2), 1.0E-6,
                "una cadena negativa no puede restar");
    }

    @Test
    @DisplayName("**Ensarte: 50 % / 100 % del dano base al cuerpo de atras**")
    void ensarte() {
        assertEquals(0.0F, CombatEnchantRules.ensarteShare(0), 1.0E-6);
        assertEquals(0.50F, CombatEnchantRules.ensarteShare(1), 1.0E-6);
        assertEquals(1.00F, CombatEnchantRules.ensarteShare(2), 1.0E-6);
        assertEquals(1.00F, CombatEnchantRules.ensarteShare(9), 1.0E-6, "un nivel de mas vale el maximo");
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
