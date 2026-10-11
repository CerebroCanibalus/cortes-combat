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
 * Quebranto: la armadura del rival vale menos, y el grupo entero se beneficia.
 *
 * <h2>Qué amplifica</h2>
 *
 * <p>Las armas pesadas ya son las que castigan la armadura: el hacha la destroza al doble por
 * {@code ArmorWearHandler} y el martillo la ignora a carga completa. <b>Quebranto es lo que hace que
 * el grupo se beneficie de lo que la pesada empezó</b> -- el golpe que abrió la brecha la deja
 * abierta unos segundos para el que pega después.</p>
 *
 * <h2>Ocho por ciento, y por qué no dieciocho</h2>
 *
 * <p><b>Este es el número más peligroso de todo el documento de encantamientos.</b> Toca
 * {@code Attributes.ARMOR} del rival, y eso afecta a <b>todo</b> el daño que le entre -- el tuyo, el
 * de tus compañeros, el de la caída y el de un mob que pasaba por ahí. Se propuso dieciocho por
 * ciento por stack y se bajó a ocho: con tres stacks son veinticuatro por ciento de armadura menos,
 * que contra una armadura completa ya es una diferencia que se siente, y es el punto de partida de
 * un playtest en vez de una promesa.</p>
 *
 * <p><b>El tope de stacks vive acá y no en el handler</b>, porque es una propiedad del efecto: el
 * número de stacks ES el {@code amplifier}, y un amplifier sin tope convertiría tres golpes en un
 * porcentaje arbitrario. Que el handler también lo clampee es defensa en profundidad, no la
 * definición.</p>
 */
public class QuebrantoEffect extends MobEffect {

    /** Ocho por ciento de armadura menos por stack, como fracción del total. */
    public static final double REDUCTION_PER_STACK = -0.08D;

    /** Tres stacks: veinticuatro por ciento con el efecto al máximo. */
    public static final int MAX_STACKS = 3;

    private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "quebranto");

    public QuebrantoEffect() {
        super(MobEffectCategory.HARMFUL, 0x5A4A2A);
    }

    /**
     * La reducción de armadura de este nivel, como fracción, y <b>pura</b>.
     *
     * <p><b>El número vive acá y no dentro de {@code createModifiers}</b>, por la misma razón que
     * todos los demás de este proyecto: el envoltorio recibe un {@code BiConsumer<Holder<Attribute>,
     * AttributeModifier>}, y esos son tipos de Minecraft -- así que un test que quisiera comprobar la
     * cuenta tendría que nombrarlos y no compilaría. La cuenta afuera, el envoltorio adentro.</p>
     */
    public static double armourReduction(int amplifier) {
        return CombatEnchantRules.quebrantoArmour(amplifier);
    }

    /**
     * Los modificadores por nivel, que es la forma en que un efecto de Minecraft cambia un atributo.
     *
     * <p><b>{@code createModifiers} y no {@code addAttributeModifier} en el constructor</b>, y la
     * diferencia es el nivel: el constructor no lo conoce, así que un modificador puesto ahí vale lo
     * mismo con un stack que con tres. Éste recibe el {@code amplifier} y es el único lugar donde el
     * nivel puede entrar en el número.</p>
     */
    @Override
    public void createModifiers(int amplifier,
                                BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        consumer.accept(Attributes.ARMOR, new AttributeModifier(MODIFIER,
                armourReduction(amplifier),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
}
