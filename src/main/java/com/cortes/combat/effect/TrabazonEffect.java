package com.cortes.combat.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Trabazon: el efecto que deja un golpe cargado del martillo.
 *
 * <h2>Sin comportamiento propio, a proposito</h2>
 *
 * <p><b>Lo que hace no es un tick que pasa: es una pregunta que otro le hace al juego.</b> "Puedo
 * saltar" y "puedo correr" se contestan en el momento en que el jugador aprieta la tecla, no cada
 * veinte ticks, asi que el trabajo vive en el handler de los eventos de salto y de sprint y este efecto
 * es la marca que dice que estan apagados. Es la misma division que {@code anti_healing}: un efecto que
 * intentara corregir el salto <em>despues</em> de que pasó estaria peleando con vanilla en vez de con
 * la regla.</p>
 *
 * <h2>Por que se llama Trabazon y no Aturdimiento</h2>
 *
 * <p>Porque el proyecto ya llama <b>stun</b> a la guardia rota, y dos cosas distintas con el mismo
 * nombre es la clase de confusion que no se arregla despues -- sobre todo cuando una es "no podes
 * golpear" y la otra es "no podes moverte".</p>
 *
 * <h2>El nivel agrega una prohibicion, no tiempo</h2>
 *
 * <p>El L1 saca el salto, que es lo que impide cruzar un hueco; el L2 saca tambien la carrera. Un
 * segundo y medio es lo que dura una ventana de guardia, y estirarlo convertiria el encantamiento en un
 * control de mas. Ver {@code CombatEnchantRules#trabazonBlocksSprint}.</p>
 */
public class TrabazonEffect extends MobEffect {

    public TrabazonEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A6B2F);
    }
}
