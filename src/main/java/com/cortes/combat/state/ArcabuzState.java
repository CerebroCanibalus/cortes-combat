package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * El estado de <b>un arcabuz</b>: si tiene la recamara cargada, si le arde una mecha, y si lo estan
 * recargando.
 *
 * <h2>Vive en el ARMA, y esa es toda la diferencia</h2>
 *
 * <p><b>Esto era un attachment del jugador y tenia que dejar de serlo.</b> La cuenta atras de la
 * mecha y la de la recarga son del arma que las empezo, no del que aprieta el boton, y tenerlas en
 * el jugador convertia "cambiar de arcabuz" en "heredar la recarga del otro". El sintoma se reporto
 * tres veces con tres caras distintas: el segundo arcabuz salia cargado, el segundo terminaba la
 * recarga del primero, y el segundo disparaba una mecha que arranco en el primero.</p>
 *
 * <p>El arreglo estructural es que el estado <b>viaje con el arma</b>, que es lo que hace la ballesta
 * de vanilla con {@code CHARGED_PROJECTILES}: un arma fuera de la mano no tiene estado en ningun
 * lado mas, asi que no hay nada que heredar. No hay reconciliacion, no hay comparacion de identidad
 * y no hay reset -- las tres cosas que este bug hizo necesarias y que fallaron una por una.</p>
 *
 * <h2>Por que guarda el tick ABSOLUTO y no un contador</h2>
 *
 * <p><b>Porque un contador obliga a escribir el componente cada tick, y eso mueve el arma.</b> El
 * proyecto ya pago eso: la animacion de la mecha vivia en un componente que se reescribia sesenta
 * veces por segundo, y cada escritura reemplaza el {@code ItemStack} que el cliente tiene en la
 * mano, lo que dispara la animacion de cambio de item de vanilla y mueve el arma sesenta
 * centimetros mientras se asienta. Cuatro frames en un segundo de combustion eran cuatro cambios de
 * item por segundo.</p>
 *
 * <p>Con un <b>tick de fin</b> el componente se escribe solo en las transiciones -- al encender, al
 * disparar, al empezar la recarga y al terminarla o cancelarla: <b>cuatro veces por ciclo de
 * disparo</b>, la misma frecuencia que ya paga la recamara, y el resto del tiempo el valor no cambia
 * y no se escribe nada. Lo que queda por tick es una <em>comparacion</em>, y una comparacion no
 * ensucia nada.</p>
 *
 * <p>El reloj es {@code level.getGameTime()}, que es monotono y esta sincronizado: el cliente lo
 * tiene, asi que puede derivar los frames sin que le manden un contador.</p>
 *
 * <h2>Lo que se pierde, y hay que saberlo</h2>
 *
 * <p>Un arma fuera de la mano <b>no se tickea</b>, asi que su mecha y su recarga quedan congeladas
 * hasta que vuelve. Al levantarla:</p>
 * <ul>
 *   <li>Una <b>recarga</b> con el boton levantado se cancela sola (nadie sostiene el boton) -- o
 *       sea que sacar el arma y volverla a agarrar no regala nada.</li>
 *   <li>Una <b>mecha</b> cuyo tick de fin ya paso <b>dispara al primer tick que el arma vuelve a la
 *       mano</b>. Es una demora y no una ganancia: la Firestone se gasto al encender, asi que el
 *       tiro es el mismo, solo que late. Se acepta a proposito porque la alternativa es adivinar
 *       "el arma se fue de la mano" con un pedazo de estado mas, que es exactamente lo que esta
 *       clase acaba de dejar de hacer.</li>
 * </ul>
 *
 * @param loaded      si la recamara tiene una Firestone. <b>Unico dueno:</b> ver la nota de
 *                    {@code ModComponents#ARCABUZ}
 * @param fuseEndsAt  el tick del mundo en que la mecha dispara, o cero si no hay mecha encendida
 * @param fuseTotal   lo que dura esa mecha entera, para que el cliente pueda dibujar el progreso.
 *                    Mecha Corta lo acorta, asi que no se puede tomar de {@link #FUSE_TICKS}
 * @param reloadEndsAt el tick del mundo en que la recarga termina, o cero si no hay recarga
 * @param reloadTotal lo que dura esa recarga entera, por la misma razon: Polvora Seca la acorta
 */
public record ArcabuzState(boolean loaded, long fuseEndsAt, int fuseTotal, long reloadEndsAt,
                           int reloadTotal) {

    /**
     * Veinte ticks: un segundo entero, por diseno.
     *
     * <p><b>El numero tambien es el de la textura.</b> {@code arcabuz.png} es una tira de 64x320 con
     * cinco frames de 64x64 -- uno apagado y cuatro encendidos -- y su {@code .mcmeta} gasta cinco
     * ticks por frame, asi que los cuatro frames encendidos llegan a lo largo de exactamente veinte.
     * El disparo espera eso y la mecha que el jugador mira dura eso, que es la unica razon por la que
     * los dos numeros se pueden confiar. {@code ModelDeclarationTest} lee el mcmeta y los compara.</p>
     */
    public static final int FUSE_TICKS = 20;

    /**
     * Cincuenta ticks: dos segundos y medio sosteniendo el boton derecho.
     *
     * <p><b>Medio segundo mas lento que el barrido entero de la alabarda.</b> Un barrido cuesta cuatro
     * golpes de alabarda y unos dos segundos; cargar un arcabuz cuesta dos y medio y ningun golpe. Esa
     * comparacion es toda la razon de que el numero no sea veinte: el boton de la alabarda se
     * <em>paga</em> en tempo y este en quedarse quieto, y si duraran lo mismo el arcabuz se leeria
     * como el boton barato.</p>
     */
    public static final int RELOAD_TICKS = 50;

    /** Lo minimo que una recarga puede costar, en ticks: un segundo. */
    public static final int MIN_RELOAD_TICKS = 20;

    /** Un arma que nunca se cargo ni se encendio. */
    public static final ArcabuzState EMPTY = new ArcabuzState(false, 0L, 0, 0L, 0);

    /**
     * El formato de guardado, y <b>cada campo es opcional con su valor por defecto</b>.
     *
     * <p>Es la regla que este proyecto aprendio a la mala: <b>el codec de un record <em>es</em> su
     * formato de guardado</b>. Un mundo que guardo un arma con la forma vieja -- {@code fuse_ticks},
     * {@code reload_ticks}, {@code loaded}, {@code reload_held} -- no tiene ninguna de estas claves, y
     * todas caen a su default. El arma vuelve vacia y sin mecha, que es el estado seguro: nada de lo
     * que habia en vuelo es algo que este codigo deba resucitar.</p>
     */
    public static final Codec<ArcabuzState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("loaded", false).forGetter(ArcabuzState::loaded),
            Codec.LONG.optionalFieldOf("fuse_ends_at", 0L).forGetter(ArcabuzState::fuseEndsAt),
            Codec.INT.optionalFieldOf("fuse_total", 0).forGetter(ArcabuzState::fuseTotal),
            Codec.LONG.optionalFieldOf("reload_ends_at", 0L).forGetter(ArcabuzState::reloadEndsAt),
            Codec.INT.optionalFieldOf("reload_total", 0).forGetter(ArcabuzState::reloadTotal)
    ).apply(instance, ArcabuzState::new));

    /**
     * Si hay una mecha encendida, <b>incluso si ya le toca disparar</b>.
     *
     * <p>Una sola pregunta para los dos momentos a proposito: "hay mecha" y "la mecha ya llego" son
     * el mismo hecho mirado en dos ticks distintos, y separarlos en dos campos es como se llega a un
     * arma que dispara dos veces o a una mecha que no dispara nunca.</p>
     */
    public boolean isBurning() {
        return fuseEndsAt > 0L;
    }

    /** Si hay una recarga corriendo, con o sin el boton apretado en este tick. */
    public boolean isReloading() {
        return reloadEndsAt > 0L;
    }

    /** Cuantos ticks le quedan a la mecha, o cero. Para el HUD y el log. */
    public int fuseLeft(long now) {
        return (int) Math.max(0L, fuseEndsAt - now);
    }

    /** Cuantos ticks le quedan a la recarga, o cero. */
    public int reloadLeft(long now) {
        return (int) Math.max(0L, reloadEndsAt - now);
    }

    /** Si la mecha ya cumplio su tiempo y hay que disparar. */
    public boolean dueToFire(long now) {
        return isBurning() && now >= fuseEndsAt;
    }

    /** Si la recarga ya cumplio su tiempo. */
    public boolean dueToLoad(long now) {
        return isReloading() && now >= reloadEndsAt;
    }

    /** Si un press empezaria una recarga. El stack de la recamara lo pregunta el llamador. */
    public boolean canReload() {
        return !loaded && !isBurning() && !isReloading();
    }

    /**
     * La mecha encendida, con el largo que le toque.
     *
     * <p><b>La recamara se vacia ACA y no en el disparo</b>, que es la regla que este arma existe para
     * expresar: el press es lo que cuesta la Firestone, asi que un arma que ya va no tiene nada mas
     * que gastar y no se le puede recargar por detras mientras la mecha arde. {@code loaded} responde
     * "puede este press encender algo", y despues de este press no puede.</p>
     */
    public ArcabuzState light(long now, int fuse) {
        return new ArcabuzState(false, now + fuse, fuse, 0L, 0);
    }

    /** La mecha de fabrica. Ver {@link #light(long, int)} para el caso con Mecha Corta. */
    public ArcabuzState light(long now) {
        return light(now, FUSE_TICKS);
    }

    /** Una recarga que arranca, con el largo que este arma vaya a tardar de verdad. */
    public ArcabuzState startReload(long now, int totalTicks) {
        return new ArcabuzState(false, 0L, 0, now + totalTicks, totalTicks);
    }

    /** El boton se solto: la recarga se tira, no se pausa. */
    public ArcabuzState cancelReload() {
        return new ArcabuzState(loaded, fuseEndsAt, fuseTotal, 0L, 0);
    }

    /** La recarga llego al final: la recamara queda cargada y no hay nada corriendo. */
    public ArcabuzState finished() {
        return new ArcabuzState(true, 0L, 0, 0L, 0);
    }

    /** Despues del disparo: se apaga la mecha y no queda nada. */
    public ArcabuzState afterShot() {
        return new ArcabuzState(false, 0L, 0, 0L, 0);
    }

    /** Si no hay nada cargado, nada ardiendo y nada recargando. */
    public boolean isEmpty() {
        return this.equals(EMPTY);
    }
}
