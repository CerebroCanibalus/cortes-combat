package com.cortes.combat.special;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.enchant.CombatEnchantments;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.ChargeState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * La carga del boton derecho de la pala: <b>sostener, y soltar cargado suelta el orbe</b>.
 *
 * <h2>Por que una carga y no un boton</h2>
 *
 * <p>El orbe de antimateria es lo mas fuerte que hay en el catalogo -- catorce de dano en un radio
 * de cuatro, mas un tiron de casi dos bloques -- y un boton que lo suelta en el press no tiene
 * contrajuego: se aprieta y ya. Los cuarenta ticks de carga son <b>el aviso</b>: el enemigo ve el
 * arma levantarse y temblar, y tiene cuarenta ticks para salir del radio. El precio no cambio
 * (sigue siendo el cooldown de seis segundos); lo que se agrego es el derecho a correrse.</p>
 *
 * <h2>Soltar antes de tiempo cancala, y soltar cargado dispara</h2>
 *
 * <p>Es la forma del ataque cargado, que es lo que el playtest pidio: sostener, y soltar en el
 * tope. <b>Soltar antes no dispara nada</b> -- y eso es lo que hace que el aviso sea de verdad, no
 * un temporizador que cobra igual. El cooldown no se toca hasta que el orbe sale: una carga
 * abandonada no cuesta nada, que es la misma regla que la recarga del arcabuz.</p>
 *
 * <h2>Y por que reusa {@link ChargeState} y el canal sostenido que ya existia</h2>
 *
 * <p>El boton derecho ya viaja: {@code SpecialHeldPayload} lo manda en cada transicion,
 * <b>reconciliado contra el boton fisico</b> (que es lo que evita el hold pegado cuando la ventana
 * pierde el foco). Hasta hoy lo consumia solo el arcabuz. Esto lo escucha tambien, y el estado es
 * el mismo {@code ChargeState} del ataque: "un boton sostenido que acumula ticks" no cambia por
 * cual boton es.</p>
 */
public final class PalaCharge {

    private PalaCharge() {
    }

    /** El boton derecho bajo o levantado. */
    public static void setHeld(ServerPlayer player, boolean held) {
        ChargeState state = player.getData(ModAttachments.SPECIAL_CHARGE.get());
        if (held) {
            if (!armed(player)) {
                return;
            }
            player.setData(ModAttachments.SPECIAL_CHARGE.get(), new ChargeState(0, true));
            return;
        }

        if (!state.holding()) {
            return;
        }
        player.setData(ModAttachments.SPECIAL_CHARGE.get(), ChargeState.EMPTY);
        if (state.ticks() < CombatEnchantRules.PALA_ANTIMATERIAL_CHARGE_TICKS) {
            // **La linea que dice que la carga se corto, y por eso el aviso es real.** Sin ella, una
            // carga abandonada y una que nunca empezo se ven igual en el log.
            CortesCombat.LOGGER.info("[pala] carga CANCELADA (soltada en {} de {})",
                    state.ticks(), CombatEnchantRules.PALA_ANTIMATERIAL_CHARGE_TICKS);
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            SpecialHandler.launchAntimatter(player, level);
        }
    }

    /** Un tick de carga, con las guardas que la tiran abajo. */
    public static void tick(ServerPlayer player) {
        ChargeState state = player.getData(ModAttachments.SPECIAL_CHARGE.get());
        if (!state.holding()) {
            return;
        }
        // El arma se fue de la mano -- cambiar a otra cosa cancela, como el lunge y como la recarga
        // del arcabuz. Y pasado el tope no acumula: la carga llena se queda llena hasta que suelten.
        if (!armed(player)) {
            player.setData(ModAttachments.SPECIAL_CHARGE.get(), ChargeState.EMPTY);
            return;
        }
        if (state.ticks() >= CombatEnchantRules.PALA_ANTIMATERIAL_CHARGE_TICKS) {
            return;
        }
        player.setData(ModAttachments.SPECIAL_CHARGE.get(), state.advance());
    }

    /** Si el arma en mano puede cargar el orbe: la pala con el encantamiento y el cooldown listo. */
    public static boolean armed(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return CombatEnchantments.level(player.level().registryAccess(), held,
                CombatEnchantments.PALA_ANTIMATERIAL) > 0 && AttackCooldown.isReady(player);
    }
}
