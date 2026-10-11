package com.cortes.combat.particle;

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
 * Que la sangre pesa, que las moscas revolotean, y -- la parte que mas vale -- que las dos se
 * llegan a dibujar.
 *
 * <h2>Por qué esto lee archivos y no objetos</h2>
 *
 * <p>Las dos partículas nombran tipos de Minecraft en su firma, así que <b>no se
 * pueden cargar en un JVM pelado</b>: una referencia a {@code ClientLevel} en el constructor hace
 * que la clase muera con {@code NoClassDefFoundError} antes de la primera aserción. Es la
 * línea que este proyecto ya cruzó varias veces, y la respuesta aqui es la misma que
 * {@code EnchantmentHooksTest} usa por lo mismo: <b>leer el código</b>, que es fragil a
 * proposito porque la alternativa es invisible.</p>
 *
 * <h2>Y el guard que mas importa es la cadena, no el numero</h2>
 *
 * <p>Una partícula que no se dibuja <b>no falla nada</b>: no crashea, no avisa, no escribe
 * en el log, y en pantalla es idéntica a arte demasiado tenue. Ya costó dos lanzamientos
 * con el slash. Por eso se afirma la cadena entera -- <b>tipo registrado + proveedor + JSON +
 * textura</b> -- y no sólo el comportamiento: cada eslabón roto da el mismo
 * silencio.</p>
 */
class ParticleWiringTest {

    @Test
    @DisplayName("la cadena entera: las dos partículas existen y se llegan a dibujar")
    void bothParticlesAreWiredEndToEnd() {
        String types = read("src/main/java/com/cortes/combat/registry/ModParticleTypes.java");
        assertTrue(types.contains("PARTICLE_TYPES.register(\"sangre\""),
                "**el tipo `sangre` no esta registrado**, asi que el helper del sangrado mandaria "
                        + "un paquete que el cliente no sabe que hacer con");
        assertTrue(types.contains("PARTICLE_TYPES.register(\"mosca\""),
                "**el tipo `mosca` no esta registrado**");

        // El proveedor. Sin el, `registerSpriteSet` no existe para ese tipo y la partícula
        // se crea con sprite nulo -- que dibuja un quad de nada sin crashear.
        String client = read("src/main/java/com/cortes/combat/client/CortesCombatClient.java");
        assertTrue(client.contains("ModParticleTypes.SANGRE.get()"),
                "**nadie le enseña al cliente a dibujar la sangre**: el tipo existe y el "
                        + "proveedor no, que es un quad invisible y sin error");
        assertTrue(client.contains("ModParticleTypes.MOSCA.get()"),
                "**lo mismo con las moscas**");

        // El JSON de descripción. Sin el, el sprite set llega vacío.
        for (String name : new String[] {"sangre", "mosca"}) {
            Path json = Path.of("src", "main", "resources", "assets", "cortescombat", "particles",
                    name + ".json");
            assertTrue(Files.exists(json), "**falta el JSON de descripción de " + name
                    + "**: sin el, el sprite set es una lista vacía y nada se dibuja");
            assertTrue(read(json.toString()).contains("cortescombat:" + name),
                    "**el JSON de " + name + " no apunta a su propia textura**");
            Path png = Path.of("src", "main", "resources", "assets", "cortescombat",
                    "textures", "particle", name + ".png");
            assertTrue(Files.exists(png), "**falta la textura de " + name + "**");
        }
    }

    @Test
    @DisplayName("la sangre pesa, y se queda en el piso el tiempo suficiente para verse")
    void theBloodFallsFastEnoughToReadAsASplatter() {
        String blood = read("src/main/java/com/cortes/combat/particle/SangreParticle.java");

        assertFalse(blood.contains("this.hasPhysics = false"),
                "**La sangre apago su fisica.** Con `hasPhysics` en falso la gota no choca con "
                        + "nada: flota donde nacio hasta que se le acabe la vida, que es una "
                        + "chispa y no una gota. Es el valor por defecto y hay que dejarlo estar.");

        float gravity = constant(blood, "GRAVITY");
        int lifetime = intConstant(blood, "LIFETIME");

        // La distancia que recorre una gota en n ticks es aproximadamente 0.02 * gravity * n^2,
        // que sale de sumar la serie de yd -= 0.04 * gravity. La pregunta no es "que tan rapido
        // cae" sino "cuantos ticks de su vida se pasa CLAVADA EN EL PISO", que es lo unico que se
        // lee como sangre.
        double bodyHeight = 1.5D;
        double ticksToLand = Math.sqrt(bodyHeight / (0.02D * gravity));
        assertTrue(ticksToLand * 2.0D <= lifetime,
                String.format("**La gota no llega a salpicar.** Con gravedad %.1f tarda %.1f ticks "
                                + "en recorrer un cuerpo, y su vida son %d: quedarian solo %.1f ticks "
                                + "de salpicadura en el suelo, que es menos de la mitad y se lee como "
                                + "una chispa que no como una herida.",
                        gravity, ticksToLand, lifetime, lifetime - ticksToLand));
        assertTrue(gravity > 1.0F,
                "**Y por debajo de la gravedad normal la gota no pesa**: 1,0 es el default de "
                        + "`Particle` y es exactamente lo que hace que se lea como una pluma");
        assertTrue(lifetime >= 10,
                "**Y la vida tiene que cubrir la caida**: una gota que muere en el aire nunca "
                        + "toca el suelo y por lo tanto nunca hay salpicadura");

        // La sangre sale del resultado del dano, no del reloj del efecto.
        String bleeding = read("src/main/java/com/cortes/combat/effect/BleedingEffect.java");
        assertTrue(bleeding.contains("\n            com.cortes.combat.particle.SangreParticles.drip(server, entity);"),
                "**Nadie suelta sangre.** Destripamiento sigue haciendo su dano y el cuerpo no "
                        + "gotea nada, que es la diferencia entre un contador y una herida");
        assertTrue(bleeding.contains("boolean landed = entity.hurt(")
                        && bleeding.contains("if (landed && entity.level()"),
                "**La sangre no depende de que el golpe entre**: un cuerpo invulnerable gotea "
                        + "sangre que no viene de ninguna parte");
    }

