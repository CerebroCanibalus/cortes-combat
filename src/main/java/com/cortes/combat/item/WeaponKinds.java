package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which weapon profiles are weapons, as opposed to things that happen to be in your hand.
 *
 * <h2>Why this is a list of profiles and not a question about the item</h2>
 *
 * <p><b>Because there is no question about the item that answers it.</b> 1.21 has no
 * {@code WeaponItem}: the closest thing is {@code ProjectileWeaponItem}, which covers the bow and
 * the crossbow and nothing on this list. So every way of asking "is this a weapon" from the item
 * fails:</p>
 *
 * <ul>
 *   <li>{@code instanceof DiggerItem} groups the axe, the pickaxe and the shovel &mdash; and the
 *       pickaxe is the one thing on this list that must NOT be a weapon.</li>
 *   <li>"does it have attack damage" covers the sword, the axe, the pickaxe and the mace, and
 *       misses the shovel and the trident.</li>
 *   <li>A tag scan is what {@code deriveProfile} already does, and it is why a shovel can have a
 *       halberd's sweep: the tag answers "what is this" and the question is "should it fight".</li>
 * </ul>
 *
 * <p>So it is a set of <em>profiles</em>, which is a statement about capability and not about an
 * item's identity. A foreign mod's sword gets in because its profile resolves to a weapon one, not
 * because somebody listed its id here.</p>
 *
 * <h2>What the empty answer is for</h2>
 *
 * <p><b>{@link WeaponProfile#NONE} is not in here, and {@link WeaponProfile#PICKAXE} is not in
 * here.</b> The first is what every unrecognised item resolves to, and the second is a deliberate
 * decision: the pickaxe is a tool and stays one.</p>
 *
 * <p>That leaves {@code NONE} with a reach of 3.0 in {@link WeaponTables}, which is longer than a
 * bare fist's 2.5 and almost a sword's 3.6 &mdash; so an item that is not a weapon still hit almost
 * as far as one that is. That was the half of "every object I hit with can charge" that got fixed
 * in {@code deriveProfile} while the other half stayed: the classification was corrected and the
 * gate in front of it was not.</p>
 *
 * <p>Pure, and it reads nothing, so a test can walk it.</p>
 */
public final class WeaponKinds {

    private static final Set<WeaponProfile> COMBAT = EnumSet.of(
            WeaponProfile.UNARMED,
            // The vanilla arms, and the vanilla tools that fight. **Not the pickaxe.**
            WeaponProfile.SWORD,
            WeaponProfile.AXE,
            WeaponProfile.SHOVEL,
            WeaponProfile.MACE,
            WeaponProfile.TRIDENT,
            // And the six of ours.
            WeaponProfile.ROPERA,
            WeaponProfile.HALBERD,
            WeaponProfile.WARHAMMER,
            WeaponProfile.DAGGER,
            WeaponProfile.SABRE,
            WeaponProfile.ARCABUZ,
            // The hoe. **It was missing here for a round**, and the gap is the whole story of the
            // playtest that reported three broken weapons at once: the profile existed, all four
            // tables had rows for it, the tag `#hoes` existed and matched -- and this list, which
            // is the door the click asks, had never heard of it. A component in place and nobody
            // calling it, which is the shape this project has now paid for four separate times.
            WeaponProfile.HOE);

    private WeaponKinds() {
    }

    /**
     * Whether a weapon that resolves to this profile is a weapon.
     *
     * <p><b>The question the click gate asks, and nothing else does.</b> It is asked once, at the
     * front of the press, and a "no" hands the whole click back to vanilla &mdash; which is the
     * correct answer for a block, for a potion and for a pickaxe.</p>
     */
    public static boolean fights(WeaponProfile profile) {
        return profile != null && COMBAT.contains(profile);
    }

    /**
     * Every profile that fights, for a test that has to walk it.
     *
     * <p><b>"May be held down" is not asked here</b>, and it was here for one round. It is
     * {@link WeaponTables#autoRepeats}, because that one has to read the charge table, and the
     * first version of it lived here and answered from the wrong table: it said the bare fist
     * repeats, and the fist charges. A test caught it.</p>
     */
    public static Set<WeaponProfile> combatProfiles() {
        return Set.copyOf(COMBAT);
    }
}