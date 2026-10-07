package com.cortes.combat;

import com.cortes.combat.attack.CombatTicker;
import com.cortes.combat.command.CortesCommand;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.registry.ModComponents;
import com.cortes.combat.registry.ModCreativeTabs;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.registry.ModParticleTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Entry point. Deliberately thin: everything real lives in the packages below, so this file
 * never becomes the place where two systems talk to each other directly.
 *
 * <p>See ARCHITECTURE.md for the rules this class exists to enforce.</p>
 */
@Mod(CortesCombat.MODID)
public class CortesCombat {

    public static final String MODID = "cortescombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Console switch for the development overlays, so a tester can be told to run one command
     * instead of editing anything. Off means the mod draws nothing it was not asked to draw.
     */
    public static volatile boolean debug = false;

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
        LOGGER.warn("[recetas] NINGUNA receta nuestra cargo, de {} en total. Revisa las carpetas "
                + "de datapack: 1.21 lee `recipe/` y `advancement/`, no `recipes/` ni "
                + "`advancements/`.", recipes.size());
    } else {
        LOGGER.info("[recetas] nuestras={} total={}", ours, recipes.size());
    }
}

public CortesCombat(IEventBus modEventBus, ModContainer modContainer) {
        ModComponents.DATA_COMPONENT_TYPES.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModParticleTypes.PARTICLE_TYPES.register(modEventBus);
        com.cortes.combat.registry.ModEffects.register(modEventBus);
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
        // **One line at startup that says what every vanilla arm resolves to, and it exists because
        // of the tag round.**
        //
        // <p>Three weapons came back from a playtest as "full vanilla". The cause was two lines
        // naming a tag path from memory &mdash; {@code #maces} and {@code #tridents}, which
        // 1.21.1 does not publish &mdash; and a `TagKey` for a path nothing fills <b>never matches
        // and never errors</b>. A green build, no log line, no warning: the item simply resolved to
        // NONE, and NONE does not fight.</p>
        //
        // <p><b>So the next guess is checkable in one line instead of another launch.</b> Every
        // vanilla tool, its resolved profile, and whether it fights. If a path is wrong again the
        // log says NONE for it here, before anybody picks anything up.</p>
        NeoForge.EVENT_BUS.addListener(CortesCombat::reportVanillaProfiles);
        // **How many of our recipes the game actually loaded, and it exists because zero looked
        // like thirty-four.** The providers wrote `data/cortescombat/recipes/` and
        // `data/cortescombat/advancements/`; 1.21 reads `recipe/` and `advancement/`; and the log
        // said `Loaded 1290 recipes`, which is vanilla's own count and read as proof ours were in
        // it. They were not, and nothing said so for three rounds of playtesting. One line with
        // our number in it is the whole fix for the diagnosis.
        NeoForge.EVENT_BUS.addListener(CortesCombat::reportOurRecipes);
        NeoForge.EVENT_BUS.register(CombatTicker.class);
        NeoForge.EVENT_BUS.register(GuardHandler.class);
        NeoForge.EVENT_BUS.register(com.cortes.combat.special.ParryHandler.class);
        NeoForge.EVENT_BUS.register(com.cortes.combat.special.LungeImmunity.class);
        NeoForge.EVENT_BUS.register(com.cortes.combat.attack.ArmorWearHandler.class);
        // Pone las recetas del mod en el libro de crafteo de quien entra. Sin esto el libro depende
        // de que se conceda un avance, y en un mundo viejo donde las 32 ya estaban completas nunca
        // pasa: el jugador se queda con el libro vacío hasta que empieza otro mundo.
        NeoForge.EVENT_BUS.register(
                com.cortes.combat.data.RecipeBookPopulation.class);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> CortesCommand.register(event.getDispatcher()));
        LOGGER.info("Cortés Combat loading");
    }
}
