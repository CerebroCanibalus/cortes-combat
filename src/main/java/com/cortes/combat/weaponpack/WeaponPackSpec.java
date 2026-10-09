package com.cortes.combat.weaponpack;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Un weapon pack parseado: la forma cruda de un YAML, sin un solo tipo de Minecraft adentro.
 *
 * <p><b>Nullable por decisión y no por pereza</b>: cada campo nulo significa "lo omití, que caiga
 * a la capa siguiente", que es la regla de oro del formato -- un YAML de dos líneas es válido, y
 * esta clase es la que lo hace posible. El parseo (SnakeYAML) es una fase posterior; los tests
 * construyen estas estructuras a mano.
 *
 * @param origin    nombre del archivo, para los warnings -- un warning sin archivo es un warning
 *                  que nadie puede arreglar
 * @param weapons   las entradas, en orden de archivo (más abajo gana)
 * @param archetypes arquetipos locales del pack; pisan a los built-in por nombre
 */
public record WeaponPackSpec(String origin, List<Entry> weapons, Map<String, ArchetypeSpec> archetypes) {

    public WeaponPackSpec {
        weapons = List.copyOf(weapons);
        archetypes = Map.copyOf(archetypes);
    }

    /**
     * Una entrada de {@code weapons}. O todos los campos son opcionales menos la identidad.
     *
     * <p><b>La identidad es una de tres</b>: {@code creates} (ítem nuevo, id que el mod registra),
     * {@code id} (exacto) o {@code tag} (membresía). Specificity: id/creates = 2, tag = 1; gana
     * el más específico y, a igualdad, el que está más abajo.
     */
    public record Entry(String creates, String id, String tag, String archetype, String profile,
                        Stats stats, String special, Cone cone, Reach reach, ShotReach shot,
                        String slash, Sounds sounds, String pose, Display display, Guard guard) {

        /** 2 = id o creates (más específico), 1 = tag; 0 = sin identidad, nunca matchea. */
        public int specificity() {
            if (creates != null || id != null) {
                return 2;
            }
            return tag != null ? 1 : 0;
        }

        /** El tag se acepta con o sin '#': el catálogo de tags no lleva almohadilla. */
        public boolean matches(String itemId, java.util.function.Predicate<String> hasTag) {
            if (creates != null) {
                return creates.equals(itemId);
            }
            if (id != null) {
                return id.equals(itemId);
            }
            if (tag == null) {
                return false;
            }
            String wanted = tag.startsWith("#") ? tag.substring(1) : tag;
            return hasTag.test(wanted);
        }

        public String identity() {
            if (creates != null) {
                return "creates=" + creates;
            }
            if (id != null) {
                return "id=" + id;
            }
            return "tag=" + tag;
        }
    }

    /**
     * El cono, en grados crudos -- radio por eje, sin bandas. Decisión del General (2026-10-09):
     * **en vez de `cone: small`, el número directo**: las seis bandas de {@code ConeSize} eran
     * apodos de unos ángulos, y un apodo no deja elegir. {@code reach} quedó separado -- la
     * distancia es un parámetro y el ángulo es otro, cada uno configurable por su cuenta.
     *
     * @param horizontal grados a izquierda y derecha de la mirada (5-85)
     * @param vertical  grados sobre y bajo el horizonte (5-85)
     */
    public record Cone(Float horizontal, Float vertical) {
    }

    /** Sólo la distancia de alcance en bloques (1.0-6.0); los ángulos son {@link Cone}. */
    public record Reach(Float distance) {
    }

    /** El segundo alcance (tiro), que sí lleva sus tres números juntos. */
    public record ShotReach(Float distance, Float horizontal, Float vertical) {
    }

    /** Empuje del golpe, con los techos del propio {@code KnockbackProfile} (1.6 / 0.45). */
    public record Knockback(Float horizontal, Float vertical) {
    }

    /**
     * La aguante del arma: puntos que aguanta bloqueando, arco en grados, regen y stun.
     * El escudo vanilla es (3, 120°, 40, 100) y la espada (1, 90°, 40, 100).
     */
    public record Guard(Integer points, Float arcDegrees, Integer regenTicks, Integer stunTicks) {
    }

    /**
     * Los números del arma, y sin {@code cooldown} a propósito: el tempo del golpe es
     * {@code attack_speed} (lo que vanilla llama toques por segundo y {@code attackInterval}
     * lee del holder). Dos campos para la misma magnitud serían dos verdades, y el jugador
     * terminaría escribiendo uno que no rige.
     */
    public record Stats(Float damage, Float attackSpeed, Integer durability,
                        Boolean canCharge, Float chargeBonus, Integer fullChargeTicks,
                        Integer maxTargets, Knockback knockback) {
    }

    /** Ids de sounds.json del resource pack; nulo = el sonido del mod. */
    public record Sounds(String swing, String hit, String charged) {
    }

    /** Sólo para {@code creates}: renombrar un ítem ajeno pisa su idioma. */
    public record Display(String name, String tab) {
    }
}
