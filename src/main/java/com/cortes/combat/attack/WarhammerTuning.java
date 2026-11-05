package com.cortes.combat.attack;

/**
 * Los dos numeros que el golpe cargado del martillo obedece, en un valor solo.
 *
 * <h2>Por que un record y no dos campos sueltos</h2>
 *
 * <p>Los dos numeros se mueven juntos: {@code damageCap} decide cuanto da el golpe y
 * {@code armourPenetration} decide cuanta armadura le hace. Un golpe con un tope pero sin
 * penetracion, o al reves, son dos combinaciones que el juego no produce -- y un par de campos
 * sueltos invites a pasarlos en el orden equivocado. Un record los junta, y el constructor
 * los deja en rango.</p>
 *
 * <h2>Y por que es puro</h2>
 *
 * <p>Ningun tipo de Minecraft entra aqui. La cuenta del tope y la de la penetracion son las dos
 * preguntas que el martillo hace, y son preguntas que un test sobre una JVM pelada puede
 * responder: "el tope de 20 recorta un golpe de 45" y "una penetracion del 50 % deja la mitad de
 * la armadura" no necesitan el juego para contestarse. La clase que las aplica
 * ({@code CombatEngine}) es la que si necesita Minecraft, y esta es la que le dice que
 * aplicar. (^o^)</p>
 *
 * <h2>Los dos valores por defecto son los de hoy</h2>
 *
 * <p>{@link #DEFAULT} es "sin tope y la armadura no participa": exactamente lo que hacia el
 * martillo antes de que estos dos numeros existieran. Un default que cambiara el comportamiento
 * seria una version del mod que rompe el balance de una partida empezada con la anterior.</p>
 */
public record WarhammerTuning(float damageCap, float armourPenetration) {

    /**
     * Sin tope, y la armadura no cuenta: el comportamiento de siempre.
     *
     * <p>Un tope de {@code 0} significa "sin tope" y una penetracion de {@code 1} significa "la
     * armadura no participa". Los dos son los valores que el martillo tenia antes de que esto
     * fuera configurable, y por eso son los defaults.</p>
     */
    public static final WarhammerTuning DEFAULT = new WarhammerTuning(0.0F, 1.0F);

    /**
     * El constructor deja los dos numeros en rango.
     *
     * <p>Un tope negativo seria un golpe que no hace dano, y una penetracion mayor que uno
     * seria una armadura negativa. Los dos son numeros que el archivo de configuracion no deberia
     * poder producir -- {@code defineInRange} ya los acota -- pero el record se construye tambien
     * desde codigo, y un valor fuera de rango que llega aqui tiene que ser inocuo y no
     * raro. (⌒‿⌒)</p>
     */
    public WarhammerTuning {
        damageCap = Math.max(0.0F, damageCap);
        armourPenetration = Math.max(0.0F, Math.min(1.0F, armourPenetration));
    }

    /**
     * El tope: el dano que el golpe no pasa de.
     *
     * <p>Un tope de {@code 0} no recorta nada, porque cero es el "no hay tope" y no "el golpe no
     * hace dano". Un golpe de 45 con tope de 20 vale 20; uno de 12 con tope de 20 vale 12, porque
     * un tope no empuja el dano hacia arriba.</p>
     */
    public float capDamage(float damage) {
        if (damageCap <= 0.0F) {
            return damage;
        }
        return Math.min(damage, damageCap);
    }

    /**
     * Si la armadura no participa en este golpe.
     *
     * <p>Es la pregunta que decide <b>que fuente de dano</b> se usa, y por eso no puede ser una
     * fraccion: la fuente que ignora la armadura es una etiqueta del tipo de dano
     * ({@code bypasses_armor}), y una etiqueta es binaria. Una penetracion parcial usa otra
     * fuente -- una que no ignora la armadura -- y baja el atributo de armadura del objetivo por
     * este golpe. Ver {@code CombatEngine#damageSource}.</p>
     */
    public boolean bypassesArmour() {
        return armourPenetration >= 1.0F;
    }

    /**
     * La fraccion de la armadura del objetivo que todavia cuenta contra este golpe.
     *
     * <p>Con una penetracion del 50 % queda {@code 0.5}: la mitad de los puntos de armadura. Es
     * el mismo mecanismo que usan Contragolpe y Estocada Perforante -- bajar el atributo por
     * este golpe y devolverlo enseguida -- y no una copia de la cuenta de armadura de vanilla,
     * que es lo que no se puede reimplementar sin que se desincronice.</p>
     */
    public float armourScale() {
        return 1.0F - armourPenetration;
    }
}
