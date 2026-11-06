package com.cortes.combat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El server no puede nombrar una clase de cliente. Y esta clase es la que lo dice.
 *
 * <h2>El crash</h2>
 *
 * <p>Un server dedicado de 1.21.1 se caia con el primer jugador que se conectaba:</p>
 *
 * <pre>
 * java.lang.RuntimeException: Attempted to load class net/minecraft/client/player/LocalPlayer
 *         for invalid dist DEDICATED_SERVER
 *     at com.cortes.combat.attack.CombatTicker.onStopTracking(CombatTicker.java:92)
 * </pre>
 *
 * <p>La causa era una sola linea: un handler registrado en el <b>game bus</b> (que corre en los
 * dos lados) comparaba contra {@code LocalPlayer}, que es una clase de cliente. En un server
 * dedicado, cargar esa clase es un error: NeoForge lo convierte en {@code RuntimeException} y
 * Forge 1.20.1 seria un {@code NoClassDefFoundError}. El tick del jugador mueria con ella, y el
 * watchdog mataba el server entero.</p>
 *
 * <p><b>Y el server de pruebas no lo vio nunca</b>, porque el evento que lo disparaba
 * ({@code PlayerEvent.StopTracking}) solo se postea desde {@code ServerEntity.removePairing}, o
 * sea cuando un jugador deja de trackear algo: hace falta un cliente conectado. El mod llevaba
 * meses en verde con la mina adentro, y el build no dice nada -- un {@code instanceof} que
 * parece inocuo.</p>
 *
 * <h2>Lo que este guard afirma</h2>
 *
 * <ol>
 *   <li><b>Ninguna clase que el server registra puede nombrar una clase de cliente.</b> La lista
 *       no esta escrita a mano: se camina el punto de entrada comun de cada pata y se leen las
 *       clases que registra en el game bus. Agregar un handler nuevo queda cubierto sin que nadie
 *       tenga que acordarse de este archivo.</li>
 *   <li><b>El crash no puede volver por el mismo camino.</b> Los dos {@code CombatTicker} --
 *       el compartido y su gemelo de 1.20.1 -- sin el {@code LocalPlayer} ni el
 *       {@code StopTracking}.</li>
 *   <li><b>La limpieza que se movio esta cableada en las dos patas.</b> Borrar el handler sin
 *       poner la limpieza en el cliente deja la fuga de memoria que el handler prometeria
 *       arreglar, y eso no lo dice ningun error: es un silencio.</li>
 * </ol>
 *
 * <p><b>Y los comentarios no cuentan.</b> Un guard de fuente que lee prosa se muerde su propia
 * explicacion -- ya pago tres rondas el mismo mordisco ({@code ModifyExpressionValue} nombrado en
 * un javadoc, {@code SoundPoolTest} tirando la carpeta, el guard del reactor que encontro su
 * comentario). El texto que se aserciona es codigo; el texto que explica vive en los
 * comentarios. (⌒‿⌒)</p>
 */
class ServerSideSafetyGuardTest {

