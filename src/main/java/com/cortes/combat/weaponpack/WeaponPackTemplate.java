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

    /** El ejemplo de YAML. La extensión {@code .example} es lo que lo saca de la vista del loader. */
    static final String CONFIG_EXAMPLE = "ejemplo.yaml.example";

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
        // Los dos son independientes: que el resource pack ya exista no puede impedir que
        // aparezca el ejemplo de config, que es de otra carpeta y de otra historia.
        Path pack = FMLPaths.GAMEDIR.get().resolve("resourcepacks").resolve(ROOT);
        if (writeResourcePackFiles(pack)) {
            CortesCombat.LOGGER.info("[weaponpack] template de resource pack en {}", pack);
        }

        Path example = FMLPaths.CONFIGDIR.get().resolve(CortesCombat.MODID)
                .resolve("weapons").resolve(CONFIG_EXAMPLE);
        if (writeConfigExample(example)) {
            CortesCombat.LOGGER.info("[weaponpack] ejemplo de config en {}", example);
        }
    }

    /**
     * El resource pack de ejemplo, con su {@code LEEME} en los dos idiomas.
     *
     * @param root la carpeta {@code resourcepacks/cortescombat-weapon-pack}
     * @return {@code true} si se escribió algo
     */
    static boolean writeResourcePackFiles(Path root) {
        if (Files.exists(root)) {
            return false;
        }
        try {
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
            Files.writeString(root.resolve("LEEME.txt"), README_ES, StandardCharsets.UTF_8);
            Files.writeString(root.resolve("README.txt"), README_EN, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            CortesCombat.LOGGER.warn("[weaponpack] no se pudo escribir el template en {}: {}",
                    root, e.getMessage());
            return false;
        }
    }

    /**
     * El ejemplo de YAML, con cada campo anotado en español y en inglés.
     *
     * <p><b>Por qué la extensión es {@code .example}</b>: el loader lee todos los {@code *.yaml}
     * y {@code *.yml} de la carpeta, así que un archivo de ejemplo con esa extensión se leería
     * como un pack vacío y dejaría un warning en el log de cada arranque. Con {@code .example}
     * el loader ni lo ve, y el usuario lo copia a {@code mi_pack.yaml} cuando lo quiere.
     *
     * @param example la ruta del ejemplo, dentro de {@code config/cortescombat/weapons}
     * @return {@code true} si se escribió
     */
    static boolean writeConfigExample(Path example) {
        if (Files.exists(example)) {
            return false;
        }
        try {
            Files.createDirectories(example.getParent());
            Files.writeString(example, CONFIG_EXAMPLE_YAML, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            CortesCombat.LOGGER.warn("[weaponpack] no se pudo escribir el ejemplo en {}: {}",
                    example, e.getMessage());
            return false;
        }
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

    /**
     * El LEEME en español, y el README en inglés al lado.
     *
     * <p><b>Dos archivos y no uno bilingüe</b>: la mezcla de dos idiomas en el mismo archivo se
     * lee mal de arriba abajo, y la mitad de quien lo abre no lee el otro idioma. Cada archivo
     * está en un idioma y el otro está al lado en la misma carpeta.
     */
    private static final String README_ES = """
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

            La primera vez, el mod también escribe un ejemplo comentado en
            config/cortescombat/weapons/ejemplo.yaml.example, con cada campo anotado en
            español y en inglés. Copialo a mi_pack.yaml y editá lo que quieras.

            Si el juego reclama el pack_format al activarlo, el número que pide es el correcto:
            pónelo en pack.mcmeta.

            Guía completa: docs/custom-weapons.es.md (español) y docs/custom-weapons.md (inglés).
            """;

    /** El mismo LEEME, en inglés. */
    private static final String README_EN = """
            WEAPON PACK OF ASSETS - Cortes Combat
            =======================================

            This folder is the home of your weapons' assets. Activate it as a resource pack
            (Options > Resource Packs), or the server offers it on join in multiplayer.

            Where everything goes (replace 'cortescombat' with your item's namespace):

              assets/<ns>/models/item/<id>.json     the item's icon (the model)
              assets/<ns>/textures/item/<id>.png     its texture
              assets/<ns>/textures/particle/slash/   the slash sprite (32x160, 5 frames)
                                                    referenced by texture.slash in the YAML
              assets/<ns>/poses/<id>.json            the animation, referenced by animation.pose
              assets/<ns>/sounds.json + sounds/      the sounds, referenced by sounds.swing, etc.

            The STATS (damage, cone, charge...) go in config/cortescombat/weapons/*.yaml, NOT
            here: that folder is read by both sides and this one is only loaded as a resource
            pack.

            On the first run the mod also writes an annotated example at
            config/cortescombat/weapons/ejemplo.yaml.example, with every field commented in
            Spanish and English. Copy it to mi_pack.yaml and edit what you like.

            If the game complains about the pack_format when you activate it, the number it asks
            for is the right one: put it in pack.mcmeta.

            Full guide: docs/custom-weapons.md (English) and docs/custom-weapons.es.md (Spanish).
            """;

    /**
     * El ejemplo de config, anotado en los dos idiomas.
     *
     * <p><b>Un archivo, dos idiomas, una línea por campo</b>: el español arriba y el inglés
     * abajo de cada campo. Al revés quedaría un archivo en inglés con un postscript en
     * español, y el que sólo lee uno de los dos se queda con la mitad de las anotaciones.
     */
    private static final String CONFIG_EXAMPLE_YAML = """
            # ==========================================================================
            #  EJEMPLO DE ARMAS PROPIAS  /  CUSTOM WEAPONS EXAMPLE
            #  Cortes Combat -- weapon packs
            # ==========================================================================
            #
            #  Este archivo NO se carga solo. Copialo a config/cortescombat/weapons/mi_pack.yaml
            #  (la extensión tiene que ser .yaml o .yml) y editá lo que quieras.
            #  This file is NOT loaded on its own. Copy it to config/cortescombat/weapons/mi_pack.yaml
            #  (the extension has to be .yaml or .yml) and edit what you like.
            #
            #  Después: /cc reload. Los assets nuevos (iconos, sonidos) piden F3+T.
            #  After that: /cc reload. New assets (icons, sounds) need F3+T.
            #
            #  La guía completa / the full guide:
            #    docs/custom-weapons.es.md (español)  /  docs/custom-weapons.md (English)
            # ==========================================================================

            # Obligatoria. Este mod entiende la 1.
            # Required. This mod understands version 1.
            version: 1

            # La lista de armas. Cada entrada es un ítem.
            # The list of weapons. Each entry is one item.
            weapons:

              # ----------------------------------------------------------------------
              # EJEMPLO 1 -- tomar un arma de otro mod y darle números
              # EXAMPLE 1 -- take another mod's weapon and give it numbers
              # ----------------------------------------------------------------------
              - # Identidad: el ítem al que aplica esta entrada.
                # Identity: the item this entry applies to.
                #   match: { id: tercermod:espada_rara }   -> por id exacto
                #   match: { tag: "#tercermod:espadas" }   -> por tag
                match: { id: minecraft:iron_sword }

                # El molde: "tratálo como esto". Da el movimiento, el especial y los defaults.
                # The template: "treat it like this". It gives the movement, the special and
                # the defaults.
                #   dagger, sword, rapier, sabre, hammer, mace, spear, polearm, axe
                archetype: sword

                # Los números. Todos opcionales: lo que no declares se hereda del arquetipo.
                # The numbers. All optional: whatever you do not declare comes from the
                # archetype.
                stats:
                  # Daño en CORAZONES. La vanilla dice 4 = 2 corazones.
                  # Damage in HEARTS. Vanilla says 4 = 2 hearts.
                  damage: 3.5
                  # Toques por segundo. El tempo. La espada vanilla es 1.6.
                  # Swings per second. The tempo. The vanilla sword is 1.6.
                  attack_speed: 1.8
                  # Usos. Sólo aplica a las entradas con `creates:`.
                  # Uses. Only applies to entries with `creates:`.
                  durability: 260
                  # La carga. El bonus NUNCA resta, y el piso es 0.
                  # The charge. The bonus NEVER subtracts, and the floor is 0.
                  charge:
                    # ¿Carga? (true / false)
                    # Does it charge? (true / false)
                    can_charge: true
                    # Multiplicador a carga completa. La guerra es 1.0.
                    # Multiplier at full charge. The war hammer is 1.0.
                    bonus: 0.6
                    # Ticks hasta la carga completa.
                    # Ticks to a full charge.
                    full_charge_ticks: 25
                  # El empuje. Techos 1.6 y 0.45: el código avisa si los pasás.
                  # The knockback. Ceilings 1.6 and 0.45: the code complains if you pass them.
                  knockback:
                    horizontal: 0.16
                    vertical: 0.03

                # El alcance, en bloques. SÓLO la distancia.
                # The reach, in blocks. The DISTANCE only.
                reach:
                  distance: 3.6

                # El cono, en GRADOS, por eje. No hay tamaños: hay grados.
                # The cone, in DEGREES, per axis. There are no sizes, there are degrees.
                cone:
                  horizontal: 35
                  vertical: 30

                # La guardia. Aguante, arco, regeneración y stun, en ticks.
                # The guard. Stamina, arc, regeneration and stun, in ticks.
                #   sword 1/90/40/100, shield 3/120
                guard:
                  points: 1
                  arc: 90
                  regen: 40
                  stun: 100

                # El click derecho. Uno de: none, multi_hit, parry, slam, shove, lunge, throw,
                # fusee. Sin declarar, el del arquetipo.
                # The right click. One of: none, multi_hit, parry, slam, shove, lunge, throw,
                # fusee. Left out, the archetype's one applies.
                special: none

                # El sprite del golpe, en el resource pack. PNG de 32x160 con 5 frames.
                # The slash sprite, in the resource pack. A 32x160 PNG with 5 frames.
                texture:
                  slash: cortescombat:textures/particle/slash/iron_sword.png

                # Los sonidos, con los ids de tu sounds.json.
                # The sounds, with the ids from your sounds.json.
                #   swing suena cuando el golpe NO conecta.
                #   swing plays when the swing misses.
                sounds:
                  swing: cortescombat:iron_sword_swing
                  hit: cortescombat:iron_sword_hit
                  charged: cortescombat:iron_sword_charged

                # La pose, en el resource pack. Si falta, se usa la del arquetipo.
                # The pose, in the resource pack. If missing, the archetype's one is used.
                animation:
                  pose: sword

              # ----------------------------------------------------------------------
              # EJEMPLO 2 -- un arma nueva de cero
              # EXAMPLE 2 -- a brand new weapon
              # ----------------------------------------------------------------------
              - # El id que tu mod va a registrar. Con esto, el ítem lo crea el mod.
                # The id your mod is going to register. With this, the mod creates the item.
                creates: mitunmod:cuchillo_de_rubi
                archetype: dagger
                stats:
                  damage: 5.0
                  durability: 260
                display:
                  # El nombre, directo. Sin archivo de idioma.
                  # The name, direct. No lang file.
                  name: "Cuchillo de Rubi"
                  # La pestaña donde aparece. Vacío = la nuestra.
                  # The tab it shows up in. Empty = ours.
                  tab: cortescombat

              # ----------------------------------------------------------------------
              # EJEMPLO 3 -- sacar un ítem del combate
              # EXAMPLE 3 -- take an item out of combat
              # ----------------------------------------------------------------------
              - match: { id: minecraft:iron_pickaxe }
                # none = "esto no es un arma".
                # none = "this is not a weapon".
                profile: none
            """;
}
