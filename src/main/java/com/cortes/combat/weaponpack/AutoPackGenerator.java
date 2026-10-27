package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.item.AutoProfile;
import com.cortes.combat.platform.WeaponAttr;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
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
 * common setup los tags de items no estan bindeados, asi que ahi el {@code stack.is(ItemTags
 * .SWORDS)} responde falso para todos y el generador produce cero entradas con el build verde.
 * Un diagnosticador que miente en la forma del bug que cacha es peor que ningun diagnosticador
 * (ModPlatform ya lo pago una vez).</p>
 *
 * <h2>Las tres reglas del archivo que sale</h2>
 *
 * <ol>
 *   <li><b>Solo se escribe si no existe</b> -- es del usuario desde el primer byte; se borra
 *       para regenerar, se edita para balancear. Un archivo que existe no se pisa nunca.</li>
 *   <li><b>No declara dano ni velocidad</b> -- el arma ajena ya trae los suyos por material, y
 *       pisarlos seria pelear contra el balance del otro mod. El tier escala la CARGA en el
 *       codigo ({@code AutoProfile.tieredChargeTicks/Bonus}), que era el numero ciego al
 *       material del molde.</li>
 *   <li><b>La cobertura ya cargada abstiene</b> -- un item que cualquier pack (a mano o el
 *       builtin de categorias) ya contesta no genera linea: el YAML del usuario manda, que es
 *       la regla de oro de la capa 2.</li>
 * </ol>
 *
 * <p><b>El texto lo arma {@link AutoPackYaml}, que es puro.</b> Esta clase es el adaptador al
 * juego y nada mas: escanea el registro, clasifica, y delega el formato. (￣ε￣＠)</p>
 */
public final class AutoPackGenerator {

    static final String PREFIX = "auto-";

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
                    if (!stack.is(ItemTags.SWORDS)) {
                        continue;
                    }
                    // El tag de vanilla clasifica por omision (casi todo arma entra en
                    // #minecraft:swords): el refine por atributos es la devolucion del favor.
                    archetype = AutoProfile.archetypeFromProfile(
                            AutoProfile.refineForeignSword(hand.damage(), hand.speed()));
                    if (archetype == null) {
                        continue;
                    }
                }
                if (WeaponPackStore.resolve(stack) != null) {
                    // Ya contestado por un pack existente (a mano o el builtin): abstener.
                    continue;
                }
                Float longReach = AutoProfile.isLongsword(id.getPath())
                        ? AutoProfile.LONGSWORD_REACH : null;
                byMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>())
                        .add(new AutoPackYaml.Detected(id.toString(), archetype, longReach,
                                AutoProfile.OreTier.of(id.getPath())));
            }
            int written = 0;
            for (Map.Entry<String, List<AutoPackYaml.Detected>> entry : byMod.entrySet()) {
                Path file = dir.resolve(PREFIX + entry.getKey() + ".yaml");
                if (Files.exists(file)) {
                    continue;
                }
                Files.writeString(file, AutoPackYaml.of(entry.getKey(), entry.getValue()),
                        StandardCharsets.UTF_8);
                written++;
            }
            if (written > 0) {
                WeaponPackStore.reload();
                CortesCombat.LOGGER.info(
                        "[weaponpack] auto: {} pack(s) generados para mods detectados "
                                + "(borra el archivo para regenerar)", written);
            }
        } catch (Exception e) {
            // Un warning con nombre y nada mas: el mundo arranca igual.
            CortesCombat.LOGGER.warn("[weaponpack] auto: fallo el generador: {}", e.toString());
        }
    }
}
