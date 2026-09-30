package com.cortes.combat.client;

import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.net.SwingPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/**
 * The left mouse button, as one press-and-release rather than as a charge and a click.
 *
 * <p>What this replaces: the button used to do two unrelated things at once. The press fired an
 * attack with whatever charge existed, which was always none, and the hold kept accumulating
 * into a buffer that the release threw away. So the charge never applied, ever, and the meter
 * climbed and then snapped back with no payoff. The symptom a player reports is that charged
 * attacks and normal ones "get in each other's way"; the cause is that there was only ever one
 * kind of attack and a second gauge that measured nothing.</p>
 *
 * <p>What it does now: the press opens a swing and the release resolves it, so one press is one
 * attack and the charge is simply however long the button was down. A tap lands as a normal
 * hit; a hold lands charged. The threshold that separates them is
 * {@link com.cortes.combat.attack.AttackTiming#MIN_CHARGE_HOLD_TICKS} and it is global, not per
 * weapon, so the same flick of the wrist means the same thing in every hand.</p>
 *
 * <p>The cost is that a tap resolves on its release rather than its press, so a fast click lands
 * a couple of ticks later than it used to. That is the price of one press being one attack, and
 * it is a couple of ticks: every game with a chargeable button resolves on the release for the
 * same reason.</p>
 *
 * <p>The client reports intent and predicts for the HUD. The charge that counts for damage is
 * the one the server accumulated on its own ticker, so a client cannot claim a hold it did not
 * make.</p>
 */
public final class SwingInputHandler {

    /**
     * What this mod decided to do with the press the client is about to see.
     *
     * <p>Read by {@code MinecraftAttackMixin} so that vanilla never also attacks. The three
     * states are three different answers and collapsing them loses something: {@link #OURS} means
     * a swing is open, {@link #REFUSED} means the click died on a cooldown that was not ready
     * and must stay invisible, and {@link #NOT_OURS} means the press belongs to vanilla, either
     * because the crosshair is on a closer block or because the item is a bow.</p>
     */
    public enum Claim {
        OURS,
        REFUSED,
        NOT_OURS
    }

    /** Local mirror of the charge, for the meter and the viewmodel. Never the source of truth. */
    private static int predictedTicks;

    private static boolean holding;

    /** The item the open swing was started with, so a swap cannot carry the charge across. */
    private static Item openItem;

    /**
     * The tick the next swing becomes legal, computed locally.
     *
     * <p>This exists because the server's cooldown arrives a round trip late, and a double click
     * inside a single tick would otherwise beat the race: the client would see a ready cooldown
     * both times, open two swings, play two swing animations, and the server would honour the
     * first and drop the second. That is exactly the failure this project rejected when it
     * decided a refused swing has to be invisible: an arm that moves and nothing happens reads
     * as dropped input, and the player's answer to that is to click harder.</p>
     *
     * <p>So the client's gate is deliberately <b>stricter</b> than the value it has. It may
     * refuse a swing the server would have allowed, for at most the length of one round trip,
     * and it must never show one the server will refuse.</p>
     */
    private static int readyAtTick;

    private static int tick;

    /**
     * The verdict on the press currently being processed.
     *
     * <p>Deliberately not cleared by whoever reads it. Vanilla can run the attack path more than
     * once in a tick when a double click lands inside one, and every one of those has to be
     * refused: a second vanilla attack on a press the player never made is exactly the free
     * swing this system exists to prevent. It is cleared at the end of the client tick instead,
     * once the whole tick has had its say.</p>
     */
    private static Claim claim = Claim.NOT_OURS;

