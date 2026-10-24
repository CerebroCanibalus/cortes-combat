package com.cortes.combat.survival;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the survival system is wired, and that its version-specific surface stays where it was put.
 *
 * <h2>Why this reads the source</h2>
 *
 * <p>Three of these files cannot be loaded by a test at all: {@code FoodDataMixin} is bytecode
 * instructions, {@code SurvivalTicker} takes a {@code Player}, and {@code CortesCombat} is the mod
 * entry point. Every failure this class exists to catch is the one this project has paid for
 * repeatedly -- <b>a line that compiles, keeps the whole suite green, and leaves a mechanic
 * switched off</b>. The exhauston tap is one such line; so is the guard gate in front of the
 * eating interrupt; so is a mixin that is written but never named in the config.</p>
 *
 * <p>Brittle by design, like {@code SwingInputHandlerWiringTest}. The two ways to be wrong are a
 * false positive when a file is reformatted and an invisible bug when nobody notices, and the
 * first is cheap.</p>
 */
class SurvivalWiringTest {

    private static final Path ROOT = Path.of("src", "main");

    private static final Path MIXIN_DIR = ROOT.resolve("java").resolve("com").resolve("cortes")
            .resolve("combat").resolve("mixin");

    private static final Path SURVIVAL_DIR = ROOT.resolve("java").resolve("com").resolve("cortes")
            .resolve("combat").resolve("survival");

    // --- The seam ---------------------------------------------------------------

    @Test
    @DisplayName("both mixins are named in the config, or neither of them runs")
    void bothMixinsAreRegistered() {
        String config = read(ROOT.resolve("resources").resolve("cortescombat.mixins.json"));

        assertTrue(config.contains("\"FoodDataMixin\""),
                "**the food tick would still be vanilla's.** A mixin class that compiles and is "
                        + "absent from the config is never applied: no error, no warning, and a "
                        + "mod whose entire survival rework does nothing. This is the same shape "
                        + "as the missing `@SubscribeEvent` that cost the arcabuz its fuse frames");
        assertTrue(config.contains("\"ItemStackDrinkDurationMixin\""),
                "**and liquids would still take a second and a half.** Same failure, second file: "
                        + "written, compiling, never applied");
    }

    @Test
    @DisplayName("the vanilla tick is cancelled before ours runs, not after")
    void theVanillaTickIsReplacedNotAddedTo() {
        String mixin = read(MIXIN_DIR.resolve("FoodDataMixin.java"));
        String body = methodBody(mixin, "private void cortescombat$survivalTick");

        int cancel = body.indexOf("ci.cancel();");
        int mine = body.indexOf("SurvivalTicker.tick(");
        assertTrue(cancel >= 0, "without this the vanilla tick is not cancelled at all");
        assertTrue(mine >= 0, "and without this there is no replacement, so nothing happens either");
        assertTrue(cancel < mine,
                "**the cancel has to come first.** Reversed, vanilla's own regen and starvation run "
                        + "and ours runs on top of them -- two healing systems, and the player "
                        + "would heal at vanilla's pace and ours at once");
    }

    @Test
    @DisplayName("the exhaustion tap is shut, not merely bypassed")
    void exhaustionIsShutOff() {
        String mixin = read(MIXIN_DIR.resolve("FoodDataMixin.java"));
        String body = methodBody(mixin, "private void cortescombat$noExhaustion");

        assertTrue(body.contains("ci.cancel();"),
                "**`addExhaustion` has to be cancelled.** Cancelling only the tick would remove "
                        + "exhaustion's effect while leaving every writer -- sprinting, jumping, "
                        + "landing a hit, breaking a block -- still climbing the number to its "
                        + "cap of forty, so any other mod reading it would be told this game has "
                        + "a full hunger system that is quietly not being used");
        assertTrue(body.contains("setExhaustion(0.0F);"),
                "and the value has to be zeroed, or a save written before this rework keeps "
                        + "showing a stale number forever");
    }

