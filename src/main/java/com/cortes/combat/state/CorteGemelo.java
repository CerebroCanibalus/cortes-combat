package com.cortes.combat.state;

/**
 * Corte Gemelo: el reloj del segundo corte del sable.
 *
 * <h2>Por que el nivel uno necesita un reloj y el dos no</h2>
 *
 * <p>Porque <b>L1 pega el corte extra cada cuatro segundos y L2 lo pega siempre</b>. Sin el reloj, el
 * nivel uno y el dos serian el mismo encantamiento con dos precios, y un jugador que paga el segundo
 * no recibiria nada -- que es la peor clase de nivel, porque no se nota como bug, se nota como "no
 * vale la pena".</p>
 *
 * <h2>Por que {@code READY} es cero</h2>
 *
 * <p>Porque la pregunta es {@code now >= readyAt}, asi que cero ya es "listo desde el primer tick del
 * mundo". Es la misma razon que {@link Adrenalina} y la contraria que {@code GuardFree}, que pregunta
 * {@code now - last >= 60} y por eso arranca en negativo.</p>
 *
 * <p>Sin codec: un reloj de cuatro segundos, y uno que sobreviviera a reconectar seria un corte gratis
 * que el jugador no se gano.</p>
 *
 * @param readyAt el tick del mundo en el que el corte extra vuelve a estar disponible
 */
public record CorteGemelo(long readyAt) {

    public static final CorteGemelo READY = new CorteGemelo(0L);

    public boolean isReady(long now) {
        return now >= readyAt;
    }

    /**
     * Lo gasta, con el intervalo que le toque.
     *
     * <p>El intervalo entra como argumento porque es del nivel, y un reloj que consultara la tabla de
     * reglas por su cuenta seria un estado que sabe de encantamientos -- la division que el proyecto
     * mantiene en todos los demas.</p>
     */
    public CorteGemelo spent(long now, int intervalTicks) {
        return new CorteGemelo(now + Math.max(0, intervalTicks));
    }
}
