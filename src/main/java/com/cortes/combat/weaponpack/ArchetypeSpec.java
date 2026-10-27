package com.cortes.combat.weaponpack;

import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Un arquetipo: lo que el mod provee a todo item de esa categoría sin que el pack declare nada.
 *
 * <p><b>El arquetipo es el tipo, no la cultura.</b> Nueve tipos de arma mapeados a perfiles que
 * ya existen -- la ropera no es una greatsword y el sable no es una katana. Dos de los nueve
 * traen una mecánica encendida porque es identidad del tipo ({@code rapier} carga lunge, es lo
 * que hace a la ropera ropera; {@code sabre} parry, {@code polearm} multi_hit, que es el barrido
 * de la alabarda) y los demás vienen
 * limpios: <b>la mecánica especial no es gratis</b>, la enciende el pack. El único false explícito
 * es {@code hammer}, porque nuestro molde WARHAMPER carga y un martillo ajeno no hereda la
 * carga de la nuestra.
 *
 * <p><b>{@code polearm} declaraba {@code SWEEP} y era un botón muerto.</b> La alabarda real corre
 * {@code MULTI_HIT} ({@code WeaponTables#SPECIAL}, y {@code WeaponTables} ya lo dice al pasar por
 * el miembro {@code SWEEP} del enum, "leftover del rework de la pala"), y ni {@code SpecialHandler}
 * ni {@code SpecialInputHandler} miran {@code SWEEP}. ∴ un pack con {@code archetype: polearm}
 * recibía un click derecho que no hacía nada, en silencio. Corregido a {@code MULTI_HIT}, que es
 * el que se maneja.
 *
 * <p>Los números no viven acá: los arquetipos heredan todo de {@code WeaponTables} por perfil
 * (decisión 1 de §14). {@code slash} y {@code pose} son la referencia al asset genérico, que
 * sólo puede resolverse en cliente con los resource packs presentes.
 */
public record ArchetypeSpec(WeaponProfile profile, SpecialType special, Boolean canCharge,
                            String slash, String pose) {

    /** Los nueve built-in, por nombre de tag -- la llave que el pack escribe. */
    public static Map<String, ArchetypeSpec> builtins() {
        return BUILTINS;
    }

    private static final Map<String, ArchetypeSpec> BUILTINS = built();

    private static Map<String, ArchetypeSpec> built() {
        Map<String, ArchetypeSpec> map = new LinkedHashMap<>();
        map.put("dagger", new ArchetypeSpec(WeaponProfile.DAGGER, SpecialType.NONE, null, null, null));
        map.put("sword", new ArchetypeSpec(WeaponProfile.SWORD, SpecialType.NONE, null, null, null));
        map.put("rapier", new ArchetypeSpec(WeaponProfile.ROPERA, SpecialType.LUNGE, null, null, null));
        map.put("sabre", new ArchetypeSpec(WeaponProfile.SABRE, SpecialType.PARRY, null, null, null));
        // La carga del WARHAMPER no es gratis: el pack la enciende con stats.charge.
        map.put("hammer", new ArchetypeSpec(WeaponProfile.WARHAMMER, SpecialType.NONE, false, null, null));
        map.put("mace", new ArchetypeSpec(WeaponProfile.MACE, SpecialType.NONE, null, null, null));
        // La lanza usa el sistema del tridente, decision del dueno: el alcance largo en cono
        // angosto, la carga que estoca y el especial se lo da el mod (vanilla lanza el tridente
        // por su cuenta -- el click derecho NO es nuestro). El placeholder anterior era ROPERA,
        // que le prestaba el lunge a un tipo que no pide ese ida y vuelta.
        map.put("spear", new ArchetypeSpec(WeaponProfile.TRIDENT, SpecialType.NONE, null, null, null));
        map.put("polearm", new ArchetypeSpec(WeaponProfile.HALBERD, SpecialType.MULTI_HIT, null, null, null));
        map.put("axe", new ArchetypeSpec(WeaponProfile.AXE, SpecialType.NONE, null, null, null));
        return Map.copyOf(map);
    }
}