    /**
     * The portability claim, asserted rather than promised.
     *
     * <p>The user asked for a system that is <b>easily portable to other versions</b>. That is a
     * property of where the code lives, not of how it is written, and the cheapest way to keep it
     * is to make a test that fails the day a {@code Player} parameter shows up in a rule.</p>
     */
    @Test
    @DisplayName("the rules import no Minecraft or NeoForge type, which is what makes them portable")
    void theRulesArePortable() {
        String[] pure = {"SurvivalRules.java", "SurvivalTuning.java", "SurvivalState.java"};

        for (String file : pure) {
            String source = read(SURVIVAL_DIR.resolve(file));
            assertFalse(source.contains("import net.minecraft"),
                    "**" + file + " must not import Minecraft.** The adapter layer is the one "
                            + "file that changes when the game does; if the rules also change, "
                            + "a port means re-deriving every balance decision instead of copying "
                            + "a package and running its tests");
            assertFalse(source.contains("import net.neoforged"),
                    "**" + file + " must not import NeoForge either**, for the same reason and "
                            + "then some: the loader moves faster than the game does");
        }
    }

    @Test
    @DisplayName("the seam stays a whole-method replacement and never becomes constant patching")
    void theSeamDoesNotPatchConstants() {
        String mixin = read(MIXIN_DIR.resolve("FoodDataMixin.java"));

        assertFalse(mixin.contains("ModifyExpressionValue"),
                "**this is the portability line, and it is the one that would be crossed first.** "
                        + "The obvious way to retune vanilla's healing floor is to patch the "
                        + "literal `18` inside its tick, addressed by ordinal -- and that is "
                        + "exactly what refs/combatify does. It works until Mojang moves the "
                        + "number, and then every injection silently stops matching on a method "
                        + "that still compiles. One replacement point either applies or fails at "
                        + "bootstrap; a wrong ordinal does neither");
        assertFalse(mixin.contains("intValue="),
                "and no constant targets at all, for the same reason");
    }

    // --- The interrupt ----------------------------------------------------------

    @Test
    @DisplayName("the guard is asked before the meal is stopped")
    void theGuardGatesTheInterrupt() {
        String handler = read(SURVIVAL_DIR.resolve("EatInterruptHandler.java"));
        String body = methodBody(handler, "public static void onDamage");

        int eating = body.indexOf("GuardHandler.isEating(player)");
        int guarding = body.indexOf("GuardHandler.isGuarding(player)");
        int stop = body.indexOf("player.stopUsingItem();");

        assertTrue(eating >= 0, "the handler has to ask whether this player is eating at all");
        assertTrue(guarding >= 0,
                "**and it has to ask whether they are covered.** DESIGN section 5 wants the hit to "
                        + "interrupt eating; the older, separate rule says covering and eating at "
                        + "the same time is legal. This one line is where the two are reconciled, "
                        + "and without it the guard becomes the thing that stops you eating");
        assertTrue(stop >= 0, "and it has to actually stop the meal, or the rule never reaches "
                + "the game");
        assertTrue(eating < stop && guarding < stop,
                "**both gates have to precede the stop.** A `stopUsingItem` sitting above its own "
                        + "conditions compiles and runs for every player who is hit, which is the "
                        + "guard doing the opposite of what the design says");
        assertTrue(body.contains("player.level().isClientSide"),
                "and the client has to be excluded: `GUARD_STAMINA` is serialized but not synced, "
                        + "so asking it there would answer 'not covering' for a player who is, and "
                        + "the two sides would disagree about the same tick");
    }

    // --- The adapter ------------------------------------------------------------

    @Test
    @DisplayName("healing goes through heal(), so it is still a heal the game can see")
    void healingGoesThroughHeal() {
        String ticker = read(SURVIVAL_DIR.resolve("SurvivalTicker.java"));

        assertTrue(ticker.contains("            player.heal(plan.healAmount());"),
                "**it has to be `heal()`.** Setting health directly would bypass `LivingHealEvent`, "
                        + "and Cicatrizador's anti-healing reduction lives on that event: our own "
                        + "healing would be the one source in the game the enchantment cannot "
                        + "touch, which is a second path for a rule the project keeps in one place");
        assertTrue(ticker.contains("        data.setExhaustion(0.0F);"),
                "and the bar has to be zeroed each tick, or a pre-rework save leaves a number "
                        + "behind that no code in this system writes or reads");
        assertTrue(ticker.contains("player.level().getGameRules()"),
                "and vanilla's natural-regeneration gamerule is still honoured -- a server that "
                        + "turned it off must not be silently overruled by a mod");
    }

