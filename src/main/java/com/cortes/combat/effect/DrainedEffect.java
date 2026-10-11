package com.cortes.combat.effect;

import com.cortes.combat.CortesCombat;

import com.cortes.combat.enchant.CombatEnchantRules;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.function.BiConsumer;

/**
 * Desvío Vampírico, la mitad del que fue desviado: <b>el techo de vida que le falta</b>.
 *
 * <h2>Y una advertencia que el diseño no tenía escrita</h2>
 *
 * <p><b>Bajarle la vida máxima a un cuerpo le baja la vida actual, y no hay forma de evitarlo.</b>
 * {@code LivingEntity#tick} clampea {@code health} al máximo en cada tick, así que un enemigo que
 * estaba a veinte y ahora tiene techo dieciocho pierde dos puntos en el acto. Se propuso "que la
 * vida actual no baje" y <b>es imposible</b>: se puede subir la vida después, pero el tick siguiente
 * la vuelve a clampear, y el resultado es un cuerpo con la vida por encima de su techo -- que
 * vanilla no permite sostener.</p>
 *
 * <p><b>Lo que sí se puede garantizar es que no mate</b>, y por eso el efecto tiene un
 * <b>piso</b>: {@link #canDrain} pregunta si al enemigo le quedaría al menos un punto de techo, y el
 * handler que lo aplica <em>no lo aplica</em> si no. Un robo que pudiera dejar el techo en cero sería
 * una ejecución gratuita sobre cualquiera que esté tocado, que es justo el veto que el proyecto ya
 * tiene escrito (C7: una ejecución sin contrajuego).</p>
 *
 * <p>Es un efecto <b>separado</b> de {@link VampiricEffect} y no el mismo con el signo al revés:
 * {@code createModifiers} no sabe a quién le están aplicando el efecto, así que un solo efecto no
 * puede sumar en uno y restar en el otro.</p>
 */
public class DrainedEffect extends MobEffect {

    /** Un corazón por nivel, en puntos de vida, y en negativo. */
    public static final double PER_LEVEL = -2.0D;

    /** Diez segundos, los mismos que dura el robo del otro lado. */
    public static final int DURATION_TICKS = 200;

    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "drained");

    /**
     * Si este cuerpo puede pagar el robo sin quedar con el techo por debajo de uno.
     *
     * <p><b>La cuenta vive en {@link CombatEnchantRules#canDrain}, que es pura.</b> Acá no hay copia
     * ni delegado: el handler del parry la llama directo, porque un {@code MobEffect} extiende una
     * clase de Minecraft y un método suyo sería un método que ningún test puede cargar. Ver el
     * javadoc de la clase para por qué el signo importa.</p>
     */
    public DrainedEffect() {
        super(MobEffectCategory.HARMFUL, 0x5C1024);
    }

    @Override
    public void createModifiers(int amplifier,
                                BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        consumer.accept(Attributes.MAX_HEALTH, new AttributeModifier(MODIFIER,
                PER_LEVEL * (Math.max(0, amplifier) + 1),
                AttributeModifier.Operation.ADD_VALUE));
    }

    /**
     * Si este cuerpo puede pagar el robo sin quedar con el techo por debajo de uno.
     *
     * <p><b>Puro y público para que el handler no invente su propia cuenta.</b> La pregunta tiene que
     * ser una sola: "¿le queda techo después de esto?", y una segunda copia de esa resta en el
     * handler es una segunda respuesta a la misma pregunta.</p>
     *
     * <p>Se pregunta con la vida <b>máxima actual</b> del cuerpo y no con su vida, porque lo que se
     * baja es el techo: un enemigo a un punto de vida con veinte de techo puede pagar el robo sin
     * morir, y uno a veinte con dos de techo no.</p>
     */
    public static boolean canDrain(float maxHealth, int amplifier) {
        return CombatEnchantRules.canDrain(maxHealth, amplifier);
    }
}
