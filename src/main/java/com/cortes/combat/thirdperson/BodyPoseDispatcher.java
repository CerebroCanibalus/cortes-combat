package com.cortes.combat.thirdperson;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.client.ItemViewModel;
import com.cortes.combat.viewmodel.Channel;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.viewmodel.ViewModelProfile;
import com.cortes.combat.viewmodel.ViewModelInput;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Holds the third-person pose for every player on screen and hands the mixin a frame of it.
 *
 * <p><b>Two poses, not one, and that is the whole bug this class was rewritten for.</b> The first
 * version kept a single static pose, filled it on the client tick, and the mixin read that same
 * value on every one of the sixty frames a second. So the torso moved at twenty hertz inside a
 * sixty frames a second renderer, and the playtest said "there is no interpolation, it goes in raw
 * frames" -- which is exactly what a twenty hertz animation looks like when everything around it is
 * smooth.</p>
 *
 * <p>It is the same mistake the viewmodel library was built to avoid: state on the tick, frame by
 * {@code snapshot(partialTick)}. The one difference from over there is that the model is drawn once
 * per frame with no partial tick handed to it, so the partial tick is read here from the same
 * timer the viewmodel reads.</p>
 *
 * <p><b>Only the local player is ever asked.</b> Other players are animated by vanilla from the
 * server's {@code player.swing()}, and that is deliberate: this project does not sync limb poses,
 * and guessing at a stranger's arms would be worse than letting vanilla's swing play. The charge
 * and the guard are therefore not visible on other players, which is a real limitation.</p>
 */
public final class BodyPoseDispatcher {

    /** Where the pose was on the previous tick. What the current one is interpolated away from. */
    private static final Map<UUID, BodyPose> PREVIOUS = new HashMap<>();

    /** Where the pose is now. Read once a tick. */
    private static final Map<UUID, BodyPose> CURRENT = new HashMap<>();

    private BodyPoseDispatcher() {
    }

    /**
     * The pose for one player at a point inside the frame.
     *
     * @param partialTick 0 at the start of the tick, approaching 1 at the end
     */
    public static BodyPose snapshot(AbstractClientPlayer player, float partialTick) {
        if (player == null) {
            return BodyPose.NONE;
        }
        BodyPose now = CURRENT.get(player.getUUID());
        if (now == null || now.isZero()) {
            return BodyPose.NONE;
        }
        BodyPose before = PREVIOUS.getOrDefault(player.getUUID(), BodyPose.NONE);
        if (before.isZero()) {
            // First frame of a pose that just appeared. Interpolating from nothing would drag the
            // arm out of the model over one frame, which is its own kind of artefact.
            return now;
        }
        return before.lerp(now, partialTick);
    }

    /**
     * Whether this weapon draws its own swing, and vanilla's must be cancelled.
     *
     * <p><b>The gate is the viewmodel profile and not "is this a mod weapon".</b> The local player's
     * swing arrives anyway, because the SERVER calls {@code player.swing()} on every accepted swing
     * and that syncs down to its owner. {@code ItemViewModel#ownsSwing} exists so the client does
     * not send a second one -- but it only stops the client sending, not the server having sent.</p>
     *
     * <p>So a vanilla axe, which has no profile and therefore {@code ownsSwing == false}, still
     * arrives with {@code attackTime} set and must keep swinging. Gating on "is it ours" instead
     * would take the swing away from six vanilla weapons.</p>
     */
    public static boolean ownsSwing(AbstractClientPlayer player) {
        if (player == null) {
            return false;
        }
        return ItemViewModel.ownsSwing(player.getMainHandItem());
    }

    /**
     * How far the guard is up, 0..1, for the mixin to know whether to write the arm or add to it.
     *
     * <p>Zero for a shield: vanilla's raised shield already has an arm and we leave it entirely
     * alone, which is the exclusion the viewmodel makes too.</p>
     */
    public static float guardAmount(AbstractClientPlayer player) {
        if (player == null || player.getMainHandItem().is(net.minecraft.world.item.Items.SHIELD)) {
            return 0.0F;
        }
        return ItemViewModel.frame().guard();
    }

    /**
     * Recompute the local player's pose. Called once per client tick.
     *
     * <p>Reads the viewmodel's own frame rather than tracking anything of its own. The charge
     * ratio, the guard ramp, the lunge and the sweep are already computed and interpolated for the
     * item in the player's hand; a second clock for the same values would be a second thing to keep
     * in step, and the day they disagreed would be a day where the body and the weapon disagreed
     * about what the player was doing.</p>
     */
    public static void tick(AbstractClientPlayer local) {
        if (local == null) {
            PREVIOUS.clear();
            CURRENT.clear();
            return;
        }
        UUID id = local.getUUID();
        BodyPose pose = poseFor(local);

        // Whatever the pose was last tick is where this one starts from. Always, including when
        // this tick's pose is zero -- otherwise a pose that is ending would snap on its last tick
        // instead of coming back down over one frame.
        PREVIOUS.put(id, CURRENT.getOrDefault(id, BodyPose.NONE));

        if (pose.isZero()) {
            CURRENT.remove(id);
            PREVIOUS.remove(id);
        } else {
            CURRENT.put(id, pose);
        }
    }