    @Test
    @DisplayName("a liquid is recognised by its animation, not by 'using an item'")
    void liquidsAreRecognisedByAnimation() {
        String mixin = read(MIXIN_DIR.resolve("ItemStackDrinkDurationMixin.java"));

        assertTrue(mixin.contains("getUseAnimation() == UseAnim.DRINK"),
                "**asking whether an item is in use would also match a shield**, which is "
                        + "`UseAnim.BLOCK` and must keep vanilla's own timing -- this is the exact "
                        + "trap docs/api-trampas.md records, since `Item#isEdible` went away in "
                        + "1.20.5 and there is nothing else to ask");
        assertTrue(mixin.contains("getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"),
                "and the target has to be the descriptor written out in full: a bare method name "
                        + "that matches two overloads is an injector error at bootstrap, while a "
                        + "guessed descriptor is a crash that names the mixin and not the guess");
    }

    // --- The config -------------------------------------------------------------

    @Test
    @DisplayName("the TOML names the mixin config, or nothing in the jar is ever applied")
    void theTomlNamesTheMixinConfig() {
        String toml = read(ROOT.resolve("templates").resolve("META-INF")
                .resolve("neoforge.mods.toml"));

        assertTrue(toml.contains("[[mixins]]"),
                "**this is the half of the chain no other assertion covers.** The config file in "
                        + "the jar does nothing on its own: FML finds it through this block. Its "
                        + "absence applies no mixins at all and reports a perfectly clean boot, "
                        + "which is the same silent failure the recipe round paid for with a "
                        + "different file");
        assertTrue(toml.contains("config=\"${mod_id}.mixins.json\""),
                "**and it has to name the file, not the mixins inside it.** The block takes one "
                        + "config file per line; naming a mixin class here is an error that says "
                        + "so in a way nobody reads, and the block is the single line this mod "
                        + "must never edit by hand");
    }

    /**
     * The boot-time seam check, and why it cannot be the na&iuml;ve version of itself.
     *
     * <p>Merely loading {@code FoodData} and logging that all is well would be <b>true in exactly
     * the case it exists to catch</b>: if the mixin config never registered, the class loads
     * perfectly, applies nothing, and the line says the rework is in place while vanilla food runs
     * the whole time. Three assertions, because there are three links and dropping any one of them
     * turns the check into decoration.</p>
     */
    @Test
    @DisplayName("the boot check reads a marker the mixin actually declares, and it is wired")
    void theBootCheckIsWiredAndCannotLie() {
        String mixin = read(MIXIN_DIR.resolve("FoodDataMixin.java"));
        assertTrue(mixin.contains("implements FoodDataSeam"),
                "**the mixin has to declare the marker.** Without it the check can only ask "
                        + "whether the class loaded, which is a question that answers 'yes' in the "
                        + "one scenario that produces no symptom -- a config that never got "
                        + "registered and therefore applied nothing");

        String entry = read(ROOT.resolve("java").resolve("com").resolve("cortes").resolve("combat")
                .resolve("platform").resolve("ModPlatform.java"));
        assertTrue(entry.contains("FoodDataSeam.class.isAssignableFrom("),
                "**and it has to ask that marker**, not just load the class and announce a result");
        assertTrue(entry.contains("ModPlatform::reportSurvivalSeam"),
                "**and the report has to be registered**, or the check runs never: the whole "
                        + "point is that `FoodData` is not loaded until the first player joins, "
                        + "so an unregistered check is a seam nobody ever looks at");
    }

