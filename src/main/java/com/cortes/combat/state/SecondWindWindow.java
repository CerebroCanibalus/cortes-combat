package com.cortes.combat.state;

/**
 * Segundo Aliento: la ventana que abre <b>que te rompan la guardia</b>.
 *
 * <h2>Qué premia</h2>
 *
 * <p>La guardia de la espada tiene tres finales: la bajás, te la rompen, o nunca la subiste. Los dos
 * primeros están cubiertos -- bajarla es gratis y romperla te deja un stun. <b>Lo que faltaba era
 * que romperla te dejara algo además del castigo.</b> Esto es la deuda que cobrás: el golpe que
 * pagaste con el stun te devuelve una ventana, y tu siguiente ataque pega más y empuja más.</p>
 *
 * <p><b>Es el espejo de Contragolpe y por eso no lo repite.</b> Contragolpe premia <em>cubrirse</em>
 * (la aguante que gastaste te abre una ventana ofensiva); Segundo Aliento premia <em>que te
 * cubran</em> -- que tu guardia no alcanzó. Los dos pueden estar en la misma espada y no se pisan:
 * uno se arma cuando bloqueás, el otro cuando te rompen.</p>
 *
 * <h2>Vencimiento absoluto, como la otra</h2>
 *
 * <p>Un contador habría que bajarlo cada tick para todos los jugadores; un vencimiento contra
 * {@code getGameTime()} se pregunta y no se toca. Misma forma que {@link ContragolpeWindow} y que
 * {@code FallCoyote}.</p>
 *
 * <p>Sin codec: tres segundos que mueren solos. Una ventana que reaparece tras reconectar es un
 * premio que el jugador no ganó.</p>
 *
 * @param expiresAtTick el tick a partir del cual la ventana está cerrada; cero significa que nunca
 *                      se abrió
 */
public record SecondWindWindow(long expiresAtTick) {

    /** Tres segundos, por el diseño: "tu siguiente golpe en 3 s". */
    public static final int WINDOW_TICKS = 60;

    /** Sin ventana. El reloj del mundo arranca en cero, así que un cero se lee solo. */
    public static final SecondWindWindow NONE = new SecondWindWindow(0L);

    /** Si un golpe dado en {@code now} cobra la ventana. */
    public boolean isOpen(long now) {
        return now < expiresAtTick;
    }

    /** La guardia rota que la abrió. */
    public static SecondWindWindow armedAt(long now) {
        return new SecondWindWindow(now + WINDOW_TICKS);
    }

    /**
     * El golpe que la usó.
     *
     * <p>"Tu siguiente golpe" es uno, y gastarla es lo que lo hace uno: una ventana que sobreviviera
     * al golpe sería un estado de tres segundos, no un premio por un golpe.</p>
     */
    public SecondWindWindow spent() {
        return NONE;
    }
}
