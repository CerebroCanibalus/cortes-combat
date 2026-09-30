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
 * <p>Every vanilla block is turned off first, always. This system is not a layer on top of the
 * shield, it <em>is</em> the shield, and leaving the original running would mean a hit got
 * blocked for free without spending a point.</p>
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

    /** The guard the player's items are currently worth. Zero means neither hand can guard. */
    public static GuardProfile bestFor(LivingEntity entity) {
        return GuardStats.ofBest(GuardStats.LivingItems.of(entity));
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

        GuardProfile profile = bestFor(player);
        if (!profile.canGuard()) {
            return;
        }

        GuardState guard = player.getData(ModAttachments.GUARD_STAMINA.get());
        if (!guard.isActive() || !blocks(event.getDamageSource(), player, profile)) {
            return;
        }

        GuardState after = guard.block(profile.stunTicks());
        player.setData(ModAttachments.GUARD_STAMINA.get(), after);

        event.setBlocked(true);
        // Deliberately NOT setBlockedDamage(0). Zero is the full incoming damage in the
        // container's eyes, and writing it there is what made the first version block nothing.
        // No durability cost either: the shield is not an item that wears out from doing the
        // job it exists for.
        event.setShieldDamage(0.0F);

        if (after.isStunned()) {
            // The guard just broke. Silence here reads as the game ignoring the click, so the
            // player has to be told by something other than the number going down.
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
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
