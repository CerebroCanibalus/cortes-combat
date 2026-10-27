package com.cortes.combat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only settings.
 *
 * <p>Balance numbers that must be identical on both sides are not here: those live in data
 * components, so a datapack can change them and both the client and the server agree. What is
 * here is only what genuinely belongs to one player's screen.</p>
 */
public final class ClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec SPEC;

    /**
     * The FOV the game starts at. Vanilla ships 70, which reads as looking down a drainpipe.
     *
     * <p>This is a <em>starting</em> value, not an override: it is applied only while the option
     * is still sitting at the vanilla default, so a player who later picks their own number
     * keeps it forever.</p>
     */
    public static final ModConfigSpec.IntValue STARTING_FOV;

    /**
     * Whether anything in the mod may move the camera.
     *
     * <p>A camera that rocks on its own is the classic motion-sickness trigger, and this project
     * treats every screen effect as something a player can switch off.</p>
     *
     * <p><b>One key for both shakes, and that is the point.</b> It used to be
     * {@code reactorShake} and only the nether star read it, which meant the orb's blast would
     * have gone on moving the camera for a player who had explicitly asked it not to. The
     * complaint this key answers is "the camera moves on its own" -- it does not know which
     * explosion moved it, and neither should the setting. Off costs the rumble and nothing else:
     * the particles, the sound and the countdown all stay.</p>
     */
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;

    /** Master toggle for the viewmodel animation (motion sickness). */
    public static final ModConfigSpec.BooleanValue VIEW_MODEL_MOTION;

    /** The red damage flash (vignette is separate and always on). */
    public static final ModConfigSpec.BooleanValue DAMAGE_FLASH;

    /** Debug flag, exposed as config so it can be flipped without recompiling. */
    public static final ModConfigSpec.BooleanValue DEBUG;

    /** Multiplier on every camera-shake impulse: 0 turns them off, 2 doubles them. */
    public static final ModConfigSpec.DoubleValue SHAKE_INTENSITY;

    /** Whether the combat crosshair replaces the vanilla one (vanilla is drawn as fallback). */
    public static final ModConfigSpec.BooleanValue HUD_CROSSHAIR;

    /** The thin cooldown bracket line around the crosshair. */
    public static final ModConfigSpec.BooleanValue HUD_COOLDOWN_BRACKETS;

    /** The guard-hit orbs above the crosshair. */
    public static final ModConfigSpec.BooleanValue HUD_GUARD_ORBS;

    /** The charge bar under the crosshair (both slots: the hit charge and the arcane one). */
    public static final ModConfigSpec.BooleanValue HUD_CHARGE_BAR;

    /** Size multiplier on the crosshair art. 1.0 is the drawn size. */
    public static final ModConfigSpec.DoubleValue HUD_CROSSHAIR_SIZE;

    /** Resting tint, as {@code #RRGGBB}. White is the art's own colour. */
    public static final ModConfigSpec.ConfigValue<String> HUD_CROSSHAIR_COLOR;

    /** Tint while a target is in reach. White keeps the shape the only difference. */
    public static final ModConfigSpec.ConfigValue<String> HUD_CROSSHAIR_AIM_COLOR;

    /** Tint of the cooldown brackets while nothing is flashing. */
    public static final ModConfigSpec.ConfigValue<String> HUD_BRACKET_COLOR;

    /** Tint of the hit-charge bar. */
    public static final ModConfigSpec.ConfigValue<String> HUD_CHARGE_BAR_COLOR;

    /** Tint of the arcane (special) charge bar. */
    public static final ModConfigSpec.ConfigValue<String> HUD_ARCANE_BAR_COLOR;

    /** Whether a landed hit may shake the crosshair. Camera shake is {@link #CAMERA_SHAKE}. */
    public static final ModConfigSpec.BooleanValue HUD_CROSSHAIR_SHAKE;

    /** Whether the bars fade out after a stretch of not fighting. */
    public static final ModConfigSpec.BooleanValue HUD_HIDE_WHEN_IDLE;

    /** How long without hitting or having a target before the bars hide, in ticks. */
    public static final ModConfigSpec.IntValue HUD_HIDE_IDLE_TICKS;

    /** Whether the idle fade also takes the crosshair itself. */
    public static final ModConfigSpec.BooleanValue HUD_HIDE_CROSSHAIR_WHEN_IDLE;

    static {
        STARTING_FOV = BUILDER
                .comment("Field of view the client starts at, on the first launch that still has",
                        "the vanilla 70. If you set the FOV later in the video settings, this mod",
                        "never touches it again.")
                .defineInRange("startingFov", 90, 30, 110);

        CAMERA_SHAKE = BUILDER
                .comment("Whether the camera may be rocked: a kill, the arcabuz firing, an arrow",
                        "passing close, the nether star's overload and the pala's antimatter orb.",
                        "Turn it off for motion sickness; the sound and the particles are unaffected,",
                        "and so is the crosshair's own feedback.")
                .define("cameraShake", true);

        VIEW_MODEL_MOTION = BUILDER
                .comment("Whether the weapon's viewmodel animates (wind-up, strike, guard, charge).",
                        "Turn it off for motion sickness; the item renders in its vanilla pose and",
                        "the charge FOV effect still works.")
                .define("viewModelMotion", true);

        DAMAGE_FLASH = BUILDER
                .comment("The red flash on the screen when you take damage. The low-health vignette",
                        "is unaffected.")
                .define("damageFlash", true);

        DEBUG = BUILDER
                .comment("Enables the mod's debug logging. Off by default.")
                .define("debug", false);

        // **Secciones FX y HUD: el resto de lo que se ve y se oye, en un solo lugar.** Las cuatro
        // llaves de arriba no se MUEVEN a secciones: ya hay config guardada con ellas en la raiz,
        // y moverlas silencia las preferencias de quien ya las habia cambiado. Lo nuevo nace con
        // seccion y la razon del multiplicador y de los tres toggles esta en cada uno.
        BUILDER.push("fx");
        SHAKE_INTENSITY = BUILDER
                .comment("How strong the camera shakes are, as a multiplier on every impulse:",
                        "0.0 is off, 1.0 is the tuned default, 2.0 is double. Applies to every",
                        "shake source at once -- the kill, the arcabuz, the arrow, the reactor --",
                        "so one number answers 'todo sacude mucho o poco' without eight dials.")
                .defineInRange("shakeIntensity", 1.0, 0.0, 2.0);
        BUILDER.pop();

        BUILDER.push("hud");
        HUD_CROSSHAIR = BUILDER
                .comment("Replace the vanilla crosshair with the combat one (grows on hit, shows",
                        "aim state). With this off the vanilla crosshair is drawn instead.")
                .define("combatCrosshair", true);
        HUD_COOLDOWN_BRACKETS = BUILDER
                .comment("The thin bracket line around the crosshair that shows the attack")
                .define("cooldownBrackets", true);
        HUD_GUARD_ORBS = BUILDER
                .comment("The small orbs above the crosshair that show guard hits remaining")
                .define("guardOrbs", true);
        HUD_CHARGE_BAR = BUILDER
                .comment("The charge bar under the crosshair: the hit charge and the arcane one.",
                        "Off leaves the crosshair and the brackets, and nothing else tells you",
                        "how far the charge got.")
                .define("chargeBar", true);
        HUD_CROSSHAIR_SIZE = BUILDER
                .comment("How big the crosshair is drawn, as a multiplier on the art's own size.",
                        "0.5 is half, 1.0 is the drawn size, 3.0 is triple. The hit and aim growth",
                        "still multiply on top of this, so the reaction never disappears.")
                .defineInRange("crosshairSize", 1.0, 0.5, 3.0);
        HUD_CROSSHAIR_COLOR = BUILDER
                .comment("Crosshair colour while nothing is happening, as #RRGGBB (or RRGGBB).",
                        "#FFFFFF leaves the art's own colour; any value multiplies it, so a dark",
                        "colour on light art reads as a dim tint. Invalid text falls back to white.")
                .define("crosshairColor", "#FFFFFF");
        HUD_CROSSHAIR_AIM_COLOR = BUILDER
                .comment("Crosshair colour while a target is in reach, as #RRGGBB.",
                        "The aim state also changes the sprite; this is the extra half for",
                        "players who want the state to read at a glance.")
                .define("crosshairAimColor", "#FFFFFF");
        HUD_BRACKET_COLOR = BUILDER
                .comment("Cooldown bracket colour while nothing is flashing, as #RRGGBB.",
                        "A landed hit still overrides it with the hit colour: the flash is feedback,",
                        "this is the resting state.")
                .define("bracketColor", "#FFFFFF");
        HUD_CHARGE_BAR_COLOR = BUILDER
                .comment("Hit-charge bar colour, as #RRGGBB. #FFFFFF keeps the art as drawn.")
                .define("chargeBarColor", "#FFFFFF");
        HUD_ARCANE_BAR_COLOR = BUILDER
                .comment("Arcane (special) bar colour, as #RRGGBB. The default is the violet the",
                        "bar has always been.")
                .define("arcaneBarColor", "#C06BFF");
        HUD_CROSSHAIR_SHAKE = BUILDER
                .comment("Whether a landed hit may shake the crosshair a couple of pixels.",
                        "Off removes the jitter and keeps the growth and the colour flash.",
                        "The camera has its own switch: cameraShake.")
                .define("crosshairShake", true);
        HUD_HIDE_WHEN_IDLE = BUILDER
                .comment("Hide the bars (brackets, charge bar and guard orbs) after a stretch of",
                        "not hitting anything and not having a target. Off keeps them on screen",
                        "always; the delay is hideIdleTicks.")
                .define("hideWhenIdle", true);
        HUD_HIDE_IDLE_TICKS = BUILDER
                .comment("How long the bars stay up without activity, in ticks (20 = one second).",
                        "The default is 160, which is the eight seconds the design asked for.")
                .defineInRange("hideIdleTicks", 160, 20, 2400);
        HUD_HIDE_CROSSHAIR_WHEN_IDLE = BUILDER
                .comment("Whether the idle fade takes the crosshair too. Off by default: the",
                        "crosshair is what the player aims with, and a pointer that vanishes is",
                        "not a cleaner HUD, it is a missing pointer.")
                .define("hideCrosshairWhenIdle", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /** Vanilla's own default. Checked so a deliberate choice is never stomped. */
    public static final int VANILLA_FOV = 70;

    /** The shake multiplier as a plain float, asked at the frequency the shakes ask it. */
    public static float shakeIntensity() {
        return SHAKE_INTENSITY.get().floatValue();
    }

    /** The crosshair size multiplier; the spec already clamps it to 0.5..3.0. */
    public static float crosshairScale() {
        return HUD_CROSSHAIR_SIZE.get().floatValue();
    }

    /** Ticks without activity before the bars hide. */
    public static int hideIdleTicks() {
        return HUD_HIDE_IDLE_TICKS.get();
    }

    private ClientConfig() {
    }
}
