package com.cortes.combat.attack;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.FallDeferral;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The maza's fall damage is held for ten ticks, so a smash landed on the way down cancels it.
 *
 * <h2>The vanilla idea, and the window this adds</h2>
 *
 * <p>Vanilla's maza already rewards the fall twice: the damage <em>is</em> the fall, and a landed
 * hit calls {@code resetFallDistance()} so the drop costs nothing. This mod replaces the attack
 * flow, so that call is gone -- and the design's version is more legible than vanilla's: the fall
 * damage is not refused, it is <b>postponed ten ticks</b>. Land the smash inside the window and it
 * is cancelled; miss and it lands on you anyway.</p>
 *
 * <p>That is the difference between a grace and a gift. Refusing the damage outright would make a
 * maza in hand a permanent immunity to falling, which is not what the mechanic is for.</p>
 *
 * <h2>Why the window is a number and not a feel</h2>
 *
 * <p>Eight ticks is two fifths of a second, and the playtest moved it down from ten. Ten read as
 * generous enough that the grace was noticed before the fall was: the window is meant to cover the
 * swing the fall was paying for, and ten ticks is longer than the maza's own twenty four tick
 * interval by enough that a player could land, walk a step and still be saved. Eight keeps the
 * hit the fall was already going into and drops the step.</p>
 *
 * <h2>Only with the maza</h2>
 *
 * <p>Asked of the held profile and not of the item, so the day a second weapon reads a fall it is
 * one condition and not a second copy of this class. Everything else takes its fall damage the
 * instant it lands, which is the whole point of it being a window and not a rule.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class FallCoyote {

    /** How long the fall is held, in ticks. Two fifths of a second. */
    public static final int WINDOW_TICKS = 8;

    private FallCoyote() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!event.getSource().is(DamageTypes.FALL)) {
            return;
        }
        FallDeferral held = player.getData(ModAttachments.FALL_DEFERRAL.get());
        // The second pass: this is the deferred damage being applied and must go through.
        if (held.applying()) {
            return;
        }
        if (!holdsMace(player)) {
            return;
        }
        // **Cancelled in both cases, and the window is not restarted when one is already open.**
        // Vanilla fires this once per landing, but a re-entrant path or a second fall tick would
        // otherwise keep pushing the deadline forward, which is an infinite grace by accident.
        event.setCanceled(true);
        if (held.pending()) {
            return;
        }
        player.setData(ModAttachments.FALL_DEFERRAL.get(),
                new FallDeferral(event.getAmount(),
                        player.level().getGameTime() + WINDOW_TICKS, false));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        FallDeferral held = player.getData(ModAttachments.FALL_DEFERRAL.get());
        if (!held.pending() || player.level().getGameTime() < held.dueTick()) {
            return;
        }
        // Marked before the hurt, so the handler above recognises its own second pass, and cleared
        // after it. See FallDeferral.applying for why this is state and not a static.
        player.setData(ModAttachments.FALL_DEFERRAL.get(),
                new FallDeferral(held.damage(), held.dueTick(), true));
        player.hurt(player.damageSources().fall(), held.damage());
        player.setData(ModAttachments.FALL_DEFERRAL.get(), FallDeferral.NONE);
    }

    /**
     * A mace hit landed, so the fall it was carrying is cancelled -- <b>always</b>.
     *
     * <h2>The bug this fixes, and it is the whole mechanic</h2>
     *
     * <p>The playtest: "el daño de caída es inconsistente, a veces me lo elimina y a veces no, y la
     * idea es que SIEMPRE QUE DES A UN OBJETIVO TE SALVA DEL DAÑO DE CAÍDA". Half of it was right
     * and half of it was missing, and the missing half is the one vanilla has.</p>
     *
     * <p>Vanilla's {@code MaceItem} calls {@code attacker.resetFallDistance()} on every smash, so
     * <b>hitting on the way down means there is no fall left to take</b> -- the landing is simply a
     * landing. That call lives in the item's own {@code hurtEnemy}, which this mod's replaced attack
     * flow never runs, so all that was left was the coyote window: a hit <em>after</em> landing
     * cancelled the deferred damage, and a hit <em>before</em> landing did nothing at all, because
     * the fall had not been deferred yet. Hence "a veces" -- and the split is exactly mid-air versus
     * after-landing.</p>
     *
     * <p>So both halves are here now, and they cover the two moments a hit can land:</p>
     * <ul>
     *   <li><b>In the air</b> -- the fall is reset, so there is nothing to take. Vanilla's own.</li>
     *   <li><b>On the ground, inside the window</b> -- the deferred damage is cancelled. The margin
     *       for error, and the reason {@link #WINDOW_TICKS} exists.</li>
     * </ul>
     *
     * <p>Called from {@code CombatEngine} <b>after</b> the swing is resolved and after the smash's
     * own sound has read the fall, and not from {@code onHit}: resetting it earlier would zero the
     * number the anvil's pitch is chosen from, and the loudest hit in the game would sound like the
     * quietest.</p>
     */
    public static void landed(Player player) {
        // Vanilla's own, dropped by the replaced flow. See the javadoc: this is the half that makes
        // "hitting on the way down" save you before you have even landed.
        player.resetFallDistance();
        if (player.getData(ModAttachments.FALL_DEFERRAL.get()).pending()) {
            player.setData(ModAttachments.FALL_DEFERRAL.get(), FallDeferral.NONE);
        }
    }

    private static boolean holdsMace(Player player) {
        ItemStack held = player.getMainHandItem();
        return WeaponStats.of(held).profile() == WeaponProfile.MACE;
    }
}
