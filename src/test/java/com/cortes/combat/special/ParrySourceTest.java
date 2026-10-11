package com.cortes.combat.special;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La pregunta que el parry no hacía: ¿este golpe vino por el aire?
 *
 * <h2>El caso que costó, contado en los dos valores que entran</h2>
 *
 * <p>Una flecha que pega tiene dos entidades en su fuente de daño: la flecha en
 * {@code getDirectEntity()} y <b>el arquero</b> en {@code getEntity()}. El parry lee la segunda,
 * porque es la que puede devolver el golpe, y por eso <b>ya devolvía flechas</b> -- sin una línea
 * que nombrara un proyectil en todo el mod, y sin un test que lo afirmara.</p>
 *
 * <p>Estos cuatro casos son el contrato: cuáles dos son un flechazo y cuáles dos no. Un test que
 * sólo probara el caso verdadero no distinguiría esta función de {@code return true}.</p>
 */
class ParrySourceTest {

    @Test
    @DisplayName("**melee: la entidad directa es el atacante, y no vino por el aire**")
    void meleeIsNotThroughTheAir() {
        assertFalse(ParrySource.cameThroughTheAir(true, true),
                "el punetazo y la espada son el caso normal del parry -- el que el sable paga con su "
                        + "alcance corto. Si esto diera true, cada golpe de melee contaria como "
                        + "proyectil y el desvio dejaria de significar algo");
    }

    @Test
    @DisplayName("**proyectil: la entidad directa es otra, y vino por el aire**")
    void aProjectileCameThroughTheAir() {
        assertTrue(ParrySource.cameThroughTheAir(true, false),
                "**este es el flechazo**: la flecha en la entidad directa, el arquero en la que "
                        + "ataca. Es el caso que el parry ya resolvia por casualidad y el que "
                        + "Capacitor necesita poder nombrar");
    }

    @Test
    @DisplayName("sin entidad directa no es un proyectil: una explosion o el entorno no vuelan")
    void nothingDirectIsNotAProjectile() {
        assertFalse(ParrySource.cameThroughTheAir(false, false),
                "una explosion o el dano del entorno no tienen entidad directa, y desviarlos seria "
                        + "otra cosa que desviar un proyectil");
        assertFalse(ParrySource.cameThroughTheAir(false, true),
                "y el segundo argumento no puede decidirlo solo: sin entidad directa no hay nada "
                        + "que haya viajado");
    }

    /**
     * **Y la respuesta se usa.** Una funcion correcta con su llamador muerto es el bug exacto que
     * este proyecto ya pago varias veces: el canal `BREAK` del sable, el `cooldownRatio` de los
     * remotos, el listener del icono plano. <b>El build queda verde y nada pasa.</b>
     *
     * <p>Se lee el archivo en vez de ejecutarlo porque un {@code ServerPlayer} en una JVM pelada no
     * existe, y el proyecto ya tiene este patron ({@code EnchantmentHooksTest} lee
     * {@code CombatEngine} por la misma razon).</p>
     */
    @Test
    @DisplayName("**el handler USA la respuesta**: una pregunta sin consumidor es el bug")
    void theHandlerUsesTheAnswer() throws java.io.IOException {
        java.nio.file.Path file = java.nio.file.Path.of("src", "main", "java", "com", "cortes",
                "combat", "special", "ParryHandler.java");
        String handler = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(handler.contains("ParrySource.cameThroughTheAir("),
                "ParryHandler no pregunta si el golpe vino por el aire: sin eso, que el parry "
                        + "devuelva flechas vuelve a ser una casualidad del motor y nadie lo afirma");
        assertTrue(handler.contains("getDirectEntity()"),
                "la pregunta necesita la entidad directa, que es la que trae el proyectil "
                        + "(getEntity() trae al arquero)");
        assertTrue(handler.contains(".discard()"),
                "**y la respuesta no se usa para nada.** El golpe se anula y vuelve -- eso ya "
                        + "pasaba -- pero la flecha se queda clavada en el que la parrio, que es un "
                        + "desvio que no desvio nada. Si esta asercion se cae, el `if (throughTheAir)` "
                        + "se quedo sin cuerpo");
    }
}
