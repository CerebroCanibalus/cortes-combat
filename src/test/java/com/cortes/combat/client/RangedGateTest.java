package com.cortes.combat.client;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponKinds;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the ranged gate does not take a fighting weapon's click away.
 *
 * <h2>The bug this exists for</h2>
 *
 * <p>{@code CombatAim#isRanged} named a bow, a crossbow <b>and a trident</b>, because a trident is
 * a projectile weapon and the check reads as "is this ranged". It is, and that was the wrong
 * question. The answer is used in two places:</p>
 *
 * <ul>
 *   <li>the crosshair, which for a trident has to read the melee cone -- it is a melee weapon here;
 *       and</li>
 *   <li>the press gate in {@code SwingInputHandler}, which runs <em>after</em> the profile
 *       question. A bow and a crossbow are already refused there -- neither is in a weapon tag, so
 *       both derive to {@code NONE} -- so for those two the ranged check is redundant. <b>For the
 *       trident it was the one that decided</b>, and it handed the whole left click back to
 *       vanilla.</li>
 * </ul>
 *
 * <p>The report was "the trident is still full vanilla" with a green build: a profile that fights,
 * excluded one layer up by a question about the item. Same shape as the {@code #tridents} tag that
 * never existed.</p>
 *
 * <h2>Why one assertion reads the source</h2>
 *
 * <p>Because {@code isRanged} asks about an item class and a test cannot hold a {@code Minecraft}.
 * The half that can be asked purely is asked purely ({@link #theTridentFights()}); the half that
 * cannot is a source read, like this project's other wiring guards.</p>
 */
class RangedGateTest {

    private static final Path AIM = Path.of("src", "main", "java", "com", "cortes", "combat",
            "client", "CombatAim.java");

    /**
     * The premise, and it is pure: the trident's profile is one that fights.
     *
     * <p>Without this the source assertion below is about nothing. With it, the pair says the thing
     * that matters: <b>a profile that fights must not be excluded by an item-shaped gate.</b></p>
     */
    @Test
    @DisplayName("the trident is a profile that fights, so no gate may exclude it")
    void theTridentFights() {
        assertTrue(WeaponKinds.fights(WeaponProfile.TRIDENT),
                "**this is why the ranged gate matters.** The trident is a melee weapon in this "
                        + "system -- reach 5.3 on a fifteen degree cone, a charge and a pull -- so "
                        + "it is in WeaponKinds.COMBAT and the press has to reach it");
    }

    /**
     * And the gate names only the two weapons that genuinely cannot fight.
     *
     * <p>Brittle by design, like the other wiring guards: the failure mode is a reformatted file,
     * and the alternative is an invisible bug where a whole weapon's button goes to vanilla.</p>
     */
    @Test
    @DisplayName("isRanged names a bow and a crossbow, and not the trident")
    void theRangedGateDoesNotNameTheTrident() throws IOException {
        String aim = Files.readString(AIM, StandardCharsets.UTF_8);

        // **The method body, not the file.** The javadoc above isRanged explains the trident at
        // length -- it has to, because the whole bug is a one-word difference -- so a file-wide
        // search for "TridentItem" matches the explanation and fails on a correct file. What
        // matters is the code the JVM runs.
        int start = aim.indexOf("public static boolean isRanged(ItemStack stack)");
        assertTrue(start > 0, "isRanged has to exist for this to be about anything");
        int end = aim.indexOf("\n    }", start);
        assertTrue(end > start, "the method has to close, or the slice below is the whole file");
        String body = aim.substring(start, end);

        assertFalse(body.contains("TridentItem"),
                "**a trident in isRanged is the whole bug.** The check is by item class and the "
                        + "press gate runs AFTER the profile question, so anything named here has "
                        + "its left click handed to vanilla -- which is only correct for a weapon "
                        + "that cannot fight. A bow and a crossbow cannot (neither is in a weapon "
                        + "tag, so both derive to NONE and the profile gate already refuses them); "
                        + "the trident can, so naming it was the one that decided. The report was "
                        + "'the trident is still full vanilla' with a green build");
        assertTrue(body.contains("BowItem") && body.contains("CrossbowItem"),
                "and the two that are genuinely ranged-only have to stay: a bow's crosshair reads "
                        + "the draw, not the melee cone, and that is a different question from the "
                        + "press");
        assertFalse(aim.contains("import net.minecraft.world.item.TridentItem"),
                "and the import goes with it. A class left in an unused import is the line the "
                        + "next reader wires back in, and the build does not complain");
    }
}
