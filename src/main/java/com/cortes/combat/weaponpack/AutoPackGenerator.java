package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.item.AutoProfile;
import com.cortes.combat.platform.WeaponAttr;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * El generador de weapon packs para los mods que podemos detectar: un
 * {@code auto-<modid>.yaml} por mod en {@code config/cortescombat/weapons/}, con una entrada
 * por arma ajena agrupada por arquetipo.
 *
 * <p><b>Corre en {@code ServerStartedEvent} y no antes, por la leccion ya pagada</b>: en
 * common setup los tags de items no estan bindeados y {@code stack.is(tag)} responde falso para
 * todos -- el generador produce cero entradas con el build verde, y la abstencion por pack
 * cargado tambien miente. Un diagnosticador que miente en la forma del bug que cacha es peor que
 * ningun diagnosticador (ModPlatform ya lo pago una vez).</p>
 *
 * <h2>Las tres reglas del archivo que sale</h2>
 *
 * <ol>
 *   <li><b>Se escribe una vez y despues es del usuario</b> -- se borra para regenerar, se edita
 *       para balancear. La unica excepcion es un salto de version del generador, que respalda el
 *       archivo viejo como {@code .bak} y escribe uno nuevo: sin eso, las reglas nuevas de
 *       deteccion no llegan nunca a una partida ya creada. (μ_μ)</li>
 *   <li><b>No declara dano ni velocidad</b> -- el arma ajena ya trae los suyos por material, y
 *       pisarlos seria pelear contra el balance del otro mod. El tier escala la CARGA en el
 *       codigo ({@code AutoProfile.tieredChargeTicks/Bonus}), que era el numero ciego al
 *       material del molde.</li>
 *   <li><b>La cobertura ya cargada abstiene</b> -- un item que cualquier pack (a mano o el
 *       builtin de categorias) ya contesta no genera linea: el YAML del usuario manda, que es
 *       la regla de oro de la capa 2.</li>
 * </ol>
 *
 * <p><b>Y clasifica sin exigir el tag de espada.</b> El nombre del id manda (es lo que el mod
 * quiso fabricar); si no dice nada, los atributos deciden; y una herramienta se veta antes de
 * que el fallback la convierta en arma. Ese orden es el que hace que Simply Swords entero tenga
 * stats aunque la mitad de sus armas no digan "sword" en el id. (⌒‿⌒)</p>
 *
 * <p><b>El texto lo arma {@link AutoPackYaml}, que es puro.</b> Esta clase es el adaptador al
 * juego y nada mas: escanea el registro, clasifica, y delega el formato. (￣ε￣＠)</p>
 */
public final class AutoPackGenerator {

    static final String PREFIX = AutoPackYaml.AUTO_PREFIX;

    /** El piso de dano de la escala del tooltip por debajo del cual el item es un juguete. */
    static final float MIN_WEAPON_DAMAGE = 1.0F;

    private AutoPackGenerator() {
    }

