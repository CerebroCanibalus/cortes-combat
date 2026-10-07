package com.cortes.combat.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That one swing spends one point, and that "one swing" is the tick and the two ends of it.
 *
 * <h2>Why the sabre is the case and not a mob</h2>
 *
 * <p>Because the sabre is the only weapon here that lands twice inside a single swing, and
 * because vanilla resolves the shield twenty six lines before it checks the invulnerability
 * window. A charged sabre against a raised shield took two points; the second report &mdash; a
 * shield that "sometimes" let a hit through &mdash; was the third point already gone by the time
 * the next swing arrived.</p>
 *
 * <p>So the question this has to answer is not "did something block a moment ago" but
 * <b>"is this the same strike"</b>, and a strike is a tick with an attacker and a target on it.</p>
 */
class GuardStampTest {

    private static final int SABRE = 42;
    private static final int ZOMBIE = 7;
    private static final int OTHER = 8;
    private static final long T0 = 1000L;

    /** The first hit of anything always pays. */
    @Test
    @DisplayName("the first block of a swing pays")
    void theFirstBlockPays() {
        assertTrue(GuardStamp.NONE.shouldCharge(T0, SABRE, ZOMBIE),
                "a guard that has never blocked must not remember one. **The empty stamp is what "
                        + "makes the first hit of a fight cost its point** rather than being free "
                        + "because the key happens to line up");
    }

    /**
     * The sabre, which is the whole bug.
     *
     * <p>Two {@code hurt()} calls, one swing, one tick, same attacker, same target. <b>One
     * point.</b></p>
     */
    @Test
    @DisplayName("a charged sabre's two cuts cost one point")
    void theTwoCutsOfOneSwingCostOnePoint() {
        GuardStamp spent = GuardStamp.NONE.charged(T0, SABRE, ZOMBIE);

        assertFalse(spent.shouldCharge(T0, SABRE, ZOMBIE),
                "**the second cut of the same swing is free.** This is the reported bug: vanilla "
                        + "checks the invulnerability window twenty six lines after it resolves "
                        + "the shield, and our loop clears that window before each cut on purpose, "
                        + "so both reached the guard");
    }

    /**
     * A different mob in the same tick still pays.
     *
     * <p><b>This is the case that makes a boolean wrong.</b> An area weapon lands one attacker's
     * cuts on several entities inside one tick, and a shield that charged once per attacker per
     * tick would be free against the third mob in a sweep &mdash; which is the same bug pointing
     * the other way.</p>
     */
    @Test
    @DisplayName("a different target in the same tick pays, or a sweep would be free after the first")
    void aDifferentTargetStillPays() {
        GuardStamp spent = GuardStamp.NONE.charged(T0, SABRE, ZOMBIE);

        assertTrue(spent.shouldCharge(T0, SABRE, OTHER),
                "**the second mob in the same sweep has to pay.** Keying on 'the player just "
                        + "blocked something' would make a raised shield free against everything "
                        + "the swing reached after the first");
    }

    /** And a different attacker, for the same reason. */
    @Test
    @DisplayName("a different attacker in the same tick pays")
    void aDifferentAttackerStillPays() {
        GuardStamp spent = GuardStamp.NONE.charged(T0, SABRE, ZOMBIE);

        assertTrue(spent.shouldCharge(T0, OTHER, ZOMBIE),
                "two attackers swinging on the same tick are two strikes, whatever the tick says");
    }

    /**
     * The next tick pays again, which is what makes a guard cost something at all.
     *
     * <p>The counterpart of the sabre test and the reason the key has a tick in it: a held
     * cooldown fights in swings, and a guard that only ever paid once would be infinite.</p>
     */
    @Test
    @DisplayName("the next swing pays again")
    void theNextSwingPaysAgain() {
        GuardStamp spent = GuardStamp.NONE.charged(T0, SABRE, ZOMBIE);

        assertTrue(spent.shouldCharge(T0 + 1, SABRE, ZOMBIE),
                "**a tick later it costs again.** A sabre swings every twenty ticks and a guard "
                        + "that did not re-arm would be a formality after the first hit of a fight");
        assertTrue(spent.shouldCharge(T0 + 8, SABRE, ZOMBIE),
                "and at any other distance in time, not just the next one");
    }

    /** The key survives a round trip through its own codec, which is what persistence uses. */
    @Test
    @DisplayName("the stamp is not an identity check that breaks after saving")
    void itSurvivesTheCodec() {
        var codec = GuardStamp.CODEC;
        var json = codec.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                GuardStamp.NONE.charged(T0, SABRE, ZOMBIE))
                .result()
                .orElseThrow(() -> new AssertionError("the stamp did not encode"));
        var parsed = codec.parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                .result()
                .orElseThrow(() -> new AssertionError("the stamp did not survive its own codec"));

        assertFalse(parsed.shouldCharge(T0, SABRE, ZOMBIE),
                "**a stamp read back from disk still remembers what it already paid for.** A key "
                        + "that changes identity on reload would hand every player a free block "
                        + "on every join, and nothing would say so");
    }
}