    private static BodyPose poseFor(AbstractClientPlayer local) {
        ItemStack stack = local.getMainHandItem();
        WeaponStats stats = WeaponStats.of(stack);
        ViewModelInput input = ItemViewModel.frame();

        // **The wind-up is skipped entirely once the chop is running.** They are the same
        // arm, and both were writing a pitch: a wind-up at -104 under a chop at -85 is -189,
        // which is past vertical, so the arm is on its way over the top. The chop starts at
        // exactly the height the wind-up left (CHOP_START is the wind-up's height over the
        // chop's, not a number somebody chose), so handing the arm over costs nothing -- and the
        // two of them never being on screen together is what keeps the total inside the ceiling.
        float chop = input.chop();
        BodyPose pose = chop > 0.0F
                ? BodyPose.NONE
                : BodyPoses.charging(stats.profile(), stats.charge(), input.windup());

        // **Two swings, and the profile decides which one.**
        //
        // The strike is a bump -- the arm leaves rest, rises and comes back -- and the playtest
        // called the ropera's and the dagger's version of it excellent, because a thrust goes that
        // way. On everything else it was reported twice as starting from below and going up: once
        // for the normal hit, and once for what happens when a charge is released.
        //
        // So the chop exists, it only falls, and **the gate is `reads(CHOP)` rather than a list of
        // weapons** -- the project's own lesson, the same one the parry taught. A list would be a
        // second copy of "which weapons thrust", and the day a weapon was added to one and not the
        // other the arm would do the wrong thing silently.
        //
        // It is the profile that answers, because the profile is the thing that already declares
        // what motion a weapon has: if it has a track on CHOP, its swing falls.
        ViewModelProfile profile = ViewModelDefaults.of(stats.profile());
        pose = pose.plus(profile.reads(Channel.CHOP)
                ? BodyPoses.chopping(input.chop())
                : BodyPoses.striking(input.strike()));

        // **The shield is vanilla's and this does not touch it.** A raised shield already has an
        // animation and an arm to go with it, and a body pose on top would move the arm the shield
        // is held in. The same exclusion the viewmodel makes by having no GUARD track.
        if (!stack.is(Items.SHIELD)) {
            pose = pose.plus(BodyPoses.guarding(
                    GuardStats.best(local).profile(), input.guard()));
        }

        // **The sabre's parry, which was missing from the body and not from the viewmodel.**
        //
        // The playtest said "the shotel has no animation for when it parries", and it was true from
        // behind: the viewmodel had a PARRY track the whole time, so in first person the blade
        // snapped across and in third person the arm stood still while the weapon in its hand did
        // something. A component in its place and nothing drawing it.
        //
        // **The gate is `reads(PARRY)` and not "is this the sabre", which is the project's own
        // lesson written down.** The parry signal is global -- it rides the player's arm, not the
        // item -- so a gate of "is it the sabre" is a question about the weapon where the useful
        // question is what the profile can do with the signal. It stays true if the sabre profile
        // is ever reused for a second item, and it is a property of the profile that a test reads.
        if (ViewModelDefaults.of(stats.profile()).reads(Channel.PARRY)) {
            pose = pose.plus(BodyPoses.parrying(input.parry()));
        }

        // The two specials that are a body motion of their own. Written here rather than in a
        // helper because `plus` returns a new record and cannot be folded from inside one:
        // the first version of this method called a static `apply(pose, ...)` and threw
        // the result away, so the ropera's lunge and the halberd's sweep silently
        // contributed nothing while the code read as if they did.
        com.cortes.combat.core.weapon.WeaponProfile weapon = stats.profile();
        if (weapon == com.cortes.combat.core.weapon.WeaponProfile.ROPERA) {
            pose = pose.plus(BodyPoses.lunging(input.lunge()));
        } else if (weapon == com.cortes.combat.core.weapon.WeaponProfile.HALBERD) {
            pose = pose.plus(BodyPoses.sweeping(input.sweep()));
        }
        return pose;
    }

    /** Drops a player, so a disconnect does not leak two entries. */
    public static void forget(UUID id) {
        PREVIOUS.remove(id);
        CURRENT.remove(id);
    }

    public static void clear() {
        PREVIOUS.clear();
        CURRENT.clear();
    }

    /** How many players have a pose. For the debug readout. */
    public static int size() {
        return CURRENT.size();
    }
}
