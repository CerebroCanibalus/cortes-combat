package com.cortes.combat.client;

import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Lo que se tira cuando el mundo se cierra: <b>el estado de pantalla que no tiene dueno</b>.
 *
 * <h2>Por que existe, y es un bug de verdad encontrado en la auditoria</h2>
 *
 * <p>{@code CameraShake.clear()}, {@code ReactorShake.clear()} y {@code RemotePoseStates.clear()}
 * existian desde sus rondas y <b>no las llamaba nadie</b>: tres metodos de limpieza huerfanos. El
 * propio javadoc de {@code CameraShake} ya avisaba -- "que vale un {@code clear()} que nadie
 * llama" -- y era literal.</p>
 *
 * <p>Las tres consecuencias son la misma forma:</p>
 *
 * <ol>
 *   <li><b>Los impulsos de temblor no vencen entre mundos.</b> Vencen comparando contra
 *       {@code level.getGameTime()}, y un mundo nuevo la hace volver a cero: un impulso de
 *       {@code diesTick = 500} del mundo anterior queda vivo hasta el tick 500 del nuevo, o sea
 *       hasta siete horas despues si el mundo nuevo tiene la hora alta. La camara del jugador
 *       temblaria al entrar a un mundo sin que nada haya explotado.</li>
 *   <li><b>Los relojes de poses remotas quedan huerfanos.</b> Se borran con el "dejo de
 *       trackear" del server, que no llega si te vas del mundo. Las ids de entidad se reusan, asi
 *       que un mob del mundo nuevo puede heredar el reloj de uno del viejo.</li>
 *   <li><b>El HUD se queda con el estado viejo</b>: el contador de inactividad, el flash de la
 *       crosshair y el objetivo cacheado del apuntado sobreviven al cambio de mundo.</li>
 * </ol>
 *
 * <p><b>Y el evento es compartido, no gemelo</b>: el rename de la pata 1.20.1 convierte
 * {@code net.neoforged.neoforge.event.level.LevelEvent} en
 * {@code net.minecraftforge.event.level.LevelEvent}, que existe con el mismo nombre y el mismo
 * {@code getLevel()}. Una sola clase para las dos patas. (⌒‿⌒)</p>
 */
public final class ClientWorldCleanup {

    private ClientWorldCleanup() {
    }

    /**
     * Un mundo se descarga: se tira todo lo que pertenecia a ese mundo.
     *
     * <p>El filtro es por lado a proposito. {@code LevelEvent} corre en las dos puntas, y el
     * cliente que limpia su estado no puede depender de que el server haya limpiado el suyo.</p>
     */
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == null || !event.getLevel().isClientSide()) {
            return;
        }
        CameraShake.clear();
        ReactorShake.clear();
        RemotePoseStates.clear();
        HudIdle.reset();
        CrosshairFeedback.reset();
        CombatAim.reset();
    }
}
