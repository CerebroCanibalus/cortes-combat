package com.cortes.combat.special;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las dos cosas que el playtest pidio sobre los especiales: <b>que el orbe cueste, y que la nube
 * caiga sobre todos</b>.
 *
 * <h2>El precio del orbe</h2>
 *
 * <p>Un golpe comun gasta <b>un</b> punto de durabilidad -- la regla de vanilla generalizada, ver el
 * {@code hurtAndBreak(1, ...)} de {@code CombatEngine}. El orbe de la pala pega catorce en un radio
 * de cuatro, tira de cinco bloques, y costaba <b>cero</b>: eso no es un especial barato, es un
 * especial que dice que vale lo mismo que un golpe. El playtest lo llamo "muy gratuito" y la palabra
 * era exacta.</p>
 *
 * <h2>El reparto de la nube</h2>
 *
 * <p>El cono ya elige a quien llega -- es el mismo cono del disparo normal, que pega sobre todos
 * hasta el tope. Tomar solo el primero convertia un arma de area en un arma de un objetivo con mucho
 * ruido, y dejaba afuera a los otros siete que el mismo cono acababa de elegir.</p>
 *
 * <h2>Por que lee archivos</h2>
 *
 * <p>Ni {@code PalaOrbCharge} ni el camino de disparo del {@code Arcabuz} se pueden cargar en una JVM
 * pelada: los dos nombran tipos de Minecraft en su firma. Es la linea de siempre, y la respuesta de
 * siempre: <b>pedir la llamada al ras</b>, con su receptor y su parentesis, y no el nombre de un
 * metodo -- un guard que busca un nombre queda verde con la llamada envuelta en un {@code if
 * (false)}.</p>
 */
class PalaYMoscasTest {

    private static final String PALA_ORB =
            "src/main/java/com/cortes/combat/special/PalaOrbCharge.java";
    private static final String ARCABUZ = "src/main/java/com/cortes/combat/special/Arcabuz.java";
    private static final String RULES =
            "src/main/java/com/cortes/combat/enchant/CombatEnchantRules.java";

    @Test
    @DisplayName("soltar el orbe gasta durabilidad de la pala, y mas que un golpe")
    void theOrbCostsDurability() {
        String charge = stripComments(read(PALA_ORB));
        // **La llamada al ras.** Reconocer + metodo + parentesis, no el nombre del metodo: si el
        // cobro se apaga, esto muere.
        assertTrue(charge.contains("ItemDamage.damageMainHand(player,"),
                "**el orbe tiene que gastar durabilidad.** Era el reporte: \"un ataque muy "
                        + "gratuito\". Un golpe comun pierde un punto y el orbe -- catorce de dano en "
                        + "un radio de cuatro con un tiron de cinco bloques -- no perdia ninguno");
        assertTrue(charge.contains("PALA_ANTIMATERIAL_DURABILITY_COST"),
                "el costo sale de `CombatEnchantRules`, como todo el balance del proyecto: no hay "
                        + "numeros de gameplay escritos en el sitio que los cobra");
        double cost = number(stripComments(read(RULES)), "PALA_ANTIMATERIAL_DURABILITY_COST");
        assertTrue(cost > 1.0D,
                "**mas de un punto**, que es lo que cuesta un golpe de la misma arma. Con uno, el "
                        + "orbe seguiria diciendo que vale lo mismo que un golpe comun; el numero es "
                        + cost);
        // **Y la regla que salio de la SEGUNDA ronda, no la del numero.** La primera correccion fue
        // cinco, razonada como "cinco veces un golpe", y el playtest la midio y la llamo "muy
        // poquito". Tenia razon por una cuenta que la primera version no hizo: **cinco sobre 2031 de
        // una pala de netherite son cuatrocientos orbes** -- o sea, lo mismo que gratis. Un costo
        // que no cambia ninguna decision no es un costo, es un numero.
        assertTrue(cost >= 16.0D,
                "el costo tiene que ser una fraccion que se sienta de una herramienta de verdad: con "
                        + "cinco, una pala de netherite (2031 de durabilidad) tira cuatrocientos "
                        + "orbes y el precio no cambia ninguna decision. Es " + cost);
    }

    @Test
    @DisplayName("la nube cae sobre TODOS los del cono, no sobre el primero")
    void everyTargetInTheConeGetsACloud() {
        String arcabuz = stripComments(read(ARCABUZ));
        int loop = arcabuz.indexOf("for (LivingEntity landing : seen) {");
        assertTrue(loop > 0,
                "**el encantamiento tiene que recorrer el cono entero.** El reporte fue \"todos los "
                        + "enemigos que das con el cono deberian infestarse, actualmente solo se "
                        + "infesta uno\". El cono ya decide a quien llega: dejar afuera a los demas "
                        + "es cobrar un disparo por lo que devuelve un golpe");
        // **La nube adentro del bucle**, y eso es lo que se afirma con las dos posiciones: crear una
        // sola y meterla en un bucle que no la usa es la forma en que este guard quedaria verde
        // afirmando lo contrario de lo que pasa.
        int spawn = arcabuz.indexOf("level.addFreshEntity(cloud);");
        int first = arcabuz.indexOf("LivingEntity first = seen.get(0);");
        assertTrue(spawn > loop,
                "el `addFreshEntity` tiene que estar DENTRO del bucle, no antes");
        assertTrue(spawn < first,
                "y antes de la linea que toma el primero, que es la que quedo para el sonido: si el "
                        + "spawn viajara despues, el bucle no estaria creando nada");
    }

    // ------------------------------------------------------------------ lectura

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static double number(String source, String name) {
        Matcher matcher = Pattern.compile(Pattern.quote(name) + "\\s*=\\s*([0-9.]+)[dDfF]?;")
                .matcher(source);
        if (!matcher.find()) {
            throw new AssertionError("no encontre la constante " + name);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static String read(String relative) {
        Path file = Path.of(relative.replace('/', java.io.File.separatorChar));
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("no se pudo leer " + file + ": " + e.getMessage(), e);
        }
    }
}