    /** El punto de entrada comun de cada pata: el que registra en el game bus. */
    private static final Path MAIN_ENTRY = Path.of("src", "main", "java", "com", "cortes",
            "combat", "CortesCombat.java");
    private static final Path V120_ENTRY = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "CortesCombat.java");

    private static final Path MAIN_ROOT = Path.of("src", "main", "java");
    private static final Path V120_ROOT = Path.of("src", "v120", "java");

    /**
     * Lo que se registra en el game bus: {@code register(Clase.class)} y
     * {@code addListener(Clase::metodo)}. Las dos formas, porque el punto de entrada usa las dos y
     * la segunda nombra la clase sin el {@code .class}.
     */
    private static final Pattern REGISTRATION = Pattern.compile(
            "EVENT_BUS\\.(?:register|addListener)\\(\\s*"
                    + "([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*)\\s*(?:\\.class\\b|::)");

    private static final Pattern IMPORT = Pattern.compile(
            "^import\\s+(com\\.cortes\\.combat\\.[A-Za-z0-9_.]+);", Pattern.MULTILINE);

    @Test
    @DisplayName("no class the server registers may name a client class")
    void theServerNeverNamesAClientClass() {
        Set<String> registered = new LinkedHashSet<>();
        registered.addAll(registeredClasses(MAIN_ENTRY));
        registered.addAll(registeredClasses(V120_ENTRY));

        assertFalse(registered.isEmpty(),
                "no game bus registration was found in " + MAIN_ENTRY + " or " + V120_ENTRY
                        + " -- the guard would be vacuous, and a vacuous guard protects nothing");

        for (String fqn : registered) {
            for (Path file : variants(fqn)) {
                String code = code(file);
                assertFalse(code.contains("net.minecraft.client."),
                        file + " names a Minecraft client class, and the server registers it on the "
                                + "game bus -- a dedicated server cannot load that class and the "
                                + "crash lands on the first player who connects");
                assertFalse(code.contains("com.cortes.combat.client."),
                        file + " names our own client package, and the server registers it on the "
                                + "game bus -- the client package is client-only by contract");
            }
        }
    }

    @Test
    @DisplayName("the crash cannot come back through CombatTicker")
    void theCrashIsGoneFromBothLegs() {
        for (String leg : new String[]{"main", "v120"}) {
            Path file = legFile(leg, "com.cortes.combat.attack.CombatTicker");
            assertTrue(Files.exists(file), file + " is missing");
            String code = code(file);
            assertFalse(code.contains("LocalPlayer"),
                    file + " names LocalPlayer again: the server runs this class, and loading a "
                            + "client class there is what killed the dedicated server");
            assertFalse(code.contains("StopTracking"),
                    file + " listens to StopTracking again: that event is posted by the SERVER "
                            + "(ServerEntity.removePairing) and never reaches the client");
        }
    }

    @Test
    @DisplayName("the cleanup that replaced it is wired on both legs")
    void theClientCleanupIsWiredInBothLegs() {
        Path shared = MAIN_ROOT.resolve(Path.of("com", "cortes", "combat", "client",
                "RemotePoseCleanup.java"));
        assertTrue(Files.exists(shared), shared + " is missing: the client cleanup has to live in "
                + "a shared file, because the event it listens to has the same name on both legs");
        String code = code(shared);
        assertTrue(code.contains("EntityLeaveLevelEvent"),
                shared + " does not listen to EntityLeaveLevelEvent, which is the event the client "
                        + "actually receives when it stops tracking an entity");
        assertTrue(code.contains("RemotePoseStates.forget"),
                shared + " does not forget the clock: the leak it replaced would come back");

        for (String leg : new String[]{"main", "v120"}) {
            Path entry = legFile(leg, "com.cortes.combat.client.CortesCombatClient");
            assertTrue(Files.exists(entry), entry + " is missing");
            assertTrue(code(entry).contains("register(RemotePoseCleanup.class)"),
                    entry + " does not register RemotePoseCleanup: a listener nobody registers is "
                            + "a listener that never fires, and nothing says so");
        }
    }

    @Test
    @DisplayName("the payload handlers the server runs never name a client class either")
    void theServerSidePayloadHandlersNeverNameAClientClass() {
        Path file = MAIN_ROOT.resolve(Path.of("com", "cortes", "combat", "net",
                "ModNetworking.java"));
        assertTrue(Files.exists(file), file + " is missing");
        String source = code(file);

        List<String> blocks = playToServerBlocks(source);
        assertFalse(blocks.isEmpty(),
                file + " has no playToServer registration: the guard would be vacuous, and a "
                        + "vacuous guard protects nothing");

        for (String block : blocks) {
            assertNoClientReference(file, block);
            // **Y el metodo al que apunta.** `playToServer(Payload.TYPE, CODEC, ModNetworking::handleX)`
            // no ejecuta el handler ahi: lo ejecuta despues, y ahi es donde puede tocar una clase
            // de cliente. El cuerpo del handler es parte de la misma puerta.
            Matcher targets = Pattern.compile("ModNetworking::(\\w+)").matcher(block);
            while (targets.find()) {
                String body = methodBody(source, "static void " + targets.group(1) + "(");
                assertTrue(body != null, file + " lost the handler " + targets.group(1));
                assertNoClientReference(file, body);
            }
        }
    }

    private static void assertNoClientReference(Path file, String code) {
        assertFalse(code.contains("net.minecraft.client."),
                file + " registers a handler on the server that names a Minecraft client class: "
                        + "the server cannot load it and the crash lands on the first packet");
        assertFalse(code.contains("com.cortes.combat.client."),
                file + " registers a handler on the server that names our own client package, "
                        + "which is client-only by contract");
    }

    /** The text of every {@code playToServer(...)} registration, with its balanced parentheses. */
    private static List<String> playToServerBlocks(String source) {
        List<String> out = new ArrayList<>();
        String marker = "playToServer(";
        int from = 0;
        while (true) {
            int at = source.indexOf(marker, from);
            if (at < 0) {
                return out;
            }
            int open = at + marker.length() - 1;
            int depth = 0;
            int end = -1;
            for (int i = open; i < source.length(); i++) {
                char c = source.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            if (end < 0) {
                return out;
            }
            out.add(source.substring(open + 1, end));
            from = end + 1;
        }
    }

    /** The body of a method, from its opening brace to the matching close. */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            return null;
        }
        int open = source.indexOf('{', start);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * The classes the entry point registers on the game bus, as fully qualified names.
     *
     * <p>Simple names are resolved through the imports of the file, because that is how the
     * registrations are written ({@code CombatTicker.class} with an import above it).</p>
     */
    private static List<String> registeredClasses(Path entry) {
        List<String> out = new ArrayList<>();
        if (!Files.exists(entry)) {
            return out;
        }
        String source = code(entry);
        Matcher matcher = REGISTRATION.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            out.add(name.indexOf('.') >= 0 ? name : resolveSimpleName(source, name));
        }
        return out;
    }

    private static String resolveSimpleName(String source, String name) {
        Matcher imports = IMPORT.matcher(source);
        while (imports.find()) {
            String fqn = imports.group(1);
            if (fqn.endsWith("." + name)) {
                return fqn;
            }
        }
        return "com.cortes.combat." + name;
    }

    /** Both legs of a class: the shared file and, when it exists, the 1.20.1 twin. */
    private static List<Path> variants(String fqn) {
        List<Path> out = new ArrayList<>();
        Path shared = MAIN_ROOT.resolve(fqn.replace('.', '/') + ".java");
        if (Files.exists(shared)) {
            out.add(shared);
        }
        Path v120 = V120_ROOT.resolve(fqn.replace('.', '/') + ".java");
        if (Files.exists(v120) && !out.contains(v120)) {
            out.add(v120);
        }
        return out;
    }

    private static Path legFile(String leg, String fqn) {
        Path root = "main".equals(leg) ? MAIN_ROOT : V120_ROOT;
        return root.resolve(fqn.replace('.', '/') + ".java");
    }

    /** The source with every comment removed: what the guard aserts on is code, not prose. */
    private static String code(Path file) {
        try {
            return stripComments(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError("the source is unreadable: " + file, e);
        }
    }

    private static String stripComments(String source) {
        String withoutBlocks = source.replaceAll("(?s)/\\*.*?\\*/", " ");
        StringBuilder out = new StringBuilder(withoutBlocks.length());
        for (String line : withoutBlocks.split("\n", -1)) {
            int cut = line.indexOf("//");
            out.append(cut < 0 ? line : line.substring(0, cut)).append('\n');
        }
        return out.toString();
    }
}
