package com.cortes.combat.effect;

import com.cortes.combat.enchant.CombatEnchantRules;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Anti-curación: lo que deja Cicatrizador en la herida que abrió.
 *
 * <h2>Qué hace, y por qué no lo hace acá</h2>
 *
 * <p><b>No tiene comportamiento propio a propósito.</b> La reducción tiene que ocurrir en el
 * momento en que la curación <em>entra</em> -- es una pregunta sobre el número que llega, no un tick
 * que pasa -- así que el trabajo vive en el handler de {@code LivingHealEvent} y el efecto es el
 * número que dice cuánto. Es la misma división que {@code antiseptic}, y por la misma razón: un
 * efecto que intentara corregir la curación después de aplicada estaría peleando con vanilla en vez
 * de con la regla.</p>
 *
 * <p><b>Reduce TODOS los sources</b> -- pociones, manzana dorada, la regeneración natural y la regen
 * de comida de este mod -- porque todos pasan por {@code LivingEntity#heal} y por lo tanto por el
 * mismo evento. Un encantamiento que sólo bloqueara las pociones sería un encantamiento que el
 * jugador aprende a esquivar comiendo.</p>
 *
 * <h2>El nivel es el nivel</h2>
 *
 * <p>Cuatro niveles del encantamiento son cuatro del efecto, y el {@code amplifier} los lleva: uno
 * menos, como todo efecto de Minecraft. La tabla de cuánto por nivel está en
 * {@link #reduction(int)}, que es pura.</p>
 */
public class AntiHealingEffect extends MobEffect {

    /** Quince por ciento por nivel: al cuatro, el sesenta. */
    public static final float REDUCTION_PER_LEVEL = 0.15F;

    public AntiHealingEffect() {
        super(MobEffectCategory.HARMFUL, 0x6E4B8A);
    }

    /**
     * La fracción de curación que se pierde con este nivel del efecto.
     *
     * <p>{@code amplifier} es el nivel menos uno, así que el nivel uno es {@code amplifier 0}. Y el
     * resultado <b>nunca llega a uno</b>: con cuatro niveles y un quince por ciento el tope es el
     * sesenta, así que una curación siempre cura algo. Un cien por ciento sería una curación
     * bloqueada, que es otro encantamiento y otro problema -- el que come no se entera de por qué
     * no le sube la vida.</p>
     */
    public static float reduction(int amplifier) {
        return CombatEnchantRules.antiHealingReduction(amplifier);
    }
}
