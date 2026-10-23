package com.cortes.combat.special;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.state.ChargeState;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Las cargas de boton derecho del mod: <b>quien las tiene, y el conductor del estado</b>.
 *
 * <h2>Agregar un arma magica es agregar una clase y una linea</h2>
 *
 * <p>Todo lo que hay abajo -- el estado, el conteo, el ratio que viaja, la barra y el sonido -- es
 * <b>agnostico de quien carga</b>. La lista de abajo es lo unico que sabe que existe la pala, y una
 * segunda arma magica entra con una clase que implemente {@link SpecialCharge} y su nombre en esa
 * lista. Nada mas: el contador, la red y el HUD no se tocan.</p>
 *
 * <p><b>Y es una lista y no un {@code switch} por encantamiento</b>, que es lo que estaba antes: el
 * conductor preguntaba por {@code PALA_ANTIMATERIAL} por su nombre en tres lugares distintos, asi
 * que el segundo arma magica obligaba a encontrar los tres.</p>
 *
 * <h2>El boton ya viajaba, y por eso esto no agrega red</h2>
 *
 * <p>{@code SpecialHeldPayload} manda el boton derecho en cada transicion, <b>reconciliado contra el
 * boton fisico</b> -- que es lo que evita el hold pegado cuando la ventana pierde el foco. Hasta la
 * ronda pasada lo consumia solo el arcabuz.</p>
 */
public final class SpecialCharges {

    /**
     * Todas las cargas del mod, y el orden importa si algun dia dos pudieran matchear el mismo item.
     *
     * <p><b>{@code List.of} y no un campo que se llena al arrancar:</b> es inmutable por
     * construccion, asi que nadie la muta en runtime y el orden es el que se lee.</p>
     */
    private static final List<SpecialCharge> ALL = List.of(new PalaOrbCharge());

    private SpecialCharges() {
    }

    /** La carga que pide el arma en mano, o {@code null}. <b>Vale en los dos lados.</b> */
    public static SpecialCharge of(ItemStack held, Level level) {
        for (SpecialCharge charge : ALL) {
            if (charge.canCharge(held, level)) {
                return charge;
            }
        }
        return null;
    }

    /** La carga que pide el arma en la mano del jugador, o {@code null}. */
    public static SpecialCharge of(Player player) {
        return of(player.getMainHandItem(), player.level());
    }

    // ------------------------------------------------------------------ el conductor

    /** El boton derecho bajo o levantado. */
    public static void setHeld(ServerPlayer player, boolean held) {
        ChargeState state = CombatState.specialCharge(player);
        if (held) {
            if (!armed(player)) {
                return;
            }
            CombatState.setSpecialCharge(player, new ChargeState(0, true));
            return;
        }

        if (!state.holding()) {
            return;
        }
        CombatState.setSpecialCharge(player, ChargeState.EMPTY);

        SpecialCharge charge = of(player);
        if (charge == null || !AttackCooldown.isReady(player)) {
            return;
        }
        if (state.ticks() < charge.ticks()) {
            // **La linea que dice que la carga se corto, y por eso el aviso es real.** Sin ella, una
            // carga abandonada y una que nunca empezo se ven igual en el log.
            CortesCombat.LOGGER.info("[carga] CANCELADA (soltada en {} de {})",
                    state.ticks(), charge.ticks());
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            charge.release(player, level);
        }
    }

    /** Un tick de carga, con las guardas que la tiran abajo. */
    public static void tick(ServerPlayer player) {
        ChargeState state = CombatState.specialCharge(player);
        if (!state.holding()) {
            return;
        }
        // El arma se fue de la mano -- cambiar a otra cosa cancela, como el lunge y como la recarga
        // del arcabuz. Y pasado el tope no acumula: la carga llena se queda llena hasta que suelten.
        SpecialCharge charge = of(player);
        if (charge == null || !AttackCooldown.isReady(player)) {
            CombatState.setSpecialCharge(player, ChargeState.EMPTY);
            return;
        }
        if (state.ticks() >= charge.ticks()) {
            return;
        }
        CombatState.setSpecialCharge(player, state.advance());
    }

    /**
     * La carga como fraccion del tope, o cero.
     *
     * <p><b>Una sola cuenta, y la usan el HUD local y la pose de los demas.</b> Dos copias de "ticks
     * sobre el tope" es exactamente lo que este proyecto ya pago con una barra de carga que iba por
     * una funcion distinta que el dano. La duracion sale de {@link SpecialCharge#ticks()}, asi que
     * tampoco esta escrita aca.</p>
     */
    public static float ratio(ServerPlayer player) {
        ChargeState state = CombatState.specialCharge(player);
        if (!state.holding()) {
            return 0.0F;
        }
        SpecialCharge charge = of(player);
        if (charge == null || charge.ticks() <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, state.ticks() / (float) charge.ticks());
    }

    /** Si el arma en mano puede cargar y el arma esta lista. */
    public static boolean armed(ServerPlayer player) {
        return of(player) != null && AttackCooldown.isReady(player);
    }
}
