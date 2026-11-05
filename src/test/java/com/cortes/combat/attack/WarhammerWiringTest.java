package com.cortes.combat.attack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que los dos numeros del martillo lleguen hasta donde tienen que llegar.
 *
 * <h2>Por que estos guards son textuales</h2>
 *
 * <p>Porque lo que se pregunta aqui es <b>donde esta escrito</b>, y nada en runtime puede
 * observar donde esta escrito: una penetracion del 50 % que se aplicara por el camino equivocado
 * compila, arranca y no se rompe. Lo unico que se puede cerrar es el camino: que la fuente del
 * golpe se elija por la penetracion, que el modificador de armadura se ponga antes del golpe y
 * se saque en el {@code finally}, y que el tipo de dano nuevo exista en el datapack.</p>
 *
 * <p><b>Y los archivos se leen sin comentarios.</b> Este proyecto ya pago tres veces el mismo
 * bug: un guard que busca una cadena en la prosa y encuentra su propia explicacion. El archivo
 * se lee pelado -- sin los comentarios de bloque ni los de linea -- y la pregunta se le hace al
 * codigo. (???)</p>
 */
class WarhammerWiringTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path DATA = Path.of("src", "main", "resources", "data");

    private static String read(Path path) {
        assertTrue(Files.isRegularFile(path), "missing: " + path);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** El archivo sin sus comentarios: la pregunta se le hace al codigo, no a la prosa. */
    private static String code(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String codeOf(String relative) {
        return code(read(MAIN.resolve(Path.of(relative))));
    }

    @Test
    @DisplayName("el motor lee los dos numeros de la configuracion del servidor")
    void theEngineReadsTheConfig() {
        String engine = codeOf("com/cortes/combat/attack/CombatEngine.java");

        assertTrue(engine.contains("ServerConfig.warhammerTuning()"),
                "**el motor tiene que leer los numeros de la config.** Sin esta llamada el tope y "
                        + "la penetracion serian numeros que existen en un archivo y no llegan a "
                        + "ningun golpe");

        assertTrue(engine.contains("tuning.bypassesArmour()"),
                "y la penetracion tiene que decidir la fuente del golpe: la que ignora la armadura "
                        + "entera y la que no");
    }

    @Test
    @DisplayName("la fuente del golpe cambia con la penetracion")
    void theSourceFollowsThePenetration() {
        String engine = codeOf("com/cortes/combat/attack/CombatEngine.java");

        assertTrue(engine.contains("ModDamageTypes.warhammerIgnoredArmour(player)"),
                "con penetracion entera el golpe usa el tipo que ignora la armadura, como siempre");

        assertTrue(engine.contains("ModDamageTypes.warhammerPartial(player)"),
                "**y con penetracion parcial usa el otro tipo**, que no ignora la armadura pero "
                        + "atraviesa el escudo. Sin el, un 50 % de penetracion seria 100 %");
    }

    @Test
    @DisplayName("la armadura se baja antes del golpe y se devuelve en el finally")
    void theArmourGoesUpAndComesBack() {
        String engine = codeOf("com/cortes/combat/attack/CombatEngine.java");

        assertTrue(engine.contains("ArmourPierce.warhammer(tuning, armour)"),
                "la penetracion parcial baja el atributo de armadura del objetivo por este golpe");

        assertTrue(engine.contains("armour.addTransientModifier(hammerPierce)"),
                "y lo sube antes del golpe, no despues");

        int finallyAt = engine.indexOf("finally {");
        int removeAt = engine.indexOf("removeModifier(hammerPierce)");
        assertTrue(finallyAt >= 0 && removeAt > finallyAt,
                "**y lo saca en el finally.** Una armadura que queda baja para siempre es el bug "
                        + "que este camino no puede dejar atras, y `hurt` puede tirar por un mod "
                        + "de terceros: fuera del finally, el modificador se queda puesto");
    }

    @Test
    @DisplayName("el tope vive en la formula del dano")
    void theCapLivesInTheFormula() {
        String resolver = codeOf("com/cortes/combat/attack/DamageResolver.java");

        assertTrue(resolver.contains("tuning.capDamage(damage)"),
                "**el tope se aplica en la formula**, que es la que el tooltip imprime. Un tope "
                        + "aplicado por el motor seria un numero que el tooltip no conoce");
    }

    @Test
    @DisplayName("el tooltip imprime el numero topado")
    void theTooltipKnowsTheCap() {
        String tooltip = codeOf("com/cortes/combat/item/tooltip/WeaponTooltip.java");
        String handler = codeOf("com/cortes/combat/client/CombatTooltipHandler.java");

        assertTrue(tooltip.contains("WarhammerTuning tuning"),
                "el tooltip recibe los numeros del martillo");

        assertTrue(handler.contains("ServerConfig.warhammerTuning()"),
                "**y el cliente los lee de la config del servidor**, que es de donde sale el numero "
                        + "que el jugador ve. En 1.21.1 NeoForge sincroniza esa config al cliente");
    }

    @Test
    @DisplayName("el tipo de dano de la penetracion parcial existe y esta en los tags correctos")
    void thePartialDamageTypeIsDeclared() {
        Path type = DATA.resolve(Path.of("cortescombat", "damage_type", "warhammer_partial.json"));
        assertTrue(Files.isRegularFile(type),
                "**falta el archivo del tipo de dano.** El id existe en Java y el datapack lo "
                        + "carga desde el JSON: sin el archivo, el primer golpe cargado parcial "
                        + "tira un MissingRegistryKeyException en mitad del swing");

        String json = read(type);
        assertTrue(json.contains("\"message_id\": \"player\""), json
                + "\n\nel mismo mensaje de muerte que el otro tipo del martillo y que el de "
                + "vanilla: un id nuevo pondria la clave en crudo en la pantalla de muerte");

        String isPlayerAttack = read(DATA.resolve(
                Path.of("minecraft", "tags", "damage_type", "is_player_attack.json")));
        assertTrue(isPlayerAttack.contains("cortescombat:warhammer_partial"),
                "es un ataque de jugador, como los otros dos: el credito de muerte y los "
                        + "encantamientos que disparan con uno siguen funcionando");

        String bypassesShield = read(DATA.resolve(
                Path.of("minecraft", "tags", "damage_type", "bypasses_shield.json")));
        assertTrue(bypassesShield.contains("cortescombat:warhammer_partial"),
                "**y atraviesa el escudo**, como el golpe cargado del martillo de siempre. Sin este "
                        + "tag un escudo de vanilla pararia el golpe, que es cambiar el arma "
                        + "entera por un numero de configuracion");

        String bypassesArmour = read(DATA.resolve(
                Path.of("minecraft", "tags", "damage_type", "bypasses_armor.json")));
        assertFalse(bypassesArmour.contains("cortescombat:warhammer_partial"),
                "**pero NO ignora la armadura**: por eso existe el tipo, para que la armadura "
                        + "cuente en fraccion en vez de no contar");
    }

    @Test
    @DisplayName("la pata de 1.20.1 tiene su propio cuerpo del modificador")
    void theOldLegHasItsOwnBody() {
        Path twin = Path.of("src", "v120", "java", "com", "cortes", "combat", "attack",
                "ArmourPierce.java");
        assertTrue(Files.isRegularFile(twin),
                "**falta el gemelo de 1.20.1.** El identificador de un modificador es una "
                        + "ResourceLocation en 1.21 y un UUID con nombre en 1.20.1, y la operacion "
                        + "que multiplica el total tiene dos nombres: el codigo compartido no "
                        + "compila en las dos patas");

        String source = code(read(twin));
        assertTrue(source.contains("MULTIPLY_TOTAL"),
                "la operacion de 1.20.1 se llama MULTIPLY_TOTAL, y hace la misma cuenta que "
                        + "ADD_MULTIPLIED_TOTAL: multiplicar el total por (1 + cantidad)");

        assertTrue(source.contains("nameUUIDFromBytes"),
                "y el id es un UUID derivado del nombre, como los de Contragolpe y Estocada");
    }
}
