package com.cortes.combat.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cortes.combat.core.weapon.WeaponProfile;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guard ramp, and the reason it is a ramp.
 *
 * <p>Nothing here was broken and then fixed; this is a channel that did not exist, and the
 * properties below are the ones that decide whether the stance reads as a motion or as a
 * toggle.</p>
 */
class GuardRampTest {

    /** The item is ready: no cooldown running, which is {@code 0}, not {@code 1}. */
    private static final float READY = 0.0F;

    private static ViewModelMotion held(boolean guarding, int ticks) {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < ticks; i++) {
            motion.tick(false, 0.0F, 0.0F, guarding, READY, false, false);
        }
        return motion;
    }

    /**
     * The value this tick produced, as opposed to the last frame's.
     *
     * <p>{@code snapshot(0)} is the value the previous tick ended on, which is the interpolation
     * convention and the right one: at the start of a tick the player is seeing the frame that
     * just finished. A test that means "what does this tick say" asks for {@code snapshot(1)}.</p>
     */
    private static float now(ViewModelMotion motion) {
        return motion.snapshot(1.0F, 0.0F).guard();
    }

    @Test
    @DisplayName("the guard is down at rest, like every other channel")
    void itRestsAtZero() {
        ViewModelMotion motion = new ViewModelMotion();
        assertEquals(0.0F, motion.snapshot(0.0F, 0.0F).guard(), 0.0F,
                "a channel that rests at anything else needs a special case in every track that "
                        + "reads it, which is the thing this design exists to avoid");
        assertTrue(ViewModelDefaults.of(WeaponProfile.SWORD)
                .evaluate(motion.snapshot(0.0F, 0.0F)).isZero(),
                "and at rest a weapon with a guard track contributes nothing at all");
    }

    @Test
    @DisplayName("the stance comes up over several ticks and does not jump")
    void raisingIsARampNotAFlag() {
        ViewModelMotion motion = new ViewModelMotion();
        motion.tick(false, 0.0F, 0.0F, true, READY, false, false);
        // The press frame is a discontinuity and deliberately does not interpolate: there is no
        // ramp to draw across it, and smearing one there would leave the weapon a third of a
        // stance behind on the exact frame the player pressed.
        assertEquals(now(motion), motion.snapshot(0.0F, 0.0F).guard(), 1.0E-6F,
                "the press frame is a step, not a ramp");

        // The tick after it is a ramp, and that is where a twenty-hertz value would show.
        motion.tick(false, 0.0F, 0.0F, true, READY, false, false);
        Set<Float> oneTick = new HashSet<>();
        for (int sub = 0; sub < 4; sub++) {
            oneTick.add(motion.snapshot(sub / 4.0F, 0.0F).guard());
        }
        assertTrue(oneTick.size() > 1,
                "one tick of the guard has to give more than one value, or the stance advances in "
                        + "visible steps and reads as a stutter rather than as a motion");
    }

    @Test
    @DisplayName("GUARD_RISE_TICKS is the number of ticks a raised guard actually takes")
    void theConstantMeansWhatItSays() {
        // Holding for exactly GUARD_RISE_TICKS has to reach the full stance, not nine elevenths
        // of it. It used not to, because the press frame counted as tick zero, and a constant
        // that does not mean what it says is a constant that gets re-tuned by accident.
        ViewModelMotion atLimit = held(true, ViewModelMotion.GUARD_RISE_TICKS);
        assertEquals(1.0F, now(atLimit), 1.0E-6F, "the stance has to be up when the constant "
                + "says it is up");

        ViewModelMotion justUnder = held(true, ViewModelMotion.GUARD_RISE_TICKS - 1);
        assertTrue(now(justUnder) < 1.0F, "and not before");
    }

    @Test
    @DisplayName("the rise is not slower than the round trip that raises the guard")
    void theRiseIsNotLatency() {
        // This is the playtest's "se siente poco responsivo" turned into a number.
        //
        // The guard goes up the instant the server reads the press, which is one packet. An
        // eleven tick ramp was not weight on top of that, it was eleven ticks of the animation
        // trailing a gameplay state that had already changed, and the gap between the two is
        // exactly what "unresponsive" feels like. It does not matter that the old constant had a
        // reason; the reason was about weight and the number was about latency.
        //
        // Four ticks is a round trip with room for a slow frame in it. Anything above that is the
        // animation inventing a delay the game does not have.
        assertTrue(ViewModelMotion.GUARD_RISE_TICKS <= 4,
                "the guard is up one packet after the press; a ramp longer than a round trip is "
                        + "the pose disagreeing with the gameplay, and it reads as lag. It is "
                        + ViewModelMotion.GUARD_RISE_TICKS);
    }

    @Test
    @DisplayName("dropping is the slower half, and it is still not the same speed")
    void theRampIsAsymmetric() {
        // The direction of the asymmetry is the opposite of what it used to be, and that is the
        // playtest's decision rather than an accident of tuning: the fall is where a little weight
        // reads as weight, because letting go is a decision the player is watching themselves make.
        // The rise is a reaction to a click, and weight there is latency.
        assertTrue(ViewModelMotion.GUARD_FALL_TICKS > ViewModelMotion.GUARD_RISE_TICKS,
                "settling is slower than snapping up, or the guard is a UI toggle");

        ViewModelMotion up = held(true, ViewModelMotion.GUARD_RISE_TICKS);
        assertEquals(1.0F, now(up), 1.0E-6F, "a held guard has to reach the full stance");

        // One tick into the fall, a two tick rise would already be halfway down. It is not,
        // because the fall takes six.
        ViewModelMotion dropping = new ViewModelMotion();
        for (int i = 0; i < ViewModelMotion.GUARD_RISE_TICKS; i++) {
            dropping.tick(false, 0.0F, 0.0F, true, READY, false, false);
        }
        dropping.tick(false, 0.0F, 0.0F, false, READY, false, false);
        float afterOneTickOfFalling = now(dropping);
        float ifSymmetric = now(held(true, 1));
        assertTrue(afterOneTickOfFalling > ifSymmetric,
                "a guard that comes up and falls at the same speed reads as a UI toggle. One tick "
                        + "into the fall it is at " + afterOneTickOfFalling + ", and a symmetric "
                        + "ramp would have put it at " + ifSymmetric);
    }

    @Test
    @DisplayName("dropping the guard starts from wherever it had got to")
    void droppingDoesNotJumpToTheFloorFirst() {
        ViewModelMotion motion = new ViewModelMotion();
        // The last tick before the stance is up. Written as the constant rather than as a
        // literal because the constant is now two: a hardcoded four would sail past the top of
        // the ramp and the assertion below would be about nothing.
        for (int i = 0; i < ViewModelMotion.GUARD_RISE_TICKS - 1; i++) {
            motion.tick(false, 0.0F, 0.0F, true, READY, false, false);
        }
        float partWayUp = now(motion);
        assertTrue(partWayUp > 0.0F && partWayUp < 1.0F, "sanity: the stance is still on its way up");

        motion.tick(false, 0.0F, 0.0F, false, READY, false, false);
        float onRelease = now(motion);

        assertTrue(onRelease > partWayUp - 1.0F / ViewModelMotion.GUARD_RISE_TICKS,
                "a drop that jumped to zero and then fell again would put the weapon down and "
                        + "bring it back up: two motions where the player asked for one. It went "
                        + "from " + partWayUp + " to " + onRelease + " in one frame");
    }

    @Test
    @DisplayName("the stance comes back down to nothing")
    void itFinishes() {
        ViewModelMotion motion = held(true, ViewModelMotion.GUARD_RISE_TICKS);
        for (int i = 0; i < ViewModelMotion.GUARD_FALL_TICKS; i++) {
            motion.tick(false, 0.0F, 0.0F, false, READY, false, false);
        }
        assertEquals(0.0F, now(motion), 0.0F,
                "the channel rests at zero, so the stance has to come all the way back");
    }

    @Test
    @DisplayName("the collapse is a short marker, and not the stagger's length")
    void theCollapseIsAMarkerAndNotTheStagger() {
        // This test used to assert the opposite, and it was the reason the animation was a piece
        // of crap. The collapse was timed by the item's own vanilla cooldown, on the argument that
        // the animation and the stun could then never drift apart -- which is true, and is the
        // wrong question. A sword's stagger is a hundred ticks. Five seconds of the weapon sliding
        // backwards is not a marker saying "your guard broke", it is a fifth animation the player
        // has to sit through.
        //
        // A collapse is a punch: something happens fast, it is over, and then the weapon is at
        // rest while the gameplay consequence plays out on its own clock. The stagger does not
        // need to be told about it -- it has its own HUD element and its own sound.
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < ViewModelMotion.GUARD_RISE_TICKS; i++) {
            motion.tick(false, 0.0F, 0.0F, true, READY, false, false);
        }
        assertTrue(now(motion) > 0.9F, "sanity: the stance is up before it breaks");

        motion.onGuardBreak();
        float peak = 0.0F;
        for (int tick = 0; tick <= ViewModelMotion.BREAK_TICKS; tick++) {
            // The item is staggered the whole time. The collapse must not care.
            motion.tick(false, 0.0F, 0.0F, false, 0.5F, false, false);
            peak = Math.max(peak, motion.guardBreak());
        }

        assertTrue(peak > 0.9F, "a collapse nobody can see is not an animation, it peaked at " + peak);
        assertTrue(ViewModelMotion.BREAK_TICKS <= 12,
                "and it has to be over in well under a second. It is " + ViewModelMotion.BREAK_TICKS
                        + " ticks; the sword's stagger is a hundred, and copying that number is "
                        + "exactly what made the weapon crawl backwards for five seconds.");
        assertEquals(0.0F, motion.guardBreak(), 0.0F,
                "and gone afterwards, or the sword stays knocked off line long after the hit");
    }

    @Test
    @DisplayName("one collapse per cooldown, however long the cooldown is")
    void oneCollapsePerCooldown() {
        // This is the bug the motion's own timing could not catch.
        //
        // The edge used to be "the cooldown number went down", which is what a countdown looks
        // like: a hundred ticks of stagger produced a hundred collapses, each restarting a seven
        // tick bump. The result was a constant partial offset held for five seconds, which the
        // playtest called a piece of crap, and the motion's tests were green the whole time --
        // because the caller was doing it, and a component test cannot see wiring.
        //
        // The edge has to be "nothing was on cooldown and now something is", because that is
        // what a break IS. Everything here is about the cooldown's length being irrelevant.
        ViewModelMotion motion = held(true, ViewModelMotion.GUARD_RISE_TICKS);

        int collapses = 0;
        float longest = 0.0F;
        int live = 0;
        for (int tick = 1; tick <= 100; tick++) {
            float remaining = 1.0F - tick / 100.0F;
            motion.observeStagger(remaining);
            motion.tick(false, 0.0F, 0.0F, false, remaining, false, false);

            if (motion.guardBreak() > 0.0F) {
                live++;
                longest = Math.max(longest, motion.guardBreak());
            } else if (live > 0) {
                collapses++;
                live = 0;
            }
        }
        assertEquals(1, collapses, "a hundred ticks of cooldown produced " + collapses
                + " separate collapses");
        assertTrue(live <= ViewModelMotion.BREAK_TICKS,
                "and the collapse cannot outlast its own length, whatever the cooldown is: it was "
                        + "still running " + live + " ticks in");
        assertTrue(longest > 0.9F, "while still being visible, it peaked at " + longest);
    }

    @Test
    @DisplayName("a cooldown that ends and starts again is two breaks")
    void twoCooldownsAreTwoBreaks() {
        ViewModelMotion motion = held(true, ViewModelMotion.GUARD_RISE_TICKS);
        int collapses = 0;
        float previous = 0.0F;
        for (int tick = 0; tick < 200; tick++) {
            // Two separate staggers with a ready gap between them, which is what a guard that
            // came back up and broke again looks like.
            float remaining = tick < 100 ? 1.0F - tick / 100.0F
                    : (tick < 150 ? 0.0F : 1.0F - (tick - 150) / 50.0F);
            if (previous <= 0.0F && remaining > 0.0F) {
                collapses++;
            }
            previous = remaining;
            motion.observeStagger(remaining);
            motion.tick(false, 0.0F, 0.0F, tick < 150, remaining, false, false);
        }
        assertEquals(2, collapses,
                "the edge is a transition, so a guard that came back and broke again is two "
                        + "breaks and not one merged one");
    }

    @Test
    @DisplayName("the collapse plays once, not once a tick")
    void theCollapseDoesNotRepeat() {
        // The edge was originally "the cooldown went down", which is a descending counter, so a
        // hundred ticks of stagger produced a hundred collapses. The trace showed one line per
        // tick for the whole stun and the animation stuttered instead of punching.
        ViewModelMotion motion = held(true, ViewModelMotion.GUARD_RISE_TICKS);
        motion.onGuardBreak();

        int restarts = 0;
        boolean everLive = false;
        for (int tick = 0; tick < 60; tick++) {
            boolean wasLive = motion.guardBreak() > 0.0F;
            motion.tick(false, 0.0F, 0.0F, false, 0.5F, false, false);
            boolean nowLive = motion.guardBreak() > 0.0F;
            // The first time it goes live is the punch itself, not a restart. Counting that one
            // would make this test fail on a correct implementation, which is the other way a
            // diagnostic gets ignored.
            if (wasLive) {
                everLive = true;
            } else if (nowLive && everLive) {
                restarts++;
            }
        }
        assertEquals(0, restarts,
                "a collapse that starts again on its own is a hundred little bumps instead of "
                        + "one, and it reads as a stutter");
    }

    @Test
    @DisplayName("zero cooldown means ready, not fully staggered")
    void zeroCooldownIsReady() {
        // The one mistake that hid the guard pose for two playtest rounds.
        //
        // `ItemCooldowns#getCooldownPercent` returns the fraction REMAINING, and returns zero
        // outright when the item has no cooldown at all. Reading it as "one means ready" made
        // every idle tick look like a full stagger, so the guard channel was forced to zero sixty
        // times a second and the weapon never left the vanilla pose -- which the player reported
        // as "I cover and nothing happens", the same sentence as four unrelated bugs.
        //
        // Both facts are pinned here because both were got wrong the same way, and both are
        // readable off the method name, which is exactly why they were got wrong.
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < ViewModelMotion.GUARD_RISE_TICKS; i++) {
            motion.tick(false, 0.0F, 0.0F, true, 0.0F, false, false);
        }
        assertTrue(now(motion) > 0.9F,
                "no cooldown at all is a ready item, so the stance has to be able to come up");

        ViewModelMotion staggered = new ViewModelMotion();
        for (int i = 0; i < ViewModelMotion.GUARD_RISE_TICKS; i++) {
            staggered.tick(false, 0.0F, 0.0F, true, 0.0F, false, false);
        }
        staggered.tick(false, 0.0F, 0.0F, true, 0.5F, false, false);
        assertEquals(0.0F, now(staggered), 0.0F,
                "and a running cooldown drops the stance on the same tick, because a knock that "
                        + "takes a third of a second to land is not a knock");
    }

    @Test
    @DisplayName("a broken guard is not a raised guard")
    void theCollapseReplacesTheStance() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < ViewModelMotion.GUARD_RISE_TICKS; i++) {
            motion.tick(false, 0.0F, 0.0F, true, READY, false, false);
        }
        motion.onGuardBreak();
        motion.tick(false, 0.0F, 0.0F, false, 0.5F, false, false);

        Offset duringBreak = ViewModelDefaults.of(WeaponProfile.SWORD)
                .evaluate(motion.snapshot(1.0F, 0.0F));
        assertTrue(motion.snapshot(1.0F, 0.0F).guard() <= 0.2F,
                "the guard channel is down while the item is staggered; the collapse is the "
                        + "BREAK track's job, not six more fall ticks of the GUARD one");
        assertFalse(duringBreak.isZero(),
                "and the sword still moves, because the player has to be able to see the hit land");
    }

    @Test
    @DisplayName("a swing is unaffected by a break, and nothing is stale after one")
    void theBreakDoesNotTouchTheSwing() {
        ViewModelMotion motion = new ViewModelMotion();
        for (int i = 0; i < 30; i++) {
            motion.tick(true, 1.0F, 1.0F, false, READY, false, false);
        }
        motion.onStrike(1.0F, 12);
        motion.tick(false, 0.0F, 0.0F, false, READY, false, false);

        assertTrue(motion.snapshot(1.0F, 0.0F).strike() > 0.0F,
                "a swing plays on its own clock and a guard break is not part of it");
        assertEquals(0.0F, motion.guardBreak(), 0.0F,
                "and no break is live because none was announced");
    }

    @Test
    @DisplayName("the sword has a guard pose and the bare hand does not")
    void onlyWeaponsThatCanGuardCarryAPose() {
        // The design makes the sword guardable, and the shield is animated by vanilla itself --
        // a profile there would compose with it rather than replace it, which is the thing to
        // avoid. And an item with no guard profile has nothing to pose for.
        Offset swordGuarded = ViewModelDefaults.of(WeaponProfile.SWORD)
                .evaluate(ViewModelInput.guarding(1.0F));
        assertFalse(swordGuarded.isZero(), "a sword that can guard has to look like it is guarding");

        Offset fistGuarded = ViewModelDefaults.of(WeaponProfile.UNARMED)
                .evaluate(ViewModelInput.guarding(1.0F));
        assertTrue(fistGuarded.isZero(),
                "the bare hand has no guard profile, so it must not move for a stance it does "
                        + "not have");
    }
}
