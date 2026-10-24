package com.cortes.combat.special;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.effect.DrainedEffect;
import com.cortes.combat.effect.VampiricEffect;
import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.enchant.CombatEnchantments;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.registry.ModEffects;
import com.cortes.combat.sound.CombatSounds;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.state.ParryState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The 1.20.1 wiring of the parry. Same duel, older event.
 *
 * <p><b>{@code LivingAttackEvent}, cancelled, instead of the 1.21.1 incoming event zeroed.</b>
 * It is the same moment by the spike's verdict: the top of {@code hurt()}, cancelable, before
 * the i-frame gate -- so the parry still sees the second hit of a fast attacker, and a negated
 * hit still leaves no invulnerability frames behind it. Everything below the gate (the three
 * printed numbers, the reflection, Capacitor, the discard, Desvio Vampirico) is identical.
 * (⌒‿⌒)</p>
 */
public final class ParryHandler {

    private ParryHandler() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ParryState state = CombatState.parry(player);
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

        // **Y el golpe que vino por el aire, nombrado.** `getEntity()` de un proyectil es el
        // ARQUERO -- la flecha es `getDirectEntity()` -- y el `instanceof LivingEntity` de arriba
        // lo aceptaba sin preguntar nada. Ver `ParrySource`, y más abajo está lo que se hace con
        // la respuesta.
        Entity direct = event.getSource().getDirectEntity();
        boolean throughTheAir = ParrySource.cameThroughTheAir(direct != null, direct == attacker);

        // **The three numbers that decide why a parry does not answer.** See the shared twin:
        // who attacked, from where, and how much arrived. One line at debug, only on the tick
        // a parry is open, which is at most six lines per press.
        org.slf4j.LoggerFactory.getLogger("cortescombat").debug(
                "[parry] golpe={} atacante={} posicion={} cantidad={} bloqueando={} porElAire={}",
                event.getSource().getMsgId(),
                attacker.getName().getString(),
                event.getSource().getSourcePosition(),
                amount,
                player.isBlocking(),
                throughTheAir);

        event.setCanceled(true);
        CombatState.setParry(player, state.consume());
        ModNetworking.sendParryState(player, false, state.cooldownTicks());

        // **Capacitor: lo que vuelve, ahora con un rayo encima.** Va aca -- donde el parry ya
        // decidio que si contesta -- y no en la rama del descarte. Y el daño es una SUMA: el
        // parry ya devolvia el proyectil antes de este encantamiento.
        if (throughTheAir) {
            int level = CombatEnchantments.level(player.level().registryAccess(),
                    player.getMainHandItem(), CombatEnchantments.CAPACITOR);
            if (level > 0) {
                attacker.hurt(player.damageSources().playerAttack(player),
                        CombatEnchantRules.capacitorDamage(level) + amount);
                // Quemar cuatro segundos: es lo que convierte una devolucion en una advertencia.
                // `attacker` ya es un LivingEntity por el gate de arriba; repetirlo con
                // `instanceof` no compila en JDK 17 (patron dominado) aunque el 21 lo deje pasar.
                attacker.setRemainingFireTicks(Math.max(attacker.getRemainingFireTicks(),
                        CombatEnchantRules.CAPACITOR_FIRE_TICKS));
                if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
                    // **La linea entera, del jugador al cuerpo**, y no un penacho en el objetivo.
                    com.cortes.combat.particle.LightningParticles.zigzag(server,
                            player.getEyePosition(),
                            new net.minecraft.world.phys.Vec3(attacker.getX(),
                                    attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ()));
                }
            }
        }

        // **Lo que se hace con la respuesta: el proyectil se descarta.** Un golpe que llegó por
        // el aire se anuló y volvió, pero la flecha seguía clavada en el jugador que la parrió.
        // Descartarla es lo que hace que "lo devolví" se vea además de leerse.
        if (throughTheAir) {
            direct.discard();
        }

        reflect(player, attacker, amount);
    }

    /**
     * The same number, back at the person who threw it.
     *
     * <p>{@code playerAttack} rather than a magic source, so the reflected damage counts as a
     * player attack for everything vanilla hangs off that.</p>
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

        // Heard from the attacker, not from the parrier.
        CombatSounds.play(level, attacker, WeaponProfile.SABRE, ModSounds.Kind.PARRY);
        ModNetworking.sendFeedback(player, CombatFeedback.GUARDED);

        // **Y el hombro del que desvio.** Igual que el sonido: lo que acaba de pasar paso alla,
        // y la distancia lo dice.
        com.cortes.combat.fx.Shake.to(player, attacker, com.cortes.combat.fx.ShakeKind.PARRY);

        // **Desvio Vampirico: el parry roba vida maxima, y CADA parry suma un corazon.**
        // Ver el gemelo compartido para la aritmética completa: el nivel es el TOPE del
        // acumulado, el enemigo pierde UNO por parry, y la vida actual viene con el techo.
        int vampiric = CombatEnchantments.level(player.level().registryAccess(),
                player.getMainHandItem(), CombatEnchantments.VAMPIRICO);
        if (vampiric > 0) {
            MobEffectInstance current = player.getEffect(ModEffects.vampiric());
            int alreadyStolen = current == null ? 0 : current.getAmplifier() + 1;
            int stolen = CombatEnchantRules.vampiricoStolen(alreadyStolen, vampiric);
            // El piso se pregunta ANTES de aplicar nada: bajarle el techo a un cuerpo le baja la
            // vida actual y no hay forma de evitarlo, asi que lo unico que se puede garantizar
            // es que no mate.
            if (CombatEnchantRules.canDrain(attacker.getMaxHealth(), 0)) {
                player.addEffect(new MobEffectInstance(ModEffects.vampiric(),
                        VampiricEffect.DURATION_TICKS, stolen - 1, false, true));
                attacker.addEffect(new MobEffectInstance(ModEffects.drained(),
                        DrainedEffect.DURATION_TICKS, 0, false, true));
                int gained = stolen - alreadyStolen;
                if (gained > 0) {
                    player.heal((float) (gained * VampiricEffect.PER_LEVEL));
                }
            }
        }
    }
}
