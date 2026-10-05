package com.cortes.combat.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que {@link RecipeBookPopulation} esté registrado, y que el filtro sea por namespace.
 *
 * <h2>Por qué un test que lee el fuente y no la clase</h2>
 *
 * <p><b>Porque la clase no se puede cargar en una JVM pelada.</b> El handler toma un
 * {@code ServerPlayer} y llama a {@code awardRecipes}, que es de vanilla: probarlo sería
 * {@code NoClassDefFoundError} en {@code <clinit>}, que es la forma de fallo que este proyecto ya
 * pagó tres veces (los enums con {@code StringRepresentable}, el {@code StreamCodec}, y el
 * {@code @SubscribeEvent} que faltaba y costó los frames de la mecha del arcabuz).</p>
 *
 * <p>Lo que queda testeable es <b>el cableado</b>: que la clase esté registrada en el bus y que no
 * sea código muerto. Eso es exactamente lo que un handler sin registrar es, y no hay forma de que
 * un test de comportamiento lo note porque <em>no hay comportamiento</em>: el componente existe, el
 * build está verde, y el jugador tiene el libro vacío.</p>
 *
 * <h2>El costo que esto paga</h2>
 *
 * <p>Es un test acoplado al texto del fuente: renombrar la clase o reformatear el registro lo rompe
 * sin que nada esté mal. Se acepta a cambio de que borrar una línea de registro se note, porque de
 * las dos formas de romperse, una es un falso positivo barato y la otra es un bug invisible.</p>
 */
class RecipeBookPopulationTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "cortes", "combat");

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Registered on the game bus.
     *
     * <p><b>The whole point.</b> {@code @SubscribeEvent} on an unregistered class is a component in
     * place and nothing drawing it: no warning, no error, and the game runs for the whole session
     * with the fix absent. That is the shape of the arcabuz losing its fuse frames, and of this
     * player losing his recipe book.</p>
     */
    @Test
    @DisplayName("the handler is registered on the game bus")
    void theHandlerIsRegistered() {
        String mod = read(MAIN.resolve("CortesCombat.java"));

        assertTrue(mod.contains("EVENT_BUS.register"),
                "the mod class has to register something on the game bus for this to work at all, "
                        + "and it has none");
        assertTrue(mod.contains("RecipeBookPopulation.class"),
                "RecipeBookPopulation is not registered. A @SubscribeEvent method on a class that "
                        + "nobody registered does not run, does not warn, and does not fail the "
                        + "build -- the player simply keeps an empty recipe book forever, and the "
                        + "symptom reads as 'you need a new world'");
    }

    /**
     * The filter asks whether the recipe is ours, not what it makes.
     *
     * <p><b>Namespace of the recipe id, and not the result item.</b> The id is what identifies a
     * recipe in the world; the result is what it produces. Filtering on the result would be asking
     * about the symptom, and it would also pull in a recipe of ours that has been rebalanced to
     * make something vanilla.</p>
     */
    @Test
    @DisplayName("the filter is the recipe's namespace, not the item it makes")
    void theFilterIsTheRecipesNamespace() {
        String handler = read(MAIN.resolve("data").resolve("RecipeBookPopulation.java"));

        assertTrue(handler.contains("holder.id().getNamespace()"),
                "the filter has to be the recipe's own namespace");
        assertTrue(handler.contains("MODID.equals"),
                "compared against this mod's id, so a vanilla recipe never enters");
        assertTrue(!handler.contains("getResultItem"),
                "and NOT the result item. That is the symptom, not the identity: a recipe of ours "
                        + "rebalanced to produce something vanilla would drop out of the book, "
                        + "which is a bug with no way to notice it");
    }

    /**
     * No list is built when there is nothing to add.
     *
     * <p><b>The empty case is answered, not assumed.</b> {@code awardRecipes} with an empty list is
     * harmless in vanilla, but a handler that builds a list of everything in the recipe manager and
     * hands it over would be doing the work of asking every recipe in the game a question whose
     * answer is always no.</p>
     */
    @Test
    @DisplayName("an empty set of our recipes returns without asking")
    void anEmptySetReturnsWithoutAsking() {
        String handler = read(MAIN.resolve("data").resolve("RecipeBookPopulation.java"));

        assertTrue(handler.contains("ours.isEmpty()"),
                "a mod with no recipes loaded yet still gets a login event, and the handler has to "
                        + "say so rather than hand an empty list over and let it be a mystery");
    }
}