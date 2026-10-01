package com.cortes.combat.special;

import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.sound.CombatSounds;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.state.ParryState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * What a parry does to a hit that arrives inside its window.
 *
 * <p><b>It negates the hit and sends it back.</b> Both halves, and the second one is the whole
 * weapon: a parry that cancelled the damage without returning it would be a block, and the sabre
 * has the shortest reach in the game precisely because it is paying for this. The reflected
 * amount is the one that was going to land, unmodified -- not the sabre's own damage, and not a
 * multiplier, because "returns the damage of the parried hit" is a statement about the same
 * number twice.</p>
 *
 * <p><b>One parry per press.</b> A hit spends the window without restarting the cooldown, so a
 * stream of enemies cannot be parried by holding the button: the first one eats the half second
 * and the rest land on a player who is no longer parrying. The alternative -- leaving the window
 * open for its full twelve ticks -- would let one press answer three attackers and turn the most
 * expensive button in the game into the cheapest.</p>
 *
 * <p><b>{@code LivingIncomingDamageEvent}, not {@code LivingDamageEvent}.</b> Read in
 * {@code LivingEntity#actuallyHurt}'s caller: the event fires while the damage container is being
 * built, which is <em>before</em> the {@code invulnerableTime > 10} check that drops a second hit
 * from the same attacker. A parry that could not see that second hit would be a parry that only
 * worked when the enemy was slow enough to be worth parrying, which is the one case it is not
 * for. Cancelling here also returns before {@code blockUsingShield} and before any of the
 * bookkeeping, so a negated hit leaves no invulnerability frames behind it.</p>
 */
public final class ParryHandler {

    private ParryHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ParryState state = player.getData(ModAttachments.PARRY.get());
        if (!state.isParrying()) {
            return;
        }

        // Someone who cannot be hit back cannot be parried, and reflecting into the environment
        // would be a free way to break a mob with your cover. No attacker, no parry: the hit
        // lands, which is correct, because "parried" is a claim about a duel.
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player) {
            return;
        }

        float amount = event.getAmount();
        event.setAmount(0.0F);
        player.setData(ModAttachments.PARRY.get(), state.consume());
        ModNetworking.sendParryState(player, false, state.cooldownTicks());

        reflect(player, attacker, amount);
    }

    /**
     * The same number, back at the person who threw it.
     *
     * <p>{@code playerAttack} rather than a magic source, so the reflected damage counts as a
     * player attack for everything vanilla hangs off that: enchantments that trigger on it,
     * armour enchantments that reduce it, and the death message. A reflection that skipped all
     * of that would be the strongest hit in the game by exactly the margin nobody could see.</p>
     */
    private static void reflect(ServerPlayer player, LivingEntity attacker, float amount) {
        if (!(player.level() instanceof ServerLevel level) || amount <= 0.0F) {
            return;
        }
        DamageSource source = player.damageSources().playerAttack(player);
        attacker.invulnerableTime = 0;
        if (attacker.hurt(source, amount)) {
            attacker.invulnerableTime = 10;
        }

        // Heard from the attacker, not from the parrier. The thing that just happened to them is
        // the parry, and the parry happened over there -- a parry cue played at your own feet is
        // the sound of something happening to you, which is exactly backwards.
        CombatSounds.play(level, attacker, WeaponProfile.SABRE, ModSounds.Kind.PARRY);
        ModNetworking.sendFeedback(player, CombatFeedback.GUARDED);
    }
}
