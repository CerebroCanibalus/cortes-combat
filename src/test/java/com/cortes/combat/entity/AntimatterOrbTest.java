package com.cortes.combat.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * El orbe de la pala: <b>contra que se clava, hasta donde llega, y que se ve</b>.
 *
 * <h2>Por que esto lee archivos y no objetos</h2>
 *
 * <p>{@code AntimatterOrb} extiende {@code Entity}, asi que no se puede cargar en una JVM pelada:
 * muere con {@code NoClassDefFoundError} antes de la primera asercion. Es la linea que este
 * proyecto cruzo varias veces -- <b>"lo que implementa algo de Minecraft no se testea"</b> -- y la
 * respuesta aca es la misma que usan {@code ParticleWiringTest} y {@code EnchantmentHooksTest}:
 * leer el codigo, que es fragil a proposito porque la alternativa es invisible.</p>
 *
 * <h2>Los guards leen el archivo SIN comentarios, y eso es obligatorio aca</h2>
 *
 * <p>Porque los comentarios de {@code AntimatterOrb} <b>nombran el bug a proposito</b> -- citan el
 * gatillo viejo para explicar por que se fue. Un guard que leyera prosa encontraria
 * {@code hitSomething || stopped || !touched.isEmpty()} adentro de la explicacion de que eso ya no
 * esta, quedaria verde con el bug de vuelta, y seria la quinta vez que este proyecto paga lo
 * mismo. La forma barata de escribirlo bien es la de siempre: <b>pedir la llamada al ras</b>, con
 * su indentacion, y no el nombre de una condicion.</p>
 */
class AntimatterOrbTest {

    private static final String ORB = "src/main/java/com/cortes/combat/entity/AntimatterOrb.java";
    private static final String RULES =
            "src/main/java/com/cortes/combat/enchant/CombatEnchantRules.java";
    private static final String NET = "src/main/java/com/cortes/combat/net/ModNetworking.java";
    private static final String CLIENT =
            "src/main/java/com/cortes/combat/client/CortesCombatClient.java";
    private static final String SHAKE = "src/main/java/com/cortes/combat/client/CameraShake.java";
    private static final String REACTOR_SHAKE = "src/main/java/com/cortes/combat/client/ReactorShake.java";
    private static final String CONFIG = "src/main/java/com/cortes/combat/config/ClientConfig.java";

    @Test
    @DisplayName("el orbe NO se detona por tocar un cuerpo: el `pull` lo empujaba adentro de su propia caja")
    void touchingABodyDoesNotDetonate() {
        String code = code();
        assertFalse(code.contains("hitSomething || stopped || "),
                "**el gatillo de la implosion volvio a incluir la lista de entidades.** El orbe "
                        + "ATRAE: el cuerpo que viene empujado entra en su caja de contacto y el orbe "
                        + "reventaba a mitad de camino, comiendose su propio tiron. Fue reportado "
                        + "exacto por el playtest: \"al atraer enemigos durante la transicion este "
                        + "explota a medio camino\"");
        assertTrue(code.contains("if (hitSomething || stopped) {"),
                "el orbe se clava **solo contra bloques**. Si esta linea no esta, el gatillo cambio "
                        + "de forma y hay que mirar cual de las dos condiciones quedo");
        assertFalse(code.contains("touched"),
                "**la lista de entidades ya no se mide**: existia para el gatillo, y el mordisco de "
                        + "contacto lo cobra `bite` por su cuenta. Una lista que nada lee es una "
                        + "invitacion a volver a usarla de gatillo");
    }

