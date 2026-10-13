package com.cortes.combat.particle;

import com.cortes.combat.CortesCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Los puntos de color para el daño que <b>no es un golpe</b>: veneno, fuego, caída, ahogo.
 *
 * <h2>El hueco que este archivo tapa</h2>
 *
 * <p>La lectura se cableó primero en {@code CombatEngine.apply}, que es donde el golpe melee
 * aterriza y donde el <b>split</b> -- cuánto entró, cuánto comió la armadura, cuánto la poción --
 * existe como dato. Pero el daño de un efecto no pasa por ahí: el veneno lo cobra el tick de la
 * poción y el fuego el tick del cuerpo ardiendo, los dos por {@code LivingEntity.hurt} y por ningún
 * otro lado.</p>
 *
 * <p><b>El playtest lo reportó:</b> <em>"los puntos de daño solo muestran de los golpes, no de
 * veneno, fuego"</em>. Y tenía razón.</p>
 *
 * <h2>El split acá es trivial, y por eso este archivo es corto</h2>
 *
 * <p>Un tick de veneno <b>no tiene armadura que lo pare</b> ni poción que lo frene: entra entero. O
 * sea que el split es un solo balde, {@code dealt}, con el total -- y lo único que hay que decidir
 * es <b>de qué color</b>, que es la pregunta del <b>tipo</b> y no la del split.</p>
 *
 * <p>Es exactamente la división que el diseño ya tenía: <b>el split es del golpe y el tipo es del
 * tick</b>, y por eso el veneno sale verde y el golpe sale rojo.</p>
 *
 * <h2>Y no se pisa con el motor</h2>
 *
 * <p>Los golpes de jugador ya se muestran desde {@code CombatEngine}, con su split de verdad. Si
 * este handler también los tomara, cada golpe mostraría <b>dos juegos de puntos</b> -- el mismo
 * mensaje dos veces, con los colores en desacuerdo. ∴ se saltea todo lo que sea un ataque de
 * jugador, y el gate es el tag {@code is_player_attack}: <b>el mismo que el mod ya escribe</b> para
 * que sus dos tipos de daño cuenten como ataques de jugador, así que una arma nueva que agregue un
 * tipo queda cubierta sola.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class OnAnyDamage {

    private OnAnyDamage() {
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel server)) {
            return;
        }
        DamageSource source = event.getSource();
        // Lo nuestro ya se mostró, con su split. Ver el javadoc.
        if (source.is(DamageTypeTags.IS_PLAYER_ATTACK)) {
            return;
        }
        float amount = event.getNewDamage();
        if (!(amount > 0.0F)) {
            return;
        }
        HitParticles.impact(server, victim, amount, rgbFor(source));
    }

    /**
     * El color del tipo de daño, en las familias de {@link HurtPalette}.
     *
     * <p><b>Se pregunta por lo específico primero.</b> {@code wither} y {@code magic} comparten
     * varios caminos en vanilla, y preguntar por la familia antes que por el tipo haría que un
     * efecto concreto cayera en el color de su vecino.</p>
     *
     * <p>Y el orden importa por una razón más dura: los tipos de vanilla <b>reusan mensajes</b> --
     * {@code MOB_ATTACK} y {@code MOB_PROJECTILE} declaran los dos {@code "mob"} -- así que
     * agruparlos por el mensaje sería agrupar por una cadena que no distingue nada.</p>
     */
    private static int rgbFor(DamageSource source) {
        if (source.is(DamageTypes.WITHER)) {
            return HurtPalette.WITHER;
        }
        if (source.is(DamageTypes.STARVE) || source.is(DamageTypes.DRY_OUT)) {
            return HurtPalette.STARVE;
        }
        if (source.is(DamageTypes.FREEZE)) {
            return HurtPalette.FREEZE;
        }
        if (source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING)) {
            return HurtPalette.SUFFOCATE;
        }
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.STING)) {
            return HurtPalette.THORNS;
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return HurtPalette.LIGHTNING;
        }
        if (source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL)
                || source.is(DamageTypes.FALLING_STALACTITE) || source.is(DamageTypes.STALAGMITE)) {
            return HurtPalette.IMPACT;
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.SONIC_BOOM)
                || source.is(DamageTypes.WITHER_SKULL)) {
            return HurtPalette.MAGIC;
        }
        if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) {
            return HurtPalette.EXPLOSION;
        }
        // El fuego va DESPUES de la magia y la explosion a proposito: el `fireball` de un blaze es
        // las dos cosas, y el jugador lee mejor "me quemo" que "me explotaron".
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return HurtPalette.FIRE;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            return HurtPalette.PROJECTILE;
        }
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC)
                || source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.OUTSIDE_BORDER)
                || source.is(DamageTypes.BAD_RESPAWN_POINT)) {
            return HurtPalette.VOID;
        }
        // El resto: un golpe de mob, una espina, cualquier cosa de otro mod. Rojo, que es el color
        // que el jugador ya asocia con perder vida.
        return HurtPalette.DEALT;
    }
}
