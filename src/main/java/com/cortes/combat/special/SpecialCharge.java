package com.cortes.combat.special;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Una carga de boton derecho: <b>cuanto pide, quien puede cargarla, que suena y que pasa al
 * soltar</b>.
 *
 * <h2>Por que una interfaz y no mas codigo en la pala</h2>
 *
 * <p>La primera version tenia el encantamiento, la duracion y el efecto escritos adentro del mismo
 * archivo que lleva la cuenta de ticks. Eso funciona para una carga y no para la segunda: agregar
 * un arma magica obligaba a tocar el contador, el sincronizador y el HUD, o sea tres archivos que
 * no saben nada de esa arma.</p>
 *
 * <p>Con esto, <b>agregar una carga es agregar una clase y una linea</b> en {@link SpecialCharges}.
 * El contador, la red y la barra no se enteran.</p>
 *
 * <h2>Lo que contesta cada metodo y en que lado</h2>
 *
 * <p>{@link #ticks()}, {@link #canCharge} y {@link #chargingSound()} valen <b>en los dos lados</b>:
 * el servidor los necesita para contar y el cliente para dibujar la barra y sonar sin esperar un
 * paquete. {@link #release} es <b>solo del servidor</b>, porque es el efecto, y el efecto lo
 * decide la autoridad.</p>
 */
public interface SpecialCharge {

    /** Cuantos ticks pide llegar al tope. */
    int ticks();

    /** Si el arma en mano puede cargarla. Se pregunta en los dos lados. */
    boolean canCharge(ItemStack held, Level level);

    /** Lo que suena mientras carga, o {@code null} si no suena nada. */
    SoundEvent chargingSound();

    /** Lo que pasa al soltar con la carga llena. Solo el servidor. */
    void release(ServerPlayer player, ServerLevel level);
}
