package com.cortes.combat.fx;

import com.cortes.combat.CortesCombat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * El temblor de <b>recibir un golpe</b>, y el filtro que lo mantiene chico.
 *
 * <h2>La pregunta es "¿me pegaron?", no "¿perdí vida?"</h2>
 *
 * <p>Perder vida es demasiado amplio para sacudir la cámara. El veneno la cobra cada 25 ticks, el
 * fuego cada 20, el cactus cada 10 y el <b>apiñamiento cada tick</b>: un jugador atrapado en un
 * hueco con veinte mobs encima tendría la pantalla temblando sin que nadie lo haya golpeado. Y no es
 * que sea feo -- es que el temblor deja de significar "me pegaron", que es todo lo que significa.</p>
 *
 * <p><b>∴ el filtro es {@code getEntity() != null}: hubo un atacante.</b> Es exactamente la misma
 * pregunta que {@code GuardHandler#blockedFraction} hace para decidir si una guardia puede parar algo
 * -- <em>"no hay atacante: caída, fuego, ahogo, inanición"</em> -- y por eso se usa la misma en vez de
 * una lista de tipos de daño que se queda vieja. Una lista enumera los casos que alguien recordó;
 * esta pregunta contesta por todos, incluidos los de un mod que todavía no existe.</p>
 *
 * <p>Lo que queda afuera es a propósito: una caída no sacude, y un veneno tampoco. Lo que entra es
 * una flecha, un golpe de mob, un jugador y <b>un proyectil</b>, porque el tirador es el atacante
 * aunque el que toca sea la flecha.</p>
 *
 * <h2>Por qué va en {@code Post} y no en el incoming</h2>
 *
 * <p>Porque el temblor es de <b>haber recibido</b>, no de haber estado por recibir. {@code Post} es
 * después de que la armadura y la guardia hayan dicho lo suyo, así que un golpe parado por el escudo
 * llega acá con daño cero -- y ése ya tiene su propio temblor, más grande, en {@code GuardHandler}.
 * En el incoming, un bloqueo perfecto sacudiría como un golpe limpio.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID)
public final class HurtShake {

    private HurtShake() {
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DamageSource source = event.getSource();
        // Sin atacante no es un golpe: es un tick de veneno, fuego, ahogo, caida o apiñamiento.
        // Ver el javadoc.
        if (source.getEntity() == null) {
            return;
        }
        // Una explosion ya tiene el suyo, y es el mas grande de la tabla. Sumar los dos seria cobrar
        // el mismo hecho dos veces y dejar el reventon fuera de escala.
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return;
        }
        Shake.to(player, player.getX(), player.getY(), player.getZ(), ShakeKind.HURT);
    }
}
