package com.cortes.combat.attack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the engine hands the fall over.
 *
 * <h2>The hole this found, and it is the project's oldest one</h2>
 *
 * <p>Every other test in this pair passes the fall <em>by hand</em> to {@code DamageResolver}.
 * That means the curve was verified, the arithmetic was verified, the cap was verified, and a
 * player falling out of the sky could still have hit for a flat fourteen -- because the engine was
 * calling the five-argument overload, which has no notion of gravity in it.</p>
 *
 * <p>Verified by sabotage: replacing {@code player.fallDistance} with {@code 0.0F} in
 * {@code CombatEngine} left the entire suite green. <b>A tested function with no caller, and a
 * caller with no test.</b> That is the shape of the bug the sable's dead {@code BREAK} track had
 * and of the "every object I hit with can charge" classification fix: something in place, nothing
 * drawing it, and a green build.</p>
 *
 * <p>So this reads the source. Brittle by design, and the alternative is invisible.</p>
 */
class MaceFallWiringTest {

    @Test
    @DisplayName("the engine passes the player's fall, and it is the only caller that matters")
    void theEngineHandsOverTheFall() {
        String engine = read();

        assertTrue(engine.contains("player.fallDistance)"),
                "**CombatEngine has to pass `player.fallDistance` into the resolver.** The curve, "
                        + "the cap and the arithmetic are all tested and all of them call "
                        + "DamageResolver directly with a fall in hand -- so without this line the "
                        + "maza is a flat fourteen in the game and every test in the project is "
                        + "green. Verified by sabotage: a literal 0.0F here passes all of them");

        assertTrue(!engine.contains("0.0F);"),
                "**and not a zero.** The five-argument overload exists precisely so that the "
                        + "fall-free callers stay readable, and it is a trap from this side: "
                        + "calling it compiles, means nothing, and looks correct in the source");
    }

    /**
     * The maza's attribute is this game's number and not vanilla's.
     *
     * <p>Six in vanilla, ten here. The whole weapon's argument is the gap between the two,
     * and a {@code removeModifier} that silently stops matching leaves the item at six with no
     * error anywhere &mdash; the damage would simply be wrong, on one item, forever.</p>
     */
    @Test
    @DisplayName("the maza's damage comes from our number, not vanilla's five")
    void theOffsetIsOurs() {
        assertTrue(MaceRebalance.OFFSET == 14.0F,
                "**the offset has to be fourteen.** Vanilla is five, which with the player's own "
                        + "one is six damage; this game gives it fifteen. A modifier that fails to "
                        + "apply is not an error: it is one item quietly doing a third of what the "
                        + "tooltip says");
    }

    /**
     * And the speed, which is the one number here that a naive derivation gets backwards.
     *
     * <p>{@code ATTACK_SPEED} is an <b>offset from the player's own 4.0</b>, so ten percent slower
     * is not ten percent off the offset. Scaling -3.4 by 0.9 gives -3.06, which is 0.94 attacks a
     * second -- <em>faster</em> than vanilla's 0.6, from a constant whose name reads "slower".</p>
     */
    @Test
    @DisplayName("the maza swings ten percent slower, and the offset is derived from the speed")
    void theSpeedIsSlower() {
        assertEquals(-3.46F, MaceRebalance.SPEED_OFFSET, 0.0001F,
                "**the offset, and it is not -3.4 times 0.9.** That would be -3.06, which is "
                        + "faster than vanilla: the attribute is an offset from the player's 4.0, "
                        + "so the slowdown has to be applied to the speed and subtracted back");
        assertEquals(0.54F, MaceRebalance.ATTACK_SPEED, 0.0001F,
                "which is vanilla's 0.6, ten percent slower");
        assertTrue(MaceRebalance.ATTACK_SPEED < MaceRebalance.PLAYER_BASE_ATTACK_SPEED
                        + MaceRebalance.VANILLA_SPEED_OFFSET,
                "and slower than vanilla, which is the assertion the sign error breaks");
    }

    /**
     * And Density, whose whole existence depends on this one read.
     *
     * <p><b>Density lived on a class nobody runs.</b> Vanilla applies it inside
     * {@code MaceItem#getAttackDamageBonus}, and this project replaced the attack flow, so a
     * Density maza carried a dead enchantment until the level was read and summed by hand. The
     * curve is tested <em>and</em> the call is tested, because one without the other is the same
     * green build with a dead enchantment inside it.</p>
     */
    @Test
    @DisplayName("the engine reads Density and passes the level beside the fall")
    void theEngineHandsOverDensity() {
        String engine = read();

        assertTrue(engine.contains("Enchantments.DENSITY"),
                "**CombatEngine has to resolve the Density enchantment.** Without the read the "
                        + "level is never known and the curve in MaceFall is a comment that costs a "
                        + "sum -- the exact shape the fall's own wiring test was written to catch");

        assertTrue(engine.contains("player.fallDistance, density"),
                "**and the level has to arrive at the resolver, beside the fall.** Density scales "
                        + "the fall, so it travels in the same call; a read that stops at a local "
                        + "variable changes nothing about the hit");
    }

