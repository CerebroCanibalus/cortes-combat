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

    /**
     * The four startup diagnostics, and the only game-bus registrations that name version-coupled
     * questions. Everything else on the game bus (tickers, guards, handlers, reactor sweep,
     * commands) stays in the constructor because it compiles on both legs; these four move here
     * because what they ask differs per Minecraft version. See each method for the difference.
     */
    public static void registerDiagnostics() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ModPlatform::reportVanillaProfiles);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ModPlatform::reportOurRecipes);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ModPlatform::reportSurvivalSeam);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ModPlatform::reportEnchantmentTags);
    }

    /**
     * Prints what every vanilla tool resolves to, once, at startup.
 *
 * <p><b>This is a diagnostic that exists because of a bug that produced no symptom a log could
 * show.</b> Two tag paths were written from memory, they did not exist, and a {@code TagKey} for a
 * path nothing publishes resolves to "matches nothing" rather than to an error &mdash; so three
 * weapons went back to vanilla with the build green and the log clean.</p>
 *
 * <p>The question a test cannot ask here is "does vanilla publish this tag", because the tags live
 * in the game's data jar and not on the test classpath. <b>So this asks the question the game can
 * answer instead</b>, by resolving every vanilla tool through the real pipeline and printing it.</p>
 *
 * <p><b>And it runs on {@code ServerStartedEvent}, not on {@code FMLCommonSetupEvent} -- which was
 * the second bug this line had.</b> Item tags are not bound until the datapack reloads, so asked at
 * common setup every {@code stack.is(tag)} answered false and the line printed {@code NONE} for all
 * seven. <b>A diagnostic that lies in exactly the shape of the bug it was built to catch is worse
 * than no diagnostic</b>: it was read as proof the tag round had failed when the profiles had been
 * right all along. By the time this fires the world is up and the tags are real.</p>
 */
private static void reportVanillaProfiles(
        net.neoforged.neoforge.event.server.ServerStartedEvent event) {
    StringBuilder line = new StringBuilder("[perfiles] ");
    for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[]{
            net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.IRON_AXE,
            net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.IRON_SHOVEL,
            net.minecraft.world.item.Items.IRON_HOE, net.minecraft.world.item.Items.MACE,
            net.minecraft.world.item.Items.TRIDENT}) {
        var profile = com.cortes.combat.attack.WeaponStats
                .of(new net.minecraft.world.item.ItemStack(item)).profile();
        boolean fights = com.cortes.combat.item.WeaponKinds.fights(profile);
        line.append(item.getDescription().getString()).append('=').append(profile)
                .append(fights ? "" : "(NO PELEA) ").append(' ');
    }
    org.slf4j.LoggerFactory.getLogger("cortescombat").info(line.toString());
}

/**
 * Prints how many of this mod's recipes and advancements the game loaded.
 *
 * <p><b>This exists because "zero" and "thirty-four" produced the same log line.</b> The datagen
 * wrote into {@code data/cortescombat/recipes/} and {@code data/cortescombat/advancements/}, 1.21
 * renamed every datapack folder to the singular, and the loader never read a file of ours.
 * {@code Loaded 1290 recipes} in the log was vanilla's own count -- and it was read, by me, as
 * proof that ours were among them.</p>
 *
 * <p>So the question is asked where the game can answer it: the manager's own list, filtered by
 * namespace. The count is also the assertion a test cannot make, because the datapack folders only
 * exist once the game has loaded them.</p>
 */
private static void reportOurRecipes(
        net.neoforged.neoforge.event.server.ServerStartedEvent event) {
    var recipes = event.getServer().getRecipeManager().getRecipes();
    long ours = recipes.stream()
            .filter(holder -> CortesCombat.MODID.equals(holder.id().getNamespace()))
            .count();
    if (ours == 0L) {
        CortesCombat.LOGGER.warn("[recetas] NINGUNA receta nuestra cargo, de {} en total. Revisa las carpetas "
                + "de datapack: 1.21 lee `recipe/` y `advancement/`, no `recipes/` ni "
                + "`advancements/`.", recipes.size());
    } else {
        CortesCombat.LOGGER.info("[recetas] nuestras={} total={}", ours, recipes.size());
    }
}