    /**
     * Escanea el registro de items, clasifica las armas ajenas y escribe un YAML por mod.
     *
     * <p>Toda clase de falla es warning: un generador roto nunca tumba el arranque de un
     * mundo -- la ronda entera es una conveniencia, no un requisito de carga.</p>
     */
    public static void generate() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve(CortesCombat.MODID).resolve("weapons");
            Files.createDirectories(dir);
            // TreeMap: los archivos salen en orden alfabetico de mod, y el mismo mod produce el
            // mismo archivo en el cliente y en el server (el disco de cada uno lleva la MISMA
            // lista, que es lo que la deuda de sync de la fase 9 pide algun dia).
            Map<String, List<AutoPackYaml.Detected>> byMod = new TreeMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (!AutoProfile.isForeign(id.getNamespace())) {
                    continue;
                }
                ItemStack stack = new ItemStack(item);
                WeaponAttr.Hand hand = WeaponAttr.of(stack);
                // Sin dano propio no pelea: un cuchillo de cocina respeta su silencio. La
                // escala es la del tooltip (1 = los punos), el mismo piso del clasificador.
                if (hand.damage() <= MIN_WEAPON_DAMAGE) {
                    continue;
                }
                String archetype = AutoProfile.archetypeOf(id.getPath());
                if (archetype == null) {
                    // **El veto de herramienta va antes del fallback por atributos.** Sin esto,
                    // una azada o un cubo ajenos con dano de ataque entrarian como espada: el
                    // unico filtro que habia era el nombre, y la lista negra es lo que evita que
                    // el archivo generado ensene a pelear a lo que no es un arma. (μ_μ)
                    if (AutoProfile.isToolLike(id.getPath())) {
                        continue;
                    }
                    // **Y la mitad del pedido "sin necesidad de que sean la herramienta".** Si el
                    // item tiene dano propio y no es herramienta, se clasifica por lo que HACE:
                    // rapido y liviano -> daga; lento y pesado -> martillo; el resto -> espada.
                    // Es exactamente lo que el refine del tag espada ya hacia en runtime, ahora
                    // tambien para lo que llega sin tag. (⌒‿⌒)
                    archetype = AutoProfile.archetypeFromStats(hand.damage(), hand.speed());
                }
                if (archetype == null) {
                    continue;
                }
                if (WeaponPackStore.resolveForGeneration(stack) != null) {
                    // Un pack ESCRITO POR UNA PERSONA ya contesta este item: abstener. Los
                    // `auto-*` no cuentan, o el generador se abstendria de su propia salida y
                    // el archivo nuevo saldria casi vacio. (μ_μ)
                    continue;
                }
                Float longReach = AutoProfile.isLongsword(id.getPath())
                        ? AutoProfile.LONGSWORD_REACH : null;
                byMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>())
                        .add(new AutoPackYaml.Detected(id.toString(), archetype, longReach,
                                AutoProfile.OreTier.of(id.getPath())));
            }
            int written = 0;
            int replaced = 0;
            for (Map.Entry<String, List<AutoPackYaml.Detected>> entry : byMod.entrySet()) {
                Path file = dir.resolve(PREFIX + entry.getKey() + ".yaml");
                if (Files.exists(file)) {
                    // **Un archivo de una version vieja del generador se respalda y se
                    // reescribe.** Es la unica excepcion a "nunca piso el archivo del usuario",
                    // y existe porque las reglas de deteccion cambian: sin esto, las armas que
                    // hoy sabemos clasificar no llegan nunca a una partida ya creada. El `.bak`
                    // conserva lo que el usuario habia editado. (￣ε￣＠)
                    if (AutoPackYaml.isCurrent(file)) {
                        continue;
                    }
                    backup(file);
                    replaced++;
                }
                Files.writeString(file, AutoPackYaml.of(entry.getKey(), entry.getValue()),
                        StandardCharsets.UTF_8);
                written++;
            }
            if (written > 0) {
                WeaponPackStore.reload();
                CortesCombat.LOGGER.info(
                        "[weaponpack] auto: {} pack(s) generados para mods detectados "
                                + "({} reemplazados por version nueva; borra el archivo para "
                                + "regenerar)", written, replaced);
            }
        } catch (Exception e) {
            // Un warning con nombre y nada mas: el mundo arranca igual.
            CortesCombat.LOGGER.warn("[weaponpack] auto: fallo el generador: {}", e.toString());
        }
    }

    /**
     * Guarda la version anterior al lado, <b>antes</b> de reescribir.
     *
     * <p>Sobrescribe un `.bak` previo a proposito: el que interesa es el de la version que se
     * esta reemplazando ahora, no el de hace tres upgrades. (￣ε￣＠)</p>
     */
    private static void backup(Path file) {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            CortesCombat.LOGGER.warn("[weaponpack] auto: no se pudo respaldar {}: {}",
                    file.getFileName(), e.toString());
        }
    }
}
