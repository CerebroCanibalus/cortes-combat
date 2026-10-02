package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The arcabuz: whether a firestone is in it, and how much fuse is left.
 *
 * <p>Pure, and a record with a Codec, for the reason this project has one rule about mutable
 * gameplay state. The two failures this exact shape exists to prevent, both of which this
 * codebase has already paid for:</p>
 *
 * <ul>
 *   <li><b>A static</b> would be one fuse for the whole server. With two players in a world the
 *       second one to press would overwrite the first's countdown, and the first would either
 *       fire on the second's fuse or never fire.</li>
 *   <li><b>A field on a listener</b> does not survive a respawn and does not reset on login.
 *       {@code loaded} is the closest thing this mod has to a button the player holds, and a
 *       button that survives something the player did not do is the bug the guard's held flag
 *       already was: coming back from the death screen with a loaded musket nobody loaded.</li>
 * </ul>
 *
 * <h2>What a second press during the fuse does</h2>
 *
 * <p><b>Nothing, and it does not restart the countdown.</b> This is the decision the whole class
 * is shaped around, and it is the same one the parry made: a window that a second press could
 * refresh is not a fuse, it is a held button, and a held button is free to keep up. Spamming the
 * right button would hold the arcabuz at nineteen ticks forever and the player would never get a
 * shot, which is worse than the opposite failure because it produces no sound, no number and no
 * message.</p>
 *
 * <p>The rule lives in {@link #canLight()} rather than at the call site, because the call site is
 * a packet handler and a packet handler that decides is a packet handler that can be wrong twice
 * in two different ways.</p>
 *
 * @param fuseTicks ticks of fuse left, zero when nothing is burning
 * @param loaded whether a firestone is in the chamber
 */
public record ArcabuzState(int fuseTicks, boolean loaded) {

    /**
     * Twenty ticks: one whole second, per the design.
     *
     * <p><b>The number is also the texture's.</b> {@code arcabuz.png} is a 64x320 strip of five
     * 64x64 frames -- one unlit and four burning -- and its {@code .mcmeta} spends five ticks a
     * frame, so the four lit frames arrive across exactly twenty. The shot waits this long and
     * the fuse the player watches is this long, which is the only reason the two numbers can be
     * trusted to stay in step. {@code ModelDeclarationTest} reads the mcmeta and compares.</p>
     */
    public static final int FUSE_TICKS = 20;

    /** Nothing burning and nothing loaded. A gun picked up off the ground. */
    public static final ArcabuzState EMPTY = new ArcabuzState(0, false);

    public static final Codec<ArcabuzState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("fuse_ticks").forGetter(ArcabuzState::fuseTicks),
            Codec.BOOL.fieldOf("loaded").forGetter(ArcabuzState::loaded)
    ).apply(instance, ArcabuzState::new));

    /** Whether a fuse is actually burning right now. */
    public boolean isBurning() {
        return fuseTicks > 0;
    }

    /**
     * Whether a press right now would light the fuse.
     *
     * <p><b>Both halves, and the second one is the rule.</b> A gun with a firestone in it and a
     * fuse going is a gun that is already going to fire; a second press must not extend it, and
     * must certainly not reset it to twenty, or holding the button would be a machine that
     * delays itself forever.</p>
     */
    public boolean canLight() {
        return loaded && !isBurning();
    }

    /**
     * Whether a press right now would load the gun.
     *
     * <p>Refused while the fuse is burning for the same reason {@link #canLight()} is: an arcabuz
     * with a live fuse and a fresh charge would be a gun that fires twice off one firestone.</p>
     */
    public boolean canReload() {
        return !loaded && !isBurning();
    }

    /**
     * The fuse lit, and the one firestone gone.
     *
     * <p><b>Spent at the light and not at the shot.</b> That is the rule the state exists to
     * express: the press is what costs the firestone, so a gun that is already going has nothing
     * left to go and cannot be topped up behind the player's back while the fuse burns. It is
     * also why {@link #loaded} is false in here even though the shot has not happened yet --
     * "loaded" answers "can this press start a fuse", and after this press it cannot.</p>
     */
    public ArcabuzState light() {
        return new ArcabuzState(FUSE_TICKS, false);
    }

    /** The firestone in, at any point in the press. */
    public ArcabuzState reload() {
        return new ArcabuzState(fuseTicks, true);
    }

    /**
     * One tick of the fuse, whether the shot goes off this tick, and whether the client has to
     * hear about it.
     *
     * <p>Copied from {@link ParryState#step()} shape for shape, and for the same reason it is
     * there. The caller used to be the place that decided, and a caller that decides can decide
     * "the window is closed, nothing to report" -- which is true of the <em>packet</em> and
     * silently also taken as true of the countdown. The fix is the same: the question lives with
     * the data and the caller only writes what it is handed.</p>
     */
    public Step step() {
        if (!isBurning()) {
            // The same instance rather than an equal new one: the caller writes this to an
            // attachment, and a record that compares equal is the difference between a quiet tick
            // and a write.
            return Step.of(this, false, false);
        }
        int left = fuseTicks - 1;
        boolean fires = left <= 0;
        return Step.of(new ArcabuzState(Math.max(0, left), loaded), fires, fires);
    }

    /**
     * The tick's outcome: the state to store, whether to shoot, whether to tell the client.
     *
     * @param state what goes into the attachment
     * @param fireNow whether the shot resolves on this tick
     * @param tellClient whether the fuse's visible state changed this tick
     */
    public record Step(ArcabuzState state, boolean fireNow, boolean tellClient) {

        /** What a caller writes to the attachment, and what it sends to the player. */
        public static Step of(ArcabuzState state, boolean fireNow, boolean tellClient) {
            return new Step(state, fireNow, tellClient);
        }
    }

    /** Whether this is exactly the state a gun picked up off the ground is in. */
    public boolean isEmpty() {
        return this.equals(EMPTY);
    }
}
