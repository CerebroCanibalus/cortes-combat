package com.cortes.combat;

import com.cortes.combat.attack.CombatTicker;
import com.cortes.combat.command.CortesCommand;
import com.cortes.combat.guard.GuardHandler;
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


public CortesCombat(IEventBus modEventBus, ModContainer modContainer) {
        // Every DeferredRegister this mod owns lives in platform.ModPlatform, one body per leg.
        // Same call site on both versions; each leg resolves it against its own loader.
        com.cortes.combat.platform.ModPlatform.registerAll(modEventBus, modContainer);
        // The four startup diagnostics live in platform.ModPlatform: they ask
        // version-coupled questions (vanilla tags, datapack folders, the food mixin seam,
        // the enchantment table tag), so each leg answers them its own way.
        com.cortes.combat.platform.ModPlatform.registerDiagnostics();
        NeoForge.EVENT_BUS.register(CombatTicker.class);
        NeoForge.EVENT_BUS.register(GuardHandler.class);
        // Gold's nine, and the only subscriber that knows what they do: every hook lives in the
        // handler so the rules stay in `GoldEnchantRules`, pure and tested.
        NeoForge.EVENT_BUS.register(com.cortes.combat.enchant.GoldEnchantmentHandler.class);
        NeoForge.EVENT_BUS.register(com.cortes.combat.special.ParryHandler.class);
        NeoForge.EVENT_BUS.register(com.cortes.combat.special.LungeImmunity.class);
        NeoForge.EVENT_BUS.register(com.cortes.combat.attack.ArmorWearHandler.class);
        // **The reactor's resumable sweep, advanced a slice per server tick.** The hook on
        // `SignalGetter` makes a component SEE the reactor's power; this is what makes it re-ask.
        // Registering it on the game bus as a `ServerTickEvent.Post` listener, because the sweep is
        // server-only and the game bus is where gameplay ticks live.
        NeoForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorScan::onServerTick);
        // **Y un componente puesto dentro de un campo vivo se re-evalua en el momento.** El barrido
        // corre al encender, al apagar y al cambiar el radio -- nunca al colocar. La lampara y el
        // piston se preguntan solos al aterrizar, pero hay componentes que no, y "lo coloque
        // despues de activar y nunca prendio" fue el reporte. Mismo llamado que el barrido, uno solo,
        // y solamente si el tag y el radio lo cubren.
        NeoForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorScan::onBlockPlace);
        // **Y el espejo: romper un cable es dejar de recibir fuerza gratis.** SÃ³lo rompimientos de
        // jugador; una explosiÃ³n no dispara este evento, y el limite queda documentado en el metodo.
        NeoForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorScan::onBlockBreak);
        // **The star's crater, carved a slice at a time.** A power-120 point blast cannot run in a
        // tick, so the destruction is an explicit sphere advanced here against a fixed per-tick
        // budget. Registered beside the sweep because it is the same shape of job: server-only,
        // resumable, one shared budget.
        NeoForge.EVENT_BUS.addListener(com.cortes.combat.block.ReactorBlast::onServerTick);
        // **Y los dos registros estaticos se vacian al cerrar el mundo.** `ReactorScan.PENDING` y
        // `ActiveReactors.BY_LEVEL` sobreviven a un mundo por ser `static`: un barrido pendiente
        // seguiria apuntando al nivel que se esta guardando y el siguiente mundo heredaria reactores
        // que no existen. El sintoma del primero es el cuelgue en "Guardando mundo".
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) -> {
            com.cortes.combat.block.ReactorScan.clear();
            com.cortes.combat.block.ActiveReactors.clear();
            // The wired region is static and keyed by dimension too: a world that closed would
            // otherwise leave its wires immune to decay for the next one loaded in this process.
            com.cortes.combat.block.ReactorNetwork.clear();
        });
        // **Las dos mecÃ¡nicas del cobre, y una de ellas existe por una restricciÃ³n verificada del
        // API.** `MobEffectInstance` no tiene setter de duraciÃ³n y `mapDuration` no muta, asÃ­ que
        // acortar un debuff sÃ³lo se puede hacer negando su aplicaciÃ³n y metiendo una mÃ¡s corta por
        // `forceAddEffect`. Ver `CopperEffects`.
        NeoForge.EVENT_BUS.register(com.cortes.combat.effect.CopperEffects.class);
        // Pone las recetas del mod en el libro de crafteo de quien entra. Sin esto el libro depende
        // de que se conceda un avance, y en un mundo viejo donde las 32 ya estaban completas nunca
        // pasa: el jugador se queda con el libro vacÃ­o hasta que empieza otro mundo.
        NeoForge.EVENT_BUS.register(
                com.cortes.combat.data.RecipeBookPopulation.class);
        NeoForge.EVENT_BUS.addListener(
                (RegisterCommandsEvent event) -> CortesCommand.register(event.getDispatcher()));
        // Los weapon packs: config/cortescombat/weapons/*.yaml + el builtin del classpath, en
        // ese orden de precedencia. Nunca lanza -- un YAML roto queda como warning nombrado y
        // el mod carga sin esa entrada, no sin cargar.
        com.cortes.combat.weaponpack.WeaponPackStore.reload();
        // El template de assets en resourcepacks/ -- si su carpeta no existe todavÃ­a. Al lado
        // del reload y no en un lifecycle event: es escritura de disco de una sola vez y aquÃ­
        // ya sabemos que los packs cargaron.
        com.cortes.combat.weaponpack.WeaponPackTemplate.writeIfMissing();
        // **El exportador de armas base NO va aca.** Necesita los items ya registrados y en el
        // constructor todavia no lo estan: corria `runData` y moria con
        // `Trying to access unbound value: cortescombat:stone_ropera`, o sea una instalacion nueva
        // crasheaba. Vive en `ServerStartedEvent`, junto al generador automatico. (μ_μ)
        LOGGER.info("CortÃ©s Combat loading");
    }
}
