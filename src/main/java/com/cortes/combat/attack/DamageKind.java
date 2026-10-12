package com.cortes.combat.attack;

/**
 * Qué clase de golpe es, <b>en un solo lugar</b>.
 *
 * <h2>Por qué un enum y no los dos booleanos que había</h2>
 *
 * <p>{@code AttackResult} llevaba {@code ignoredArmour} y {@code axeCharged}, y su javadoc explicaba
 * que <em>"son mutuamente excluyentes por construcción"</em>. <b>Eso era una promesa, no un
 * tipo</b>: nada impedía construir un resultado con los dos en {@code true}, y el único guard era
 * un comentario.</p>
 *
 * <p>Y la pregunta de fondo es <b>una sola</b> -- <em>"¿qué le hace este golpe a la armadura?"</em>
 * -- que estaba contestada en cuatro lugares con tres mecanismos distintos: dos booleanos acá, un
 * tipo de daño taggeado en {@code ModDamageTypes}, y dos modificadores de atributo en
 * {@code CombatEngine}. Un enum contesta una vez.</p>
 *
 * <h2>Las tres consecuencias, y por qué son campos y no una deducción</h2>
 *
 * <p>Se podrían derivar ({@code breaksGuard} es hoy exactamente "no es {@link #NORMAL}"), y <b>no se
 * derivan a propósito</b>: el día que exista un golpe que atraviese la armadura sin romper la
 * guardia, una deducción lo obligaría a mentir en una de las dos. Tres campos dicen las tres cosas
 * que el juego hace con un golpe, y cada consumidor lee la que le importa.</p>
 *
 * <h2>Y la fracción no entra acá, por una razón dura</h2>
 *
 * <p>Contragolpe y Estocada ignoran <b>un cuarto</b> de la armadura, y eso <b>no</b> es un valor de
 * este enum. La razón está escrita en {@code CombatEngine} y es la que decide todo el diseño:
 * {@code bypasses_armor} <b>es un booleano</b>, y vanilla lo anida dentro de
 * {@code bypasses_shield} -- así que un pico fraccionario por el camino del tag también atravesaría
 * la guardia del rival. Los dos encantamientos bajan el <b>atributo</b> de armadura para un golpe,
 * que es una consecuencia que no arrastra nada más.</p>
 *
 * <p>∴ este enum es <b>la clase de golpe</b> (all-or-nothing, decidida por el arma) y el pico
 * fraccionario es <b>otro mecanismo</b>, que vive donde se aplica. Confundirlos fue lo que tuvo a
 * la pureza contestada en tres lugares.</p>
 */
public enum DamageKind {

    /** Un golpe normal: la armadura reduce, la guardia no se rompe sola. */
    NORMAL(false, false, false),

    /**
     * El martillo cargado: <b>la armadura no participa</b>.
     *
     * <p>Es el único que usa el camino del tag, y arrastra lo que vanilla decide arrastrar: el
     * mismo {@code bypasses_armor} está anidado dentro de {@code bypasses_shield}, así que también
     * pasa por encima de un escudo levantado. <b>No es una consecuencia nuestra y no se puede
     * elegir</b> -- es lo que el tag significa.</p>
     */
    PURE(true, false, true),

    /**
     * El hacha cargada: <b>conserva la armadura y la destroza al doble</b>.
     *
     * <p>Deliberadamente <b>no</b> atraviesa. Un hacha cargada que ignorara la armadura sería
     * estrictamente mejor que el martillo en lo único para lo que el martillo existe.</p>
     */
    AXE_CHARGED(false, true, true);

    private final boolean piercesArmour;
    private final boolean wearsArmourTwice;
    private final boolean breaksGuard;

    DamageKind(boolean piercesArmour, boolean wearsArmourTwice, boolean breaksGuard) {
        this.piercesArmour = piercesArmour;
        this.wearsArmourTwice = wearsArmourTwice;
        this.breaksGuard = breaksGuard;
    }

    /** Si la armadura reduce este golpe. Falso sólo para {@link #PURE}. */
    public boolean piercesArmour() {
        return piercesArmour;
    }

    /** Si el golpe desgasta la armadura al doble. Sólo {@link #AXE_CHARGED}. */
    public boolean wearsArmourTwice() {
        return wearsArmourTwice;
    }

    /** Si el golpe vacía la guardia del defensor de un saque. Los dos cargados. */
    public boolean breaksGuard() {
        return breaksGuard;
    }
}
