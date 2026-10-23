package com.cortes.combat.net;

import com.cortes.combat.platform.CombatPackets;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.guard.GuardStats;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.state.GuardState;
import com.cortes.combat.state.ParryState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server side: tell everyone who can see a player what that player is doing.
 *
 * <h2>Why this is a rate and not a set of edges</h2>
 *
 * <p><b>Because one of the nine fields is a counter that climbs.</b> {@code holdTicks} is how many
 * ticks the attack button has been down, and it goes 1, 2, 3, 4. There is no "edge" in it: a swing
 * at a third of a charge and the same swing at two thirds are the same <em>state</em> as far as an
 * edge detector is concerned and very different poses. Sending only on change would send two
 * packets for a swing and then nothing while the arm came back.</p>
 *
 * <p>So it goes out every tick while something is happening, and every {@link #IDLE_HEARTBEAT}
 * ticks when nothing is. The heartbeat is not politeness: a client that has just started watching a
 * player has no state for them at all, and without one packet per idle player per twenty ticks the
 * first thing anybody sees of a stranger standing still is nothing at all.</p>
 *
 * <h2>Nine fields, read once each</h2>
 *
 * <p>Every one is a fact about the player and none is a picture of a motion. The channels, the
 * rates and the ages stay on the client and are computed there from these, by the same
 * {@link com.cortes.combat.viewmodel.ViewModelMotion} the local player runs &mdash; so the pose is
 * the pose.</p>
 *
 * <p>The item is read twice and it is worth saying why that is not a waste: the weapon's
 * <em>profile</em> is the server's decision and travels, while the item's charge and guard profiles
 * do not, because the receiving client already has the hotbar and can look them up. Sending all
 * three would be sending a data component across the network to arrive next to an identical one.</p>
 */
public final class PlayerStateSync {

    /**
     * Ticks between packets for a player who is doing nothing at all.
     *
     * <p><b>One second, and the reason is a client that just started watching.</b> A player enters
     * somebody's view and there is no packet to arrive, because nothing changed and nothing
     * happened. Without a heartbeat the first thing anybody sees of a stranger standing still is a
     * stranger with no pose data at all, which is not the same as a stranger standing still.</p>
     */
    private static final int IDLE_HEARTBEAT = 20;

    private PlayerStateSync() {
    }

    /**
     * Describe one player to the players who can see them.
     *
     * <p>Called once per server tick for every player. <b>{@code sendToPlayersTrackingEntity} and
     * not the {@code AndSelf} variant</b>: the chunk map's {@code broadcast} iterates
     * {@code seenBy} without the entity's own player, and the one that adds them is a separate
     * method. The local client already has a clock for itself and one that ran twice would draw
     * the swing at double speed for the player doing it and at normal speed for everybody
     * watching.</p>
     */
    public static void sync(ServerPlayer player) {
        PlayerCombatState state = describe(player);
        if (state == null) {
            return;
        }
        CombatPackets.sendToPlayersTrackingEntity(player,
                new PlayerCombatStatePayload(state));
    }

    /**
     * This player's nine fields, or {@code null} if there is nothing worth saying yet.
     *
     * <p>The {@code null} is the heartbeat decision, and it is a question about whether the clock
     * would be <em>doing</em> something &mdash; not whether a field changed. A counter climbing by
     * one is not a change to an edge detector, and a swing that only sends on change is a swing
     * that plays its first and last frame and nothing between.</p>
     */
    private static PlayerCombatState describe(ServerPlayer player) {
        ChargeState charge = CombatState.charge(player);
        GuardState guard = CombatState.guard(player);
        ParryState parry = CombatState.parry(player);

        // **The stagger is the guard item's remaining cooldown, not the weapon's.**
        //
        // Two different cooldowns are in play and they answer different questions: the weapon's
        // drives the strike's length and the guard item's drives the collapse animation. Reading
        // the wrong one makes a player stagger when they are merely unable to attack, which is a
        // guard that breaks for no reason the player can see.
        GuardStats.Resolved resolved = GuardStats.best(player);
        float stagger = cooldownPercent(player, resolved.item());

        boolean lunge = player.hasEffect(com.cortes.combat.registry.ModEffects.LUNGE);

        if (!charge.holding() && !guard.isActive() && !parry.isParrying()
                && stagger <= 0.0F && !lunge) {
            // **Idle.** The heartbeat is the only thing that gets this far, so it goes out on a
            // count rather than on a tick counter that has to live somewhere.
            if ((player.tickCount % IDLE_HEARTBEAT) != 0) {
                return null;
            }
        }

        ItemStack hand = player.getMainHandItem();
        return new PlayerCombatState(
                player.getId(),
                WeaponStats.of(hand).profile(),
                charge.holding(),
                charge.ticks(),
                guard.isActive(),
                stagger,
                parry.isParrying(),
                parry.cooldownTicks(),
                lunge,
                // **The readiness, in the same direction the local player's own bar uses.**
                //
                // `CooldownState#ratio` is one minus the fraction spent, so one means the next
                // swing is allowed -- which is what `CombatStatePayload` sends and what
                // `CooldownBracketLayer` draws. Sending the *remaining* fraction instead would put
                // a weapon in an attacking player's hand back at full readiness the moment they
                // swung, and the strike's follow-through would never play for anybody watching.
                //
                // Without this field the remote's COOLDOWN channel reads as permanently ready,
                // which is the shape of bug this project has already paid for once with the
                // sabre's BREAK track: pinned at a value nothing could move, in every frame of
                // every session, and nobody noticed for a round.
                CombatState.cooldown(player)
                        .ratio(AttackCooldown.intervalFor(player)),
                // **La carga del boton derecho, que hoy solo tiene la pala.**
                //
                // Va como fraccion porque la duracion la decide una regla del encantamiento y no un
                // `ChargeProfile` que el cliente ya tenga: re-derivarla alla seria una segunda copia
                // de "cuanto dura" que se desincroniza en el primer tuneo. Ver el clamp de
                // `PlayerCombatState`, que tiene el argumento entero.
                PlayerStateSync.specialChargeRatio(player));
    }

    /**
     * La carga del boton derecho como fraccion de su tope, o cero si no esta cargando.
     *
     * <p><b>Delega entera en {@code SpecialCharges}, y eso es el punto del refactor.</b> Antes esta
     * cuenta vivia aca y dividia por la constante de la PALA por su nombre: el sincronizador sabia
     * de que arma era la carga. Ahora no sabe ni que existe -- pregunta quien carga y cuanto pide
     * esa carga, y el dia que haya dos esto sigue siendo una linea.</p>
     */
    public static float specialChargeRatio(ServerPlayer player) {
        return com.cortes.combat.special.SpecialCharges.ratio(player);
    }

    /**
     * How much of that item's cooldown is left, as a fraction.
     *
     * <p><b>The fraction that is <em>remaining</em>, and zero when the item is ready.</b> That is
     * what {@code ItemCooldowns#getCooldownPercent} returns and it is the direction the client's
     * {@code staggerPercent} reads: the number goes up while a player is unable to act and back to
     * zero when they can. Inverting it would make the guard collapse animation play when the player
     * is <em>ready</em>.</p>
     */
    private static float cooldownPercent(ServerPlayer player, ItemStack item) {
        if (item.isEmpty()) {
            return 0.0F;
        }
        return player.getCooldowns().getCooldownPercent(item.getItem(), 0.0F);
    }
}