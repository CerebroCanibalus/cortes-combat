package com.cortes.combat.guard;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.GuardState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The guard: what blocks, what it costs, and when it refuses.
 *
 * <p>It hooks {@link LivingShieldBlockEvent} rather than adding a mixin, because that event
 * already is the decision point. NeoForge hands it over with a yes or no that vanilla computed,
 * and {@code getBlocked()} is what the game then obeys.</p>
 *
 * <p><b>The one thing to get right here:</b> the event does not reduce anything. It only
 * reports how much <em>was</em> prevented, and the actual subtraction happens afterwards in
 * {@code DamageContainer#setBlockedDamage}, which does {@code newDamage -= blockedDamage}.
 * Writing zero into it therefore means "nothing was blocked" and lets the whole hit through.
 * The default value is already the full damage, which is what a full block is, so the correct
 * move is to leave it alone.</p>
 *
 * <p><b>The stagger is vanilla's, not ours.</b> Minecraft already has exactly this: an axe
 * striking a raised shield puts the shield on a five second item cooldown, and the vanilla HUD
 * already draws that cooldown. So there is no stun counter in {@link GuardState} and nothing to
 * draw ourselves. Exhaustion adds a cooldown, an axe hit adds a cooldown, and both are read
 * back through {@code isOnCooldown}. {@code ServerItemCooldowns} sends the packet, so this
 * arrives at the client already synced and already rendered.</p>
 */
public final class GuardHandler {

    private GuardHandler() {
    }

    /**
     * Server side of the guard input.
     *
     * <p>A raise is refused while the guard has no points. That is not a special case for the
     * exhausted case: it is the same rule as the one that stops an empty guard from blocking,
     * applied one step earlier. It is also what stops a deadlock, because otherwise a player
     * who held the button down on a fresh shield would be holding an empty guard, would not be
     * regenerating (holding costs the regen) and would never become ready.</p>
     */
    public static void setGuard(ServerPlayer player, boolean raising) {
        GuardState current = player.getData(ModAttachments.GUARD_STAMINA.get());
        // The playtest's order rule: you cover first, you eat after. A raise that arrives while
        // the player is already chewing is refused, which is what makes the order a fact instead
        // of something that happens to fall out of which hand holds what. The guard and the meal
        // can still run at the same time -- they start on the same press and the guard resolves
        // instantly while the food takes 32 ticks -- and this only refuses the second press.
        //
        // One predicate, shared with the item-use refusal below, because "may the guard go up" has
        // to have exactly one answer on the server.
        if (raising && !guardIsUsable(player)) {
            return;
        }
        GuardState next = raising ? current.raise() : current.lower();
        if (next != current) {
            player.setData(ModAttachments.GUARD_STAMINA.get(), next);
        }
        if (raising && next.held() && !current.held()) {
            // Covering abandons a swing that was already open. Same shape as the menu case and
            // the same reason: the player pressed attack, then chose to cover, and the release
            // would land a hit against whatever happened to be in the cone in between. Clearing
            // the charge is enough -- CombatEngine#resolveSwing stops when nothing is open, so
            // this costs no cooldown and the abandoned swing simply never was.
            AttackCooldown.endCharge(player);
        }
    }

    /**
     * Whether the guard is up, which is what stops an attack.
     *
     * <p>The playtest called attacking while covered "un gravísimo error", and the reason it
     * matters is not that it looks wrong. It removes the only cost the guard has. A guard that
     * lets you swing is not a stance, it is a passive buff you hold with a button, and every
     * other decision in the design -- points that are only spent by a blocked hit, a stagger
     * that costs you two seconds -- is priced against a player who has to choose between covering
     * and hitting.</p>
     *
     * <p>It is deliberately the strongest guard in the hands, not a per-item case. A shield in
     * the off hand with a sword in the main one would otherwise block and swing for free, which
     * makes the off hand strictly better than the sword and quietly dissolves the unified guard
     * the design is built on.</p>
     */
    public static boolean isGuarding(Player player) {
        return player.getData(ModAttachments.GUARD_STAMINA.get()).isActive();
    }

    /** The guard the player's items are currently worth, and which item is providing it. */
    public static GuardStats.Resolved bestFor(LivingEntity entity) {
        return GuardStats.best(entity);
    }

    /**
     * Whether the guard is up right now, from the server's own numbers and nothing else.
     *
     * <p>Shared by the two things that have to agree on it: refusing the raise in
     * {@link #setGuard} and refusing the item use below. Two copies of this question is how a
     * client and a server end up with two different opinions about the same tick.</p>
     */
    public static boolean guardIsUsable(Player player) {
        GuardState state = player.getData(ModAttachments.GUARD_STAMINA.get());
        return !state.isExhausted() && !isEating(player);
    }

    /**
     * The server's half of "the shield does not go up without points".
     *
     * <p>Fires on both sides in NeoForge, and only the server branch is here: the client needs
     * the synced stamina rather than the live one, and common code cannot reach it. Both sides
     * run {@link GuardRules#mayStartItemUse}, so the rule is written once and only the number it
     * reads differs.</p>
     *
     * <p>Nothing here is a special case for the empty guard. It is the same refusal as an
     * exhausted one, arriving through vanilla's item use instead of through our own input.</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!GuardRules.mayStartItemUse(GuardStats.of(stack).canGuard(), guardIsUsable(player))) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether the player is halfway through eating or drinking.
     *
     * <p>Ask the item's <em>use animation</em> rather than whether the player is using an item at
     * all. "Using an item" is true for a shield that is blocking, and gating the guard on that
     * would deadlock: a shield would prevent its own guard, and the player could never raise it.
     * The three cases the game itself distinguishes are exactly the three this needs -- {@code
     * BLOCK} is the shield, {@code EAT} and {@code DRINK} are the meal -- so the distinction is
     * read off the game rather than reinvented here.</p>
     *
     * <p>There is no {@code Item#isEdible} in 1.21.1 to ask instead. Verified with {@code javap}
     * against the compiled jar, like everything else in this comment block.</p>
     */
    public static boolean isEating(LivingEntity entity) {
        if (!entity.isUsingItem()) {
            return false;
        }
        UseAnim anim = entity.getUseItem().getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    /** Whether the item being used is a blocking one, which is the only use a guard cancels. */
    public static boolean usedItemBlocks(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getUseAnimation() == UseAnim.BLOCK;
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity entity = event.getEntity();
        // Ours only, and only the server decides anything.
        if (!(entity instanceof Player player) || player.level().isClientSide) {
            return;
        }

        // Kill the vanilla block first, always. Whatever vanilla thought, this system answers.
        event.setBlocked(false);

        GuardStats.Resolved guard = bestFor(player);
        if (!guard.canGuard()) {
            return;
        }

        // A staggered guard. Vanilla's own axe stagger lands here for the shield, and ours
        // lands here for the sword, through the same check and the same cooldown.
        if (player.getCooldowns().isOnCooldown(guard.item().getItem())) {
            return;
        }

        GuardState state = player.getData(ModAttachments.GUARD_STAMINA.get());
        if (!state.isActive() || !blocks(event.getDamageSource(), player, guard.profile())) {
            return;
        }

        GuardState after = state.block(guard.profile().regenTicks());
        player.setData(ModAttachments.GUARD_STAMINA.get(), after);

        event.setBlocked(true);
        // Deliberately NOT setBlockedDamage(0). Zero is the full incoming damage in the
        // container's eyes, and writing it there is what made the first version block nothing.
        // No durability cost either: the shield is not an item that wears out from doing the
        // job it exists for.
        event.setShieldDamage(0.0F);

        // A block is the one event that gets no crosshair growth. The player did not throw
        // anything, so making their crosshair jump would claim credit for a swing they never
        // took; the colour carries it on its own.
        if (player instanceof ServerPlayer server) {
            com.cortes.combat.net.ModNetworking.sendFeedback(server, CombatFeedback.GUARDED);
        }

        if (after.isExhausted()) {
            stagger(player, guard, guard.profile().stunTicks());
        }
        applyAxeStagger(player, guard, event.getDamageSource());
    }

    /**
     * Puts the guard item on cooldown and says so out loud.
     *
     * <p>The sound is not decoration. Without it the only feedback is a number going to zero,
     * which reads as the game ignoring the click, and the player has no way of knowing that the
     * two seconds they are now helpless is a rule and not a bug.</p>
     */
    private static void stagger(Player player, GuardStats.Resolved guard, int ticks) {
        player.getCooldowns().addCooldown(guard.item().getItem(), Math.max(1, ticks));
        // A broken sword and a broken shield are different noises, and the game's own is a wooden
        // shield cracking, which is the wrong material for a blade going down.
        //
        // The guard is one mechanic covering two items, so the sound is chosen by what was actually
        // holding, read from the same resolved pair the cooldown went on. One guard with two
        // sounds, rather than one sound that has to sound right for both.
        boolean shield = guard.item().is(net.minecraft.world.item.Items.SHIELD);
        if (shield) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
            return;
        }
        var event = com.cortes.combat.sound.CombatSounds.event(
                com.cortes.combat.sound.ModSounds.of(null,
                        com.cortes.combat.sound.ModSounds.Kind.GUARD_BROKEN));
        if (event != null) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    event, SoundSource.PLAYERS, 1.0F, 0.9F);
        }
    }

    /**
     * The design's "an axe stuns in one hit", for the sword.
     *
     * <p>It reuses vanilla's own predicate, {@code canDisableShield}, so the list of weapons
     * that do this is whatever the game says it is and not a second list here that drifts. For
     * the shield vanilla has already done this by the time we are called, and adding the
     * cooldown again is idempotent, so the same call is correct for both.</p>
     */
    private static void applyAxeStagger(Player player, GuardStats.Resolved guard,
                                        DamageSource source) {
        Entity origin = source.getEntity();
        if (!(origin instanceof LivingEntity attacker)) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.canDisableShield(guard.item(), attacker, player)) {
            stagger(player, guard, guard.profile().stunTicks());
        }
    }

    /**
     * Whether this particular damage source is blockable at all, and from the front.
     *
     * <p>Explanations and falls are refused on purpose, and for the design's own reason: a
     * guard is a decision about a fight, and environmental damage is not a fight.</p>
     */
    private static boolean blocks(DamageSource source, Player player, GuardProfile profile) {
        // The design's one exception. An explosion is not something you can face.
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        // Whatever vanilla already considers unshieldable stays that way.
        if (source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return false;
        }
        Entity origin = source.getEntity();
        if (origin == null || origin == player) {
            // Fall damage, fire, drowning, starvation: the "source" is the victim, or nobody.
            return false;
        }
        Vec3 position = source.getSourcePosition();
        if (position == null) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 self = player.position();
        return GuardRules.isFromFront(
                self.x, self.y, self.z,
                look.x, look.y, look.z,
                position.x, position.y, position.z,
                profile.safeArc());
    }
}
