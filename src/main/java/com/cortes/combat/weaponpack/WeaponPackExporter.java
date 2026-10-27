package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponTables;
import com.cortes.combat.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vuelca las armas propias del mod a YAML editable en {@code config/cortescombat/weapons/}.
 */
public final class WeaponPackExporter {

    private WeaponPackExporter() {
    }

    /** Archivo con nombre fijo: las armas base del mod, expuestas para editar. */
    public static final String BASE_WEAPONS_FILE = "cortescombat_weapons.yaml";

    public static Path export() throws IOException {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(CortesCombat.MODID).resolve("weapons");
        Files.createDirectories(dir);
        String name = "cortescombat_weapons_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".yaml";
        Path out = dir.resolve(name);
        String yaml = build();
        Files.writeString(out, yaml, StandardCharsets.UTF_8);
        return out;
    }

    /**
     * Genera el archivo de armas base con nombre fijo si no existe ya.
     * Así el jugador puede editar las armas del mod directamente.
     * No sobrescribe archivos existentes (el usuario pudo haberlos editado).
     *
     * <p><b>Devuelve si escribio, y eso importa:</b> el que llama tiene que recargar la tienda de
     * packs para que el archivo nuevo se lea en el mismo arranque.</p>
     *
     * <p><b>Y se llama desde {@code ServerStartedEvent}, NO desde el constructor del mod.</b>
     * Ahi los {@code DeferredItem} todavia no estan bindeados, y {@code build()} los recorre:
     * {@code runData} murio con
     * {@code NullPointerException: Trying to access unbound value:
     * ResourceKey[minecraft:item / cortescombat:stone_ropera]}. En el server de prueba no se veia
     * porque el archivo ya existia y el metodo salia antes de mirar los items: <b>una instalacion
     * nueva crasheaba al arrancar</b>. Un `return` temprano que depende de un archivo del usuario
     * es la razon de que esto viviera semanas sin aparecer.</p>
     *
     * @return {@code true} si escribio el archivo, {@code false} si ya existia o fallo
     */
    public static boolean exportIfMissing() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get().resolve(CortesCombat.MODID).resolve("weapons");
            Files.createDirectories(dir);
            Path out = dir.resolve(BASE_WEAPONS_FILE);
            if (Files.exists(out)) {
                return false; // ya existe, no pisar
            }
            String yaml = build();
            Files.writeString(out, yaml, StandardCharsets.UTF_8);
            CortesCombat.LOGGER.info("[weaponpack] armas base exportadas a {} (primera ejecucion)", out);
            return true;
        } catch (IOException e) {
            CortesCombat.LOGGER.warn("[weaponpack] no se pudo generar {}: {}", BASE_WEAPONS_FILE, e.getMessage());
            return false;
        }
    }

    private static String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Exportado por /cc export weapons\n");
        sb.append("# Cortes Combat - armas propias en formato weapon pack\n");
        sb.append("# Edita lo que quieras y recarga con /cc reload\n\n");
        sb.append("version: 1\n\n");
        sb.append("weapons:\n");

        List<ResourceLocation> ids = new ArrayList<>();
        for (var item : ModItems.weapons()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.get());
            if (id != null) {
                ids.add(id);
            }
        }

        for (ResourceLocation id : ids) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            WeaponStats stats = WeaponStats.of(stack);
            WeaponProfile profile = stats.profile();
            if (profile == null || profile == WeaponProfile.NONE) {
                continue;
            }
            ReachProfile reach = WeaponTables.reach(profile);
            ChargeProfile charge = WeaponTables.charge(profile);
            SpecialType special = WeaponTables.special(profile);
            KnockbackProfile knockback = WeaponTables.knockback(profile);
            ReachProfile shot = WeaponTables.shotReach(profile);

            sb.append("  - match: { id: ").append(id.toString()).append(" }\n");
            sb.append("    archetype: ").append(archetypeOf(profile)).append("\n");
            sb.append("    stats:\n");
            if (charge != null && charge.canCharge()) {
                sb.append("      charge:\n");
                sb.append("        can_charge: true\n");
                sb.append("        bonus: ").append(fmt(charge.bonus())).append("\n");
                sb.append("        full_charge_ticks: ").append(charge.fullChargeTicks()).append("\n");
            } else {
                sb.append("      charge:\n");
                sb.append("        can_charge: false\n");
            }
            if (knockback != null) {
                sb.append("      knockback: { horizontal: ").append(fmt(knockback.horizontal())).append(", vertical: ").append(fmt(knockback.vertical())).append(" }\n");
            }
            if (reach != null) {
                sb.append("    reach: { distance: ").append(fmt(reach.distance())).append(" }\n");
                sb.append("    cone: { horizontal: ").append(Math.round(reach.horizontalAngle())).append(", vertical: ").append(Math.round(reach.verticalAngle())).append(" }\n");
            }
            if (shot != null && shot.distance() > 0) {
                sb.append("    shot: { distance: ").append(fmt(shot.distance())).append(", horizontal: ").append(Math.round(shot.horizontalAngle())).append(", vertical: ").append(Math.round(shot.verticalAngle())).append(" }\n");
            }
            if (special != null && special != SpecialType.NONE) {
                sb.append("    special: ").append(special.id()).append("\n");
            }
            sb.append("    display:\n");
            sb.append("      name: \"").append(nameOf(profile)).append("\"\n");
            sb.append("      tab: cortescombat\n\n");
        }

        sb.append("# Para aplicar: /cc reload\n");
        return sb.toString();
    }

    private static String archetypeOf(WeaponProfile profile) {
        return switch (profile) {
            case DAGGER -> "dagger";
            case SWORD -> "sword";
            case ROPERA -> "rapier";
            case SABRE -> "sabre";
            case WARHAMMER -> "hammer";
            case MACE -> "mace";
            case AXE -> "axe";
            case HALBERD -> "polearm";
            case TRIDENT -> "polearm";
            case ARCABUZ -> "axe";
            case HOE -> "sword";
            default -> "sword";
        };
    }

    private static String nameOf(WeaponProfile profile) {
        return switch (profile) {
            case ROPERA -> "Ropera";
            case HALBERD -> "Alabarda";
            case WARHAMMER -> "Martillo de Guerra";
            case DAGGER -> "Daga";
            case SABRE -> "Shotel";
            case ARCABUZ -> "Arcabuz";
            case MACE -> "Maza";
            default -> profile.id().substring(0, 1).toUpperCase(Locale.ROOT) + profile.id().substring(1);
        };
    }

    private static String fmt(float v) {
        if (Math.abs(v - Math.round(v)) < 1e-4f) {
            return String.valueOf(Math.round(v));
        }
        return String.valueOf(v);
    }
}
