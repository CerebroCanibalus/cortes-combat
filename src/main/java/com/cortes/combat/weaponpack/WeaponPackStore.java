package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La tabla en memoria: los packs ya parseados, y la puerta por la que {@code WeaponStats.of()}
 * los consulta.
 *
 * <p><b>Tabla en memoria y no componentes sobre stacks vivos</b>: escribir un componente en el
 * stack que la mano lleva reemplaza el {@code ItemStack} del cliente y mueve el arma mientras se
 * asienta. El resolver es puro y testeado; esta clase es sólo el adaptador al juego -- un
 * fast-path, un {@code stack.is} por tag y nada más.
 *
 * <p><b>Fast-path primero</b>: sin entradas de usuario (la instalación típica no tiene packs),
 * {@link #resolve} devuelve {@code null} antes de tocar un registro, así que el costo de este
 * sistema para quien no lo usa es una lectura de dos campos volátiles.
 */
public final class WeaponPackStore {

    private static final TagKey<Item> INVALID_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("empty"));

    private static volatile List<WeaponPackSpec> packs = List.of();
    /** Sin ninguna entrada de arma en ningún pack: el fast-path de {@link #resolve}. */
    private static volatile boolean empty = true;
    private static final Map<String, TagKey<Item>> TAG_CACHE = new ConcurrentHashMap<>();

    private WeaponPackStore() {
    }

    /** Lo que `/cc reload` reporta. {@code null} = no se pudo cargar (el log nombra la causa). */
    public record Summary(int packs, int entries, int warnings) {
    }

    /** Built-in + {@code config/cortescombat/weapons/*.yaml}, en orden de precedencia. */
    public static synchronized Summary reload() {
        WeaponPackLoader.Result result;
        try {
            result = WeaponPackLoader.loadAll(
                    FMLPaths.CONFIGDIR.get().resolve(CortesCombat.MODID).resolve("weapons"));
        } catch (RuntimeException e) {
            // Un loader roto no tumbara el mod: se queda sin packs y el mensaje nombra la causa.
            CortesCombat.LOGGER.error("[weaponpack] no se pudo cargar: {}", e.getMessage());
            return null;
        }
        for (String warning : result.warnings()) {
            CortesCombat.LOGGER.warn("[weaponpack] {}", warning);
        }
        for (String error : result.errors()) {
            CortesCombat.LOGGER.error("[weaponpack] {}", error);
        }
        TAG_CACHE.clear();
        packs = result.packs();
        empty = packs.stream().allMatch(pack -> pack.weapons().isEmpty());
        int entries = packs.stream().mapToInt(pack -> pack.weapons().size()).sum();
        CortesCombat.LOGGER.info("[weaponpack] {} packs, {} entradas de arma", packs.size(), entries);
        return new Summary(packs.size(), entries, result.allMessages().size());
    }

    /** {@code true} cuando ningún pack declara un arma: el fast-path. */
    public static boolean isEmpty() {
        return empty;
    }

    /**
     * Los packs parseados, en orden de precedencia. Sólo lo consume
     * {@link WeaponPackItems} en el {@code RegisterEvent} -- la lista se lee durante el
     * registro y no cambia después, así que viaja como copia inmutable.
     */
    public static List<WeaponPackSpec> packs() {
        return packs;
    }

    /**
     * La capa 2 contra un stack concreto, o {@code null} si ningún pack habla de este ítem.
     *
     * <p>Los tags se resuelven con {@code stack.is} -- preguntarle al bind de datos, no inventar
     * una colección de membresías que el juego no expone -- y cada tag se parsea una vez y se
     * cachea. Un tag malformado en el YAML queda como {@link #INVALID_TAG}: avisa una vez y
     * nunca matchea, en vez de tirar un {@code ResourceLocation} por swing.
     */
    public static ResolvedWeapon resolve(ItemStack stack) {
        if (empty || stack == null || stack.isEmpty()) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return PackResolver.resolve(id, tag -> {
            TagKey<Item> key = tagKey(tag);
            return key != INVALID_TAG && stack.is(key);
        }, packs);
    }

    private static TagKey<Item> tagKey(String tag) {
        return TAG_CACHE.computeIfAbsent(tag, raw -> {
            try {
                return TagKey.create(Registries.ITEM, ResourceLocation.parse(raw));
            } catch (RuntimeException e) {
                CortesCombat.LOGGER.warn("[weaponpack] tag malformado '{}' -- nunca matchea", raw);
                return INVALID_TAG;
            }
        });
    }
}
