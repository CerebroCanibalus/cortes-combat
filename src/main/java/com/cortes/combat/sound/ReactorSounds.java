package com.cortes.combat.sound;

/**
 * The nether reactor's sounds, as names and nothing else.
 *
 * <p><b>Phase one ships the recordings and no gameplay that plays them.</b> The six files are the
 * designer's and they belong to the spectacle phase -- charging up, running, overheating on
 * firestone and on the nether star, switching off, and the blast. Naming them here and in
 * {@code sounds.json} is the seam: the events exist and resolve, so phase two is a call to
 * {@link CombatSounds#event(String)} and not a change to the data format.</p>
 *
 * <p><b>Why name them at all, and not leave the files staged.</b> {@code SoundPoolTest} refuses a
 * recording no event names -- a file that is stitched into the jar and never plays is dead weight,
 * and the check that catches it is the only one in the project that notices. Naming the six is what
 * makes them loadable, and the constants below are what stops phase two from spelling an id that
 * does not match the file.</p>
 */
public final class ReactorSounds {

    /** The reactor switching on. */
    public static final String UP = "cortescombat:reactor_up";

    /** The reactor switching off. */
    public static final String OFF = "cortescombat:reactor_off";

    /** The running loop while the reactor is active. */
    public static final String LOOP_ON = "cortescombat:reactor_loop_on";

    /** The overload warning while firestones push the charge past the normal ceiling. */
    public static final String OVERHEAT_FIRESTONE = "cortescombat:reactor_overheat_firestone";

    /** The overload warning once a nether star is in the slot. */
    public static final String OVERHEAT_NETHERSTAR = "cortescombat:reactor_overheat_netherstar";

    /** The blast. */
    public static final String NUCLEAR_EXPLOSION = "cortescombat:nuclear_explosion";

    private ReactorSounds() {
    }
}
