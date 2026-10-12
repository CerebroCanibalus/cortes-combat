package com.cortes.combat.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * El sonido de una carga de boton derecho, en bucle, para poder <b>cortarlo</b>.
 *
 * <h2>Por que del lado del cliente y no con {@code ServerLevel#playSound}</h2>
 *
 * <p>Porque hay que <b>pararlo</b>, y el servidor no puede. {@code ServerLevel#playSound} le entrega
 * el sonido a {@code PlayerList#broadcast} y de ahi sale un paquete sin nada que lo nombre: la unica
 * forma de terminarlo seria un stop sin referencia, y eso ya esta escrito en {@code Arcabuz}, que por
 * esa misma razon no reproduce el sonido de la recarga al apretar.</p>
 *
 * <p>Del lado del cliente el asunto tiene dueño: se guarda la instancia y se la pasa a
 * {@code SoundManager#stop}. Y suena <b>una vez por jugador</b>, que es lo que una carga necesita --
 * el de al lado no tiene por que oir el arma de otro cargando.</p>
 *
 * <h2>En bucle y sin atenuacion</h2>
 *
 * <p>En bucle porque la carga dura cuarenta ticks y un sonido corto terminaria antes, lo que leeria
 * como "la carga se corto". Y <b>no posicional</b> porque el dueño del sonido es el jugador que
 * carga: un sonido atado a una posicion se apaga cuando la camara se aleja, y este tiene que durar
 * lo que dure el boton.</p>
 */
public class SpecialChargeSound extends AbstractTickableSoundInstance {

    public SpecialChargeSound(SoundEvent event) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        // **En bucle, relativo y sin atenuacion, y son los tres campos de la superclase.**
        //
        // `looping` porque la carga dura cuarenta ticks y un sonido corto terminaria antes, lo que
        // leeria como "la carga se corto" -- exactamente lo contrario de lo que hay que comunicar.
        // `relative` para que suene pegado al oyente y no a una posicion del mundo, y `NONE` para
        // que no se apague cuando la camara se aleja: el dueño de este sonido es el jugador que
        // carga, y tiene que durar lo que dure el boton.
        this.looping = true;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.7F;
        this.pitch = 1.0F;
    }

    /** Nada que hacer por tick: el bucle y el corte los maneja quien la creo. */
    @Override
    public void tick() {
    }
}
