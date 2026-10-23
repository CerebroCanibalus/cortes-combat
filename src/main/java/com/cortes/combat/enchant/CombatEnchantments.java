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
    public static final ResourceKey<Enchantment> ADRENALINA =
            key(CombatEnchantRules.ADRENALINA_ID);

    /** E4 -- Ritmo de Hierro: la ventana de la cadena del martillo. */
    public static final ResourceKey<Enchantment> RHYTHM_OF_IRON =
            key(CombatEnchantRules.RHYTHM_OF_IRON_ID);

    /** E7 -- Contragolpe: la ventana ofensiva que abre un bloqueo de verdad. */
    public static final ResourceKey<Enchantment> RIPOSTE = key(CombatEnchantRules.RIPOSTE_ID);

    /** E1 -- Pólvora Seca: la recarga del arcabuz. */
    public static final ResourceKey<Enchantment> DRY_POWDER =
            key(CombatEnchantRules.DRY_POWDER_ID);

    /** Ropera -- Punta Fina. */
    public static final ResourceKey<Enchantment> PUNTA_FINA =
            key(CombatEnchantRules.PUNTA_FINA_ID);

    /** Hacha -- Filo Astillado. */
    public static final ResourceKey<Enchantment> FILO_ASTILLADO =
            key(CombatEnchantRules.FILO_ASTILLADO_ID);

    /** Daga -- Apuñalar. */
    public static final ResourceKey<Enchantment> APUNALAR =
            key(CombatEnchantRules.APUNALAR_ID);

    /** Daga -- Sanguijuela. */
    public static final ResourceKey<Enchantment> SANGUJUELA =
            key(CombatEnchantRules.SANGUJUELA_ID);

    /** Espada -- Aventurero. */
    public static final ResourceKey<Enchantment> AVENTURERO =
            key(CombatEnchantRules.AVENTURERO_ID);

    /** Arcabuz -- Culata. */
    public static final ResourceKey<Enchantment> CULATA =
            key(CombatEnchantRules.CULATA_ID);

    /** Ropera -- Estocada Perforante. */
    public static final ResourceKey<Enchantment> ESTOCADA =
            key(CombatEnchantRules.ESTOCADA_ID);

    /** Espada -- Segundo Aliento. */
    public static final ResourceKey<Enchantment> SEGUNDO_ALIENTO =
            key(CombatEnchantRules.SEGUNDO_ALIENTO_ID);

    /** Espada -- Guardia Firme. */
    public static final ResourceKey<Enchantment> GUARDIA_FIRME =
            key(CombatEnchantRules.GUARDIA_FIRME_ID);

    /** Azada -- Cicatrizador. */
    public static final ResourceKey<Enchantment> CICATRIZADOR =
            key(CombatEnchantRules.CICATRIZADOR_ID);

    /** Alabarda -- Destripamiento. */
    public static final ResourceKey<Enchantment> DESTRIPAMIENTO =
            key(CombatEnchantRules.DESTRIPAMIENTO_ID);

    /** Pesadas -- Quebranto. */
    public static final ResourceKey<Enchantment> QUEBRANTO =
            key(CombatEnchantRules.QUEBRANTO_ID);

    /** Shotel -- Desvio Vampirico. */
    public static final ResourceKey<Enchantment> VAMPIRICO =
            key(CombatEnchantRules.VAMPIRICO_ID);

    /** Arcabuz -- Mecha Corta. */
    public static final ResourceKey<Enchantment> MECHA_CORTA =
            key(CombatEnchantRules.MECHA_CORTA_ID);

    /** Alabarda -- Barredor. */
    public static final ResourceKey<Enchantment> BARREDOR =
            key(CombatEnchantRules.BARREDOR_ID);

    /** Hacha -- Astilladora. */
    public static final ResourceKey<Enchantment> ASTILLADORA =
            key(CombatEnchantRules.ASTILLADORA_ID);

    /** Hacha -- Mella. */
    public static final ResourceKey<Enchantment> MELLA =
            key(CombatEnchantRules.MELLA_ID);

    /** Alabarda -- Segunda Pasada. */
    public static final ResourceKey<Enchantment> SEGUNDA_PASADA =
            key(CombatEnchantRules.SEGUNDA_PASADA_ID);

    /** Azada -- Santa Muerte. */
    public static final ResourceKey<Enchantment> SANTA_MUERTE =
            key(CombatEnchantRules.SANTA_MUERTE_ID);

    /** Martillo -- Inercia. */
    public static final ResourceKey<Enchantment> INERCIA =
            key(CombatEnchantRules.INERCIA_ID);



    /** Largas -- Ensarte. */
    public static final ResourceKey<Enchantment> ENSARTE =
            key(CombatEnchantRules.ENSARTE_ID);

    /** Pala -- Paliza. */
    public static final ResourceKey<Enchantment> PALIZA = key(CombatEnchantRules.PALIZA_ID);

    /** Ropera -- Retirada. */
    public static final ResourceKey<Enchantment> RETIRADA = key(CombatEnchantRules.RETIRADA_ID);

    /** Martillo -- Sentencia. */
    public static final ResourceKey<Enchantment> SENTENCIA = key(CombatEnchantRules.SENTENCIA_ID);

    /** Martillo -- Trabazon. */
    public static final ResourceKey<Enchantment> TRABAZON = key(CombatEnchantRules.TRABAZON_ID);

    /** Daga -- Gripa. */
    public static final ResourceKey<Enchantment> GRIPA = key(CombatEnchantRules.GRIPA_ID);

    /** Shotel -- Corte Gemelo. */
    public static final ResourceKey<Enchantment> CORTE_GEMELO =
            key(CombatEnchantRules.CORTE_GEMELO_ID);

    /** Shotel -- Capacitor. */
    public static final ResourceKey<Enchantment> CAPACITOR = key(CombatEnchantRules.CAPACITOR_ID);

    /** Pala -- Pala Antimaterial. */
    public static final ResourceKey<Enchantment> PALA_ANTIMATERIAL =
            key(CombatEnchantRules.PALA_ANTIMATERIAL_ID);

    /** Arcabuz -- Rey de las Moscas. */
    public static final ResourceKey<Enchantment> REY_DE_LAS_MOSCAS =
            key(CombatEnchantRules.REY_DE_LAS_MOSCAS_ID);

    /** Arcabuz -- Mungus. */
    public static final ResourceKey<Enchantment> MUNGUS = key(CombatEnchantRules.MUNGUS_ID);

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

    /**
     * El nivel de Barredor del arma en la mano principal del que la sostiene, o cero.
     *
     * <p>Vive aca y no en {@code MaceAir} porque este archivo <b>ya</b> nombra tipos de Minecraft
     * (su {@code level} declara un {@code HolderLookup.Provider}) y {@code MaceAir} se carga en
     * tests de JVM pelada. Un helper alla adentro habria matado tres tests con
     * {@code NoClassDefFoundError} antes de la primera asercion.</p>
     *
     * <p>Y es un metodo y no dos lineas repetidas en cuatro sitios de display, porque el sitio que
     * se olvide de leer el nivel es el sitio que dibuja el cono viejo -- que es exactamente el bug
     * que el veto C4 describe.</p>
     */
    public static int barredorLevel(net.minecraft.world.entity.LivingEntity holder) {
        return level(holder.level().registryAccess(), holder.getMainHandItem(), BARREDOR);
    }

    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                CortesCombat.rl(id));
    }

    private CombatEnchantments() {
    }
}
