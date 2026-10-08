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
     * Whether the nether star's overload may move the camera.
     *
     * <p>A camera that rocks on its own is the classic motion-sickness trigger, and this project
     * treats every screen effect as something a player can switch off. The shake is small
     * ({@code ReactorOverload.QUAKE_MAX_DEGREES}), so on is the right default, and off costs the
     * player nothing but the rumble -- the particles, the sound and the countdown all stay.</p>
     */
    public static final ModConfigSpec.BooleanValue REACTOR_SHAKE;

    static {
        STARTING_FOV = BUILDER
                .comment("Field of view the client starts at, on the first launch that still has",
                        "the vanilla 70. If you set the FOV later in the video settings, this mod",
                        "never touches it again.")
                .defineInRange("startingFov", 90, 30, 110);

        REACTOR_SHAKE = BUILDER
                .comment("Whether a nether star overload may rock the camera. Turn it off for",
                        "motion sickness; the sound and the particles are unaffected.")
                .define("reactorShake", true);

        SPEC = BUILDER.build();
    }

    /** Vanilla's own default. Checked so a deliberate choice is never stomped. */
    public static final int VANILLA_FOV = 70;

    private ClientConfig() {
    }
}
