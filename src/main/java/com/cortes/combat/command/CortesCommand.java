package com.cortes.combat.command;

import com.cortes.combat.CombatTrace;
import com.cortes.combat.CortesCombat;
import com.cortes.combat.client.ItemViewModel;
import com.cortes.combat.registry.ModItems;
import net.neoforged.fml.loading.FMLEnvironment;import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
                        .executes(CortesCommand::dumpInput)));
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
                Items.MACE, Items.TRIDENT));
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
        player.setData(com.cortes.combat.registry.ModAttachments.GUARD_STAMINA.get(),
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

        // The charge a swing is being held to right now, in the numbers the rule uses. The report
        // that prompted this was "every click registers as a charge", and a tap being read as a
        // full charge is not something a damage number can explain: it needs the count and the
        // threshold side by side, which is the whole difference between a feeling and a fact.
        com.cortes.combat.state.ChargeState live =
                player.getData(com.cortes.combat.registry.ModAttachments.CHARGE.get());
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