    @Test
    @DisplayName("las moscas revolotean, y no flotan")
    void theFliesDartInsteadOfFloating() {
        String flies = read("src/main/java/com/cortes/combat/particle/MoscaParticle.java");

        assertTrue(flies.contains("this.gravity = 0.0F"),
                "**Las moscas tienen gravedad**: la nube se apila en el piso en dos segundos y es "
                        + "un charco de insectos en vez de una nube");
        assertTrue(flies.contains("this.hasPhysics = false"),
                "**Y chocan con los bloques**, así que la nube se estancaría en la "
                        + "primera pared y no entraría por una cueva -- que es donde un tiro "
                        + "de asedio se usa");

        // Los dos tiempos del revoloteo. Sin el dardo es estatica; sin el acercamiento es una
        // linea recta que dobla, o sea un proyectil.
        //
        // Se pide la LINEA DE USO y no el nombre de la constante, porque la declaracion de
        // `STEERING` sobrevive a borrar el lerp -- es la cuarta vez que este proyecto paga esa
        // diferencia, y un guard que afirma un nombre en vez de una llamada muerde por la mitad.
        assertTrue(flies.contains("if (++sinceDart >= DART_TICKS) {"),
                "**Nunca cambia de rumbo**: una mosca que no decide es un punto flotando");
        assertTrue(flies.contains("this.xd += (targetX - this.xd) * STEERING;"),
                "**Y no se acelera hacia el rumbo nuevo**, asi que el cambio es un teletransporte "
                        + "y se lee como parpadeo, no como un animal");
        assertTrue(flies.indexOf("this.xd += (targetX - this.xd) * STEERING;")
                        < flies.indexOf("super.tick();"),
                "**El dardo se decide DESPUES de mover**: la velocidad puesta despues de "
                        + "`super.tick()` solo llega al frame siguiente, que es un lag por "
                        + "particula y no se nota hasta que hay doscientas");

        // Y nadie la dispara todavia, que esta anotado a proposito y no es un descuido.
        String helper = read("src/main/java/com/cortes/combat/particle/MoscaParticles.java");
        assertTrue(helper.contains("public static void swarm("),
                "**El helper de la nube desaparecio**, y Rey de las Moscas va a tener que "
                        + "reescribirlo en vez de llamarlo");
        assertTrue(helper.contains("ModParticleTypes.MOSCA.get()"),
                "**El helper no spawnea el tipo registrado**");
    }

    @Test
    @DisplayName("los nombres de las constantes siguen siendo los que el código declara")
    void constantsAreDeclaredWhereTheTestReadsThem() {
        // Un regex que no matchea devuelve un default que hace pass, que es peor que no testear:
        // el guard se queda verde mientras la constante cambio de nombre. Por eso se afirma que
        // ESTA leyendo un numero.
        String blood = read("src/main/java/com/cortes/combat/particle/SangreParticle.java");
        assertTrue(Pattern.compile("public static final float GRAVITY = ").matcher(blood).find(),
                "**`GRAVITY` cambio de forma**, y el test de arriba dejaria de leerla -- con un "
                        + "default que hace pass. Un guard que no puede fallar no es un guard");
    }

    private static float constant(String source, String name) {
        Matcher m = Pattern.compile("float\\s+" + name + "\\s*=\\s*([0-9.]+)F").matcher(source);
        if (!m.find()) {
            throw new AssertionError("no se pudo leer la constante " + name + " en el codigo");
        }
        return Float.parseFloat(m.group(1));
    }

    /**
     * Lo mismo para un {@code int}, y es un metodo aparte a proposito.
     *
     * <p>El lector de arriba busca {@code float X = 1.0F} y <b>un {@code int} no matchea</b>: si
     * se le pasara el de floats devolveria un default y el guard quedaria verde con la constante
     * fuera de rango. Que dos formas de declarar un numero necesiten dos lectores es exactamente
     * por qué un lector que falla callado es peor que uno que revienta.</p>
     */
    private static int intConstant(String source, String name) {
        Matcher m = Pattern.compile("int\\s+" + name + "\\s*=\\s*(\\d+)").matcher(source);
        if (!m.find()) {
            throw new AssertionError("no se pudo leer la constante entera " + name + " en el codigo");
        }
        return Integer.parseInt(m.group(1));
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
