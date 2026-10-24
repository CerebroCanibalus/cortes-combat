package com.cortes.combat.enchant;

import com.cortes.combat.platform.ResourceIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The 1.20.1 twin of the combat enchantments' keys and level reads.
 *
 * <p>Same keys (the ids live in {@link CombatEnchantRules} and the JSON ships the rest),
 * but the read goes through the global {@link net.minecraftforge.registries.ForgeRegistries}
 * instead of a registry lookup: 1.20.1 has no {@code HolderLookup} handoff and
 * {@code ItemStack} has no holder-based {@code getEnchantmentLevel}. The first parameter
 * stays {@code Object} so the shared call sites do not fork: on this leg it arrives
 * unused on purpose, exactly like {@code ModPlatform.registerAll}'s container. (⌒‿⌒)</p>
 *
 * <p>Missing is still loud: a key with no enchantment behind it throws naming the id,
 * the same promise the shared twin makes with {@code holderOrThrow}.</p>
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
     * @param registries unused on this leg (the lookup is global); kept so the call sites
     *                   compile unchanged on both legs.
     */
    public static int level(Object registries, ItemStack stack,
                            ResourceKey<Enchantment> enchantment) {
        ResourceLocation id = enchantment.location();
        Enchantment found = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS
                .getValue(id);
        if (found == null) {
            throw new IllegalStateException("Missing enchantment: " + id
                    + " -- la instalacion esta rota, no es que el item no lo tenga");
        }
        return net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(found, stack);
    }

    /**
     * El nivel de Barredor del arma en la mano principal del que la sostiene, o cero.
     */
    public static int barredorLevel(net.minecraft.world.entity.LivingEntity holder) {
        return level(holder.level().registryAccess(), holder.getMainHandItem(), BARREDOR);
    }

    /**
     * The weapon's Sweeping Edge level, straight off the stack.
     *
     * <p>1.20.1 enchantments are registry objects, so there is no holder to resolve: missing
     * would mean a broken install, but the install cannot be half-built here the way a
     * datapack registry can, so a plain read is the honest shape.</p>
     */
    public static int sweepingEdgeLevel(net.minecraft.server.level.ServerLevel level, ItemStack weapon) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, weapon);
    }

    /**
     * The weapon's Density level, straight off the stack.
     *
     * <p>Zero, always, on this leg: Density is a 1.21 enchantment and 1.20.1 has no such key.
     * The mace's fall still grows from the fall itself; only the enchanted bonus is absent,
     * which is the same answer as "the player has no Density" on the other leg.</p>
     */
    public static int densityLevel(net.minecraft.world.entity.player.Player player) {
        return 0;
    }

    private static ResourceKey<Enchantment> key(String id) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                ResourceIds.rl(id));
    }

    private CombatEnchantments() {
    }
}
