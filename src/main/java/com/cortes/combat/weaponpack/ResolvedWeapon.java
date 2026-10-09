package com.cortes.combat.weaponpack;

import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;

import java.util.List;

/**
 * El resultado de resolver un ítem contra los packs: qué contestó la capa 2, con fuente.
 *
 * <p><b>Campo nulo = "esta capa no contestó"</b>, y el llamador cae a la capa siguiente (tag
 * molde → tag vanilla → NONE). Nada se materializa como componente del ItemStack: la tabla en
 * memoria es la que consulta {@code WeaponStats.of()}, porque escribir un componente en el stack
 * de la mano reemplaza el ItemStack del cliente y mueve el arma mientras se asienta.
 *
 * @param source la identidad de la entrada que ganó ({@code id=...}, {@code tag=...}), para la
 *               columna {@code source} de {@code /cc weapons} -- un número sin fuente es un
 *               número que nadie puede depurar
 * @param warnings anomalías de esta resolución (arquetipo desconocido, identidad ausente);
 *                 siempre con archivo de origen en el mensaje
 */
public record ResolvedWeapon(String source,
                             WeaponProfile profile,
                             SpecialType special,
                             Boolean canCharge,
                             Float damage,
                             Float attackSpeed,
                             Integer cooldown,
                             Integer durability,
                             WeaponPackSpec.Reach reach,
                             String slash,
                             String pose,
                             String swing,
                             String hit,
                             String charged,
                             String name,
                             String tab,
                             List<String> warnings) {

    public ResolvedWeapon {
        warnings = List.copyOf(warnings);
    }
}
