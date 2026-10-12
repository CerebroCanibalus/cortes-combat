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

    static {
        STARTING_FOV = BUILDER
                .comment("Field of view the client starts at, on the first launch that still has",
                        "the vanilla 70. If you set the FOV later in the video settings, this mod",
                        "never touches it again.")
                .defineInRange("startingFov", 90, 30, 110);

        CAMERA_SHAKE = BUILDER
                .comment("Whether explosions may rock the camera (the nether star's overload and",
                        "the pala's antimatter orb). Turn it off for motion sickness; the sound",
                        "and the particles are unaffected.")
                .define("cameraShake", true);

        SPEC = BUILDER.build();
    }

    /** Vanilla's own default. Checked so a deliberate choice is never stomped. */
    public static final int VANILLA_FOV = 70;

    private ClientConfig() {
    }
}
