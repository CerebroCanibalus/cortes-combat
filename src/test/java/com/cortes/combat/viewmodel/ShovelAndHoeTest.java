package com.cortes.combat.viewmodel;

import com.cortes.combat.core.weapon.WeaponProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shovel and the hoe, which were the last two vanilla tools without a motion.
 *
 * <h2>Two decisions, and each one is a direction</h2>
 *
 * <p><b>The shovel is the only strike in the table that finishes going up.</b> Every other way of
 * hitting ends low or level -- the sword, the axe, the hammer and the maza fall, and the ropera,
 * the dagger, the fist and the bayonet drive forward. A shovel moves material, so its swing is a
 * scoop: the head drops while the charge builds and the load is flung forward and up. The test
 * pins the direction rather than the numbers, because the numbers are a playtest question and the
 * direction is the part that is derived from what the weapon already does (its knockback is the
 * strongest horizontal in the set after the shot).</p>
 *
 * <p><b>The hoe is the only weapon that fights an area with its ordinary swing and its right
 * button</b>, so it gets two motions: a reap that comes across on the left click, and a cleave that
 * goes past a half turn on the sweep. Both are pinned against the halberd, which is the other
 * {@code MULTI_HIT} weapon and whose track is the thing the hoe must NOT look like -- a hoe that
 * swung like a halberd would be a second halberd with a worse reach.</p>
 */
class ShovelAndHoeTest {

    private static final ViewModelProfile HALBERD = ViewModelDefaults.of(WeaponProfile.HALBERD);

    private static ViewModelProfile of(WeaponProfile profile) {
        return ViewModelDefaults.of(profile);
    }

    @Test
    @DisplayName("both have a profile, both own the swing, and neither is held differently at rest")
    void bothAreRealProfiles() {
        for (WeaponProfile profile : new WeaponProfile[]{
                WeaponProfile.SHOVEL, WeaponProfile.HOE}) {
            ViewModelProfile viewModel = of(profile);
            assertFalse(viewModel.isEmpty(), profile + " has no motion, which is the old gap");
            assertTrue(viewModel.ownsSwing(),
                    profile + " has a profile that contains its whole strike, so vanilla's arm "
                            + "swing has to be cancelled or the two play on top of each other");
            assertTrue(viewModel.evaluate(ViewModelInput.RESTING).isZero(),
                    profile + " moved at rest. Only the ropera is held differently, and neither of "
                            + "these two asked for a resting pose");
        }
    }

    @Test
    @DisplayName("the shovel is a scoop: it reads the strike, charges, and the throw ends UP")
    void theShovelFlingsForwardAndUp() {
        ViewModelProfile shovel = of(WeaponProfile.SHOVEL);
        assertTrue(shovel.reads(Channel.STRIKE),
                "the scoop is a throw forward, which is the bump; a fall is the other channel");
        assertFalse(shovel.reads(Channel.CHOP), "and not both at once");
        assertTrue(shovel.reads(Channel.WINDUP),
                "the shovel charges (0.4 over thirty-three ticks), so it has a wind-up to draw");

        // The load: down and back.
        Offset loaded = shovel.evaluate(ViewModelInput.of(1.0F, 0.0F));
        assertTrue(loaded.y() < 0.0, "the head drops to gather the load, was y=" + loaded.y());
        assertTrue(loaded.z() > 0.0,
                "and the shaft comes back toward the body (positive z is toward the camera), was z="
                        + loaded.z());

        // The throw: forward and up.
        Offset thrown = shovel.evaluate(ViewModelInput.of(0.0F, 1.0F));
        assertTrue(thrown.z() < 0.0,
                "the throw goes away from the camera, was z=" + thrown.z());
        assertTrue(thrown.pitch() > 0.0,
                "and the blade finishes pointing UP, which is the half no other profile has. This "
                        + "is the shovel's whole identity: a scoop that ends where it started never "
                        + "emptied, and it read pitch=" + thrown.pitch());
    }

    @Test
    @DisplayName("the hoe's left click is a reap that comes across, not a halberd's jab")
    void theHoeReaps() {
        ViewModelProfile hoe = of(WeaponProfile.HOE);
        assertTrue(hoe.reads(Channel.CHOP),
                "the left click is an area reap that falls, so it is the chop and not the bump");
        assertFalse(hoe.reads(Channel.STRIKE), "and not both at once");

        Offset reap = hoe.evaluate(ViewModelInput.chopping(1.0F));
        Offset halberdChop = HALBERD.evaluate(ViewModelInput.chopping(1.0F));

        assertTrue(reap.x() > 0.5,
                "the reap crosses the view, so the lateral travel has to be most of an item; was x="
                        + reap.x());
        assertTrue(Math.abs(reap.yaw()) > Math.abs(halberdChop.yaw()) * 1.5,
                "and it turns far further than the halberd's chop (" + reap.yaw() + " against "
                        + halberdChop.yaw() + "). A sixty-degree cone that reaches six bodies is "
                        + "not communicated by a chop that lands on the crosshair");
        assertTrue(reap.roll() < 0.0,
                "the head turns over on the way, which is what makes it a scythe rather than a "
                        + "straight sweep; was roll=" + reap.roll());
    }

    @Test
    @DisplayName("the hoe's right button is a cleave past a half turn, not the halberd's arc")
    void theHoeCleavesTheCircle() {
        ViewModelProfile hoe = of(WeaponProfile.HOE);
        assertTrue(hoe.reads(Channel.SWEEP),
                "the hoe throws the same MULTI_HIT special as the halberd, on the sweep channel");

        Offset cleave = hoe.evaluate(ViewModelInput.sweeping(1.0F));
        Offset arc = HALBERD.evaluate(ViewModelInput.sweeping(1.0F));

        assertTrue(Math.abs(cleave.yaw()) > 180.0 - 1.0E-6,
                "the cleave has to be a cleave: more than a half turn, so the head arrives from the "
                        + "far side. Was yaw=" + cleave.yaw() + " against the halberd's " + arc.yaw());
        assertTrue(Math.abs(cleave.yaw()) > Math.abs(arc.yaw()) * 2.0,
                "and it is at least twice the halberd's arc, or the two area weapons are the same "
                        + "move and the hoe has no identity");
        assertTrue(cleave.x() > 0.5,
                "it also travels across, so the circle is not a spin in place; was x=" + cleave.x());
        assertFalse(cleave.equals(arc),
                "the hoe's cleave and the halberd's sweep cannot be the same offset. Two weapons "
                        + "sharing a track is a copy, and the hoe's whole premise is that it is not "
                        + "the halberd");
    }
}
