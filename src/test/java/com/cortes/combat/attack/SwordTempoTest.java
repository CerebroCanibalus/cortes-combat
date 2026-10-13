package com.cortes.combat.attack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El tempo de la espada: <b>la fila que la frenaba, y el guard que impide que vuelva.</b>
 *
 * <h2>Por que esto lee un archivo</h2>
 *
 * <p>{@code WeaponStats} nombra {@code LivingEntity} y {@code Attributes}, asi que no arranca en una
 * JVM pelada: muere con {@code NoClassDefFoundError} antes de la primera asercion. Es la linea que
 * este proyecto cruzo varias veces -- lo que nombra un tipo de Minecraft se testea leyendo el codigo
 * -- y aca hay una razon de mas: <b>el bug vivia en la tabla, y la tabla no tiene otra puerta.</b></p>
 *
 * <h2>El bug, para que no se repita</h2>
 *
 * <p>{@code TEMPO.put(WeaponProfile.SWORD, 0.85F)} con un comentario que decia <em>"0.85 puts it at
 * 10"</em>. {@code scaleFor} <b>multiplica la velocidad de ataque</b> por el tempo, asi que un valor
 * por debajo de uno <b>frena</b>: {@code 1.6 * 0.85 = 1.36}, y {@code ticksFor(1.36)} son quince
 * ticks contra los trece de vanilla. La espada estuvo un round entero dos ticks mas lenta que vanilla
 * con su propia nota diciendo que era mas rapida.</p>
 *
 * <p><b>Y ningun test la miraba.</b> {@code IframesTest} recorria la tabla y listaba a la espada
 * como el caso veloz, y {@code AxeBalanceTest} media el hacha, que usa el mismo 0.85 con una nota que
 * si dice "slowed". El numero era correcto para el hacha y la nota era correcta para el hacha, asi
 * que la tabla entera parecia coherente y la unica fila que discordaba era la que nadie afirmaba.</p>
 *
 * <h2>Por que los comentarios se tiran antes de preguntar</h2>
 *
 * <p>Porque el comentario que explica este bug <b>cita la fila vieja</b> para poder contarlo. Un
 * guard que lea el texto crudo la encuentra en su propia explicacion y se cae con el codigo intacto
 * -- la tercera vez que este proyecto paga lo mismo ({@code ModifyExpressionValue} en un javadoc,
 * {@code SoundPoolTest} tirando la carpeta). <b>La forma barata de escribirlo bien es preguntarle al
 * codigo.</b></p>
 */
class SwordTempoTest {

    /** El archivo de la tabla, y el unico lugar donde el tempo de la espada podria volver a estar. */
    private static final String STATS = "src/main/java/com/cortes/combat/attack/WeaponStats.java";

    @Test
    @DisplayName("la espada corre al tempo de su item, y no a una fila que la frene")
    void theSwordIsNotInTheTempoTable() {
        String code = stripComments(read(STATS));
        assertFalse(code.contains("TEMPO.put(WeaponProfile.SWORD"),
                "**la espada volvio a tener un tempo propio.** La fila que estaba era 0.85, y ese "
                        + "numero multiplica la VELOCIDAD: frena. Quince ticks en vez de los trece de "
                        + "vanilla, con el comentario diciendo que la hacia mas rapida. Si alguna vez "
                        + "hace falta un tempo real va POR ENCIMA de uno; si no cambia nada, la fila "
                        + "no va -- y `tempoFor` devuelve el 1.0 por defecto.");
    }

    @Test
    @DisplayName("y un tempo por debajo de uno FRENA, que es lo que el nombre promete al reves")
    void aTempoBelowOneSlows() {
        int vanilla = AttackTiming.scaleFor(1.6F, 1.0F);
        // **Doce, y no trece: `20.0 / 1.6` da 12,4999... en punto flotante** y `Math.round` baja. El
        // comentario de la tabla decia "12-13 ticks" y el numero real es el de abajo de ese rango,
        // que es otra razon por la que el error de la fila paso desapercibido: la nota no decia un
        // numero exacto contra el cual medirla.
        assertEquals(12, vanilla,
                "la espada de vanilla: cuatro de base menos dos punto cuatro de offset, a tempo 1.0");
        assertTrue(AttackTiming.scaleFor(1.6F, 0.85F) > vanilla,
                "0.85 sobre la velocidad de una espada la FRENA ("
                        + AttackTiming.scaleFor(1.6F, 0.85F) + " ticks contra " + vanilla
                        + "). El nombre del parametro promete lo contrario y esa es la trampa exacta "
                        + "que se pago: la direccion tiene que estar afirmada, no supuesta");
    }

    /** El archivo sin comentarios. Ver la nota de la clase: aca es obligatorio. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String read(String relative) {
        try {
            return Files.readString(Path.of(relative.replace('/', java.io.File.separatorChar)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("no pude leer " + relative, e);
        }
    }
}