    private SwingInputHandler() {
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_LEFT) {
            return;
        }
        // The mouse can be clicked while still on the title screen, in a world preview, or
        // while a server is loading, and getConnection() is null in all of those. Sending on
        // a null connection throws and takes the render thread with it.
        if (Minecraft.getInstance().getConnection() == null) {
            return;
        }
        if (event.getAction() == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            press(Minecraft.getInstance());
        } else {
            release(Minecraft.getInstance());
        }
    }

    /**
     * Open a swing, unless this press belongs to something else.
     *
     * <p>The hard cooldown is refused here rather than on the server, and refused silently. Let
     * the click through and let the server reject it was worse than useless: the arm swung, the
     * bar stayed empty, and nothing happened. A swing that visibly happens and does nothing
     * reads as the game dropping input, and the player's response is to click harder. Refused
     * means the click does not visibly do anything either, and the one thing that <em>does</em>
     * move is the bracket bar filling up.</p>
     */
    private static void press(Minecraft mc) {
        claim = Claim.NOT_OURS;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator() || player.isUsingItem()) {
            return;
        }
        // A bow, a crossbow and a trident are not ours. Letting vanilla own the whole press is
        // not a fallback: the cone deliberately does not contain projectiles, so there is no
        // target to find and nothing for this system to decide.
        if (CombatAim.isRanged(player.getMainHandItem())) {
            return;
        }
        if (!ready()) {
            claim = Claim.REFUSED;
            return;
        }
        // An item that cannot charge does not get a charge bar and does not get a wind-up. This
        // is asked before the swing is opened rather than at the release, because a block that
        // fills a charge meter while you hold it is already telling the player it is a weapon.
        if (!WeaponStats.canCharge(player.getMainHandItem())) {
            return;
        }

        Optional<LivingEntity> target = ConeTargeting.findTargetForHeldItem(player);
        if (target.isPresent() ? blockIsNearer(mc, player, target.get()) : isOnBlock(mc)) {
            // Mining. Deciding this by "the crosshair is on a block" alone is what made a wall
            // behind the enemy eat the swing, and deciding it by "there is no target" alone is
            // what makes a zombie behind your back stop you mining. Whichever is in front is
            // what the player was pointing at.
            return;
        }

        holding = true;
        predictedTicks = 0;
        openItem = player.getMainHandItem().getItem();
        claim = Claim.OURS;
        send(new SwingPayload(SwingPayload.NO_TARGET, true));
    }

    /**
     * Whether the crosshair rests on a block that is in front of the target.
     *
     * <p>Measured to the target's centre rather than its nearest point, because this is a
     * question about what the player is looking at rather than a question about the cone: the
     * block is where the crosshair ray actually lands, and the target is a body, and a body has
     * a middle.</p>
     */
    private static boolean blockIsNearer(Minecraft mc, LocalPlayer player, LivingEntity target) {
        if (!isOnBlock(mc)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        return mc.hitResult.getLocation().distanceToSqr(eye)
                <= target.getBoundingBox().getCenter().distanceToSqr(eye);
    }

    private static void release(Minecraft mc) {
        if (!holding) {
            // Nothing was open, so this release is not ours. Vanilla keeps its own handling for
            // bows, for mining, and for a press that died on the cooldown.
            return;
        }
        holding = false;
        LocalPlayer player = mc.player;
        if (player == null) {
            predictedTicks = 0;
            return;
        }

        // The client's own gate, tightened before anything is sent. See readyAtTick.
        readyAtTick = tick + WeaponStats.attackInterval(player);

        int targetId = resolveTargetId(player);
        send(new SwingPayload(targetId, false));

        // Local prediction of the two things the player sees immediately. Neither is the damage:
        // that is the server's, and predicting it would double-apply it, because this mod
        // deliberately clears the target's invulnerability window before hitting.
        //
        // The charge passed to the viewmodel goes through the *same* threshold the server
        // applies, so a tap is a tap on screen as well as in the damage. Passing the raw
        // predicted ratio instead is what made a two tick click draw the whole wind-up briefly
        // and read as a charge that had gone wrong.
        ItemViewModel.onStrike(chargedRatio());
        if (!ItemViewModel.ownsSwing(player.getMainHandItem())) {
            // A weapon whose viewmodel draws its own strike must not also play the vanilla arm
            // animation, or the item moves twice and the two motions land out of step.
            player.swing(InteractionHand.MAIN_HAND);
        }
        predictedTicks = 0;
    }

    /**
     * The entity the swing is aimed at, at the moment it is released.
     *
     * <p>Resolved on the release and not on the press, which is the other half of why the attack
     * resolves there: the target is wherever the player was looking when they committed, so a
     * player who starts a swing, adjusts onto a mob and lets go hits the mob.</p>
     */
    private static int resolveTargetId(LocalPlayer player) {
        return ConeTargeting.findTargetForHeldItem(player)
                .map(net.minecraft.world.entity.Entity::getId)
                .orElse(SwingPayload.NO_TARGET);
    }

    private static boolean isOnBlock(Minecraft mc) {
        return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    /** Both gates: the server's own answer, and the local latch that is never behind it. */
    private static boolean ready() {
        return tick >= readyAtTick && ClientCombatState.cooldownRatio() >= 1.0F;
    }

    private static void send(SwingPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /**
     * The verdict on this press, for the mixin that stops vanilla from also attacking.
     *
     * <p>Anything other than {@link Claim#NOT_OURS} cancels vanilla, including a refused press:
     * that one has to stay invisible, and the only way to keep it invisible is to stop it there
     * too.</p>
     */
    public static Claim claim() {
        return claim;
    }

    /** Called once per client tick, after the attack path has run. */
    public static void endTick() {
        claim = Claim.NOT_OURS;
    }

    public static boolean holding() {
        return holding;
    }

    public static int predictedTicks() {
        return predictedTicks;
    }

    /**
     * One client tick of prediction, and the two things that can invalidate an open swing.
     *
     * <p>Advancing the charge is the obvious job. The other two are here because an open swing
     * outlives the button in ways that are each a hole on their own.</p>
     */
    public static void tick() {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            if (holding) {
                holding = false;
                ItemViewModel.onCancel();
            }
            return;
        }
        if (holding && player.getMainHandItem().getItem() != openItem) {
            // The swing was opened with a different weapon in hand, which happens when a player
            // scrolls the hotbar mid hold. Resolving it as a release is the only honest option:
            // the cooldown is paid, because the player did commit to a swing, and the charge is
            // closed. Left open, a long hold on a slow weapon becomes a free fully charged swing
            // on a fast one, because the release reads the charge in ticks and the ticks were
            // counted against the other weapon's much longer scale.
            release(mc);
            return;
        }
        if (holding) {
            predictedTicks++;
        }
        ItemViewModel.tick(holding, predictedRatio(player));
    }

    /** The local charge as a ratio, which is what the viewmodel and the meter read. */
    public static float predictedRatio() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0F : predictedRatio(player);
    }

    /**
     * The charge this swing will actually be paid with, threshold applied.
     *
     * <p>The same {@link AttackTiming#chargeRatioFor} the server runs, on the client's own tick
     * count. It is a prediction and the server's count wins, but the threshold is a fixed number
     * that both sides evaluate the same way, so the classification agrees rather than the value:
     * a click under four ticks is a tap on both sides no matter whose count is off by one.</p>
     */
    public static float chargedRatio() {
        return AttackTiming.chargeRatioFor(predictedTicks, predictedRatio());
    }

    private static float predictedRatio(LocalPlayer player) {
        if (!holding || predictedTicks <= 0) {
            return 0.0F;
        }
        int full = WeaponStats.of(player.getMainHandItem()).charge().fullChargeTicks();
        if (full <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) predictedTicks / (float) full);
    }

    /** Called when the server tells us the real value, so lag does not leave the bar lying. */
    public static void acceptServerState(float chargeRatio, int fullChargeTicks) {
        if (fullChargeTicks <= 0) {
            predictedTicks = 0;
            return;
        }
        // Snap toward the server value rather than overwriting, so a single late packet does
        // not make the indicator jump backwards.
        int serverTicks = Math.round(chargeRatio * fullChargeTicks);
        predictedTicks = Math.max(serverTicks, predictedTicks - 2);
        if (serverTicks < predictedTicks) {
            predictedTicks = serverTicks;
        }
    }

    /** Test hook and logout path: forget the open swing rather than leaving it dangling. */
    public static void reset() {
        holding = false;
        predictedTicks = 0;
        openItem = null;
        claim = Claim.NOT_OURS;
        ItemViewModel.onCancel();
    }
}