    /**
     * The order the smash's landing keeps, which is the one thing a maza with Wind Burst needs.
     *
     * <h2>The bug this guards</h2>
     *
     * <p>Wind Burst is a POST_ATTACK effect on the attacker, and its explosion calls
     * {@code ServerPlayer#onExplosionHit}, which clears the fall immunity the smash had just armed.
     * So the immunity has to be set again <em>after</em> {@code doPostAttackEffects}; a
     * re-assertion placed before it is silently undone on every empowered swing, and the build is
     * green because nothing in a bare JVM can see the two calls in the same method.</p>
     *
     * <p>And the descent stop has to stay in the <em>other</em> half: done after Wind Burst it would
     * overwrite the upward launch the enchantment just gave, which is a different bug on the same
     * line. Reading the source is brittle on purpose -- the alternative is a fix that cannot be
     * distinguished from its absence.</p>
     *
     * <p>The swing-level landing is one object because the maza hits up to three bodies: the
     * descent stop runs on the first, the immunity re-assert after every body's effects. The order
     * asserted here is the same either way; the class that holds the split is checked separately by
     * {@link #theStatesAreThere()}.</p>
     */
    @Test
    @DisplayName("the smash is begun before the effects and re-asserted after")
    void theSmashSurvivesWindBurst() {
        String engine = read();

        int begin = engine.indexOf("smash.onLanding(");
        int effects = engine.indexOf("EnchantmentHelper.doPostAttackEffects(");
        int reaffirm = engine.indexOf("smash.afterEffects(");

        assertTrue(begin >= 0, "**CombatEngine has to land the maza's smash at all.** Without the "
                + "`onLanding` call the smash never stops the descent nor arms the fall immunity -- "
                + "the exact shape of the dropped `resetFallDistance` the replaced flow already paid for");
        assertTrue(effects >= 0, "and the post-attack effects have to keep running; Wind Burst is one of them");
        assertTrue(reaffirm >= 0, "**and the immunity has to be re-asserted.** Wind Burst's explosion "
                + "clears it (`onExplosionHit`), so a maza with the enchantment falls from the height "
                + "it was thrown to unless this call exists");
        assertTrue(begin < effects,
                "the descent stop goes BEFORE the effects: after them it would cancel Wind Burst's "
                        + "own upward launch");
        assertTrue(effects < reaffirm,
                "**and the re-assert goes AFTER the effects, which is the whole point.** Placed "
                        + "before `doPostAttackEffects` it is cleared again by the very explosion it "
                        + "exists to survive, and every test still passes");
    }

    /**
     * That the replicated states are actually the four vanilla sets, and the min-guard is kept.
     *
     * <p>The order test above only proves <em>when</em> the landing runs. This one proves it does
     * something: the descent stop and its motion packet, the impact position, the immunity flag, and
     * the grounded-victim particle flag -- verbatim {@code MaceItem#hurtEnemy}. And the
     * minimum-with-the-saved guard, which is the half a simplification drops and which decides the
     * height the fall is forgiven from.</p>
     */
    @Test
    @DisplayName("the landing replicates vanilla's four states, min-guard included")
    void theStatesAreThere() {
        String smash = readMaceSmash();

        assertTrue(smash.contains("setIgnoreFallDamageFromCurrentImpulse(true)"),
                "the fall immunity -- the flag that makes a smash forgive the drop");
        assertTrue(smash.contains("currentImpulseImpactPos"),
                "and the impact point it forgives down to");
        assertTrue(smash.contains("ClientboundSetEntityMotionPacket"),
                "and the motion packet, so the client sees the descent stop");
        assertTrue(smash.contains("setDeltaMovement("),
                "which is the descent stop itself");
        assertTrue(smash.contains("setSpawnExtraParticlesOnFall("),
                "and the grounded-victim flag");
        assertTrue(smash.contains("isIgnoringFallDamageFromCurrentImpulse()"),
                "**and the saved-minimum guard, which is not a simplification to drop.** Without it "
                        + "a second smash on one fall resets the forgiveness height to the later, "
                        + "higher point and the earlier drop is charged again");
        assertTrue(smash.contains("Direction.Axis.Y"),
                "the descent stop is on Y only, as vanilla writes it");
    }

    private static String read() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "attack",
                "CombatEngine.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }

    private static String readMaceSmash() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "attack",
                "MaceSmash.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
