package com.cortes.combat.special;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El invariante del refactor: <b>el conductor de las cargas no sabe de que arma es la carga</b>.
 *
 * <h2>Por que esto se guarda leyendo y no corriendo</h2>
 *
 * <p>Porque {@code SpecialCharges} habla con {@code ServerPlayer} y no se puede cargar en una JVM
 * pelada, asi que un test que lo ejercite no existe. Lo que si se puede afirmar es la <b>forma</b>
 * del archivo, y la forma es justamente lo que se rompe: el dia que alguien agregue una segunda arma
 * magica, la manera facil de que funcione es preguntar por su encantamiento adentro del conductor
 * -- y ahi el conductor deja de ser reutilizable en silencio, sin que ningun test de comportamiento
 * se entere.</p>
 *
 * <h2>Lo que este guard ya tiene de historia</h2>
 *
 * <p>Antes del refactor el encantamiento de la pala estaba escrito en <b>tres lugares</b> del
 * conductor: en la guarda del press, en la del tick y en la cuenta del ratio. Agregar una segunda
 * arma obligaba a encontrar los tres. Este test es el que impide que vuelvan a ser tres.</p>
 */
class SpecialChargesTest {

    private static final Path CONDUCTOR = Path.of("src", "main", "java", "com", "cortes", "combat",
            "special", "SpecialCharges.java");

    private static String code() throws IOException {
        String source = Files.readString(CONDUCTOR, StandardCharsets.UTF_8);
        // **Sin comentarios, y esa linea es la mitad del valor del test.** La prosa de este archivo
        // nombra la pala A PROPOSITO, para explicar que el conductor no la nombra; un guard que lee
        // prosa queda verde con el sabotaje que apaga la llamada. Es la cuarta vez que este proyecto
        // paga esa leccion.
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    @Test
    @DisplayName("el conductor no nombra el encantamiento: quien carga lo dice la carga, no el")
    void theConductorNamesNoEnchantment() throws IOException {
        assertTrue(!code().contains("PALA_ANTIMATERIAL"),
                "**`SpecialCharges` pregunta quien carga, no si es la pala.** El encantamiento va en "
                        + "`PalaOrbCharge#canCharge`; si aparece aca, el conductor dejo de ser "
                        + "reutilizable y la proxima arma magica obliga a tocar el contador, la red "
                        + "o el HUD");
    }

    @Test
    @DisplayName("el conductor no nombra el efecto: al soltar lo decide la carga, no el")
    void theConductorNamesNoEffect() throws IOException {
        assertTrue(!code().contains("launchAntimatter") && !code().contains("SpecialHandler"),
                "**el efecto va en `PalaOrbCharge#release`.** Un conductor que sabe que pasa al "
                        + "soltar sabe de que arma es la carga, que es exactamente lo que este "
                        + "archivo dejo de hacer");
    }

    @Test
    @DisplayName("la lista de cargas existe y es inmutable, para que agregar una sea una linea")
    void theListIsThere() throws IOException {
        String code = code();
        assertTrue(code.contains("List.of("),
                "las cargas se resuelven desde una lista y no desde un `switch`: agregar una es una "
                        + "linea y un archivo, y con un `switch` son tres lugares");
        assertTrue(code.contains("private static final"),
                "la lista es `static final`: es inmutable por construccion y nadie la muta en "
                        + "runtime");
    }
}
