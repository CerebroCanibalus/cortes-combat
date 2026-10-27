package com.cortes.combat.reach;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Que la version con las tangentes preparadas conteste <b>exactamente lo mismo</b> que la de
 * angulos.
 *
 * <p><b>Por que este test existe:</b> la optimizacion del cono (un {@code prepare} por escaneo en
 * vez de dos {@code Math.tan} por candidato) toca el codigo que decide <em>a que le pega un
 * golpe</em>. Un cambio ahi que "casi siempre" coincida es la peor clase de cambio: un hit que
 * desaparece cada tanto, en un borde, y que nadie puede reproducir. Asi que se afirma la
 * equivalencia exhaustiva sobre una grilla -- dentro, fuera, detras, al limite, con y sin correa,
 * con la vista normalizada y sin normalizar -- y no un par de casos felices.</p>
 *
 * <p>Puro: {@code ConeMath} no tiene un solo tipo de Minecraft, que es justo lo que hace que este
 * test pueda existir. (μ_μ)</p>
 */
class ConeMathPreparedTest {

    private static final double[] DISTANCES = {1.5, 2.4, 3.6, 5.3};
    private static final double[] H_ANGLES = {12.0, 15.0, 30.0, 35.0, 75.0, 85.0};
    private static final double[] V_ANGLES = {15.0, 25.0, 30.0, 60.0, 89.0};

    @Test
    @DisplayName("the prepared cone answers bit-for-bit like the angle one")
    void preparedEqualsAngles() {
        // Origenes: a la altura de los ojos y con la vista recta, y uno mirando hacia arriba
        // (el caso degenerado del frame).
        double[][] looks = {
                {0.0, 0.0, 1.0},      // recto
                {0.0, 1.0, 0.0},      // mirando arriba
                {0.0, 0.0, 2.0},      // sin normalizar: el test lo normaliza adentro
                {0.3, -0.2, 0.9},     // mixto
        };
        // Objetivos: adelante cerca, adelante lejos, al costado, atras, sobre el ojo.
        double[][] targets = {
                {0.0, 64.4, 2.6},
                {0.0, 64.0, 5.0},
                {2.5, 64.2, 2.5},
                {0.0, 64.0, -1.0},
                {0.0, 64.0, 0.0},
                {-0.9, 65.4, 0.7},
        };

        int checked = 0;
        for (double[] look : looks) {
            for (double h : H_ANGLES) {
                for (double v : V_ANGLES) {
                    for (double d : DISTANCES) {
                        ConeMath.Prepared prepared = ConeMath.prepare(d, h, v);
                        for (double[] target : targets) {
                            for (double leash : new double[]{0.0, 0.3, 0.8}) {
                                boolean byAngles = ConeMath.inCone(
                                        0.0, 64.0, 0.0,
                                        look[0], look[1], look[2],
                                        target[0], target[1], target[2],
                                        d, h, v, leash, leash);
                                boolean byPrepared = ConeMath.inCone(
                                        0.0, 64.0, 0.0,
                                        look[0], look[1], look[2],
                                        target[0], target[1], target[2],
                                        prepared, leash, leash);
                                assertEquals(byAngles, byPrepared,
                                        "las dos formas discrepan para look="
                                                + java.util.Arrays.toString(look)
                                                + " h=" + h + " v=" + v + " d=" + d
                                                + " target=" + java.util.Arrays.toString(target)
                                                + " leash=" + leash);
                                checked++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(4 * 6 * 5 * 4 * 6 * 3, checked, "la grilla se camino entera");
    }

    @Test
    @DisplayName("preparing mints the same tangents the angle version computes")
    void preparedMintsTheSameTangents() {
        ConeMath.Prepared prepared = ConeMath.prepare(3.6, 35.0, 30.0);
        assertEquals(3.6, prepared.maxDistance());
        assertEquals(ConeMath.tangent(35.0), prepared.tanHorizontal(), 0.0,
                "la tangente horizontal tiene que ser EXACTAMENTE la misma, no una aproximacion");
        assertEquals(ConeMath.tangent(30.0), prepared.tanVertical(), 0.0);
        // Y los bordes que `tangent` ya cuidaba siguen cuidados: cero, negativo y noventa.
        assertEquals(0.0, ConeMath.prepare(3.0, 0.0, -10.0).tanHorizontal(), 0.0);
        assertEquals(ConeMath.tangent(89.0), ConeMath.prepare(3.0, 90.0, 90.0).tanHorizontal(),
                0.0, "noventa grados se sigue clampeando a ochenta y nueve");
    }
}
