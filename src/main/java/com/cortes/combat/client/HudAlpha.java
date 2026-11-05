package com.cortes.combat.client;

/**
 * Los numeros de transparencia del HUD, en una clase pura.
 *
 * <h2>Por que no viven en la capa que los dibuja</h2>
 *
 * <p>Porque la comparacion que los justifica <b>es una comparacion</b>: "la mira es lo mas
 * brillante del centro de la pantalla, y el widget esta debajo". Esa comparacion la hace un
 * test, y <b>un test no puede cargar una clase de Minecraft</b> (la mira se dibuja con
 * {@code GuiGraphics}, que no existe en una JVM pelada). Con el numero escondido en la capa,
 * el test tiene que escribir el numero a mano -- y un numero escrito a mano en un test es una
 * copia que se desactualiza en silencio: el test seguiria verde con la mira en 0,85 y el
 * widget en 0,42, que es exactamente el estado que nadie quiere.</p>
 *
 * <p>Los numeros del widget (la barra y los corchetes) viven en {@link ConeGauge}, que ya era
 * pura; esta es la otra mitad de la misma idea.</p>
 */
public final class HudAlpha {

    /**
     * La opacidad base de la mira.
     *
     * <p><b>Bajada a 0,62 en la ronda de la transparencia.</b> El dueno pidio que la mira y las
     * barras fueran "mas transparentes", y esta es la mira: vive en el centro de la pantalla, en
     * el pixel mas caro que hay, y con el mundo de fondo ahi se come contraste. Bajarla no la
     * vuelve mas dificil de leer -- el sprite es una marca de borde duro, no un resplandor, y
     * la forma se lee igual -- pero le devuelve el fondo al jugador.</p>
     *
     * <p><b>Tiene que seguir ganándole al widget</b>, y eso no es una opinion: es el invariante
     * de la ronda del HUD intrusive ("lo mas brillante del centro de la pantalla es lo que
     * estas apuntando"), y hay un test que lo afirma.</p>
     */
    public static final float CROSSHAIR = 0.62F;

    private HudAlpha() {
    }
}
