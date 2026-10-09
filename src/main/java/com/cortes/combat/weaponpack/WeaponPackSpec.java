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
                        Stats stats, String special, String cone, Reach reach, String slash,
                        Sounds sounds, String pose, Display display) {

        /** 2 = id o creates (más específico), 1 = tag; 0 = sin identidad, nunca matchea. */
        public int specificity() {
            if (creates != null || id != null) {
                return 2;
            }
            return tag != null ? 1 : 0;
        }

        /** El tag se acepta con o sin '#': el catálogo de tags no lleva almohadilla. */
        public boolean matches(String itemId, Collection<String> itemTags) {
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
            return itemTags.contains(wanted);
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

    /** Metros y grados crudos del cono; nulo = la banda {@code cone} o el molde. */
    public record Reach(Float distance, Float horizontal, Float vertical) {
    }

    /** Los números del arma. {@code canCharge} nulo hereda el molde. */
    public record Stats(Float damage, Float attackSpeed, Integer cooldown, Integer durability,
                        Boolean canCharge) {
    }

    /** Ids de sounds.json del resource pack; nulo = el sonido del mod. */
    public record Sounds(String swing, String hit, String charged) {
    }

    /** Sólo para {@code creates}: renombrar un ítem ajeno pisa su idioma. */
    public record Display(String name, String tab) {
    }
}
