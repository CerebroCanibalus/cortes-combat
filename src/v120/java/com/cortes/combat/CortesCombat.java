package com.cortes.combat;

import com.cortes.combat.command.CortesCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * The 1.20.1 entry point. Same subscriptions, native constructor.
 *
 * <p>Forge 1.20.1 only constructs the mod class with no arguments or with a
 * {@code FMLJavaModLoadingContext} (verified in its bytecode: it tries the context
 * constructor, then the no-arg one -- there is no {@code (IEventBus, ModContainer)}
 * form on this leg). The no-arg form takes the mod bus off the loading context, which
 * is already the active one while the mod constructs. Everything below that line is
 * the shared constructor verbatim, against {@code MinecraftForge#EVENT_BUS}. (⌒‿⌒)</p>
 */
@net.minecraftforge.fml.common.Mod(CortesCombat.MODID)
public class CortesCombat {

    public static final String MODID = "cortescombat";

    /**
     * Console switch for the development overlays, so a tester can be told to run one command
     * instead of editing anything. Off means the mod draws nothing it was not asked to draw.
     */
    public static volatile boolean debug = false;

    public static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();

    public CortesCombat() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Every DeferredRegister this mod owns lives in platform.ModPlatform, one body per leg.
        // Same call site on both versions; each leg resolves it against its own loader.
        com.cortes.combat.platform.ModPlatform.registerAll(modEventBus, null);
        // The four startup diagnostics live in platform.ModPlatform: they ask
        // version-coupled questions (vanilla tags, datapack folders, the food mixin seam,
        // the enchantment table tag), so each leg answers them its own way.
        com.cortes.combat.platform.ModPlatform.registerDiagnostics();
        MinecraftForge.EVENT_BUS.register(com.cortes.combat.attack.CombatTicker.class);
        MinecraftForge.EVENT_BUS.register(com.cortes.combat.guard.GuardHandler.class);
        // Gold's nine, and the only subscriber that knows what they do: every hook lives in the
        // handler so the rules stay in `GoldEnchantRules`, pure and tested.
        MinecraftForge.EVENT_BUS.register(com.cortes.combat.enchant.GoldEnchantmentHandler.class);
        MinecraftForge.EVENT_BUS.register(com.cortes.combat.special.ParryHandler.class);
        MinecraftForge.EVENT_BUS.register(com.cortes.combat.special.LungeImmunity.class);
        MinecraftForge.EVENT_BUS.register(com.cortes.combat.attack.ArmorWearHandler.class);
        // **The reactor's resumable sweep, advanced a slice per server tick.**
        MinecraftForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorScan::onServerTick);
        // **Y un componente puesto dentro de un campo vivo se re-evalua en el momento.**
        MinecraftForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorScan::onBlockPlace);
        // **Y el espejo: romper un cable es dejar de recibir fuerza gratis.**
        MinecraftForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorScan::onBlockBreak);
        // **The star's crater, carved a slice at a time.**
        MinecraftForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorBlast::onServerTick);
        // **Y los dos registros estaticos se vacian al cerrar el mundo.**
        MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppingEvent event) -> {
                    com.cortes.combat.block.ReactorScan.clear();
                    com.cortes.combat.block.ActiveReactors.clear();
                    com.cortes.combat.block.ReactorNetwork.clear();
                });
        // **Las dos mecanicas del cobre.**
        MinecraftForge.EVENT_BUS.register(com.cortes.combat.effect.CopperEffects.class);
        // Pone las recetas del mod en el libro de crafteo de quien entra.
        MinecraftForge.EVENT_BUS.register(
                com.cortes.combat.data.RecipeBookPopulation.class);
        MinecraftForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> CortesCommand.register(event.getDispatcher()));
        // Los weapon packs: config/cortescombat/weapons/*.yaml + el builtin del classpath.
        com.cortes.combat.weaponpack.WeaponPackStore.reload();
        // El template de assets en resourcepacks/ -- si su carpeta no existe todavia.
        com.cortes.combat.weaponpack.WeaponPackTemplate.writeIfMissing();
        LOGGER.info("Cortes Combat loading");
    }
}
