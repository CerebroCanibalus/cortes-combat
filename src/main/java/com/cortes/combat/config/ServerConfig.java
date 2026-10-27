package com.cortes.combat.config;

import com.cortes.combat.survival.SurvivalTuning;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's balance numbers for food and healing.
 *
 * <p>Server config and not client config, because these decide how much damage a player survives
 * -- a value that could differ between two sides of the same world is a bug, and NeoForge syncs
 * {@link ModConfig.Type#SERVER} to every client that joins. {@code ARCHITECTURE.md} section 12
 * already ruled that {@code ModConfigSpec} keeps the static balance numbers ({@code healInterval},
 * {@code foodRegenInterval}); this is that decision, made.</p>
 *
 * <h2>Why every reader asks {@link ModConfigSpec#isLoaded()} first</h2>
 *
 * <p>Because {@code ModConfigSpec.ConfigValue#get()} does not fall back to the default -- it
 * throws {@code IllegalStateException: Cannot get config value before config is loaded}. A server
 * config is attached to a world, so there is a real window at startup where nothing has loaded it,
 * and the drink-duration reader in particular is reachable from item code rather than from a tick.
 * The check costs one field read and removes a crash that would be reported as a bug in the mod
 * rather than as a config being read too early.</p>
 */
public final class ServerConfig {

    /**
     * Ticks to drink. Vanilla drinks for thirty-two; {@code DESIGN.md} section 5 sets liquids at
     * twenty so a potion or a stew is a decision a player can make mid-fight.
     *
     * <p>Its own constant rather than a fifth field of {@link SurvivalTuning}: it is item timing,
     * not one of the two clocks, and {@code SurvivalRules} never reads it.</p>
     */
    private static final int DEFAULT_DRINK_TICKS = 20;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec SPEC;

    /** Ticks between one point of natural healing. Vanilla's is eighty. */
    public static final ModConfigSpec.IntValue HEAL_INTERVAL;

    /** Food spent per point healed. This is what makes healing a resource and not a timer. */
    public static final ModConfigSpec.IntValue HEAL_FOOD_COST;

    /** The food floor: below this, healing does not start. Vanilla's is eighteen. */
    public static final ModConfigSpec.IntValue MIN_FOOD_TO_HEAL;

    /**
     * Ticks per turn of the food clock, which both returns a food point and paces starvation.
     *
     * <p>{@code DESIGN.md} calls this the most important number in the project: fast enough and
     * food is decoration, slow enough and fighting is a dead end. It is here so that finding out
     * which one it is costs editing a file, not shipping a build.</p>
     */
    public static final ModConfigSpec.IntValue FOOD_REGEN_INTERVAL;

    /** Ticks to drink any liquid. */
    public static final ModConfigSpec.IntValue DRINK_TICKS;

    /** Master toggle for the survival rework (food, healing, exhaustion). */
    public static final ModConfigSpec.BooleanValue SURVIVAL_REWORK;

    /** Master toggle for the mod's particles (spawned server-side). */
    public static final ModConfigSpec.BooleanValue PARTICLES;

    /** Master toggle for the whole combat rework. Off reverts to vanilla combat. */
    public static final ModConfigSpec.BooleanValue COMBAT_REWORK;

    /**
     * Las trazas de gameplay (`[guardia]`, `[curar]`, `[pala]`, `[carga]`, `[frenzy]`).
     *
     * <p><b>Apagadas por defecto, y la auditoria de rendimiento es la razon.</b> Eran
     * instrumentacion de playtest encendida siempre: una linea de log por golpe bloqueado, por
     * curacion y por orbe -- trabajo de I/O en el hilo del server en el camino del combate, y un
     * `latest.log` de megabytes por sesion de pelea. Una traza sin interruptor no es diagnostico,
     * es deuda. Se prenden para depurar y se apagan para jugar.</p>
     */
    public static final ModConfigSpec.BooleanValue DEBUG;

    static {
        HEAL_INTERVAL = BUILDER
                .comment("Ticks between one point of natural healing. Vanilla uses 80 (4 seconds);",
                        "this mod uses 40 (2 seconds). Lower is faster healing.")
                .defineInRange("healInterval", 40, 1, 400);

        HEAL_FOOD_COST = BUILDER
                .comment("Food points spent for every point of health healed. This is the price",
                        "that closes the cycle: healing spends food and food returns on its own.",
                        "Cannot be zero, or healing stops costing anything and food becomes decor.")
                .defineInRange("healFoodCost", 1, 1, 20);

        MIN_FOOD_TO_HEAL = BUILDER
                .comment("The food floor. At or above this many food points natural healing runs;",
                        "below it, nothing heals. Vanilla requires 18; this mod proposes 6.")
                .defineInRange("minFoodToHeal", 6, 0, 20);

        FOOD_REGEN_INTERVAL = BUILDER
                .comment("Ticks for the food clock to return one food point. It also paces",
                        "starvation. Vanilla has no food regeneration at all and starves every",
                        "80 ticks. This mod uses 80 (4 seconds). The most important number to",
                        "tune in the mod: fast makes food decoration, slow makes fighting a",
                        "dead end.")
                .defineInRange("foodRegenInterval", 80, 1, 1200);

        DRINK_TICKS = BUILDER
                .comment("Ticks to drink a liquid: stews, honey, milk and potions. Vanilla drinks",
                        "for 32 ticks; this mod proposes 20 so drinking is usable mid-fight.")
                .defineInRange("drinkTicks", DEFAULT_DRINK_TICKS, 1, 64);

        SURVIVAL_REWORK = BUILDER
                .comment("Master toggle for the survival rework: exhaustion removed, food regenerates,",
                        "healing spends food. Off reverts to vanilla hunger and exhaustion.")
                .define("survivalRework", true);

        PARTICLES = BUILDER
                .comment("Master toggle for the mod's particles: hit indicators, blood drops, the flies",
                        "around the mob cloud and the slash trails. Sound and the rest of the feedback",
                        "are unaffected.")
                .define("particles", true);

        COMBAT_REWORK = BUILDER
                .comment("Master toggle for the whole combat rework: cone reach, charge, guard,",
                        "specials, hard cooldown. Off reverts to vanilla combat entirely. The FX",
                        "toggles (particles, damage flash, viewmodel, camera shake) stay independent.")
                .define("combatRework", true);

        DEBUG = BUILDER
                .comment("Gameplay trace lines: [guardia] per blocked hit, [curar] per heal,",
                        "[pala] per orb, [carga] per cancelled charge, [frenzy] per stack milestone.",
                        "Off by default; these were playtest instrumentation that logged on the",
                        "combat path every time it ran. Turn on to diagnose, off to play.")
                .define("debug", false);

        SPEC = BUILDER.build();
    }

    /**
     * The four survival numbers, read from config and ready to hand to
     * {@link com.cortes.combat.survival.SurvivalRules}.
     *
     * <p>A fresh record every call, which is one small allocation per player per tick. That is
     * deliberate and it is cheap: {@code CombatTicker} already writes several state records every
     * tick, and caching this one would need a config-reload hook to stay correct, which is a second
     * moving part bought to avoid sixteen bytes.</p>
     *
     * <p>Before a world exists this returns {@link SurvivalTuning#DEFAULTS} instead of throwing.</p>
     */
    public static SurvivalTuning tuning() {
        if (!SPEC.isLoaded()) {
            return SurvivalTuning.DEFAULTS;
        }
        return new SurvivalTuning(HEAL_INTERVAL.get(), HEAL_FOOD_COST.get(),
                MIN_FOOD_TO_HEAL.get(), FOOD_REGEN_INTERVAL.get());
    }

    /** Ticks to drink, or the default when no world has loaded the config yet. */
    public static int drinkTicks() {
        return SPEC.isLoaded() ? DRINK_TICKS.get() : DEFAULT_DRINK_TICKS;
    }

    /** Whether the survival rework is on. Defaults to true before a world loads. */
    public static boolean survivalRework() {
        return !SPEC.isLoaded() || SURVIVAL_REWORK.get();
    }

    /** Whether the mod's particles are on. Defaults to true before a world loads. */
    public static boolean particles() {
        return !SPEC.isLoaded() || PARTICLES.get();
    }

    /** Whether the combat rework is on. Defaults to true before a world loads. */
    public static boolean combatRework() {
        return !SPEC.isLoaded() || COMBAT_REWORK.get();
    }

    /**
     * Whether the gameplay traces are on. <b>Off before the config loads</b>, which is the safe
     * answer: a trace that prints during the loading window is a trace nobody asked for.
     */
    public static boolean debug() {
        return SPEC.isLoaded() && DEBUG.get();
    }

    private ServerConfig() {
    }
}
