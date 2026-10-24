package com.cortes.combat.command;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.CombatTrace;
import com.cortes.combat.CortesCombat;
import com.cortes.combat.block.NetherReactorBlock;
import com.cortes.combat.block.NetherReactorBlockEntity;
import com.cortes.combat.client.ItemViewModel;
import com.cortes.combat.registry.ModItems;
import net.minecraftforge.fml.loading.FMLEnvironment;import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The testing harness, as a command. Development only.
 *
 * <p>Exists so that testing a weapon does not require a survival run to a chest. Every
 * subcommand takes effect immediately and is meant to be typed in a test world, not shipped
 * in a competitive server.</p>
 */
public final class CortesCommand {

    private CortesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("weapons")
                        .executes(CortesCommand::giveAllWeapons))
                .then(Commands.literal("weapon")
                        .then(Commands.argument("item", StringArgumentType.word())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(CortesCommand::giveWeapon)))
                .then(Commands.literal("shield")
                        .executes(ctx -> give(ctx, Items.SHIELD)))
                .then(Commands.literal("fullguard")
                        .executes(CortesCommand::refillGuard))
                .then(Commands.literal("nofood")
                        .executes(CortesCommand::drainFood))
                .then(Commands.literal("speed")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 1000000))
                                .executes(CortesCommand::setSpeed)))
                .then(Commands.literal("teleport")
                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 32))
                                .executes(CortesCommand::teleportAhead)))
                .then(Commands.literal("stats")
                        .executes(CortesCommand::showStats))
                .then(Commands.literal("reload")
                        .executes(CortesCommand::reloadPacks))
                .then(Commands.literal("packs")
                        .executes(CortesCommand::showPacks))
                .then(Commands.literal("viewmodel")
                        .executes(CortesCommand::showViewmodel))
                .then(Commands.literal("cone")
                        .executes(CortesCommand::toggleCone))
                .then(Commands.literal("trace")
                        .executes(CortesCommand::toggleTrace))
                .then(Commands.literal("gui")
                        .executes(CortesCommand::toggleGui))
                .then(Commands.literal("hud")
                        .executes(CortesCommand::dumpHud))
                .then(Commands.literal("input")
                        .executes(CortesCommand::dumpInput))
                .then(Commands.literal("reactor")
                        .then(Commands.literal("charge")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 40))
                                        .executes(CortesCommand::reactorCharge)))
                        .then(Commands.literal("active")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(CortesCommand::reactorActive)))
                        .then(Commands.literal("clock")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 1000000))
                                        .executes(CortesCommand::reactorClock)))
                        .then(Commands.literal("overload")
                                .then(Commands.literal("firestone")
                                        .executes(CortesCommand::reactorOverloadFirestone))
                                .then(Commands.literal("star")
                                        .executes(CortesCommand::reactorOverloadStar))
                                .then(Commands.literal("none")
                                        .executes(CortesCommand::reactorOverloadNone)))
                        .then(Commands.literal("status")
                                .executes(CortesCommand::reactorStatus))));
    }

    /**
     * One of every weapon: the eleven vanilla ones and all thirty of ours.
     *
     * <p>Ours come from {@link ModItems#weapons()} rather than from a list written here, so the
     * command cannot fall behind the catalogue. It used to be a literal list of vanilla items and
     * it was already stale by the time this mod had any weapons of its own: eleven items for a
     * catalogue of thirty plus eleven, and no way for the mismatch to be noticed except running
     * the command and counting the hotbar.</p>
     *
     * <p>Vanilla first, then ours in catalogue order, because the vanilla set is what the derived
     * profiles are checked against and ours is what has explicit components. A playtest that wants
     * to compare a ropera against a sword needs them adjacent, and family order puts every ropera
     * together instead.</p>
     */
    private static int giveAllWeapons(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        List<Item> weapons = new ArrayList<>(List.of(
                Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
                Items.IRON_AXE, Items.DIAMOND_AXE,
                Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE,
                Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL,
                // No vanilla mace on this leg to hand out; ours come from the catalogue below.
                Items.TRIDENT));
        ModItems.weapons().forEach(ours -> weapons.add(ours.get()));
        int slot = 0;
        for (Item item : weapons) {
            giveAt(player, new ItemStack(item), slot++);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                weapons.size() + " armas en la hotbar. Probá el alcance y la carga de cada una."), false);
        return weapons.size();
    }

    private static int giveWeapon(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "item");
        ResourceLocation id = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("No se pudo parsear: " + raw));
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            ctx.getSource().sendFailure(Component.literal("Item desconocido: " + id));
            return 0;
        }
        give(player, item);
        return 1;
    }

    private static int refillGuard(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        // Refill against the item's own maximum, not a fixed number: a sword is worth 1, so
        // "full" means a different thing depending on what is in hand.
        int max = com.cortes.combat.guard.GuardHandler.bestFor(player).profile().safePoints();
        CombatState.setGuard(player,
                new com.cortes.combat.state.GuardState(false, max, max, 40));
        ctx.getSource().sendSuccess(() -> Component.literal("Aguante restaurada (" + max + ")."),
                false);
        return 1;
    }

    /** Set food low enough that natural healing is visibly gated, for testing the regen loop. */
    private static int drainFood(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        player.getFoodData().setFoodLevel(2);
        ctx.getSource().sendSuccess(() -> Component.literal("Comida a 2. La regen deberÃ­a frenarse."), false);
        return 1;
    }

    private static int setSpeed(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, false));
        ctx.getSource().sendSuccess(() -> Component.literal("Velocidad por " + ticks + " ticks."), false);
        return ticks;
    }

    private static int teleportAhead(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        int distance = IntegerArgumentType.getInteger(ctx, "distance");
        var look = player.getLookAngle();
        player.teleportTo(player.getX() + look.x * distance,
                player.getY() + look.y * distance,
                player.getZ() + look.z * distance);
        player.setDeltaMovement(0, 0, 0);
        ctx.getSource().sendSuccess(() -> Component.literal("Adelante " + distance + " bloques."), false);
        return distance;
    }

    /**
     * Prints the live viewmodel state, on the client.
     *
     * <p>Deliberately a client side question and not a server one: the viewmodel is drawn from
     * client state the server knows nothing about, so {@code /cc stats} can be answered anywhere
     * and this one cannot. Run it <b>while charging</b>, not at rest, because at rest every
     * channel is zero by design and a correct pipeline looks identical to a dead one.</p>
     *
     * <p>Every reference to {@code ItemViewModel} sits behind a dist guard, because this class is
     * common and that one imports {@code Minecraft}. Referenced directly it compiles happily and
     * then takes a dedicated server down with a {@code NoClassDefFoundError} the first time the
     * command tree is built, which is the same family of bug as the {@code InputEvent} import
     * that made the client entry point separate in the first place. The guard is a plain
     * {@code if} rather than {@code DistExecutor} because that class does not exist in NeoForge
     * 21.1, verified by listing the loader jar.</p>
     */
    private static int showViewmodel(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        if (!FMLEnvironment.dist.isClient()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "El viewmodel solo existe en el cliente."), false);
            return 0;
        }
        String line = ItemViewModel.debug();
        if (line == null) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "El viewmodel solo existe en el cliente."), false);
            return 0;
        }
        player.connection.send(
                new ClientboundSetActionBarTextPacket(Component.literal("viewmodel: " + line)));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Viewmodel enviado al cliente. Mirmilo con la carga en el aire."), false);
        return 1;
    }

    /** Print the resolved stats of the held weapon. This is the one that catches surprises. */
    /**
     * {@code /cc reload}: releer los weapon packs sin reiniciar. Los stats y los sonidos ya se
     * resuelven contra la tabla nueva al instante; lo que NO se recarga acá son los assets del
     * resource pack (atlas de sprites, sounds.json, modelos): esos piden F3+T.
     */
    private static int reloadPacks(CommandContext<CommandSourceStack> ctx) {
        var summary = com.cortes.combat.weaponpack.WeaponPackStore.reload();
        if (summary == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No se pudo leer los weapon packs -- el log nombra el archivo."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[weaponpack] " + summary.packs() + " packs, " + summary.entries()
                        + " entradas, " + summary.warnings() + " warnings (en el log)."
                        + " Assets nuevos: F3+T."), false);
        return summary.entries();
    }

    /** {@code /cc packs}: qué hay cargado, y qué entrada matchea el arma de la mano. */
    private static int showPacks(CommandContext<CommandSourceStack> ctx) {
        var packs = com.cortes.combat.weaponpack.WeaponPackStore.packs();
        if (packs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Sin weapon packs. Crea config/cortescombat/weapons/*.yaml "
                            + "(guía: docs/custom-weapons.md)."), false);
            return 0;
        }
        for (var pack : packs) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    pack.origin() + " — " + pack.weapons().size() + " entradas"), false);
        }
        ServerPlayer player = player(ctx);
        if (player != null) {
            var resolved = com.cortes.combat.weaponpack.WeaponPackStore
                    .resolve(player.getMainHandItem());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    resolved != null
                            ? "en mano: " + player.getMainHandItem().getHoverName().getString()
                            + " ← " + resolved.source()
                            : "en mano: ninguna entrada matchea el arma actual"), false);
        }
        return packs.size();
    }

    private static int showStats(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        var stats = com.cortes.combat.attack.WeaponStats.of(player.getMainHandItem());
        var reach = stats.reach();
        var charge = stats.charge();
        int interval = com.cortes.combat.attack.AttackCooldown.intervalFor(player);

        ctx.getSource().sendSuccess(() -> Component.literal(
                "Arma: " + player.getMainHandItem().getHoverName().getString()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "perfil=" + stats.profile().id()
                        + " alcance=" + reach.distance()
                        + " h=" + reach.horizontalAngle() + "Â°"
                        + " v=" + reach.verticalAngle() + "Â°"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "cooldown=" + interval + "t"
                        + " carga=" + (charge.canCharge()
                        ? "+" + Math.round(charge.bonus() * 100) + "% en " + charge.fullChargeTicks() + "t"
                        : "no")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "especial=" + stats.special().id()
                        + " knockback=h" + stats.knockback().horizontal()
                        + "/v" + stats.knockback().vertical()), false);
        // **De dónde salió cada número**, que es lo que convierte una duda en una dirección.
        // Sin pack la respuesta sigue siendo una fuente: el árbol completo (molde → vanilla →
        // NONE), no "ninguna".
        var pack = com.cortes.combat.weaponpack.WeaponPackStore.resolve(
                player.getMainHandItem());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "source=" + (pack != null ? "pack " + pack.source()
                        : "sin pack (tag molde → vanilla → NONE)")), false);

        // The charge a swing is being held to right now, in the numbers the rule uses. The report
        // that prompted this was "every click registers as a charge", and a tap being read as a
        // full charge is not something a damage number can explain: it needs the count and the
        // threshold side by side, which is the whole difference between a feeling and a fact.
        com.cortes.combat.state.ChargeState live =
                CombatState.charge(player);
        int threshold = com.cortes.combat.attack.AttackTiming.MIN_CHARGE_HOLD_TICKS;
        String verdict = !live.holding()
                ? "sin swing abierto"
                : (live.ticks() < threshold
                ? "TAP (no carga)"
                : "CARGADO (" + live.ticks() + "t)");
        ctx.getSource().sendSuccess(() -> Component.literal(
                "carga viva=" + live.ticks() + "t"
                        + " umbral=" + threshold + "t"
                        + " -> " + verdict), false);
        return 1;
    }

    /** Toggle the cone visualisation. The flag lives on the client, so it travels as a packet. */
    private static int toggleCone(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        DebugFlags flags = get(player);
        boolean now = !flags.cone();
        push(player, now, flags.hud(), flags.trace());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cono de alcance: " + (now ? "VISIBLE" : "oculto") + " (toggle con /cc cone)"), false);
        return 1;
    }

    /**
     * Turn the four-line-per-attack trace on or off.
     *
     * <p>Off by default, and that is the fix rather than the setting: it wrote four lines per
     * click on both sides, which made an hour of playtest into an unreadable log, and an
     * unreadable log is how a genuine error gets read as a clean one.</p>
     */
    private static int toggleTrace(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        DebugFlags flags = get(player);
        boolean now = CombatTrace.toggle();
        push(player, flags.cone(), flags.hud(), now);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Trazado de combate: " + (now ? "ON" : "OFF")
                        + (now ? " (unas lineas por golpe, cliente y servidor, en latest.log)" : "")),
                false);
        return 1;
    }

    /** Toggle our own HUD layers, so the vanilla indicator can be compared side by side. */
    private static int toggleGui(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        DebugFlags flags = get(player);
        boolean now = !flags.hud();
        push(player, flags.cone(), now, flags.trace());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "HUD de Cortes Combat: " + (now ? "visible" : "oculto") + " (toggle con /cc gui)"), false);
        return 1;
    }

    /** Server-side mirror of the client's debug flags, one entry per player. */
    private static final Map<java.util.UUID, DebugFlags> DEBUG = new HashMap<>();

    /**
     * @param trace kept here rather than in {@code CombatTrace} alone, because that static is
     *              the server's copy and a command has to be able to say what the player asked
     *              for without assuming it travelled
     */
    private record DebugFlags(boolean cone, boolean hud, boolean trace) {
    }

    /** One call for all three, so no toggle can ever leave one of them behind. */
    private static void push(ServerPlayer player, boolean cone, boolean hud, boolean trace) {
        put(player, new DebugFlags(cone, hud, trace));
        com.cortes.combat.net.ModNetworking.sendDebugToggle(player, cone, hud, trace, false, false);
    }

    /**
     * Ask the client to print its HUD geometry, without changing a single flag.
     *
     * <p>A one-shot request rather than a toggle, and it is the fourth thing riding the same
     * packet because it is the same trip for the same reason: the numbers are the client's, and
     * a command on the server cannot read them.</p>
     */
    private static int dumpHud(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        DebugFlags flags = get(player);
        com.cortes.combat.net.ModNetworking.sendDebugToggle(player,
                flags.cone(), flags.hud(), flags.trace(), true, false);
        return 1;
    }

    /**
     * Prints every gate that can refuse an attack press, and which one is refusing right now.
     *
     * <p>The command a playtest report of "the click broke" needed and did not have. The server
     * trace shows the presses arriving and then stopping, and nothing else in the build can tell
     * eight silent gates apart -- so this reads them, in order, with the value each one consults.</p>
     */
    private static int dumpInput(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        DebugFlags flags = get(player);
        com.cortes.combat.net.ModNetworking.sendDebugToggle(player,
                flags.cone(), flags.hud(), flags.trace(), false, true);
        return 1;
    }

    /**
     * The reactor a debug subcommand acts on: the one the player is looking at.
     *
     * <p>No coordinate argument on purpose. A reactor is a block you are standing at, and a tester
     * typing coordinates is a tester mis-typing one; twenty blocks is the reach every other "look
     * at the thing" debug uses.</p>
     */
    private static NetherReactorBlockEntity reactorAt(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return null;
        }
        HitResult hit = player.pick(20.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            ctx.getSource().sendFailure(Component.literal("Mira a un reactor dentro de 20 bloques."));
            return null;
        }
        if (!(player.level().getBlockEntity(blockHit.getBlockPos())
                instanceof NetherReactorBlockEntity reactor)) {
            ctx.getSource().sendFailure(Component.literal("El bloque que miras no es un reactor."));
            return null;
        }
        return reactor;
    }

    private static int reactorCharge(CommandContext<CommandSourceStack> ctx) {
        NetherReactorBlockEntity reactor = reactorAt(ctx);
        ServerPlayer player = player(ctx);
        if (reactor == null || player == null) {
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        reactor.setCharge(player.serverLevel(), value);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reactor: carga=" + reactor.charge() + "/40"), false);
        return 1;
    }

    private static int reactorActive(CommandContext<CommandSourceStack> ctx) {
        NetherReactorBlockEntity reactor = reactorAt(ctx);
        ServerPlayer player = player(ctx);
        if (reactor == null || player == null) {
            return 0;
        }
        boolean on = BoolArgumentType.getBool(ctx, "value");
        reactor.setDebugActive(player.serverLevel(), on);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reactor forzado " + (on ? "ACTIVO" : "libre") + " (la palanca real manda si es false)."),
                false);
        return 1;
    }

    private static int reactorClock(CommandContext<CommandSourceStack> ctx) {
        NetherReactorBlockEntity reactor = reactorAt(ctx);
        ServerPlayer player = player(ctx);
        if (reactor == null || player == null) {
            return 0;
        }
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        reactor.addClock(player.serverLevel(), ticks);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reloj del reactor +" + ticks + " t (hora completa en "
                        + com.cortes.combat.block.ReactorCharge.HOUR_TICKS + ")."), false);
        return 1;
    }

    private static int reactorOverloadFirestone(CommandContext<CommandSourceStack> ctx) {
        return forceOverload(ctx, com.cortes.combat.block.ReactorOverload.Kind.FIRESTONE,
                "Sobrecarga de FIRESTONE: fusible de 300 t. La palanca la corta.");
    }

    private static int reactorOverloadStar(CommandContext<CommandSourceStack> ctx) {
        return forceOverload(ctx, com.cortes.combat.block.ReactorOverload.Kind.STAR,
                "Sobrecarga de NETHER STAR: radio 100, fusible de 300 t, solo el agua la corta.");
    }

    private static int reactorOverloadNone(CommandContext<CommandSourceStack> ctx) {
        return forceOverload(ctx, com.cortes.combat.block.ReactorOverload.Kind.NONE,
                "Reactor apagado: sin sobrecarga.");
    }

    private static int forceOverload(CommandContext<CommandSourceStack> ctx,
                                     com.cortes.combat.block.ReactorOverload.Kind kind, String message) {
        NetherReactorBlockEntity reactor = reactorAt(ctx);
        ServerPlayer player = player(ctx);
        if (reactor == null || player == null) {
            return 0;
        }
        reactor.forceOverload(player.serverLevel(), kind);
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    /** One line with everything the reactor is doing, because a debug command that lies is worse than none. */
    private static int reactorStatus(CommandContext<CommandSourceStack> ctx) {
        NetherReactorBlockEntity reactor = reactorAt(ctx);
        if (reactor == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Reactor: carga=" + reactor.charge()
                        + " conCarga=" + reactor.getBlockState().getValue(NetherReactorBlock.CHARGED)
                        + " activo=" + reactor.getBlockState().getValue(NetherReactorBlock.ACTIVE)
                        + " senal=" + reactor.powered()
                        + " sobrecarga=" + reactor.overload()
                        + " fusible=" + reactor.fuse()
                        + " reloj=" + reactor.hourTicks()), false);
        return 1;
    }

    private static DebugFlags get(ServerPlayer player) {
        return DEBUG.getOrDefault(player.getUUID(), new DebugFlags(false, true, false));
    }

    private static void put(ServerPlayer player, DebugFlags flags) {
        DEBUG.put(player.getUUID(), flags);
    }

    private static int give(CommandContext<CommandSourceStack> ctx, Item item) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        give(player, item);
        return 1;
    }

    private static void give(ServerPlayer player, Item item) {
        giveAt(player, new ItemStack(item), player.getInventory().selected);
    }

    private static void giveAt(ServerPlayer player, ItemStack stack, int slot) {
        if (slot >= player.getInventory().items.size()) {
            player.getInventory().add(stack);
            return;
        }
        player.getInventory().setItem(slot, stack);
        player.containerMenu.broadcastChanges();
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Este comando necesita un jugador, no la consola."));
            return null;
        }
    }

    private static final SuggestionProvider<CommandSourceStack> ITEM_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    BuiltInRegistries.ITEM.keySet().stream()
                            .map(ResourceLocation::getPath)
                            .filter(path -> path.contains(builder.getRemaining().toLowerCase(Locale.ROOT))),
                    builder);
}
