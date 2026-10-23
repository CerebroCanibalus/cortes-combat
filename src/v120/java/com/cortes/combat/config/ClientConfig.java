package com.cortes.combat.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The 1.20.1 body of the client config. Same keys, defaults and comments as the shared twin;
 * only the spec type changes ({@code ForgeConfigSpec}).
 */
public final class ClientConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec SPEC;

    /**
     * The FOV the game starts at. Vanilla ships 70, which reads as looking down a drainpipe.
     *
     * <p>This is a <em>starting</em> value, not an override: it is applied only while the option
     * is still sitting at the vanilla default, so a player who later picks their own number
     * keeps it forever.</p>
     */
    public static final ForgeConfigSpec.IntValue STARTING_FOV;

    /**
     * Whether anything in the mod may move the camera.
     *
     * <p>A camera that rocks on its own is the classic motion-sickness trigger, and this project
     * treats every screen effect as something a player can switch off.</p>
     *
     * <p><b>One key for both shakes, and that is the point.</b> It used to be
     * {@code reactorShake} and only the nether star read it, which meant the orb's blast would
     * have gone on moving the camera for a player who had explicitly asked it not to.</p>
     */
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;

    /** Master toggle for the viewmodel animation (motion sickness). */
    public static final ForgeConfigSpec.BooleanValue VIEW_MODEL_MOTION;

    /** The red damage flash (vignette is separate and always on). */
    public static final ForgeConfigSpec.BooleanValue DAMAGE_FLASH;

    /** Debug flag, exposed as config so it can be flipped without recompiling. */
    public static final ForgeConfigSpec.BooleanValue DEBUG;

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

        SPEC = BUILDER.build();
    }

    /** Vanilla's own default. Checked so a deliberate choice is never stomped. */
    public static final int VANILLA_FOV = 70;

    private ClientConfig() {
    }
}
