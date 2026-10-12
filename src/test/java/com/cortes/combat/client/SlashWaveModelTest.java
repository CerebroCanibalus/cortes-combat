package com.cortes.combat.client;

import com.cortes.combat.weapon.SweepingEdge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ata los números del modelo de la onda de filo a los del JSON, y la orientación a su invariante.
 *
 * <h2>Por qué estos tests existen</h2>
 *
 * <p>Porque {@link SlashWaveModel} es una <b>copia</b> de un archivo del dueño: el centro, el alto,
 * el largo y el {@code texture_size} salen de
 * {@code assets/cortescombat/models/projectile/sweeping_edge.json}, y una copia de un número es una
 * segunda verdad. Es la misma jugada que {@code SabrePoseMatchesJavaTest} con la pose del sable --
 * el archivo no es autoritativo, es transcrito, y este test es lo que hace segura la transcripción.
 * </p>
 *
 * <h2>Y el que más vale: el encuadre de las UV</h2>
 *
 * <p>El bug que este test habría cazado: el export de Blockbench traía {@code texture_size: [64,64]}
 * con UV que llegan hasta 12, así que el plano dibujaba la región (0,0)-(12,6.5) de la textura -- un
 * trocito de la esquina -- mientras el dibujo vive en x 5..42, y 1..25. <b>Nada falla cuando eso
 * pasa</b>: el modelo se hornea, el renderer dibuja, y lo que se ve es un pedazo de tajo. Por eso el
 * test no pregunta si el archivo existe, pregunta si <b>el dibujo cae dentro de lo que el modelo
 * muestra</b> -- la misma distinción que dejó el bug de los encantamientos, donde tres aserciones
 * verdes convivían con un mundo que no cargaba.</p>
 */
class SlashWaveModelTest {

    private static final Path MODEL = Path.of("src", "main", "resources", "assets", "cortescombat",
            "models", "projectile", "sweeping_edge.json");
    private static final Path TEXTURE = Path.of("src", "main", "resources", "assets", "cortescombat",
            "textures", "projectile", "slash.png");

    private static String model() throws IOException {
        return Files.readString(MODEL, StandardCharsets.UTF_8);
    }

