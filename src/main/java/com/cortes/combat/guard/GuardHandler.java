package com.cortes.combat.guard;

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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

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

    /** Server side of the guard input. Called by the payload handler. */
    public static void setGuard(ServerPlayer player, boolean raising) {
        GuardState current = player.getData(ModAttachments.GUARD_STAMINA.get());
        GuardState next = raising ? current.raise() : current.lower();
        if (next != current) {
            player.setData(ModAttachments.GUARD_STAMINA.get(), next);
        }
    }

    /** The guard the player's items are currently worth, and which item is providing it. */
    public static GuardStats.Resolved bestFor(LivingEntity entity) {
        return GuardStats.best(entity);
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

        GuardState after = state.block();
        player.setData(ModAttachments.GUARD_STAMINA.get(), after);

        event.setBlocked(true);
        // Deliberately NOT setBlockedDamage(0). Zero is the full incoming damage in the
        // container's eyes, and writing it there is what made the first version block nothing.
        // No durability cost either: the shield is not an item that wears out from doing the
        // job it exists for.
        event.setShieldDamage(0.0F);

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
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
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
