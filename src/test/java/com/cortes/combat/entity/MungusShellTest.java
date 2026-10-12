package com.cortes.combat.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * El Firestone de Mungus: <b>que vuele como una flecha</b>, que es lo que arregla los tirones.
 *
 * <h2>El guard que mas vale de este archivo</h2>
 *
 * <p>Es {@link #accelerationIsOffOnBothSides()}, y no por el numero: por <b>donde</b> esta el
 * numero. {@code accelerationPower} <b>no viaja en el paquete de spawn</b>, y el cliente construye
 * la entidad con el constructor {@code (EntityType, Level)} mientras el servidor la dispara con el
 * otro. Un valor puesto en un solo constructor deja a las dos mitades simulando trayectorias
 * distintas -- que es exactamente el bug que {@code MungusShell} existe para no tener, y que es
 * facil de reintroducir porque <b>el lado que se olvida es el que nadie escribe nunca</b>: el
 * cliente no dispara, asi que su constructor parece de relleno.</p>
 *
 * <p>Hay un paquete que sincroniza ese campo, pero llega <b>despues</b> del primer tick del
 * cliente. Depender de el es depender de que el error se corrija solo.</p>
 *
 * <h2>Por que lee archivos y no objetos</h2>
 *
 * <p>{@code MungusShell} extiende {@code LargeFireball}, o sea un tipo de Minecraft: la clase no se
 * puede cargar en una JVM pelada. Misma linea que el resto de los guards de fuente del proyecto.</p>
 */
class MungusShellTest {

    private static final String SHELL = "src/main/java/com/cortes/combat/entity/MungusShell.java";
    private static final String RULES =
            "src/main/java/com/cortes/combat/enchant/CombatEnchantRules.java";

    @Test
    @DisplayName("la auto-aceleracion esta apagada, y en LOS DOS constructores")
    void accelerationIsOffOnBothSides() {
        String code = code();
        // **Dos asignaciones y no una.** El servidor dispara por uno y el cliente se construye por
        // el otro; una sola significa que una de las dos mitades se quedo con el 0,1 por defecto de
        // `AbstractHurtingProjectile` y va a acelerar mientras la otra no.
        assertEquals(2, count(code, "this.accelerationPower = "),
                "**`accelerationPower` tiene que fijarse en los DOS constructores.** El cliente no "
                        + "usa el constructor que dispara -- lo crea el paquete de spawn con "
                        + "`(EntityType, Level)` -- asi que un valor puesto solo del lado del "
                        + "servidor deja al cliente simulando otra trayectoria. Es el bug que esta "
                        + "clase entera existe para evitar");
        assertTrue(code.contains("ACCELERATION_POWER = 0.0D"),
                "**la auto-aceleracion tiene que estar en cero.** Con el 0,1 de vanilla, "
                        + "`AbstractHurtingProjectile#tick` hace `v = (v + v̂·0.1)·0.95` y la "
                        + "velocidad se multiplica por DIECINUEVE a lo largo del vuelo: arranca "
                        + "arrastrandose y termina a 1,9 bloques por tick, y como la gravedad dobla "
                        + "la direccion la auto-aceleracion empuja siempre hacia donde la curva ya "
                        + "va. Eso es el arco que se cierra sobre el piso");
    }

    @Test
    @DisplayName("el arrastre es el de la flecha, no el de la bola de fuego")
    void theInertiaIsTheArrows() {
        String code = code();
        assertTrue(code.contains("float getInertia()"),
                "**sin la inercia propia queda el 0,95 de la bola de fuego**, que frena un cinco por "
                        + "ciento por tick -- el ochenta y cinco por ciento de la velocidad perdida "
                        + "en un vuelo de cuarenta ticks. `AbstractArrow` usa 0,99 y es lo que hace "
                        + "que una flecha siga siendo una flecha al final del arco");
        assertTrue(code.contains("INERTIA = 0.99F"),
                "0,99 es la inercia de la flecha vanilla. Es el numero, no un valor cualquiera: la "
                        + "pregunta del playtest fue literalmente como lo maneja la flecha");
    }

    @Test
    @DisplayName("la gravedad va en los dos lados, y antes de super.tick()")
    void gravityIsOnBothSides() {
        String code = code();
        assertFalse(code.contains("isClientSide"),
                "**un `if (!isClientSide)` aca deja al cliente volando recto.** Un proyectil lo "
                        + "simula tambien el cliente: si una mitad cae y la otra no, la posicion del "
                        + "cliente se corrige contra la del servidor en cada paquete, y eso se lee "
                        + "como una bola que tiembla, no como un arco. Ya fue el bug una vez");
        int gravity = code.indexOf("motion.y - GRAVITY_PER_TICK");
        int superCall = code.indexOf("super.tick();");
        assertTrue(gravity > 0 && superCall > 0 && gravity < superCall,
                "**la gravedad se aplica ANTES de `super.tick()`.** `AbstractHurtingProjectile#tick` "
                        + "lee `getDeltaMovement()` adentro: de ahi saca la posicion a la que avanza. "
                        + "Empujar hacia abajo despues de `super` mueve una bola que ya se movio "
                        + "recto, y eso es un tiron por tick");
    }

    @Test
    @DisplayName("el tipo propio: sin esto el cliente simula una bola de fuego VANILLA")
    void theTypeIsItsOwn() {
        String code = code();
        // **El bug de la ronda, y el sintoma era "tirones raros, la fisica se siente correcta".**
        //
        // `LargeFireball(Level, LivingEntity, Vec3, int)` -- el constructor que dispara -- hace
        // `super(EntityType.FIREBALL, ...)`, asi que heredar el disparo significa heredar el tipo de
        // vanilla. Y el paquete de spawn lleva `getType()`, no la clase: el cliente creaba una bola
        // de fuego de vanilla (sin gravedad, con auto-aceleracion 0,1, inercia 0,95) mientras el
        // servidor simulaba esta clase. Dos fisicas sobre la misma entidad, corregidas cada dos
        // ticks. **Poner la gravedad en los dos lados no podia alcanzar: el cliente nunca ejecutaba
        // esta clase.**
        assertTrue(code.contains("public EntityType<?> getType()"),
                "**`MungusShell` tiene que devolver su PROPIO tipo.** No es un detalle de registro: "
                        + "el paquete de spawn lo lleva, y sin esta sobreescritura el cliente "
                        + "construye una bola de fuego vanilla y simula otra fisica. Silencio total "
                        + "en el log -- el tipo de vanilla existe y la entidad se crea");
        assertTrue(code.contains("ModEntities.MUNGUS_SHELL.get()"),
                "y el que devuelve es `MUNGUS_SHELL`, el tipo que el cliente puede crear: "
                        + "`EntityType.create` usa la fabrica del tipo, y la de ese es `MungusShell::new`");
    }

    @Test
    @DisplayName("el playtest pidio la potencia un 30% abajo, y el numero lo dice")
    void thePowerCameDown() {
        double power = number(stripComments(read(RULES)), "MUNGUS_POWER");
        // Vanilla TNT es 4. La primera version fue 12, el playtest la subio al doble (24) y el
        // segundo playtest pidio "un treinta por ciento menos": 24 por 0,7 son 16,8, o sea 17.
        assertTrue(power <= 24 * 0.75,
                "la potencia es " + power + " y el playtest pidio 24 menos un treinta por ciento "
                        + "(16,8 -> 17). Sigue siendo mas de cuatro TNT juntas");
        assertTrue(power > 4 * 4,
                "y sigue siendo un tiro de asedio: por debajo de cuatro TNT (16) el cráter deja de "
                        + "abrir paredes y el encantamiento pierde su unica razon de ser. Es "
                        + "potencia " + power);
    }

    // ------------------------------------------------------------------ lectura

    private static String code() {
        return stripComments(read(SHELL));
    }

    private static int count(String haystack, String needle) {
        int found = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            found++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return found;
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
