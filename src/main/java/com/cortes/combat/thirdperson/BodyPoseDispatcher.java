package com.cortes.combat.thirdperson;

import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.client.ItemViewModel;
import com.cortes.combat.client.RemotePoseStates;
import com.cortes.combat.viewmodel.ViewModelDefaults;
import com.cortes.combat.pose.PoseSolver;
import com.cortes.combat.pose.PoseState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
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
 * <p><b>Still only the local player is asked, and the reason has changed.</b> Other players are
 * animated by vanilla from the server's {@code player.swing()}, so the charge and the guard are
 * not visible on them. The old version of this file called that deliberate and quoted the choice;
 * it was not a choice, it was a consequence of the pose being computed from things that only the
 * local player has.</p>
 *
 * <p><b>That consequence is gone.</b> {@link #poseFor} now asks the world four questions -- the
 * weapon, its charge, whether the main hand is a vanilla shield, and the guard -- and hands them to
 * {@link PoseSolver}, which is a pure function of a value. So the pose can be computed for anybody
 * whose state has been received, and what is still missing is the payload that receives it, not the
 * arithmetic. The {@code Map} was already keyed by {@code UUID} for exactly that.</p>
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
     * <p><b>Two sources, and which one is decided by asking whether this is us.</b> The local
     * player is posed from {@code ItemViewModel}'s own frame, which is the client's prediction and
     * is right up to the tick. Everybody else is posed from what the server told us about them, run
     * through the same solver by the same clock, so the pose is the pose and only the lag differs.
     *
     * <p>Before this there was one source and the other players had none, which is why the javadoc
     * of this class used to quote the limitation as a decision.</p>
     *
     * @param partialTick 0 at the start of the tick, approaching 1 at the end
     */
    public static BodyPose snapshot(AbstractClientPlayer player, float partialTick) {
        if (player == null) {
            return BodyPose.NONE;
        }
        if (player != Minecraft.getInstance().player) {
            return RemotePoseStates.snapshot(player.getId(), partialTick);
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

        // **Four reads out of the world, and then nothing.**
        //
        // The main hand's weapon and its charge profile, whether the main hand is a vanilla
        // shield, and the guard the two hands together are worth. Those are the four facts that
        // cannot be derived, and each one is asked of the game exactly once.
        //
        // Everything after this -- which swing, whether the weapon is shouldered, whether the
        // parry reaches the body, whether the guard is ours to write -- is `PoseSolver's, and it
        // is there because it was testable nowhere: this method used to hold all of it inline,
        // which meant a hundred lines of gating no test could reach and the only verification was
        // a playtest. The parry was already missed once exactly that way, and a gate left out of
        // a rewrite renders a weapon whose arm does not move without failing anything.
        return PoseSolver.body(new PoseState(
                stats.profile(),
                stats.charge(),
                GuardStats.best(local).profile(),
                stack.is(Items.SHIELD),
                ItemViewModel.frame(),
                ViewModelDefaults.of(stats.profile())));
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
