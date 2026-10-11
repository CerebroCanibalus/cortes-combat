package com.cortes.combat.special;

/**
 * Si el golpe que llega al parry lo trajo <b>algo distinto del atacante</b>.
 *
 * <h2>Por qué esta pregunta existe, y por qué no la hacía nadie</h2>
 *
 * <p>El parry devuelve el daño <b>al atacante de la fuente</b>: {@code ParryHandler} lee
 * {@code DamageSource#getEntity()} y le pega a esa entidad. Para un flechazo eso <b>no es la
 * flecha</b> -- {@code getEntity()} de un proyectil es <b>el arquero</b>, y la flecha es
 * {@code getDirectEntity()}.</p>
 *
 * <p><b>∴ el parry ya devolvía flechas, y por casualidad.</b> No hay una línea que nombre un
 * proyectil en todo el mod: funciona porque vanilla pone al tirador en la fuente, y el
 * {@code instanceof LivingEntity} del handler lo acepta. El dueño lo probó en gameplay y tenía
 * razón; el que se equivocó fue el que buscó {@code AbstractArrow} en el código y no lo encontró.
 * </p>
 *
 * <p><b>Y una consecuencia imprevista es un bug esperando.</b> Nada afirma que el parry desvía
 * proyectiles, así que nada avisa el día que deje de hacerlo, y la mitad del diseño de Capacitor
 * ("desviar un proyectil") colgaría de un accidente. Esta clase es la formalización: la pregunta
 * tiene nombre, tiene test, y se lee en el handler.</p>
 *
 * <h2>La pregunta es por identidad y no por tipo</h2>
 *
 * <p><b>No</b> es {@code direct instanceof Projectile}, y es a propósito: la onda de filo
 * ({@code SlashWave}) es una entidad propia que <em>no</em> extiende {@code Projectile} y también
 * llega por el aire. Preguntar por el tipo la dejaría afuera, y preguntar "¿el que causó el daño es
 * el mismo que atacó?" cubre las flechas, los tridentes, las bolas de fuego <b>y</b> la onda -- todo
 * lo que viaja -- sin nombrar ni una clase de vanilla.</p>
 */
public final class ParrySource {

    private ParrySource() {
    }

    /**
     * ¿El daño lo causó algo distinto del atacante?
     *
     * <p>Tres casos, y los tres importan:</p>
     *
     * <ul>
     *   <li><b>Sin entidad directa</b> ({@code hasDirectEntity} false): no es un proyectil. Una
     *       explosión o el entorno no "vienen por el aire", y desviarlos sería otra cosa.</li>
     *   <li><b>La entidad directa ES el atacante</b>: melee. El puñetazo, la espada, el hacha --
     *       el caso normal del parry, y el que el sable paga con su alcance corto.</li>
     *   <li><b>La entidad directa es otra</b>: vino por el aire. Alguien disparó, y el daño viajó
     *       hasta acá dentro de otra cosa.</li>
     * </ul>
     */
    public static boolean cameThroughTheAir(boolean hasDirectEntity, boolean directIsTheAttacker) {
        return hasDirectEntity && !directIsTheAttacker;
    }
}