    @Test
    @DisplayName("al agotar el rango IMPLOSIONA, y no se apaga sin cobrar")
    void theEndOfTheRangeDetonates() {
        // La rama, anclada en la llamada al ras y acotada a su propio cuerpo. Anclarse en el nombre
        // de la constante no sirve: aparece en la prosa de dos archivos mas.
        String code = code();
        String anchor = "if (age >= CombatEnchantRules.PALA_ANTIMATERIAL_LIFETIME_TICKS) {";
        int at = code.indexOf(anchor);
        assertTrue(at > 0,
                "**la rama del fin de rango no existe con esta forma.** Sin ella, el orbe se apaga "
                        + "sin cobrar al agotar el alcance, y eso le enseña al que se corrio que "
                        + "correr es gratis -- que es lo contrario de lo que el orbe cobra");
        int end = code.indexOf("\n        }", at);
        String branch = code.substring(at, end);
        assertTrue(branch.contains("imploding = true"),
                "**el fin de rango tiene que ENTRAR A LA IMPLOSION**, con el mismo camino que la "
                        + "colision contra un bloque: un cuerpo que se va a reventar en seis ticks y "
                        + "otro que se deshace sin cobrar no pueden ser la misma rama a medias");
        assertFalse(branch.contains("discard()"),
                "**y NO se descarta.** Un `discard()` aca apaga el orbe en el aire, que es "
                        + "exactamente el comportamiento que el playtest mando cambiar");
    }

    @Test
    @DisplayName("el rango subio un 20%: velocidad por vida, y la palanca fue la vida")
    void theRangeGrew() {
        String rules = stripComments(read(RULES));
        double speed = number(rules, "PALA_ANTIMATERIAL_SPEED");
        int ticks = (int) number(rules, "PALA_ANTIMATERIAL_LIFETIME_TICKS");
        // El alcance es velocidad por vida. 0,35 por 80 daba veintiocho bloques; el playtest pidio
        // un veinte por ciento mas, o sea treinta y tres y medio.
        double range = speed * ticks;
        assertTrue(range >= 33.0,
                "el orbe llega a " + range + " bloques y el playtest pidio el rango +20% (de 28 a "
                        + "33 y medio). Si alguien bajo la vida o la velocidad, esto lo caza");
        assertTrue(speed <= 0.5,
                "**y la palanca es la VIDA, no la velocidad.** Subir la velocidad acorta el viaje en "
                        + "segundos, que es justo lo que hace que el orbe sea un ataque con aviso y "
                        + "no un fogonazo: el `pull` del camino dejaria de tener tiempo de pasar");
    }

    @Test
    @DisplayName("el temblor de camara esta cableado de punta a punta, y las dos explosiones lo comparten")
    void theShakeIsWiredEndToEnd() {
        assertTrue(read(ORB).contains("CameraShakePayload("),
                "el reventon no avisa a ninguna camara");
        assertTrue(read(NET).contains("CameraShakePayload.TYPE, CameraShakePayload.STREAM_CODEC"),
                "**el payload existe y nadie lo registra**: el cliente lo recibiria sin saber que "
                        + "hacer con el, que es el silencio exacto de un listener huerfano");
        assertTrue(read(CLIENT).contains("register(CameraShake.class)"),
                "**la clase escucha el evento y nadie la registra.** Es la forma del listener "
                        + "huerfano que ya costo un icono violeta en el arcabuz: el build queda "
                        + "verde y no pasa nada");
        assertTrue(read(SHAKE).contains("ClientConfig.CAMERA_SHAKE.get()"),
                "la camara del orbe tiene que poder apagarse: es un efecto de pantalla y este "
                        + "proyecto trata todos como opt-out");
        assertTrue(read(REACTOR_SHAKE).contains("ClientConfig.CAMERA_SHAKE.get()"),
                "**una sola llave para todo el temblor de camara.** El reactor quedo leyendo la "
                        + "suya, asi que apagar el temblor apagaba uno y dejaba el otro -- y la "
                        + "queja que responde esa llave es \"la camara se mueve sola\", que no sabe "
                        + "cual explosion la movio");
        assertFalse(read(CONFIG).contains("REACTOR_SHAKE"),
                "la llave vieja sigue existiendo con el nombre que mentia: solo el reactor la leia, "
                        + "y su reventon no es el unico que mueve la camara");
    }

    // ------------------------------------------------------------------ lectura

    /** El archivo del orbe, sin comentarios. Ver la nota de la clase: es obligatorio aca. */
    private static String code() {
        return stripComments(read(ORB));
    }

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