    private static double[] firstVector(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\\[\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)"
                + "\\s*,\\s*(-?[\\d.]+)\\s*\\]").matcher(json);
        assertTrue(m.find(), "el modelo no tiene un '" + field + "' de tres numeros: el test no "
                + "puede comparar nada, y un test que no encuentra su dato tiene que decirlo");
        return new double[] {Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
                Double.parseDouble(m.group(3))};
    }

    @Test
    @DisplayName("el centro y el tamano del modelo son los que la clase dice")
    void theConstantsAreTheModel() throws IOException {
        String json = model();
        double[] from = firstVector(json, "from");
        double[] to = firstVector(json, "to");

        assertEquals(SlashWaveModel.CENTER_X, (from[0] + to[0]) / 2.0 / 16.0, 1.0E-9,
                "el centro en X del modelo y la constante se separaron");
        assertEquals(SlashWaveModel.CENTER_Y, (from[1] + to[1]) / 2.0 / 16.0, 1.0E-9,
                "**el centro en Y es el que centra el tajo**: sin el, el modelo nace colgado de la "
                        + "esquina de un bloque y la onda sale del hombro del jugador");
        assertEquals(SlashWaveModel.CENTER_Z, (from[2] + to[2]) / 2.0 / 16.0, 1.0E-9);
        assertEquals(SlashWaveModel.HEIGHT, (to[1] - from[1]) / 16.0, 1.0E-9);
        assertEquals(SlashWaveModel.LENGTH, (to[2] - from[2]) / 16.0, 1.0E-9);
    }

    @Test
    @DisplayName("**el modelo muestra el dibujo, no una esquina de la textura**")
    void theUvWindowCoversTheArt() throws IOException {
        String json = model();
        Matcher size = Pattern.compile("\"texture_size\"\\s*:\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\]")
                .matcher(json);
        assertTrue(size.find(), "el modelo no declara texture_size, y sin eso las UV no significan "
                + "nada");
        int declared = Integer.parseInt(size.group(1));
        assertEquals(SlashWaveModel.TEXTURE_SIZE, declared,
                "**el texture_size declara en que escala estan las UV.** El export de Blockbench "
                        + "traia 64 con UV que llegan hasta 12, y eso hacia que el plano dibujara la "
                        + "region (0,0)-(12,6.5): un trocito de la esquina, sin un solo error en el "
                        + "log");

        Matcher uv = Pattern.compile("\"uv\"\\s*:\\s*\\[\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*,"
                + "\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*\\]").matcher(json);
        double maxU = 0.0;
        double maxV = 0.0;
        while (uv.find()) {
            maxU = Math.max(maxU, Math.max(Double.parseDouble(uv.group(1)),
                    Double.parseDouble(uv.group(3))));
            maxV = Math.max(maxV, Math.max(Double.parseDouble(uv.group(2)),
                    Double.parseDouble(uv.group(4))));
        }
        assertTrue(maxU > 0.0 && maxV > 0.0, "el modelo no tiene UV");

        BufferedImage art = ImageIO.read(TEXTURE.toFile());
        assertFalse(art == null, "no se pudo leer " + TEXTURE);
        int[] box = artBounds(art);
        assertFalse(box[0] < 0, "la textura del tajo esta vacia: no hay dibujo que encuadrar");

        // La ventana UV, en pixeles de la textura real.
        double left = 0.0 / declared * art.getWidth();
        double top = 0.0 / declared * art.getHeight();
        double right = maxU / declared * art.getWidth();
        double bottom = maxV / declared * art.getHeight();

        assertTrue(box[0] >= left - 1.0 && box[2] <= right + 1.0,
                "**el dibujo del tajo (x " + box[0] + ".." + box[2] + ") se sale de lo que el modelo "
                        + "muestra (x " + Math.round(left) + ".." + Math.round(right) + ").** Se ve "
                        + "un pedazo de arco, no el tajo: se arregla en texture_size, que es la "
                        + "escala en la que estan escritas las UV");
        assertTrue(box[1] >= top - 1.0 && box[3] <= bottom + 1.0,
                "**el dibujo del tajo (y " + box[1] + ".." + box[3] + ") se sale de lo que el modelo "
                        + "muestra (y " + Math.round(top) + ".." + Math.round(bottom) + ")");
    }

    /** El bounding box del contenido no transparente, o {@code -1} si no hay nada. */
    private static int[] artBounds(BufferedImage art) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < art.getHeight(); y++) {
            for (int x = 0; x < art.getWidth(); x++) {
                if ((art.getRGB(x, y) >>> 24) > 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? new int[] {-1, -1, -1, -1} : new int[] {minX, minY, maxX, maxY};
    }

    @Test
    @DisplayName("**la rotacion lleva el -Z del modelo a la direccion de vuelo**")
    void theAimPointsTheSlashAlongItsFlight() {
        // La invariante, y no la aritmetica de una linea: se aplican las dos rotaciones con las
        // convenciones de Minecraft y se pregunta a donde fue a parar **el frente del modelo, que es
        // -Z**. Es la unica forma de verificar los signos sin abrir el juego -- y los signos son
        // exactamente lo que este proyecto pago tres veces con el arcabuz.
        //
        // **Y el `-Z` no es un detalle:** hasta el playtest esta invariante se escribia sobre el +Z,
        // y era cierta con un sprite que miraba para atras. Ver `SlashWaveModel#yawDegrees`.
        double[][] directions = {
                {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0},
                {0.6, 0.0, 0.8}, {0.6, -0.6, 0.53}, {-0.3, 0.4, 0.86},
        };
        for (double[] dir : directions) {
            // **El test normaliza, el renderer tambien, y el vector de entrada es feo a proposito.**
            // La primera version de este test comparaba contra las componentes crudas y fallaba con
            // [0.6, -0.6, 0.53], que no mide uno -- o sea que el que estaba mal era el test. Dejarlo
            // asi es lo que hace que tambien pruebe la normalizacion.
            double length = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
            double[] aimed = aim(dir[0], dir[1], dir[2]);
            assertEquals(dir[0] / length, aimed[0], 1.0E-5, "X con " + java.util.Arrays.toString(dir));
            assertEquals(dir[1] / length, aimed[1], 1.0E-5, "Y con " + java.util.Arrays.toString(dir));
            assertEquals(dir[2] / length, aimed[2], 1.0E-5, "Z con " + java.util.Arrays.toString(dir));
        }
    }

    /**
     * A dónde va a parar el {@code (0,0,1)} del modelo después de las dos rotaciones del renderer.
     *
     * <p>Las convenciones, que son las de JOML y las que usa {@code PoseStack}: rotar alrededor de Y
     * por {@code t} manda {@code (x,z)} a {@code (x cos t + z sin t, -x sin t + z cos t)}, y rotar
     * alrededor de X por {@code p} manda {@code (y,z)} a {@code (y cos p - z sin p, y sin p +
     * z cos p)}. El orden es el del renderer: {@code R_y * R_x}, o sea X primero.</p>
     */
    private static double[] aim(double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double nx = dx / length;
        double ny = dy / length;
        double nz = dz / length;

        double horizontal = SlashWaveModel.horizontal(nx, nz);
        double yaw = Math.toRadians(SlashWaveModel.yawDegrees(nx, nz));
        double pitch = Math.toRadians(SlashWaveModel.pitchDegrees(ny, horizontal));

        // R_x sobre (0,0,-1) -- el **frente** del modelo, que es -Z y no +Z. Ver la nota de
        // `SlashWaveModel#yawDegrees`: el elemento tiene cero de espesor en X, asi que lo que decide
        // hacia donde mira es su normal, y la normal esta en X. Alinear el +Z dejaba la cara visible
        // mirando para atras, que es el bug que reporto el playtest ("el sprite esta volteado al
        // reves, mira hacia mi").
        double x = 0.0;
        double y = Math.sin(pitch);
        double z = -Math.cos(pitch);
        // y despues R_y.
        return new double[] {
                x * Math.cos(yaw) + z * Math.sin(yaw),
                y,
                -x * Math.sin(yaw) + z * Math.cos(yaw),
        };
    }

    @Test
    @DisplayName("el modelo se agranda con el nivel, y el tamano es el ancho del tajo")
    void theModelGrowsWithTheLevel() {
        assertEquals(SweepingEdge.width(1), SlashWaveModel.scale(1), 1.0E-9,
                "la escala ES el ancho del tajo: un segundo numero para lo mismo se desincroniza");
        assertEquals(SweepingEdge.width(2), SlashWaveModel.scale(2), 1.0E-9);
        assertEquals(SweepingEdge.width(3), SlashWaveModel.scale(3), 1.0E-9);
        assertTrue(SlashWaveModel.scale(2) > SlashWaveModel.scale(1),
                "**un nivel que no se ve no existe**: si los tres escalan igual, el jugador no "
                        + "puede saber que subio");
        assertTrue(SlashWaveModel.scale(3) > SlashWaveModel.scale(2));
        assertEquals(0.0, SlashWaveModel.scale(0), 1.0E-9,
                "sin el encantamiento no hay onda, y una escala de cero no la dibuja");
    }

    @Test
    @DisplayName("el sprite esta declarado en el atlas de bloques, o el modelo se dibuja magenta")
    void theSpriteIsInTheAtlas() throws IOException {
        // **El ultimo eslabon, y el unico que ningun otro test miraba.**
        //
        // Un modelo side-loaded (`ModelEvent.RegisterAdditional`) llega a hornearse -- el log lo
        // dijo: `horneado=true` -- y **igual le falta el sprite**. Un modelo sin hornear y un sprite
        // sin coser se dibujan los dos como el patron magenta y negro, asi que el sintoma no
        // distingue la causa y el diagnostico costo una ronda entera. Ver
        // `SlashWaveRenderer#onBakingCompleted`, que ahora los separa con una linea.
        //
        // Se arregla declarando el sprite en `assets/minecraft/atlases/blocks.json`, y **eso no pisa
        // nada**: `SpriteSourceList#load` lee con `getResourceStack` y CONCATENA los sources de todos
        // los packs (`list.addAll`), verificado en las fuentes de 1.21.1. Era el miedo que me habia
        // frenado -- un `blocks.json` mal puesto y desaparecen todas las texturas del juego.
        Path atlas = Path.of("src", "main", "resources", "assets", "minecraft", "atlases",
                "blocks.json");
        assertTrue(Files.isRegularFile(atlas), "falta " + atlas
                + ": sin el, el sprite no esta en el atlas y la onda se dibuja magenta");
        String atlasJson = Files.readString(atlas, StandardCharsets.UTF_8);

        Matcher sprite = Pattern.compile("\"resource\"\\s*:\\s*\"([^\"]+)\"").matcher(atlasJson);
        assertTrue(sprite.find(), "el atlas no declara ningun sprite: " + atlasJson);
        String id = sprite.group(1);

        // Y el nombre tiene que ser EL MISMO que el modelo pide: dos nombres para una textura es un
        // bug que se ve y no se lee en ningun archivo.
        String model = Files.readString(MODEL, StandardCharsets.UTF_8);
        assertTrue(model.contains("\"" + id + "\""),
                "el atlas mete `" + id + "` y el modelo pide otra cosa: el sprite se cose al atlas y "
                        + "el modelo sigue dibujando el faltante");

        String[] parts = id.split(":", 2);
        assertEquals(2, parts.length, "el sprite tiene que llevar namespace: " + id);
        Path png = Path.of("src", "main", "resources", "assets", parts[0],
                "textures", parts[1] + ".png");
        assertTrue(Files.isRegularFile(png), "el atlas nombra `" + id + "` y no hay " + png);
    }
}
