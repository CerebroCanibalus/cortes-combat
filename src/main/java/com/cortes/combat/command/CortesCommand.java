package com.cortes.combat.command;

import com.cortes.combat.CortesCombat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                        .executes(CortesCommand::showStats)));
    }

    /** One of every vanilla weapon, so all the derived profiles can be checked at once. */
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
        int slot = 0;
        for (Item item : weapons) {
            giveAt(player, new ItemStack(item), slot++);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Datos " + weapons.size() + " armas en la hotbar. Probá el alcance y la carga de cada una."), false);
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
        player.setData(com.cortes.combat.registry.ModAttachments.GUARD_STAMINA.get(),
                com.cortes.combat.state.GuardState.FULL);
        ctx.getSource().sendSuccess(() -> Component.literal("Aguante restaurada."), false);
        return 1;
    }

    /** Set food low enough that natural healing is visibly gated, for testing the regen loop. */
    private static int drainFood(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = player(ctx);
        if (player == null) {
            return 0;
        }
        player.getFoodData().setFoodLevel(2);
        ctx.getSource().sendSuccess(() -> Component.literal("Comida a 2. La regen debería frenarse."), false);
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
                        + " h=" + reach.horizontalAngle() + "°"
                        + " v=" + reach.verticalAngle() + "°"), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "cooldown=" + interval + "t"
                        + " carga=" + (charge.canCharge()
                        ? "+" + Math.round(charge.bonus() * 100) + "% en " + charge.fullChargeTicks() + "t"
                        : "no")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "especial=" + stats.special().id()), false);
        return 1;
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