    @Test
    @DisplayName("the balance numbers are registered as a SERVER config")
    void theConfigIsRegistered() {
        String entry = read(ROOT.resolve("java").resolve("com").resolve("cortes").resolve("combat")
                .resolve("platform").resolve("ModPlatform.java"));

        assertTrue(entry.contains("modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);"),
                "**without this line `ServerConfig.SPEC.isLoaded()` is always false**, so every "
                        + "reader falls back to the defaults and the config file is written but "
                        + "never consulted -- a settings screen that changes nothing, which is the "
                        + "worst version of a silent bug");
    }

    /**
     * The two defaults are written in two files, and one of them is the number the game actually
     * runs.
     *
     * <p>{@code SurvivalTuning.DEFAULTS} is what runs before a config has loaded;
     * {@code ServerConfig}'s {@code defineInRange} is what the config file is written with and what
     * every world that has a {@code cortescombat-server.toml} already has on disk. They are two
     * copies of the same number, which is the exact shape this project keeps paying for -- so when
     * the General halved the healing interval and doubled the food clock on 2026-10-12, moving one
     * and not the other would have produced a game whose pre-config boot and whose config file
     * disagreed, and no test anywhere would have said so.</p>
     *
     * <p>Parsed out of the source rather than loaded, because {@code ServerConfig} is NeoForge and
     * cannot be touched by a bare JVM test. That also makes it survive a rename or a reformat, as
     * long as the call still says what it does.</p>
     */
    @Test
    @DisplayName("the config defaults and SurvivalTuning.DEFAULTS are the same numbers")
    void theConfigDefaultsAreTheOnlyTruth() {
        String config = read(ROOT.resolve("java").resolve("com").resolve("cortes").resolve("combat")
                .resolve("config").resolve("ServerConfig.java"));

        assertEquals(SurvivalTuning.DEFAULTS.healInterval(),
                defaultFrom(config, "healInterval"),
                "**healing interval is two different numbers.** One runs before the config loads, "
                        + "the other is what every existing world already has on disk; a mismatch "
                        + "means the game changes depending on which of the two you happen to be "
                        + "reading, and nothing reports it");
        assertEquals(SurvivalTuning.DEFAULTS.foodCycleInterval(),
                defaultFrom(config, "foodRegenInterval"),
                "**and so is the food clock.** Same argument, and this is the one the design calls "
                        + "the most important number in the project");
    }

    // --- helpers ----------------------------------------------------------------

    private static int defaultFrom(String source, String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("defineInRange\\(\"" + name + "\", (\\d+),").matcher(source);
        assertTrue(matcher.find(),
                "`defineInRange(\"" + name + "\", <default>, ...)` has to be in ServerConfig, or "
                        + "this guard is now asserting on nothing");
        return Integer.parseInt(matcher.group(1));
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "the method `" + signature + "` has to exist for this to be about "
                + "anything -- if it was renamed, this guard is now asserting on nothing");
        int stop = source.indexOf("\n    }", start);
        return source.substring(start, stop > start ? stop : source.length());
    }

    /**
     * Reads a file, dropping comments first when it is Java.
     *
     * <p><b>This exists because a guard that reads prose is a guard that passes for the wrong
     * reason.</b> The first version of {@code theSeamDoesNotPatchConstants} failed on its own
     * javadoc: the class documents, at length, that it deliberately does <em>not</em> do what
     * {@code refs/combatify} does, names {@code @ModifyExpressionValue} to say so, and the
     * assertion then found the word in the comment and concluded the code had it. A test that
     * cannot tell code from an explanation of code is worse than no test, because it is green
     * until the day it matters.</p>
     *
     * <p>Stripping is deliberately naive -- it does not understand string literals. That is safe
     * here and checked rather than assumed: no file under {@code src/main/java} in this project
     * puts a {@code //} inside a string literal (searched, not remembered), so a line comment
     * cannot be mistaken for code and code cannot be mistaken for one.</p>
     */
    private static String read(Path file) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
        if (!file.toString().endsWith(".java")) {
            return source;
        }
        String noBlocks = source.replaceAll("(?s)/\\*.*?\\*/", "");
        return noBlocks.replaceAll("//[^\n]*", "");
    }
}
