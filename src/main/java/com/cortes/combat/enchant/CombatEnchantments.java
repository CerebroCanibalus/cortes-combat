package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Los encantamientos de combate propios del mod: sus claves, y cómo se lee su nivel.
 *
 * <h2>Por qué estos son archivos de datos y no código, al revés que los del oro</h2>
 *
 * <p>{@link ModEnchantments} registra sus nueve en Java porque la lista de ítems es <b>un filtro por
 * nombre</b> ("los nuestros que dicen {@code golden}") que ningún datapack puede expresar. Estos
 * tienen el problema exacto al revés: la pregunta es <b>"qué perfil de arma es esto"</b>, y esa
 * respuesta ya existe como tag -- {@code #cortescombat:profile/sabre} y
 * {@code #cortescombat:profile/ropera}, que el datagen del mod escribe y que
 * {@code WeaponStats} ya consulta. Un {@code HolderSet.direct(..)} construido en Java sería una
 * segunda lista de las mismas armas, y una segunda lista es una segunda verdad: la primera que se
 * olvide de un tier nuevo deja el encantamiento sin esa arma <b>en silencio</b>.</p>
 *
 * <p>El archivo de datos además compra lo que la doc de weapon packs promete: un arma de un pack
 * que declare {@code profile: sabre} hereda el encantamiento agregándose al tag, sin Java.</p>
 *
 * <h2>Lo que NO cambia por ser datos</h2>
 *
 * <p><b>El comportamiento sigue en código.</b> El JSON describe en qué ítems puede estar, cuánto
 * cuesta y hasta qué nivel; el número que significa algo lo pone {@link CombatEnchantRules} en el
 * call site. Un encantamiento sin su hook es un nombre en una mesa de encantamientos.</p>
 */
public final class CombatEnchantments {

    /** E2 -- Reflejo de Acero: el cooldown del parry del shotel. */
    public static final ResourceKey<Enchantment> STEEL_REFLEX = key(CombatEnchantRules.STEEL_REFLEX_ID);

    /** E3 -- Audacia: el cooldown del lunge de la ropera. */
    public static final ResourceKey<Enchantment> AUDACITY = key(CombatEnchantRules.AUDACITY_ID);

    /** E5 -- Filo Fugaz: la Velocidad que la daga se gana al conectar. */
    public static final ResourceKey<Enchantment> FLEETING_EDGE =
            key(CombatEnchantRules.FLEETING_EDGE_ID);

    /**
     * El nivel de un encantamiento propio sobre un ítem, por clave.
     *
     * <p><b>{@code holderOrThrow} y no una búsqueda que devuelva cero.</b> El encantamiento viaja en
     * el jar del mod, así que no encontrarlo es una instalación rota y tiene que decir su nombre en
     * vez de leerse como "no lo tiene" -- un encantamiento que dejó de funcionar sin un error es la
     * forma que este proyecto ya pagó varias veces.</p>
     */
    public static int level(HolderLookup.Provider registries, ItemStack stack,
                            ResourceKey<Enchantment> enchantment) {
        return stack.getEnchantmentLevel(registries.holderOrThrow(enchantment));
    }

    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, id));
    }

    private CombatEnchantments() {
    }
}
