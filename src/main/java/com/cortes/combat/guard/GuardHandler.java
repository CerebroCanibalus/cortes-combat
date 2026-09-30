package com.cortes.combat.guard;

import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.GuardState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * The guard: what blocks, what it costs, and when it refuses.
 *
 * <p>It hooks {@link LivingShieldBlockEvent} rather than adding a mixin, because that event
 * already is the decision point. NeoForge hands it over with a yes or no that vanilla computed,
 * and {@code getBlocked()} is what the game then obeys. Replacing that boolean is the whole
 * job, and it is also why the sword works: the event fires whether or not a shield is up, so
 * one code path covers both items.</p>
 *
 * <p>Every vanilla block is always turned off first. This system is not a layer on top of the
 * shield, it <em>is</em> the shield, and leaving the original running would mean a hit got
 * blocked for free without spending a point of stamina.</p>
 */
public final class GuardHandler {

    private GuardHandler() {
    }

    /** Server side of the guard input. Called by the payload handler. */
    public static void setGuard(ServerPlayer player, boolean holding) {
        GuardState current = player.getData(ModAttachments.GUARD_STAMINA.get());
        if (holding == current.held()) {
            return;
        }
        player.setData(ModAttachments.GUARD_STAMINA.get(),
                holding ? current.raise() : current.lower());
    }

    /**
     * Whether an item can be held up as a guard.
     *
     * <p>Shield or sword, in either hand. That is the design's unified stamina: one pool, one
     * cost, and covering with the sword costs nothing extra. It also falls out of the rule
     * rather than being a case, because stamina is only ever spent on a blocked hit.</p>
     */
    public static boolean canGuard(LivingEntity entity) {
        return canGuardItem(entity.getMainHandItem()) || canGuardItem(entity.getOffhandItem());
    }

    public static boolean canGuardItem(ItemStack stack) {
        return stack.is(Items.SHIELD) || stack.getItem() instanceof SwordItem;
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity entity = event.getEntity();
        // Ours only, and only the server decides anything.
        if (!(entity instanceof Player player) || player.level().isClientSide) {
            return;
        }

        // Kill the vanilla block first, always. Whatever vanilla thought, this system is the
        // one that answers, and a leftover vanilla block would be a free hit.
        event.setBlocked(false);

        GuardState guard = player.getData(ModAttachments.GUARD_STAMINA.get());
        if (!guard.isActive() || !canGuard(player)) {
            return;
        }

        DamageSource source = event.getDamageSource();
        if (!blocks(source, player)) {
            return;
        }

        GuardState after = guard.block();
        player.setData(ModAttachments.GUARD_STAMINA.get(), after);

        // Full reduction, and no durability cost: the shield is not an item that wears out from
        // doing the job it exists for.
        event.setBlocked(true);
        event.setBlockedDamage(0.0F);
        event.setShieldDamage(0.0F);
    }

    /**
     * Whether this particular damage source is blockable at all, and from the front.
     *
     * <p>Explanations and falls are refused on purpose, and the reason is the same as the
     * design's own reason for not letting a shield cover from behind: a guard is a decision
     * about a fight, and environmental damage is not a fight.</p>
     */
    private static boolean blocks(DamageSource source, Player player) {
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
                GuardRules.GUARD_ARC_DEGREES);
    }
}
