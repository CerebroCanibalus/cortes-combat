package com.cortes.combat.particle;

/**
 * Cuántos corazones vale una curación. <b>Sin nada de Minecraft</b>, para que un test lo alcance.
 *
 * <h2>Por qué está separado de {@link CurarParticles}</h2>
 *
 * <p>Porque <b>la clase que un test carga no puede nombrar un tipo de Minecraft, ni siquiera en un
 * método que el test no llame.</b> {@code CurarParticles} tiene un {@code puff(ServerLevel, ...)} y
 * eso alcanza para que la clase entera no arranque en una JVM pelada -- el test muere con
 * {@code NoClassDefFoundError} <em>antes de la primera aserción</em>, y el síntoma parece un test
 * roto en vez de una regla violada.</p>
 *
 * <p><b>Y no es teoría: pasó al escribir esto.</b> La primera versión tenía {@code heartsFor} dentro
 * de {@code CurarParticles} y los cinco tests murieron igual. Es la misma separación que ya existe
 * entre {@code HitParticles} (que spawnea) y {@code HitReadout} (que reparte), y la razón de que
 * las dos existan.</p>
 *
 * <h2>El número es la lectura</h2>
 *
 * <p>Un punto de vida es un corazón y una manzana dorada son cuatro: el jugador ve de un vistazo si
 * se curó de la regeneración de comida -- que es constante y chica -- o si se gastó un recurso. Sin
 * el número, los dos momentos más distintos del juego se verían igual.</p>
 */
public final class CurarReadout {

    /**
     * El techo de corazones por curación.
     *
     * <p><b>Y es la defensa contra el acuario.</b> La regeneración de comida de este mod cura un
     * punto por segundo, para siempre: el efecto sólo vale algo si aparece cuando pasa algo. Un
     * techo alto no sería un adorno, sería la diferencia entre "se curó" y una pantalla con
     * corazones permanentes.</p>
     */
    public static final int MAX_HEARTS = 4;

    /** Cada cuántos puntos de vida entra un corazón. Dos, así una cura de ocho da los cuatro. */
    public static final float HEALTH_PER_HEART = 2.0F;

    private CurarReadout() {
    }

    /**
     * Cuántos corazones vale una curación de {@code amount} puntos de vida.
     *
     * <p>Cero para una curación de cero o negativa, y <b>nunca menos de uno para una cura real</b>:
     * una curación de medio punto tiene que verse, porque el jugador hizo algo para ganarla. El
     * techo es lo que la contiene.</p>
     */
    public static int heartsFor(float amount) {
        if (!(amount > 0.0F)) {
            return 0;
        }
        return Math.min(MAX_HEARTS, Math.max(1, (int) Math.ceil(amount / HEALTH_PER_HEART)));
    }
}