/**
 * Asks {@code FoodData} itself whether this mod's tick replaced its own, once the world is up.
 *
 * <p><b>Three things have to be true for survival to work, and a test can only reach two of
 * them.</b> The config file has to be in the jar, {@code neoforge.mods.toml}'s {@code [[mixins]]}
 * block has to name it, and the injection has to match. The first two are asserted at build time
 * by {@code SurvivalWiringTest}; the third only happens inside a running game, and it happens when
 * {@code FoodData} is first <em>loaded</em>.</p>
 *
 * <p><b>Which is not at startup, and that is the whole problem.</b> Verified with
 * {@code jcmd GC.class_histogram} against a live dedicated server with nobody connected: neither
 * {@code FoodData} nor {@code Player} was loaded at all, while {@code ItemStack} already had 4594
 * instances. So nothing in the boot sequence would have noticed a broken seam until the first
 * player joined, and the first symptom would have been a crash for whoever was testing. Loading
 * the class here moves that to world start.</p>
 *
 * <p>And it is {@code isAssignableFrom} rather than a bare class literal, on purpose: merely
 * loading {@code FoodData} and then logging "seam in place" would be <em>true</em> in the case
 * where the mixin config never registered &mdash; which is precisely the case that produces no
 * symptom at all. Asking the class what it became is the only version of this line that cannot
 * lie. See {@code FoodDataSeam}.</p>
 *
 * <p><b>It reports and does not crash.</b> A seam that failed to apply leaves vanilla food
 * working, so the correct response is a line nobody can miss and a game that still runs &mdash;
 * which is also this project's rule about not crashing over something that could be reported.</p>
 */
private static void reportSurvivalSeam(
        net.neoforged.neoforge.event.server.ServerStartedEvent event) {
    Class<?> target = net.minecraft.world.food.FoodData.class;
    if (com.cortes.combat.survival.FoodDataSeam.class.isAssignableFrom(target)) {
        CortesCombat.LOGGER.info("[supervivencia] FoodDataMixin aplicado: este server corre con la comida "
                + "reescrita (ciclo cerrado, sin exhaustion).");
    } else {
        CortesCombat.LOGGER.error("[supervivencia] FoodDataMixin NO se aplico a {}. La comida corre en vanilla "
                + "y el exhaustion sigue activo, y el arranque no mostro ningun error. Revisa que "
                + "neoforge.mods.toml tenga [[mixins]] config=\"{}.mixins.json\" y que "
                + "FoodDataMixin este en la lista de ese archivo.",
                target.getName(), CortesCombat.MODID);
    }
}

    /**
     * Asks the game whether every one of our enchantments is actually obtainable, once the world is
     * up and the tags are real.
     *
     * <p><b>This exists because "exists" and "obtainable" are two different questions.</b> An
     * enchantment with a file, a name and a hook that is not listed in
     * {@code minecraft:in_enchanting_table} does not error anywhere: the registry loads it, the
     * table simply never offers it, and the player never finds out. And {@code /enchant} uses the
     * same tag to decide whether an item accepts it ({@code ItemStack#supportsEnchantment}), so a
     * missing entry reads as "that enchantment cannot be applied to that item" with no hint of
     * why.</p>
     *
     * <p><b>It reports and does not crash.</b> The game keeps running; the line is the diagnosis,
     * which is also this project's rule: not crashing over something that can be reported.</p>
     */
    private static void reportEnchantmentTags(
            net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        var registry = event.getServer().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var tableTag = net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE;
        var missing = new java.util.ArrayList<String>();
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.addAll(com.cortes.combat.enchant.CombatEnchantRules.IDS);
        ids.addAll(com.cortes.combat.enchant.CombatEnchantRules.GOLD_IDS);
        for (String id : ids) {
            var key = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ENCHANTMENT,
                    com.cortes.combat.platform.ResourceIds.rl(id));
            var holder = registry.getHolder(key);
            if (holder.isEmpty() || !holder.get().is(tableTag)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            CortesCombat.LOGGER.info("[encantamientos] los {} nuestros estan en in_enchanting_table.",
                    ids.size());
        } else {
            CortesCombat.LOGGER.error("[encantamientos] FALTAN en in_enchanting_table: {}. Sin esa entrada el "
                    + "jugador no los consigue en la mesa y /enchant dice que el item no los admite. "
                    + "Revisa data/minecraft/tags/enchantment/in_enchanting_table.json.",
                    missing);
        }
    }
}
