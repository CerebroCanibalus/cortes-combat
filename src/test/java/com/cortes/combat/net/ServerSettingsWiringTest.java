package com.cortes.combat.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Que la config del server llegue al cliente, y que el cliente no lea la suya.
 *
 * <h2>Por que estos guards son textuales</h2>
 *
 * <p>Porque lo que se pregunta aqui es <b>donde esta escrito</b>, y nada en runtime puede
 * observar donde esta escrito: un lector queTodavia pregunta la config local produce el mismo
 * numero que un lector que pregunta la puerta, y el bug aparece solo cuando el admin cambia el
 * valor en el server. Lo unico que se puede cerrar es el camino.</p>
 *
 * <p><b>Y los archivos se leen sin comentarios.</b> Este proyecto ya pago tres veces el mismo
 * bug: un guard que busca una cadena en la prosa y encuentra su propia explicacion. El archivo
 * se lee pelado y la pregunta se le hace al codigo. (???)</p>
 */
class ServerSettingsWiringTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path V120 = Path.of("src", "v120", "java");

    private static String read(Path path) {
        assertTrue(Files.isRegularFile(path), "missing: " + path);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** El archivo sin sus comentarios: la pregunta se le hace al codigo, no a la prosa. */
    private static String code(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String codeOf(Path root, String relative) {
        return code(read(root.resolve(Path.of(relative))));
    }

    @Test
    @DisplayName("el server manda su config cuando el jugador entra")
    void theServerSendsOnJoin() {
        for (Path root : List.of(MAIN, V120)) {
            String ticker = codeOf(root, "com/cortes/combat/attack/CombatTicker.java");
            assertTrue(ticker.contains("ServerSettingsPayload"),
                    "**el server tiene que mandar la config al entrar.** Sin esto el cliente usa "
                            + "su propia copia del archivo y los dos lados cuentan cosas distintas");
            assertTrue(ticker.contains("ServerConfig.settings()"),
                    "y lo que manda es la config DEL SERVER, no una reimplementacion");
        }
    }

    @Test
    @DisplayName("la puerta responde con lo que le mandaron, y con su copia local si no le mandaron nada")
    void theDoorHasOneRule() {
        String door = codeOf(MAIN, "com/cortes/combat/client/SyncedSettings.java");

        assertTrue(door.contains("settings != null ?"),
                "**la puerta tiene una regla y es una**: si tengo el valor del server, ese; si "
                        + "no, mi copia local");
        assertTrue(door.contains("ServerConfig.combatRework()")
                        && door.contains("ServerConfig.particles()")
                        && door.contains("ServerConfig.warhammerTuning()"),
                "y el fallback es la copia local del cliente, que es lo que el cliente hacia "
                        + "siempre: el cambio no rompe a nadie que entre antes de que llegue el "
                        + "mensaje");
    }

    @Test
    @DisplayName("ningun lector del cliente pregunta la config directamente")
    void noReaderAsksTheConfigDirectly() {
        List<String> offenders = new ArrayList<>();
        for (Path root : List.of(MAIN, V120)) {
            Path client = root.resolve(Path.of("com", "cortes", "combat", "client"));
            if (!Files.isDirectory(client)) {
                continue;
            }
            try (Stream<Path> files = Files.list(client)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String name = file.getFileName().toString();
                    // La puerta SI puede mirar la config: es su fallback.
                    if (name.equals("SyncedSettings.java")) {
                        continue;
                    }
                    if (code(read(file)).contains("ServerConfig.")) {
                        offenders.add(root.getFileName() + "/" + name);
                    }
                }
            } catch (IOException e) {
                throw new AssertionError("could not walk " + client, e);
            }
        }

        assertEquals(List.of(), offenders,
                "**un lector del cliente que pregunta la config local lee la copia del cliente, "
                        + "no la del server.** Estos tienen que preguntar la puerta: "
                        + offenders);
    }

    @Test
    @DisplayName("la puerta se limpia cuando se termina el mundo")
    void theDoorIsCleanedOnUnload() {
        String cleanup = codeOf(MAIN, "com/cortes/combat/client/ClientWorldCleanup.java");

        assertTrue(cleanup.contains("SyncedSettings.clear()"),
                "**sin esto la puerta sigue respondiendo con los numeros del ultimo server** en el "
                        + "inventario creativo, que no tiene server");
    }

    @Test
    @DisplayName("el mensaje de 1.20.1 va al final de la lista del canal")
    void theOldLegMessageIsLast() {
        String networking = codeOf(V120, "com/cortes/combat/net/ModNetworking.java");

        int settings = networking.indexOf("messageBuilder(ServerSettingsPayload.class");
        assertTrue(settings > 0, "el mensaje tiene que estar registrado en el canal");

        assertEquals(-1, networking.substring(settings).replace(
                        "messageBuilder(ServerSettingsPayload.class", "").indexOf("messageBuilder("),
                "**y tiene que ser el ULTIMO.** Un id desconocido en un canal de Forge se loguea y "
                        + "se tira (verificado en el fuente de 47.4.28, IndexedMessageCodec#consume), "
                        + "asi que un cliente con un jar anterior no se desconecta -- pero solo si "
                        + "el id que le llega es el nuevo y no uno que el viejo ya usaba para otra "
                        + "cosa");
    }

    @Test
    @DisplayName("en 1.21.1 el payload es opcional, para no caer la conexion de un cliente viejo")
    void theNewLegPayloadIsOptional() {
        String networking = codeOf(MAIN, "com/cortes/combat/net/ModNetworking.java");

        int optional = networking.indexOf("registrar.optional()");
        int payload = networking.indexOf("playToClient(ServerSettingsPayload.TYPE");
        assertTrue(optional > 0 && payload > optional,
                "**el payload tiene que estar marcado como opcional.** Un cliente con un jar "
                        + "anterior no lo conoce, y si el server lo manda como obligatorio la "
                        + "conexion se cae");
    }

    @Test
    @DisplayName("la pata de 1.20.1 tiene su propio cuerpo del mensaje")
    void theOldLegHasItsOwnBody() {
        Path twin = V120.resolve(Path.of("com", "cortes", "combat", "net",
                "ServerSettingsPayload.java"));
        assertTrue(Files.isRegularFile(twin),
                "**falta el gemelo de 1.20.1.** 1.20.1 no tiene CustomPacketPayload: el mensaje "
                        + "es un record con encode/decode a mano");

        String source = code(read(twin));
        assertTrue(source.contains("ServerSettings.encode(buf, settings)"),
                "y delega en la clase que tiene el orden de los campos, para que las dos patas no "
                        + "tengan dos opiniones");
    }

    @Test
    @DisplayName("el gemelo esta en la lista de archivos con cuerpo propio")
    void theTwinIsListedInTheBuild() {
        String build = read(Path.of("build.gradle"));
        assertTrue(build.contains("'com/cortes/combat/net/ServerSettingsPayload.java'"),
                "**el archivo compartido tiene que estar en `v120Replaces`.** Sin eso la pata de "
                        + "1.20.1 compila el cuerpo de 1.21.1 -- que no tiene CustomPacketPayload "
                        + "-- y el error apunta al archivo equivocado");
    }
}
