package com.cortes.combat.effect;

import com.cortes.combat.CortesCombat;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.function.BiConsumer;

/**
 * Desvío Vampírico, la mitad del que lo desvía: <b>la vida máxima que te quedaste</b>.
 *
 * <h2>Las dos mitades del robo</h2>
 *
 * <p>Un robo son dos cosas y por eso son <b>dos efectos</b>: éste, que le suma vida máxima al
 * jugador, y {@link DrainedEffect}, que se la resta al enemigo. Podrían haber sido uno con el signo
 * decidido por quién lo tiene, y no se puede: {@code createModifiers} no sabe a quién le están
 * aplicando el efecto. Dos efectos es la forma honesta de decir dos cosas.</p>
 *
 * <p><b>Diez segundos en todos los niveles y el timer se reinicia</b> -- no se acumula duración,
 * pero sí el robo: desviar otra vez antes de que se vaya renueva los diez segundos con el nuevo
 * nivel. El nivel compra <b>cuánto</b>, no <b>cuánto dura</b>.</p>
 *
 * <h2>Un corazón por nivel</h2>
 *
 * <p>Cuatro niveles son cuatro corazones, y no es una cantidad arbitraria: es la mitad de la vida de
 * un jugador sin armadura. Con el tope por nivel que el dueño pidió, lo máximo que se puede robar de
 * un cuerpo es eso -- y el techo se reinicia, así que no se acumula sobre el mismo enemigo.</p>
 */
public class VampiricEffect extends MobEffect {

    /** Un corazón por nivel, en puntos de vida. En Minecraft un corazón son dos. */
    public static final double PER_LEVEL = 2.0D;

    /** Diez segundos, iguales en los cuatro niveles. */
    public static final int DURATION_TICKS = 200;

    private static final ResourceLocation MODIFIER = CortesCombat.rl("vampiric");

    public VampiricEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9B1B3A);
    }

    @Override
    public void createModifiers(int amplifier,
                                BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        consumer.accept(Attributes.MAX_HEALTH, new AttributeModifier(MODIFIER,
                PER_LEVEL * (Math.max(0, amplifier) + 1),
                AttributeModifier.Operation.ADD_VALUE));
    }
}
