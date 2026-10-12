package com.cortes.combat.client;

import com.cortes.combat.special.SpecialCharge;
import com.cortes.combat.special.SpecialCharges;

import net.minecraft.client.Minecraft;

/**
 * El sonido de carga: <b>arranca cuando la carga arranca y se corta cuando se corta</b>.
 *
 * <h2>El corte es la mitad del aviso</h2>
 *
 * <p>Un sonido que sigue sonando despues de soltar dice lo contrario de lo que paso: el jugador
 * recibe "estoy cargando" mientras el orbe ya se cancelo. Por eso esto no es "reproducir un sonido
 * al apretar" -- es <b>seguir el estado</b>: si el servidor dice que hay carga, suena; si dice que
 * no, se para. Y el estado ya viaja todos los ticks, asi que el corte sale del mismo numero que la
 * barra morada.</p>
 *
 * <h2>Una sola instancia, y es cliente</h2>
 *
 * <p>{@code playing} es un campo estatico y eso es correcto <b>aqui</b>: no es estado de gameplay --
 * el proyecto lo veta para eso -- es el dueño de un sonido en este cliente. {@code GuardInputHandler}
 * tiene exactamente la misma forma con {@code predictedHolding}. Si esto viviera en el servidor
 * habria un sonido por jugador y el vecino oiria el arma de otro.</p>
 */
public final class SpecialChargeAudio {

    /** Lo que esta sonando ahora, o {@code null}. Ver la nota de la clase. */
    private static SpecialChargeSound playing;

    private SpecialChargeAudio() {
    }

    /** Un tick: se asegura de que suene si hay carga y de que no suene si no la hay. */
    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            stop(mc);
            return;
        }
        if (ClientCombatState.specialCharge() <= 0.0F) {
            // El corte, y es el caso que importa: soltar antes de tiempo, cambiar de arma o morir
            // todos terminan aca, por el mismo camino y sin un caso especial cada uno.
            stop(mc);
            return;
        }
        if (playing != null) {
            // Ya suena: no se re-arranca. Re-arrancarlo cada tick seria un sonido que se reinicia
            // sesenta veces por segundo, o sea un chasquido.
            return;
        }
        SpecialCharge charge = SpecialCharges.of(mc.player);
        if (charge == null || charge.chargingSound() == null) {
            return;
        }
        playing = new SpecialChargeSound(charge.chargingSound());
        mc.getSoundManager().play(playing);
    }

    private static void stop(Minecraft mc) {
        if (playing == null) {
            return;
        }
        mc.getSoundManager().stop(playing);
        playing = null;
    }
}
