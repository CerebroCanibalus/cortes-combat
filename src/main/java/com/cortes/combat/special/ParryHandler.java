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

        // **Y el golpe que vino por el aire, nombrado.** Hasta acá el parry devolvía flechas por
        // casualidad: `getEntity()` de un proyectil es el ARQUERO -- la flecha es
        // `getDirectEntity()` -- y el `instanceof LivingEntity` de arriba lo aceptaba sin preguntar
        // nada. El dueño lo probó en gameplay y tenía razón; lo que faltaba era la línea que lo
        // dice. Ver `ParrySource`, y más abajo está lo que se hace con la respuesta.
        Entity direct = event.getSource().getDirectEntity();
        boolean throughTheAir = ParrySource.cameThroughTheAir(direct != null, direct == attacker);

        // **The three numbers that decide why a parry does not answer.**
        //
        // The playtest says "nothing parries a player, everything parries a mob and an explosion",
        // and every route in this mod builds its source as `playerAttack(player)`, so
        // `getEntity()` is a `ServerPlayer` and the `LivingEntity` cast at the top of this method
        // passes. The remaining suspect is `DamageSource#getSourcePosition`, because
        // `LivingEntity#isDamageSourceBlocked` -- which runs eleven lines *after* the event this
        // handler listens to, at 1.21.1's `hurt()` 1164 against this one's 1153 -- decides
        // "is this hit coming from the front" with it, and a player attacking through
        // `CombatEngine` may not have one where the melee code would put it.
        //
        // So the three things that answer it are printed: who attacked, from where, and how much
        // arrived. One line at debug, only on the tick a parry is open, which is at most six
        // lines per press.
        org.slf4j.LoggerFactory.getLogger("cortescombat").debug(
                "[parry] golpe={} atacante={} posicion={} cantidad={} bloqueando={} porElAire={}",
                event.getSource().getMsgId(),
                attacker.getName().getString(),
                event.getSource().getSourcePosition(),
                amount,
                player.isBlocking(),
                throughTheAir);

        event.setAmount(0.0F);
        CombatState.setParry(player, state.consume());
        ModNetworking.sendParryState(player, false, state.cooldownTicks());

        // **Capacitor: lo que vuelve, ahora con un rayo encima.**
        //
        // Va aca -- donde el parry ya decidio que si contesta -- y no en la rama del descarte, porque
        // son dos hechos distintos: el descarte dice que el proyectil no sigue, y esto dice que lo que
        // se devuelve lleva un rayo. Y el daño es una SUMA: el parry ya devolvia el proyectil antes de
        // este encantamiento, asi que lo que agrega es el rayo y el golpe que ya existia no se toca.
        if (throughTheAir) {
            int level = CombatEnchantments.level(player.level().registryAccess(),
                    player.getMainHandItem(), CombatEnchantments.CAPACITOR);
            if (level > 0) {
                attacker.hurt(player.damageSources().playerAttack(player),
                        CombatEnchantRules.capacitorDamage(level) + amount);
                // Quemar cuatro segundos: es lo que convierte una devolucion en una advertencia, porque
                // el que disparo tiene que apagarse en vez de disparar otra vez. `attacker` ya es un
                // LivingEntity por el gate de arriba; repetirlo con `instanceof` no compila en
                // JDK 17 (patron dominado) aunque el 21 lo deje pasar.
                attacker.setRemainingFireTicks(Math.max(attacker.getRemainingFireTicks(),
                        CombatEnchantRules.CAPACITOR_FIRE_TICKS));
                if (player.level() instanceof net.minecraft.server.level.ServerLevel server) {
                    // **La linea entera, del jugador al cuerpo**, y no un penacho en el objetivo.
                    // Un rayo sin origen no dice quién lo tiro, y en un duelo eso es la mitad de la
                    // información -- el playtest lo vio como "particulas que aparecen ahi".
                    com.cortes.combat.particle.LightningParticles.zigzag(server,
                            player.getEyePosition(),
                            new net.minecraft.world.phys.Vec3(attacker.getX(),
                                    attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ()));
                }
            }
        }

        // **Lo que se hace con la respuesta: el proyectil se descarta.**
        //
        // Un golpe que llegó por el aire se anuló y volvió -- eso ya pasaba --, pero la flecha
        // seguía clavada en el jugador que la parrió, que es la parte visible de un desvío que no
        // desvió nada. Descartarla es lo que hace que "lo devolví" se vea además de leerse.
        //
        // **Es también el punto de enganche de Capacitor**: convertir el proyectil en un rayo es
        // esta misma rama más el daño y el fuego. La condición ya está, y tiene test.
        if (throughTheAir) {
            direct.discard();
        }

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

        // **Y el hombro del que desvio.** El parry es el boton mas caro del sable -- cinco segundos
        // de cooldown, una ventana de medio segundo que no se puede sostener -- y hasta ahora su
        // unico aviso era un color en el crosshair y un sonido puesto en el atacante. O sea que el
        // momento que el arma entera paga no se sentia.
        //
        // Va en la posicion del ATACANTE, igual que el sonido: lo que acaba de pasar paso alla, y la
        // distancia lo dice. Desviar una flecha a treinta bloques no sacude como desviar un hacha.
        com.cortes.combat.fx.Shake.to(player, attacker, com.cortes.combat.fx.ShakeKind.PARRY);

        // **Desvio Vampirico: el parry roba vida maxima, y CADA parry suma un corazon.**
        //
        // Dos efectos y no uno: ver DrainedEffect -- un solo efecto no puede sumarle al que desvia y
        // restarle al desviado, porque `createModifiers` no sabe a quien se lo estan aplicando.
        //
        // **El nivel es el TOPE del acumulado, no el tamano del robo.** Cada desvio suma un corazon
        // al jugador y se lo quita al enemigo, y el nivel dice cuantos corazones aguanta la cadena:
        // al cuatro hay que desviar cuatro veces para llegar. El acumulado ES el amplifier del
        // efecto -- contarlo aparte seria una segunda verdad sobre cuanta vida maxima tiene el
        // jugador, y la primera es el efecto.
        //
        // **Y el enemigo pierde UNO por parry, no el acumulado.** Aplicarle el total le bajaria el
        // techo por todo lo que robaste en esta cadena, incluso lo que le robaste a otro.
        int vampiric = CombatEnchantments.level(player.level().registryAccess(),
                player.getMainHandItem(), CombatEnchantments.VAMPIRICO);
        if (vampiric > 0) {
            MobEffectInstance current = player.getEffect(ModEffects.VAMPIRIC);
            int alreadyStolen = current == null ? 0 : current.getAmplifier() + 1;
            int stolen = CombatEnchantRules.vampiricoStolen(alreadyStolen, vampiric);
            // El piso se pregunta ANTES de aplicar nada: bajarle el techo a un cuerpo le baja la
            // vida actual (vanilla la clampea cada tick) y no hay forma de evitarlo, asi que lo
            // unico que se puede garantizar es que no mate. Un desvio sin premio es mejor que un
            // boton de borrar sobre cualquiera que este tocado.
            if (CombatEnchantRules.canDrain(attacker.getMaxHealth(), 0)) {
                player.addEffect(new MobEffectInstance(ModEffects.VAMPIRIC,
                        VampiricEffect.DURATION_TICKS, stolen - 1, false, true));
                attacker.addEffect(new MobEffectInstance(ModEffects.DRAINED,
                        DrainedEffect.DURATION_TICKS, 0, false, true));
                // **Y la vida actual viene con el techo, que era el bug que reporto el playtest.**
                //
                // Subirle `MAX_HEALTH` a un cuerpo <b>no le sube la vida actual</b>: el jugador se
                // queda con los corazones nuevos vacios y lee "no me lo lleve". Es la misma
                // asimetria que el comentario de arriba documenta para el enemigo -- bajarle el
                // techo SI le baja la vida, porque vanilla la clampea cada tick -- y del otro lado
                // no pasa sola. El robo que se ve era un robo que solo cobraba el enemigo.
                //
                // Se cura por los corazones <b>nuevos</b> de este desvio y no por el acumulado:
                // los que ya tenias los cobraste cuando los robaste, y curarlos de nuevo seria
                // convertir cada parry en una pocion.
                int gained = stolen - alreadyStolen;
                if (gained > 0) {
                    player.heal((float) (gained * VampiricEffect.PER_LEVEL));
                }
            }
        }
    }
}
