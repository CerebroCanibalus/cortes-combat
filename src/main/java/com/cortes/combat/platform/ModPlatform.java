package com.cortes.combat.platform;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.config.ServerConfig;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.registry.ModArmor;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.registry.ModBlocks;
import com.cortes.combat.registry.ModComponents;
import com.cortes.combat.registry.ModCreativeTabs;
import com.cortes.combat.registry.ModEffects;
import com.cortes.combat.registry.ModEntities;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.registry.ModParticleTypes;
import com.cortes.combat.registry.ModTools;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

/**
 * Every {@code DeferredRegister} this mod owns, registered in one place.
 *
 * <p><b>Extracted from the {@code CortesCombat} constructor, unchanged.</b> The constructor is
 * the mod's front door and was growing a registration line per system; the order below is the
 * order the game needs (components and attachments before the items that carry them, blocks
 * before the block entities that live in them), and a reorder that breaks something breaks it
 * here, in the open, instead of in a 300-line constructor.</p>
 *
 * <p>The 1.20.1 twin registers the Forge equivalents as each registry cluster lands. The two
 * lines that vanish there on purpose are components and attachments: capabilities attach
 * through gameplay events and need no bus registration at all. (⌒‿⌒)</p>
 */
public final class ModPlatform {

    private ModPlatform() {
    }

    public static void registerAll(IEventBus modEventBus, ModContainer modContainer) {
        // Supervivencia, y va en el constructor común y no en el cliente porque es SERVER: decide
        // cuánto vida se cura y a qué precio en comida, así que los dos lados tienen que leer lo
        // mismo. NeoForge lo sincroniza a quien entra al mundo. `ClientConfig` vive del otro lado,
        // en `CortesCombatClient`, porque sólo dibuja.
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        ModComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        // **The copper gear: a set vanilla 1.21.1 does not ship.** Two registers of its own, one
        // for the five tools and one for the armour and its material, and neither touches ModItems
        // -- the catalogue is thirty weapons of five shapes, and a pickaxe is not one of them.
        ModArmor.register(modEventBus);
        ModTools.register(modEventBus);
        // **The first block this mod has ever had.** Its own register, the same way the copper
        // tools and armour have theirs: the catalogue in ModItems is a table of weapons, and a
        // block is not an entry in it.
        ModBlocks.register(modEventBus);
        // **The first block entity this mod has ever had.** Its own register, and it carries the
        // reactor's item-handler capability -- the thirty-cap door a hopper sees. The capability is
        // registered against the block entity TYPE, so it is per reactor and not shared.
        com.cortes.combat.registry.ModBlockEntities.register(modEventBus);
        modEventBus.addListener(com.cortes.combat.registry.ModBlockEntities::registerCapabilities);
        ModCreativeTabs.TABS.register(modEventBus);
        ModParticleTypes.PARTICLE_TYPES.register(modEventBus);
        com.cortes.combat.registry.ModEntities.ENTITY_TYPES.register(modEventBus);
        com.cortes.combat.registry.ModEffects.register(modEventBus);
        // **Gold, and it is two registrations.** The enchantments are their own registry, and the
        // mark they all feed is an effect -- both are declarations, so both go on the mod bus, and
        // the behaviour that reads them is a subscriber on the game bus instead.
        com.cortes.combat.enchant.ModEnchantments.ENCHANTMENTS.register(modEventBus);
        com.cortes.combat.enchant.GoldEnchantmentHandler.EFFECTS.register(modEventBus);
        // The shiftable crafting recipe's serializer, and **this call is what makes it exist.**
        //
        // `SHIFTABLE_SHAPED` is a static field in a nested class, so it is initialised when that
        // class is first touched -- and nothing else touches it. The only other reader is
        // `getSerializer()`, which only runs on a recipe that has already been built, and a recipe
        // is built after the manager looked its `"type"` up in the registry. So left here the
        // lookup finds nothing, the class never loads, and the field initialises never. That is a
        // loop, not a bug that throws: the symptom would be thirty weapons whose recipes silently
        // do not exist.
        com.cortes.combat.core.crafting.ShiftableShapedRecipe.ModRecipes.register(modEventBus);
        modEventBus.addListener(ModNetworking::register);
    }
}
