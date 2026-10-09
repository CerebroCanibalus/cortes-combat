package com.cortes.combat.weaponpack;

import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.weaponpack.WeaponPackSpec.Entry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La función de las cinco capas: {@code (ítem, tags, packs) -> ResolvedWeapon}.
 *
 * <p><b>El orden que el packmate aprende en una línea: el YAML manda, el tag molde es el
 * fallback.</b> Esta función es la capa 2 entera y es pura -- sin reloj, sin azar, sin estado
 * estático -- porque así se testea en JVM pelada: precedencia, campo omitido y arquetipo
 * desconocido son asserts de un archivo, no de una sesión de juego.
 *
 * <p>Los packs se pasan en orden de precedencia creciente (built-in primero, después los de
 * {@code config/} en alfabético): dentro de la lista, <b>más abajo gana</b>. Entre entradas,
 * <b>gana el más específico</b> (id/creates sobre tag) y la especificidad domina el orden -- un
 * match por id no lo tapa un match por tag posterior.
 */
public final class PackResolver {

    private PackResolver() {
    }

    /** {@code null} = el ítem no está en ningún pack: la capa 2 no opina, siguen las de abajo. */
    public static ResolvedWeapon resolve(String itemId, Collection<String> itemTags,
                                         List<WeaponPackSpec> packs) {
        Map<String, ArchetypeSpec> archetypes = new LinkedHashMap<>(ArchetypeSpec.builtins());
        Entry winner = null;
        String winnerOrigin = null;
        for (WeaponPackSpec pack : packs) {
            pack.archetypes().forEach(archetypes::put);
            for (Entry entry : pack.weapons()) {
                if (!entry.matches(itemId, itemTags)) {
                    continue;
                }
                // Ganar es: más específico, o igual de específico y más abajo (>= con orden de
                // iteración) -- specificity manda aunque esté arriba del todo.
                if (winner == null || entry.specificity() >= winner.specificity()) {
                    winner = entry;
                    winnerOrigin = pack.origin();
                }
            }
        }
        if (winner == null) {
            return null;
        }
        return resolve(winner, winnerOrigin, archetypes);
    }

    private static ResolvedWeapon resolve(Entry entry, String origin,
                                          Map<String, ArchetypeSpec> archetypes) {
        List<String> warnings = new ArrayList<>();
        ArchetypeSpec archetype = null;
        if (entry.archetype() != null) {
            archetype = archetypes.get(entry.archetype());
            if (archetype == null) {
                warnings.add(origin + " :: arquetipo desconocido '" + entry.archetype()
                        + "' -- se ignora; el perfil cae al tag molde");
            }
        }

        WeaponProfile profile = entry.profile() != null
                ? profileById(entry.profile(), origin, warnings)
                : archetype != null ? archetype.profile() : null;
        SpecialType special = entry.special() != null
                ? specialById(entry.special(), origin, warnings)
                : archetype != null ? archetype.special() : null;

        WeaponPackSpec.Stats stats = entry.stats();
        Boolean canCharge = stats != null && stats.canCharge() != null
                ? stats.canCharge()
                : archetype != null ? archetype.canCharge() : null;

        WeaponPackSpec.Sounds sounds = entry.sounds();
        String slash = entry.slash() != null ? entry.slash()
                : archetype != null ? archetype.slash() : null;
        String pose = entry.pose() != null ? entry.pose()
                : archetype != null ? archetype.pose() : null;
        WeaponPackSpec.Display display = entry.display();

        return new ResolvedWeapon(entry.identity(), profile, special, canCharge,
                stats != null ? stats.damage() : null,
                stats != null ? stats.attackSpeed() : null,
                stats != null ? stats.cooldown() : null,
                stats != null ? stats.durability() : null,
                slash, pose,
                sounds != null ? sounds.swing() : null,
                sounds != null ? sounds.hit() : null,
                sounds != null ? sounds.charged() : null,
                display != null ? display.name() : null,
                display != null ? display.tab() : null,
                warnings);
    }

    /**
     * String a enum por el id del propio enum, no por {@code valueOf}: el nombre que se escribe
     * en el YAML es el id ({@code warhammer}), y los enums no implementan
     * {@code StringRepresentable} porque es clase de Minecraft. Un id desconocido es warning y
     * {@code null} -- cae a la capa siguiente, igual que un campo omitido. El loader de YAML
     * usa estos mismos dos: un solo lugar donde un id se traduce.
     */
    static WeaponProfile profileById(String id, String origin, List<String> warnings) {
        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile.id().equals(id)) {
                return profile;
            }
        }
        warnings.add(origin + " :: profile desconocido '" + id + "' -- cae al tag molde");
        return null;
    }

    static SpecialType specialById(String id, String origin, List<String> warnings) {
        for (SpecialType special : SpecialType.values()) {
            if (special.id().equals(id)) {
                return special;
            }
        }
        warnings.add(origin + " :: special desconocido '" + id + "' -- se ignora");
        return null;
    }
}
