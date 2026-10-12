package com.cortes.combat.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El estado de un arcabuz: <b>que dice, y por que ahora lo dice todo junto</b>.
 *
 * <h2>Esta clase se reescribio entera, y eso es parte del arreglo</h2>
 *
 * <p>La version anterior probaba una maquina de estados: {@code step()} avanzaba un contador por
 * tick, {@code withChamber} reconciliaba la recamara contra el stack, {@code canLight} y
 * {@code canReload} leian una bandera cacheada. Los quince tests que la cubrian estaban todos
 * verdes mientras el arma cargaba el arcabuz equivocado tres veces distintas, <b>porque probaban la
 * maquina y no la propiedad</b>: lo que importaba no era que el contador bajara, era que el estado
 * fuera <em>del arma</em>.</p>
 *
 * <p>Lo que quedo es una funcion pura de comparar un tick de fin contra el reloj. Los tests que
 * siguen fijan las tres invariantes que hacen posible el refactor -- el tick absoluto, la mecha que
 * gasta la recamara al encender, y que terminar una recarga deja un arma cargada y nada mas -- y la
 * mitad estructural vive en {@code ArcabuzGuardsTest}, porque "el estado esta en el stack" no se
 * puede preguntar en una JVM pelada.</p>
 */
class ArcabuzStateTest {

    /** Un tick de mundo cualquiera, para que las cuentas se lean. */
    private static final long NOW = 1000L;

    @Test
    @DisplayName("un arma recien salida de la caja no tiene nada")
    void emptyIsEmpty() {
        ArcabuzState state = ArcabuzState.EMPTY;
        assertFalse(state.loaded(), "sin Firestone");
        assertFalse(state.isBurning(), "sin mecha");
        assertFalse(state.isReloading(), "sin recarga");
        assertTrue(state.isEmpty(), "y `isEmpty` es la pregunta que el tick hace para no escribir nada");
        assertTrue(state.canReload(), "y se puede cargar, que es lo unico que un arma vacia hace");
    }

    @Test
    @DisplayName("la mecha se apaga cuando dice, y el reloj es el del mundo")
    void theFuseEndsWhenItSays() {
        ArcabuzState lit = ArcabuzState.EMPTY.light(NOW, 20);

        assertFalse(lit.loaded(),
                "**la Firestone se gasta al ENCENDER, no al disparar.** Es la regla que hace que un "
                        + "arma que ya va no tenga nada mas que gastar, y que no se le pueda recargar "
                        + "por detras mientras la mecha arde");
        assertTrue(lit.isBurning(), "y hay una mecha encendida");
        assertEquals(20, lit.fuseLeft(NOW), "que le quedan veinte ticks");
        assertFalse(lit.dueToFire(NOW), "y todavia no le toca");

        assertEquals(5, lit.fuseLeft(NOW + 15), "a los quince ticks le quedan cinco");
        assertFalse(lit.dueToFire(NOW + 19), "y a uno del final, todavia no");
        assertTrue(lit.dueToFire(NOW + 20), "**y al tick exacto, si**");
        assertTrue(lit.dueToFire(NOW + 500),
                "y si el arma estuvo guardada y el tick ya paso de largo, tambien: el tick de fin es "
                        + "un instante del mundo, no un contador que alguien tenga que hacer bajar");
    }

    @Test
    @DisplayName("la recarga termina cuando dice, y deja el arma cargada")
    void theReloadEndsWhenItSays() {
        ArcabuzState loading = ArcabuzState.EMPTY.startReload(NOW, 50);

        assertTrue(loading.isReloading());
        assertEquals(50, loading.reloadLeft(NOW));
        assertFalse(loading.dueToLoad(NOW), "todavia no");
        assertFalse(loading.canReload(),
                "**y no se puede recargar dos veces.** Un segundo press a los cuatro decimos no "
                        + "reinicia una recarga que ya va");
        assertFalse(loading.isBurning(), "ni tiene mecha");
        assertTrue(loading.dueToLoad(NOW + 50), "y a los cincuenta ticks, si");

        ArcabuzState done = loading.finished();
        assertTrue(done.loaded(), "**terminar deja el arma cargada**");
        assertFalse(done.isReloading(), "y sin recarga corriendo");
        assertFalse(done.isEmpty(), "y por eso no es un arma vacia");
    }

    @Test
    @DisplayName("soltar el boton tira la recarga y NO toca la mecha")
    void cancellingAReloadLeavesTheFuseAlone() {
        ArcabuzState loading = ArcabuzState.EMPTY.startReload(NOW, 50);
        ArcabuzState cancelled = loading.cancelReload();
        assertFalse(cancelled.isReloading(), "el timer se tira, no se pausa");
        assertTrue(cancelled.isEmpty(), "y no quedo nada");

        // **Y la cancelacion no es un `EMPTY` a secas, aunque lo parezca.** Un arma a medio cargar no
        // tiene mecha, asi que los dos coinciden -- pero la firma dice que solo toca su campo, y este
        // test es el que hace que siga siendo asi el dia que un arma pueda recargar y arder a la vez.
        ArcabuzState both = new ArcabuzState(true, NOW + 20, 20, NOW + 50, 50);
        ArcabuzState afterCancel = both.cancelReload();
        assertTrue(afterCancel.isBurning(), "la mecha sigue encendida");
        assertEquals(NOW + 20, afterCancel.fuseEndsAt(), "y en el mismo tick de siempre");
        assertTrue(afterCancel.loaded(), "y la recamara tambien sigue");
    }

