package com.cortes.combat.attack;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.state.FallDeferral;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The 1.20.1 wiring of the maza's held fall. Same window, older events.
 *
 * <p>Fall damage cancels on {@code LivingAttackEvent} (pre-gate, like the parry and the
 * lunge: a fall that arrives while i-frames are up is still a fall the maza is holding,
 * and the deferral must see it). The tick runs on {@code PlayerTickEvent} gated to
 * {@code END}, reading {@code event.player}. The two halves -- reset in the air,
 * deferred on the ground -- are untouched. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class FallCoyote {

    /** How long the fall is held, in ticks. Two fifths of a second. */
    public static final int WINDOW_TICKS = 8;

    private FallCoyote() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!event.getSource().is(DamageTypes.FALL)) {
            return;
        }
        FallDeferral held = CombatState.fallDeferral(player);
        // The second pass: this is the deferred damage being applied and must go through.
        if (held.applying()) {
            return;
        }
        if (!holdsMace(player)) {
            return;
        }
        // **Cancelled in both cases, and the window is not restarted when one is already open.**
        event.setCanceled(true);
        if (held.pending()) {
            return;
        }
        CombatState.setFallDeferral(player,
                new FallDeferral(event.getAmount(),
                        player.level().getGameTime() + WINDOW_TICKS, false));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        FallDeferral held = CombatState.fallDeferral(player);
        if (!held.pending() || player.level().getGameTime() < held.dueTick()) {
            return;
        }
        // Marked before the hurt, so the handler above recognises its own second pass, and cleared
        // after it. See FallDeferral.applying for why this is state and not a static.
        CombatState.setFallDeferral(player,
                new FallDeferral(held.damage(), held.dueTick(), true));
        player.hurt(player.damageSources().fall(), held.damage());
        CombatState.setFallDeferral(player, FallDeferral.NONE);
    }

    /**
     * A mace hit landed, so the fall it was carrying is cancelled -- <b>always</b>.
     *
     * <p>In the air the fall is reset (vanilla's own); on the ground inside the window the
     * deferred damage is cancelled. Called from {@code CombatEngine} after the swing is
     * resolved, so the anvil's pitch still reads the fall. See the shared twin.</p>
     */
    public static void landed(Player player) {
        player.resetFallDistance();
        if (CombatState.fallDeferral(player).pending()) {
            CombatState.setFallDeferral(player, FallDeferral.NONE);
        }
    }

    private static boolean holdsMace(Player player) {
        ItemStack held = player.getMainHandItem();
        return WeaponStats.of(held).profile() == WeaponProfile.MACE;
    }
}
