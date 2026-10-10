package com.cortes.combat.item.tooltip;

import com.cortes.combat.state.ArcabuzState;
import com.cortes.combat.state.ParryState;
import com.cortes.combat.state.StreakState;

/**
 * Los números que el tooltip imprime y que <b>un encantamiento puede cambiar</b>.
 *
 * <h2>Por qué son un registro y no tres parámetros más</h2>
 *
 * <p>{@code linesFor} ya arrastraba catorce parámetros, y cada encantamiento que mueve un número
 * visible agregaba uno: Quick Charge movió la recarga, Reflejo de Acero el cooldown del parry,
 * Ritmo de Hierro la ventana del martillo. Quince posicionales del mismo tipo -- {@code int},
 * {@code int}, {@code int} -- son <b>un bug esperando</b>: intercambiar dos compila y el tooltip
 * imprime el número del otro sistema sin que nada chille. Con un registro, el intercambio tiene que
 * escribirse, y se lee.</p>
 *
 * <p>Y agruparlos <b>nombra el concepto</b>: no son "tres enteros", son "lo que el arma hace distinto
 * por sus encantamientos", que es una sola cosa y se construye en un solo lugar
 * ({@link #base()} para el arma sin ninguno, y {@code CombatTooltipHandler} para la que el jugador
 * tiene en la mano).</p>
 *
 * <p>Puro a propósito: los tres estados de los que salen los defaults son records sin nada de
 * Minecraft, así que un test en JVM pelada puede construir esto y preguntarle al tooltip.</p>
 *
 * @param reloadTicks        cuánto tarda la recarga del arcabuz, con Quick Charge aplicado
 * @param parryCooldownTicks el lockout del parry del shotel, con Reflejo de Acero aplicado
 * @param streakWindowTicks  la ventana de la cadena del martillo, con Ritmo de Hierro aplicado
 */
public record EnchantmentNumbers(int reloadTicks, int parryCooldownTicks, int streakWindowTicks) {

    /**
     * Un arma sin ningún encantamiento: los tres números de diseño.
     *
     * <p>Es lo que se imprime cuando no hay un jugador al que preguntarle -- el creativo, el JEI --
     * y es una respuesta honesta a una pregunta que en ese contexto no tiene respuesta: sin
     * encantamiento, un arcabuz recarga en {@code RELOAD_TICKS} y un martillo encadena en cuatro
     * segundos.</p>
     */
    public static EnchantmentNumbers base() {
        return new EnchantmentNumbers(ArcabuzState.RELOAD_TICKS, ParryState.COOLDOWN_TICKS,
                StreakState.WINDOW_TICKS);
    }
}
