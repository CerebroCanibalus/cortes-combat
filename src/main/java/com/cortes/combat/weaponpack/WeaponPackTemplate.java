package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * El template de resource pack que la primera ejecución escribe en {@code resourcepacks/}.
 *
 * <p><b>Por qué existe:</b> los assets sólo viven en resource packs -- {@code config/} no los
 * carga nunca -- y un packmate que acaba de crear su primer ítem necesita dónde soltar el
 * primer PNG sin adivinar ni el {@code pack.mcmeta} ni la estructura de carpetas. Un solo
 * gesto de escritura la primera vez, y después es del usuario.
 *
 * <p><b>No se pisa nada</b>: si la carpeta ya existe, no se escribe un byte -- el usuario pudo
 * borrar lo que no quiera, renombrar, o ya tener lo suyo.
 */
public final class WeaponPackTemplate {

    static final String ROOT = "cortescombat-weapon-pack";

    private WeaponPackTemplate() {
    }

    /**
     * Escribe el template si su carpeta no existe. Cualquier fallo es warning, nunca crash.
     *
     * <p>El logger vive <em>acá</em> y no en el método de disco: cargar {@code CortesCombat}
     * arrastra tipos de Minecraft y el test del template corre en JVM pelada sin ellos --
     * la primera versión falló con {@code NoClassDefFoundError: ItemLike} desde un
     * {@code LOGGER.info}. El I/O es puro y el logger es del wrapper.
     */
    public static void writeIfMissing() {
        Path root = FMLPaths.GAMEDIR.get().resolve("resourcepacks").resolve(ROOT);
        if (Files.exists(root)) {
            return;
        }
        try {
            writeIfMissing(root);
            CortesCombat.LOGGER.info("[weaponpack] template de resource pack en {}", root);
        } catch (IOException e) {
            CortesCombat.LOGGER.warn("[weaponpack] no se pudo escribir el template en {}: {}",
                    root, e.getMessage());
        }
    }

    /** La raíz como parámetro para que el test lo pruebe sin un juego ni {@code FMLPaths}. */
    static void writeIfMissing(Path root) throws IOException {
        if (Files.exists(root)) {
            return;
        }
        Files.createDirectories(root.resolve("assets").resolve(CortesCombat.MODID)
                .resolve("models").resolve("item"));
        Files.createDirectories(root.resolve("assets").resolve(CortesCombat.MODID)
                .resolve("textures").resolve("item"));
        Files.createDirectories(root.resolve("assets").resolve(CortesCombat.MODID)
                .resolve("textures").resolve("particle").resolve("slash"));
        Files.createDirectories(root.resolve("assets").resolve(CortesCombat.MODID)
                .resolve("poses"));
        Files.createDirectories(root.resolve("assets").resolve(CortesCombat.MODID)
                .resolve("sounds"));
        Files.writeString(root.resolve("pack.mcmeta"), PACK_METADATA, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("LEEME.txt"), README, StandardCharsets.UTF_8);
    }

    /**
     * {@code pack_format} 34: el de recursos de cliente para 1.21.1. Si una versión de vanilla
     * lo cambia, el juego lo dice en pantalla al cargar el pack y el número se corrige acá --
     * un formato viejo igual carga (con aviso), uno corrupto no.
     */
    private static final String PACK_METADATA = """
            {
              "pack": {
                "pack_format": 34,
                "description": "Iconos, sonidos y poses de tus weapon packs (Cortes Combat)"
              }
            }
            """;

    private static final String README = """
            WEAPON PACK DE ASSETS - Cortes Combat
            ======================================

            Esta carpeta es la casa de los assets de tus armas. Activala como resource pack
            (Opciones > Paquetes de recursos), o el server la ofrece al conectar en multijugador.

            Donde va cada cosa (reemplaza 'cortescombat' por el namespace de tu ítem):

              assets/<ns>/models/item/<id>.json     icono del ítem (el modelo)
              assets/<ns>/textures/item/<id>.png     su textura
              assets/<ns>/textures/particle/slash/   sprite del golpe (32x160, 5 frames)
                                                    se referencia con texture.slash en el YAML
              assets/<ns>/poses/<id>.json            animación, se referencia con animation.pose
              assets/<ns>/sounds.json + sounds/      sonidos, se referencian con sounds.swing, etc.

            Los STATS (daño, cono, carga...) van en config/cortescombat/weapons/*.yaml, NO acá:
            esa carpeta la leen los dos lados y esta sólo se carga como resource pack.

            Si el juego reclama el pack_format al activarlo, el número que pide es el correcto:
            pónelo en pack.mcmeta.

            Ejemplo completo comentado: docs/weapon-packs-design.md (§4) del repositorio.
            """;
}
