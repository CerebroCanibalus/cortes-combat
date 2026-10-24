package com.cortes.combat.attack;

import com.cortes.combat.particle.HurtPalette;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Qué le pasó al daño de un golpe, en cuatro números. <b>La lectura del impacto.</b>
 *
 * <h2>Los tres baldes, y por qué son una partición</h2>
 *
 * <p>El daño crudo de un golpe se reparte entero entre tres destinos, sin solaparse:</p>
 *
 * <ul>
 *   <li><b>{@link #dealt}</b> -- llegó a la vida. Es lo que el jugador llama "el daño".</li>
 *   <li><b>{@link #armour}</b> -- lo comió la armadura.</li>
 *   <li><b>{@link #effect}</b> -- lo comió una poción (Resistencia).</li>
 * </ul>
 *
 * <p>Y {@link #pierced} no es un balde sino una <b>marca</b>: dice que la fuente atravesó la
 * armadura, o sea que el balde de armadura está vacío <em>porque no pudo participar</em> y no porque
 * el defensor no llevara nada. Es el color blanco del diseño, y sin la marca un golpe del martillo
 * contra un cuerpo con armadura completa se vería igual que uno contra un cuerpo desnudo.</p>
 *
 * <h2>La aritmética la hace vanilla, y sólo una parte hace falta</h2>
 *
 * <p><b>La armadura se le pregunta a {@code CombatRules.getDamageAfterAbsorb}</b> -- la misma
 * función que el juego va a usar dos líneas después, con los mismos argumentos. No es una segunda
 * copia de la regla: es la regla.</p>
 *
 * <p><b>Y el efecto se saca por resta de la verdad observada.</b> Replicar el lado de la poción
 * exigiría reconstruir {@code f3} -- la protección de encantamientos más la Resistencia -- que vive
 * dentro de un método {@code protected} de {@code LivingEntity}. <b>La vida que el golpe bajó de
 * verdad es un dato, no una reconstrucción</b>, así que se lee y se resta. Es más corto y no puede
 * discrepar de lo que pasó.</p>
 *
 * <h2>El caso que obliga a una rama: la muerte</h2>
 *
 * <p>Si el golpe mató, <b>la vida observada está topeada por la que quedaba</b>: a un cuerpo con 3
 * de vida un golpe de 20 le baja 3, no 20. La resta daría una reducción por poción de 17 puntos que
 * <b>no existió</b>, y el jugador vería una nube de gris en el golpe que más rojo merece.</p>
 *
 * <p>∴ cuando el cuerpo murió, el balde del efecto queda en cero y todo lo que no comió la armadura
 * se cuenta como daño hecho. <b>Es la lectura honesta</b>: no se puede saber cuánto habría bajado, y
 * el golpe fue el que lo mató.</p>
 */
public record DamageSplit(float dealt, float armour, float effect, boolean pierced) {

    /** El orden de los baldes, para el reparto de puntos. Uno por color. */
    public static final int DEALT = 0;
    public static final int ARMOUR = 1;
    public static final int EFFECT = 2;

    /** Cuántos colores tiene un impacto. */
    public static final int BUCKETS = 3;

    /** Un golpe que no conectó: todo en cero. */
    public static final DamageSplit NONE = new DamageSplit(0.0F, 0.0F, 0.0F, false);

    /** Los pesos de cada balde, en el orden de {@link #DEALT}, {@link #ARMOUR} y {@link #EFFECT}. */
    public float[] amounts() {
        return new float[]{dealt, armour, effect};
    }

    /**
     * El color de cada balde.
     *
     * <p><b>Y el blanco depende de {@link #pierced}.</b> Cuando la fuente atraviesa la armadura, el
     * daño que llegó se pinta blanco en vez de rojo: es el hecho que el jugador no puede deducir de
     * ningún otro canal, porque el número de daño es el mismo y el mob se ve igual.</p>
     */
    public int[] colours() {
        return new int[]{
                pierced ? HurtPalette.PURE : HurtPalette.DEALT,
                HurtPalette.ARMOUR,
                HurtPalette.EFFECT
        };
    }

    /** El daño crudo del golpe: los tres baldes sumados. */
    public float raw() {
        return dealt + armour + effect;
    }

    /**
     * Mide el impacto de un golpe que ya se aplicó.
     *
     * @param target       el cuerpo golpeado, <b>después</b> del golpe
     * @param source       la fuente exacta que se usó, con sus tags
     * @param raw          el daño crudo que el swing valía, antes de cualquier reducción
     * @param healthBefore la vida de {@code target} antes del golpe
     * @param healthAfter  la vida después
     */
    public static DamageSplit of(LivingEntity target, DamageSource source, float raw,
                                 float healthBefore, float healthAfter) {
        if (!(raw > 0.0F)) {
            return NONE;
        }

        boolean pierced = source.is(DamageTypeTags.BYPASSES_ARMOR);

        // La armadura, por la regla de vanilla y con los atributos de ahora mismo. Los modificadores
        // transitorios de riposte ya se sacaron cuando esto corre, así que lo que se lee es lo que
        // el golpe encontró.
        float afterArmour = raw;
        if (!pierced) {
            float armourValue = (float) target.getAttributeValue(Attributes.ARMOR);
            float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            afterArmour = CombatRules.getDamageAfterAbsorb(raw, armourValue, toughness);
        }
        float lostToArmour = Math.max(0.0F, raw - afterArmour);

        // La verdad observada, y el tope de la muerte -- ver el javadoc del record.
        float dealt = afterArmour;
        if (healthAfter > 0.0F) {
            dealt = Math.max(0.0F, Math.min(afterArmour, healthBefore - healthAfter));
        }
        float lostToEffect = Math.max(0.0F, afterArmour - dealt);

        return new DamageSplit(dealt, lostToArmour, lostToEffect, pierced);
    }
}