    @Test
    @DisplayName("un arma con la mecha encendida no se enciende ni se recarga dos veces")
    void aBurningGunDoesNothingElse() {
        ArcabuzState lit = ArcabuzState.EMPTY.light(NOW, 20);
        assertFalse(lit.canReload(),
                "**un arma con la mecha encendida y una recarga nueva serian dos tiros de una "
                        + "Firestone.** Por eso el press pregunta esto antes que nada");
    }

    @Test
    @DisplayName("un arma que ya disparo no tiene nada, y volver a encenderla es una recarga de por medio")
    void afterTheShotThereIsNothingLeft() {
        ArcabuzState lit = ArcabuzState.EMPTY.light(NOW, 20);
        ArcabuzState fired = lit.afterShot();
        assertFalse(fired.isBurning(), "la mecha se apago");
        assertFalse(fired.loaded(),
                "**y la recamara sigue vacia** -- la Firestone se gasto al encender, asi que no hay "
                        + "nada que cobrar de nuevo");
        assertTrue(fired.isEmpty());
        assertTrue(fired.canReload(), "y para pegar otro tiro hay que recargar");
    }

    @Test
    @DisplayName("el tick de fin es ABSOLUTO: dos armas encendidas en momentos distintos no se pisan")
    void twoGunsHaveTheirOwnClock() {
        // **Esta es la invariante entera del refactor, vista desde el estado.**
        //
        // Con un contador relativo, el estado hay que bajarlo cada tick y por lo tanto hay que
        // guardarlo en algun lado que se tickee -- el jugador, que es de donde salieron los tres
        // bugs. Con un tick absoluto el estado es un dato de la comparacion y no hay nada que
        // avanzar: se escribe cuatro veces por ciclo de disparo y nunca entre medio, que es lo que
        // permite que viva en el componente del arma sin mover el modelo.
        ArcabuzState first = ArcabuzState.EMPTY.light(100L, 20);
        ArcabuzState second = ArcabuzState.EMPTY.light(5000L, 20);

        assertEquals(20, first.fuseLeft(100L));
        assertEquals(20, second.fuseLeft(5000L), "los dos arrancan llenos, en su propio momento");
        assertEquals(0, first.fuseLeft(5000L), "y el primero ya termino cuando el segundo empieza");
        assertNotSame(first, second, "y son dos estados distintos, como dos armas distintas");
    }

    @Test
    @DisplayName("terminar una recarga es un arma cargada y nada mas: no queda ningun conteo colgando")
    void finishingLeavesNothingDangling() {
        // **Un conteo colgando es lo que hacia que el segundo arcabuz terminara la recarga del
        // primero.** `finished()` construye el estado entero en vez de apagar un campo, asi que no
        // hay forma de que un `reloadEndsAt` viejo sobreviva al final de la recarga.
        ArcabuzState done = ArcabuzState.EMPTY.startReload(NOW, 50).finished();
        assertEquals(ArcabuzState.EMPTY.reloadEndsAt(), done.reloadEndsAt(),
                "sin recarga: el tick de fin es el de un arma vacia");
        assertEquals(ArcabuzState.EMPTY.fuseEndsAt(), done.fuseEndsAt(), "y sin mecha");
        assertEquals(ArcabuzState.EMPTY.fuseTotal(), done.fuseTotal());
        assertEquals(ArcabuzState.EMPTY.reloadTotal(), done.reloadTotal());

        // Y lo mismo del otro lado: encender no deja la recarga encendida.
        ArcabuzState lit = ArcabuzState.EMPTY.startReload(NOW, 50).light(NOW, 20);
        assertEquals(ArcabuzState.EMPTY.reloadEndsAt(), lit.reloadEndsAt(),
                "**encender mata la recarga.** Los dos usan el mismo campo de fin y el mismo reloj, "
                        + "asi que no pueden quedar los dos vivos");
    }

    @Test
    @DisplayName("construir dos veces el mismo estado da dos objetos, y eso es lo que el tick compara")
    void rebuildingIsHowTheCallerKnowsSomethingChanged() {
        // El servidor escribe el componente **solo** cuando el estado nuevo no es el mismo objeto que
        // el viejo, y esa comparacion por identidad es de donde sale "cuatro escrituras por ciclo y
        // ninguna entre medio". Un metodo que devolviera `this` cuando nada cambio seria una
        // micro-optimizacion; el que importa es el inverso: que un cambio real devuelva otro objeto.
        ArcabuzState state = ArcabuzState.EMPTY;
        assertNotSame(state, state.light(NOW, 20), "encender es un cambio");
        assertNotSame(state, state.startReload(NOW, 50), "y empezar una recarga tambien");
        assertSame(state, ArcabuzState.EMPTY, "y dos veces el mismo valor son el mismo objeto, porque "
                + "es una constante");
    }

    @Test
    @DisplayName("los largos de mecha y recarga son los de la textura y el diseno")
    void theLengthsAreTheDocumentedOnes() {
        assertEquals(20, ArcabuzState.FUSE_TICKS,
                "y no es un numero redondo: `arcabuz.png` es una tira de cinco frames que el mcmeta "
                        + "corre a cinco ticks por frame, asi que los cuatro encendidos duran veinte. "
                        + "`ModelDeclarationTest` lee el mcmeta y compara");
        assertEquals(50, ArcabuzState.RELOAD_TICKS,
                "dos segundos y medio de sostener el boton, que es medio segundo mas que el barrido "
                        + "entero de la alabarda -- el boton de la alabarda se paga en tempo y este en "
                        + "quedarse quieto");
    }
}
