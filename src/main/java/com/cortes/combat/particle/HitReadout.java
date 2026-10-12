package com.cortes.combat.particle;

import java.util.Arrays;

/**
 * Reparte un puñado de puntos entre varios colores, <b>sin que el total mienta</b>.
 *
 * <h2>El problema que resuelve</h2>
 *
 * <p>La lectura de daño dice dos cosas a la vez: <b>cuánto</b> (la cantidad de puntos, que sigue la
 * regla de vanilla) y <b>qué pasó</b> (el color de cada punto). Las dos salen del mismo golpe, y el
 * reparto no es exacto: si el split son 7,3 rojos, 2,1 azules y 0,6 grises, un total de 5 puntos no
 * se divide en números enteros sin que sobre o falte.</p>
 *
 * <p><b>Y redondear cada uno por su lado rompe justo lo que el diseño promete.</b> Cinco puntos
 * repartidos con {@code Math.round} pueden dar 7, 2 y 1 -- nueve puntos donde había cinco, o cuatro
 * donde había cinco. El jugador que <em>cuenta</em> para saber el daño leería un número que no es el
 * del golpe, y la mitad de la mecánica se cae.</p>
 *
 * <h2>Mayor resto</h2>
 *
 * <p>Se toma la parte entera de cada proporción y <b>los puntos que sobran van a los que tienen la
 * fracción más grande</b>, de mayor a menor. El total cierra exacto por construcción, y el error de
 * cada color queda por debajo de un punto -- que es lo mejor que se puede hacer con enteros.</p>
 *
 * <p>El desempate es por <b>índice</b> y no al azar, y eso importa: un reparto aleatorio haría que
 * el mismo golpe se viera distinto en cada cliente, que es exactamente el error que el proyecto ya
 * evitó al sembrar el temblor del viewmodel por golpe en vez de por frame.</p>
 *
 * <h2>Por qué es pura</h2>
 *
 * <p>Enteros que entran, enteros que salen. Un {@code int[]} y un {@code float[]} no son tipos de
 * Minecraft, así que <b>corre en una JVM pelada</b> -- que es la línea del proyecto y la razón de
 * que este archivo exista: el reparto es la parte con aritmética de verdad, y tenía que quedar
 * donde un test la alcance.</p>
 */
public final class HitReadout {

    private HitReadout() {
    }

    /**
     * Cuántos puntos de cada color, sumando exactamente {@code total}.
     *
     * @param amounts el peso de cada color, en unidades de daño. Puede traer ceros
     * @param total   cuántos puntos hay para repartir; cero o menos devuelve todo en cero
     * @return un array de la misma longitud, con las cantidades repartidas
     */
    public static int[] distribute(float[] amounts, int total) {
        int[] counts = new int[amounts.length];
        if (total <= 0) {
            return counts;
        }

        float sum = 0.0F;
        for (float amount : amounts) {
            if (amount > 0.0F) {
                sum += amount;
            }
        }
        // Un golpe sin daño positivo no tiene nada que mostrar. Devolver el total repartido acá
        // pintaría puntos de un color que ninguna cuenta respalda -- y el caso existe: un golpe
        // parado entero por la armadura tiene un `dealt` de cero.
        if (sum <= 0.0F) {
            return counts;
        }

        // La parte entera de cada proporción, y la fracción que sobra, para el reparto final.
        float[] remainders = new float[amounts.length];
        int assigned = 0;
        for (int i = 0; i < amounts.length; i++) {
            float exact = Math.max(0.0F, amounts[i]) / sum * total;
            counts[i] = (int) Math.floor(exact);
            remainders[i] = exact - counts[i];
            assigned += counts[i];
        }

        // **Y los que sobran van a las fracciones más grandes.** Nunca más de `length - 1` puntos,
        // porque cada piso pierde menos de uno -- así que un pasada alcanza y no hace falta un
        // bucle que pueda no terminar.
        int left = total - assigned;
        while (left > 0) {
            int best = -1;
            for (int i = 0; i < remainders.length; i++) {
                if (remainders[i] <= 0.0F) {
                    continue;
                }
                if (best < 0 || remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            if (best < 0) {
                // Todos los restos son cero y todavía sobran puntos: solo puede pasar por un error
                // de coma flotante en la suma, y el índice 0 es tan bueno como cualquier otro.
                counts[0] += left;
                break;
            }
            counts[best]++;
            remainders[best] = 0.0F;
            left--;
        }
        return counts;
    }

    /** Los puntos de un solo color: el atajo para el caso sin reparto. */
    public static int[] allOf(int buckets, int bucket, int total) {
        int[] counts = new int[buckets];
        if (bucket >= 0 && bucket < buckets) {
            counts[bucket] = Math.max(0, total);
        }
        return counts;
    }

    /** Sólo para los tests: el total de un reparto. */
    public static int sum(int[] counts) {
        return Arrays.stream(counts).sum();
    }
}
