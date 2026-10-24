package com.cortes.combat.platform;

import com.cortes.combat.CortesCombat;

/**
 * The 1.20.1 twin of the registrations. Same call site, Forge objects.
 *
 * <p>Two lines vanish here on purpose and not by omission: components and attachments have no
 * 1.20.1 equivalent to register. Item data travels in NBT (see {@code WeaponData}) and combat
 * state attaches through gameplay events (see the v120 {@code CombatState}), and neither needs
 * a bus registration at all. Every other line below gains its Forge twin as its registry
 * cluster lands; a cluster that is not here yet is simply not registered yet, which is exactly
 * what "the 1.20.1 leg does not have that system" means. (⌒‿⌒)</p>
 *
 * <p>The parameters arrive as {@code Object} because the two loaders do not share a bus or
 * container type, and the shared call site cannot name either one. The casts are checked by
 * compilation on this leg; a wrong object here fails the build, not the game.</p>
 */
public final class ModPlatform {

    private ModPlatform() {
    }

    public static void registerAll(Object modEventBus, Object modContainer) {
        net.minecraftforge.eventbus.api.IEventBus bus =
                (net.minecraftforge.eventbus.api.IEventBus) modEventBus;
        // Forge 1.20.1 registers configs through the loading context, not the container: the
        // shared call site hands both over and this leg takes the context. `modContainer`
        // arrives unused on purpose -- dropping the parameter would fork the call site instead.
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                com.cortes.combat.config.ServerConfig.SPEC);
        com.cortes.combat.registry.ModItems.ITEMS.register(bus);
        com.cortes.combat.registry.ModArmor.register(bus);
        com.cortes.combat.registry.ModTools.register(bus);
        com.cortes.combat.registry.ModBlocks.register(bus);
        // The block-entity type registers on the mod bus; its item-handler capability attaches
        // per instance through the class's own @Mod.EventBusSubscriber, which defaults to the
        // Forge bus where AttachCapabilitiesEvent fires. No manual game-bus line needed here,
        // unlike the shared twin's RegisterCapabilitiesEvent listener.
        com.cortes.combat.registry.ModBlockEntities.register(bus);
        com.cortes.combat.registry.ModCreativeTabs.TABS.register(bus);
        com.cortes.combat.registry.ModParticleTypes.PARTICLE_TYPES.register(bus);
        com.cortes.combat.registry.ModEntities.ENTITY_TYPES.register(bus);
        com.cortes.combat.registry.ModEffects.register(bus);
        com.cortes.combat.enchant.ModEnchantments.ENCHANTMENTS.register(bus);
        // The thirty-seven combat enchantments are datapack JSON on 1.21.1 and classes here:
        // 1.20.1 has no datapack enchantment registry, so this leg transcribes the same
        // thirty-seven rows (same ids, same numbers) into code. (⌒‿⌒)
        com.cortes.combat.enchant.CombatEnchantRegistry.ENCHANTMENTS.register(bus);
        com.cortes.combat.enchant.GoldEnchantmentHandler.EFFECTS.register(bus);
        com.cortes.combat.core.crafting.ShiftableShapedRecipe.ModRecipes.register(bus);
        bus.addListener(com.cortes.combat.net.ModNetworking::register);
        // The client entry lives behind a dist gate: loading it on a dedicated server would
        // fail on the client-only references, which is why the shared twin splits the class.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist
                == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            com.cortes.combat.client.CortesCombatClient.register(bus,
                    (net.minecraftforge.fml.ModContainer) modContainer);
        }
    }

    /**
     * The four startup diagnostics, answered the 1.20.1 way. Same questions as the shared twin,
     * asked where this version can answer them: no mace in the vanilla list (it does not exist
     * yet), no tag table for enchantments (obtainability is a different system here, so the
     * check is registry presence instead), and the recipe count by id rather than by holder.
     */
    public static void registerDiagnostics() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ModPlatform::reportVanillaProfiles);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ModPlatform::reportOurRecipes);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ModPlatform::reportSurvivalSeam);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ModPlatform::reportEnchantmentTags);
    }

    private static void reportVanillaProfiles(
            net.minecraftforge.event.server.ServerStartedEvent event) {
        StringBuilder line = new StringBuilder("[perfiles] ");
        for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[]{
                net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.IRON_AXE,
                net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.IRON_SHOVEL,
                net.minecraft.world.item.Items.IRON_HOE, net.minecraft.world.item.Items.TRIDENT}) {
            var profile = com.cortes.combat.attack.WeaponStats
                    .of(new net.minecraft.world.item.ItemStack(item)).profile();
            boolean fights = com.cortes.combat.item.WeaponKinds.fights(profile);
            line.append(item.getDescription().getString()).append('=').append(profile)
                    .append(fights ? "" : "(NO PELEA) ").append(' ');
        }
        org.slf4j.LoggerFactory.getLogger("cortescombat").info(line.toString());
    }

    private static void reportOurRecipes(
            net.minecraftforge.event.server.ServerStartedEvent event) {
        var recipes = event.getServer().getRecipeManager().getRecipes();
        long ours = recipes.stream()
                .filter(holder -> CortesCombat.MODID.equals(holder.getId().getNamespace()))
                .count();
        if (ours == 0L) {
            CortesCombat.LOGGER.warn("[recetas] NINGUNA receta nuestra cargo, de {} en total.",
                    recipes.size());
        } else {
            CortesCombat.LOGGER.info("[recetas] nuestras={} total={}", ours, recipes.size());
        }
    }

    private static void reportSurvivalSeam(
            net.minecraftforge.event.server.ServerStartedEvent event) {
        Class<?> target = net.minecraft.world.food.FoodData.class;
        if (com.cortes.combat.survival.FoodDataSeam.class.isAssignableFrom(target)) {
            CortesCombat.LOGGER.info("[supervivencia] FoodDataMixin aplicado: este server corre con la comida "
                    + "reescrita (ciclo cerrado, sin exhaustion).");
        } else {
            CortesCombat.LOGGER.error("[supervivencia] FoodDataMixin NO se aplico a {}. Revisa que "
                    + "mods.toml tenga [[mixins]] config=\"{}.mixins.json\" y que FoodDataMixin "
                    + "este en la lista de ese archivo.", target.getName(), CortesCombat.MODID);
        }
    }

    private static void reportEnchantmentTags(
            net.minecraftforge.event.server.ServerStartedEvent event) {
        // 1.20.1 has no in_enchanting_table tag: obtainability is a different system here
        // (treasure flags, not tags), so this leg asks the question it CAN answer -- are ours
        // even registered -- and leaves the table behavior to a playtest with a real table.
        var missing = new java.util.ArrayList<String>();
        java.util.List<String> ids = new java.util.ArrayList<>();
        ids.addAll(com.cortes.combat.enchant.CombatEnchantRules.IDS);
        ids.addAll(com.cortes.combat.enchant.CombatEnchantRules.GOLD_IDS);
        for (String id : ids) {
            if (!net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.containsKey(
                    ResourceIds.rl(id))) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            CortesCombat.LOGGER.info("[encantamientos] los {} nuestros estan registrados.",
                    ids.size());
        } else {
            CortesCombat.LOGGER.error("[encantamientos] FALTAN del registro: {}. Hasta que la "
                    + "tanda de encantamientos se porte, ni /enchant ni la mesa los conocen.",
                    missing);
        }
    }
}
