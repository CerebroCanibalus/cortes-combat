package com.cortes.combat.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Un jugador remoto dejo de estar a la vista, y su reloj de poses se va con el.
 *
 * <h2>El bug que esta clase arregla</h2>
 *
 * <p>El mod lleva un reloj por jugador remoto: la pose que el otro cliente esta mostrando, su
 * carga, su guardia. El reloj vive en {@link RemotePoseStates} y mientras el jugador este a la
 * vista se actualiza con cada paquete de estado.</p>
 *
 * <p>Cuando el jugador remoto se va (se aleja, se desconecta, muere, cambia de dimension) el
 * reloj tiene que irse con el: una entrada que no se borra es memoria que crece con cada
 * jugador que estuvo alguna vez a la vista, y un id de entidad que se reusa puede dejarle el
 * reloj a otro.</p>
 *
 * <h2>Por que el primer intento de esto estaba en el servidor</h2>
 *
 * <p>La primera version escuchaba {@code PlayerEvent.StopTracking} desde
 * {@code attack/CombatTicker}, y comparaba {@code event.getEntity() instanceof LocalPlayer} para
 * asegurarse de estar en el cliente. <b>Eso era un crash de server dedicado</b>, y la razon es
 * que el evento <i>nunca</i> llega al cliente:</p>
 *
 * <ul>
 *   <li><b>NeoForge 21.1.250</b>: {@code PlayerEvent.StopTracking} lo postea
 *       {@code EventHooks.onStopEntityTracking}, y el unico que lo llama es
 *       {@code ServerEntity.removePairing} -- el <b>server</b> dejando de trackear una entidad.
 *       Verificado sobre las fuentes.</li>
 *   <li><b>Forge 47.4.28</b>: lo mismo, escrito en el parche de {@code ServerEntity}.</li>
 * </ul>
 *
 * <p>Asi que la comparacion con {@code LocalPlayer} se evaluaba siempre en el servidor, y cargar
 * una clase de cliente ahi es un error: en NeoForge el {@code RuntimeDistCleaner} lo convierte en
 * un {@code RuntimeException} que tumba el tick del jugador (y el watchdog mata el server), y en
 * Forge 1.20.1 seria un {@code NoClassDefFoundError}. El crash report lo decia con una linea
 * {@code CombatTicker.onStopTracking(CombatTicker.java:92)} y una excepcion sobre
 * {@code LocalPlayer}.</p>
 *
 * <p><b>Y el server de pruebas no lo vio nunca</b>, porque el evento solo se dispara cuando un
 * jugador deja de trackear algo: hace falta un cliente conectado. El mod llevaba meses en verde
 * con la mina adentro.</p>
 *
 * <h2>Por que este evento si</h2>
 *
 * <p>{@code EntityLeaveLevelEvent} lo postean <b>las dos patas</b> desde el {@code ClientLevel},
 * en el callback {@code onTrackingEnd}, que es exactamente "el cliente dejo de trackear esta
 * entidad" (verificado en las fuentes de 1.21.1 y en el parche de {@code ClientLevel} de 1.20.1).
 * Es el mismo hecho, del lado donde ocurre.</p>
 *
 * <p><b>Y es compartido, no gemelo</b>: el rename de la pata 1.20.1 convierte
 * {@code net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent} en
 * {@code net.minecraftforge.event.entity.EntityLeaveLevelEvent}, y la API es la misma en las dos
 * ({@code getEntity()} y {@code getLevel()}). Una sola clase para las dos patas, como
 * {@code ClientWorldCleanup}.</p>
 *
 * <p>La clase se registra <b>solo en el cliente</b>, desde {@code CortesCombatClient}, que es
 * {@code @Mod(dist = Dist.CLIENT)}: en el server este archivo no se carga nunca, y por eso no
 * importa que nombre clases de cliente.</p>
 */
public final class RemotePoseCleanup {

    private RemotePoseCleanup() {
    }

    /**
     * Una entidad dejo el nivel del cliente.
     *
     * <p>Si era un jugador, su reloj de poses se borra. Los demas tipos de entidad no tienen
     * reloj, asi que se van sin dejar nada.</p>
     *
     * <p><b>El jugador local tambien entra por aqui</b> (cambia de dimension, se desconecta) y no
     * hay nada que borrar: el jugador local no tiene reloj en {@link RemotePoseStates}, su reloj
     * es el del viewmodel local, que vive en otro lugar y se limpia con el mundo. El
     * {@code forget} de una id que no esta es un no-op.</p>
     */
    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        RemotePoseStates.forget(player.getId());
    }
}
