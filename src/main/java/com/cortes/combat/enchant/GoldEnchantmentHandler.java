package com.cortes.combat.enchant;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModAttachments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Random;

/**
 * Gold's nine, and what each one actually does.
 *
 * <h2>Why this is a set of events and not a mixin</h2>
 *
 * <p>Every one of them hangs off something NeoForge already publishes: a kill, a hit, a broken
 * block, a tick. <b>Nine mixins for nine numbers would be nine things to maintain across versions
 * for behaviour the bus already exposes</b>, and the project's own rule is the simplest
 * intervention that answers the question.</p>
 *
 * <h2>The two events that are not the same event</h2>
 *
 * <p><b>{@link LivingIncomingDamageEvent} is before armour and {@link LivingHurtEvent} is after
 * it.</b> That single ordering decides which rules read which: life steal must heal from the number
 * the target actually lost, so it reads the late one, and phantom strike has to change the number
 * before armour sees it, so it reads the early one.</p>
 */
public final class GoldEnchantmentHandler {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, CortesCombat.MODID);

    /** The mark; one effect, one timer, and the multiplier is read from the effect. */
    public static final DeferredHolder<MobEffect, MobEffect> DEATH_MARKED =
            EFFECTS.register("death_marked",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0xFFC94A) {
                    });

    private static final int MARK_TICKS = 100;

    /**
     * The drops midas on a tool turns to gold. <b>An explicit list and not a tag or a predicate
     * over hardness</b>: the question is "did this block come out of the ground with a metal in
     * it", and vanilla's own ore set answers it by naming nine items, where a hardness rule would
     * turn a netherrack into gold.
     */
    private static final java.util.Set<net.minecraft.world.item.Item> ORES =
            java.util.Set.of(net.minecraft.world.item.Items.COAL,
                    net.minecraft.world.item.Items.RAW_IRON,
                    net.minecraft.world.item.Items.RAW_GOLD,
                    net.minecraft.world.item.Items.RAW_COPPER,
                    net.minecraft.world.item.Items.DIAMOND,
                    net.minecraft.world.item.Items.EMERALD,
                    net.minecraft.world.item.Items.LAPIS_LAZULI,
                    net.minecraft.world.item.Items.REDSTONE,
                    net.minecraft.world.item.Items.QUARTZ);

    private GoldEnchantmentHandler() {
    }

    // ------------------------------------------------------------------ helper

    /**
     * The level of one of ours on the stack, resolved <b>por clave y no por holder</b>.
     *
     * <h2>Por que por clave</h2>
     *
     * <p>{@code ModEnchantments} declara sus once como {@code DeferredHolder}, y esos holders
     * <b>nunca se atan</b>: los encantamientos son datos, y una {@code DeferredRegister} sobre un
     * registro de datapack no registra nada. El problema es que un {@code DeferredHolder} sin atar
     * <b>no es la misma instancia</b> que el {@code Holder.Reference} que el item guarda, y
     * {@code ItemEnchantments} compara por <b>identidad</b> -- {@code Reference2IntMap#getInt},
     * verificado en las fuentes.</p>
     *
     * <p>Asi que {@code getEnchantmentLevel(holder)} devolvia <b>cero siempre</b>: el encantamiento
     * se ponia en la mesa, se leia en el tooltip, se gastaba el nivel del yunque... y <b>no hacia
     * absolutamente nada</b>. Sin un error y sin una linea de log: la peor forma en que un
     * encantamiento puede estar roto, porque se ve como si funcionara.</p>
     *
     * <p>La clave del holder si existe desde que se construye, asi que se resuelve contra el
     * componente del propio item y no hace falta acceso al registro. Es la misma idea que
     * {@code CombatEnchantments.level} hace contra el registro: <b>por clave, nunca por
     * instancia.</b></p>
     */
    private static int level(ItemStack stack, DeferredHolder<Enchantment, Enchantment> enchantment) {
        net.minecraft.resources.ResourceKey<Enchantment> key = enchantment.unwrapKey().orElse(null);
        if (key == null) {
            return 0;
        }
        for (net.minecraft.core.Holder<Enchantment> held : stack.getEnchantments().keySet()) {
            if (held.unwrapKey().filter(key::equals).isPresent()) {
                return stack.getEnchantments().getLevel(held);
            }
        }
        return 0;
    }

    /** A fresh roll source per table, so two kills in the same tick do not correlate. */
    private static Random roll(ServerLevel level) {
        return new Random(level.random.nextLong());
    }

    // ------------------------------------------------------------------ midas, weapon

    /**
     * A kill, and the drop table comes out of it.
     *
     * <p><b>Only a player's weapon, and only a living victim.</b> The design's gold is for the
     * fights a player takes: a skeleton that died in a fall is not a kill the player made, and the
     * logs say the same thing about a drowned that drowned.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)
                || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity killer = victim.getKillCredit() instanceof LivingEntity living ? living : null;
        if (!(killer instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        int enchantLevel = level(weapon, ModEnchantments.MIDAS_TOUCH);
        if (enchantLevel <= 0) {
            return;
        }
        Random random = roll(level);
        add(event.getDrops(), victim, enchantLevel, random);
    }

    /** The items themselves, so the numbers stay in {@link GoldEnchantRules}. */
    private static void add(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops, LivingEntity victim,
                            int level, Random random) {
        int nuggets = GoldEnchantRules.midasNuggets(level, random);
        if (nuggets > 0) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, nuggets));
        }
        int ingots = GoldEnchantRules.midasIngots(level, random);
        if (ingots > 0) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, ingots));
        }
        if (GoldEnchantRules.midasGoldenApple(level, random)) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE));
        }
        int carrots = GoldEnchantRules.midasGoldenCarrots(level, random);
        if (carrots > 0) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.GOLDEN_CARROT, carrots));
        }
        if (GoldEnchantRules.midasEnchantedApple(level, random)) {
            addStack(drops, victim, new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE));
        }
    }

    /**
     * A new entity per stack, at the victim's feet, exactly where its own drops land.
     *
     * <p><b>Not a mutation of the existing drops.</b> {@code LivingDropsEvent} owns the list, and
     * pushing onto it is the contract; the entity is built by hand because the victim has already
     * dropped its own and this table is a second, separate pile.</p>
     */
    private static void addStack(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops, LivingEntity victim,
                                 ItemStack stack) {
        drops.add(new net.minecraft.world.entity.item.ItemEntity(victim.level(), victim.getX(),
                victim.getY(), victim.getZ(), stack));
    }

    // ------------------------------------------------------------------ the mark

    /** Miner's frenzy, written on every block and read by the attribute pass. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onBreakBlock(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        int frenzy = level(player.getMainHandItem(), ModEnchantments.MINER_FRENZY);
        if (frenzy <= 0) {
            return;
        }
        int now = (int) level.getGameTime();
        int ceiling = GoldEnchantRules.frenzyMaxStacks(frenzy);
        FrenzyState current = player.getData(ModAttachments.FRENZY).asOf(now);
        FrenzyState grown = current.broken(now, ceiling);
        player.setData(ModAttachments.FRENZY, grown);
        // **Una linea cada cinco bloques, y existe porque "no lo noto" no se contesta mirando.**
        //
        // El bono es una multiplicacion sobre el minado: no aparece en el inventario ni en ningun
        // HUD, asi que sin una linea el jugador y yo discutimos sobre una sensacion -- que es
        // literalmente lo que paso. Cada cinco stacks son tres lineas por cadena: suficiente para
        // ver que la cuenta sube y poco para ahogar el log de una sesion de mineria.
        if (grown.stacks() > current.stacks()
                && grown.stacks() % GoldEnchantRules.FRENZY_LOG_EVERY == 0) {
            com.cortes.combat.CortesCombat.LOGGER.info("[frenzy] stacks={} bonus={}%",
                    grown.stacks(),
                    Math.round(GoldEnchantRules.frenzyBonus(frenzy, grown.stacks()) * 100.0F));
        }
    }

    /**
     * The four that happen to a block's drops: golden hands, midas on a tool, the phantom block
     * and the golden harvest -- all of them answering one question with the same list.
     *
     * <p><b>Cancelled only after the moves are made.</b> The event carries the drop entities
     * themselves, so moving them out and then cancelling is what stops vanilla from spawning a
     * second copy. Cancelling first would leave a list as a corpse nobody reads.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onBlockDrops(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) {
            return;
        }
        ItemStack tool = event.getTool();
        java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = event.getDrops();

        // **The shovel first, and it is the only one that does not care what was broken.**
        // Golden hands is a shovel enchantment because the shovel is the tool that clears ground:
        // a trench over lava, a shaft over a drop, a beach under water. Anywhere the botin would
        // fall into the world, it falls into the inventory instead.
        if (level(tool, ModEnchantments.GOLDEN_HANDS) > 0) {
            for (net.minecraft.world.entity.item.ItemEntity entity : drops) {
                player.getInventory().placeItemBackInInventory(entity.getItem());
            }
            event.setCanceled(true);
            return;
        }

        // **The hoe: the field comes back and the second level pays double.** Replanting reads the
        // state the event holds, and writing age zero is all of it; the double is applied to the
        // list, and the two never read each other's work because they are on the same pass.
        int harvest = level(tool, ModEnchantments.GOLDEN_HARVEST);
        if (harvest > 0
                && event.getState().getBlock() instanceof net.minecraft.world.level.block.CropBlock) {
            if (GoldEnchantRules.harvestReplants(harvest)
                    && event.getState().hasProperty(net.minecraft.world.level.block.CropBlock.AGE)) {
                event.getLevel().setBlock(event.getPos(),
                        event.getState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 0),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
            if (GoldEnchantRules.harvestDoubles(harvest)) {
                doubleUp(drops);
                event.setCanceled(true);
                return;
            }
        }

        // **Phantom block, and the experience is left alone on purpose:** the event sets it and
        // vanilla halves it per level of fortune, so an item that doubled the block AND the levels
        // would turn every shaft into a level ten.
        if (GoldEnchantRules.toolDropsTwice(level(tool, ModEnchantments.PHANTOM_BLOCK),
                roll(event.getLevel()))) {
            doubleUp(drops);
            event.setCanceled(true);
            return;
        }

        // **Midas on a tool: the drop is replaced, not added to.** A coal vein that becomes gold
        // is worth mining; a coal vein that also pays in gold would be a different game -- one
        // that pays in gold for what it came for anyway.
        int midas = level(tool, ModEnchantments.MIDAS_TOOL);
        if (midas <= 0) {
            return;
        }
        Random random = roll(event.getLevel());
        if (!GoldEnchantRules.toolMidasTurned(midas, random)) {
            return;
        }
        int bonus = GoldEnchantRules.toolMidasBonusNuggets(midas);
        for (net.minecraft.world.entity.item.ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (!ORES.contains(stack.getItem())) {
                continue;
            }
            entity.setItem(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT,
                    stack.getCount()));
            if (bonus > 0) {
                drops.add(new net.minecraft.world.entity.item.ItemEntity(event.getLevel(),
                        entity.getX(), entity.getY(), entity.getZ(),
                        new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, bonus)));
            }
            return;
        }
    }

    /** Copies every drop once, which is the whole of a doubled block. */
    private static void doubleUp(java.util.List<net.minecraft.world.entity.item.ItemEntity> drops) {
        int size = drops.size();
        for (int i = 0; i < size; i++) {
            net.minecraft.world.entity.item.ItemEntity original = drops.get(i);
            drops.add(new net.minecraft.world.entity.item.ItemEntity(original.level(),
                    original.getX(), original.getY(), original.getZ(), original.getItem().copy()));
        }
    }
    @net.neoforged.bus.api.SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // **The mark multiplies for everyone, and that is the point of a mark.** One player with
        // echo of gold makes the whole party hit harder, which is the only reading of "marcado de
        // muerte" that is not a solo buff wearing a name.
        if (victim.hasEffect(DEATH_MARKED)) {
            event.setAmount(event.getAmount() * GoldEnchantRules.DEATH_MARK_MULTIPLIER);
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        int steal = level(weapon, ModEnchantments.LIFE_STEAL);
        if (steal <= 0) {
            return;
        }
        float heal = GoldEnchantRules.lifeStealHeal(event.getAmount(), steal);
        if (heal > 0.0F) {
            player.heal(heal);
        }
        int echo = level(weapon, ModEnchantments.GOLD_ECHO);
        if (echo > 0) {
            victim.addEffect(new MobEffectInstance(DEATH_MARKED, MARK_TICKS, echo - 1,
                    false, true, true));
        }
    }

    // ------------------------------------------------------------------ the weapon extras

    /**
     * Wind and feather weight, both attributes and therefore both recomputed, not stacked.
     *
     * <p><b>On the tick rather than on the change event.</b> An enchantment can arrive without the
     * item ever changing -- a grindstone, a command, another mod writing the stack -- and a
     * modifier that is applied once and forgotten becomes a permanent buff. Recomputing from the
     * equipment every tick is one pass over six slots, which is what this project already pays for
     * the guard.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel)) {
            return;
        }
        apply(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                CortesCombat.MODID + ":wind_walker", AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                GoldEnchantRules.windMovementBonus(level(player.getMainHandItem(),
                        ModEnchantments.WIND_WALKER)));
        // **The frenzy, and it is the same pass as the other two.** The stack is read from the
        // attachment, resolved against the current game time so a window that closed pays nothing,
        // and written only by the break event -- this reads, the block one writes.
        FrenzyState frenzy = player.getData(ModAttachments.FRENZY)
                .asOf((int) player.level().getGameTime());
        int frenzyLevel = level(player.getMainHandItem(), ModEnchantments.MINER_FRENZY);
        apply(player, net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED,
                CortesCombat.MODID + ":miner_frenzy", AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                GoldEnchantRules.frenzyBonus(frenzyLevel, frenzy.stacks()));
        // **An offset and not a ratio on this one, because vanilla's own is an offset.**
        // `SwordItem.createAttributes` puts `attackSpeed - 4.0` on the same attribute with ADD_VALUE,
        // so a speed bonus has to be added in the same unit or it stops meaning "ten percent faster".
        apply(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                CortesCombat.MODID + ":feather_weight", AttributeModifier.Operation.ADD_VALUE,
                GoldEnchantRules.featherAttackSpeedOffset(level(player.getMainHandItem(),
                        ModEnchantments.FEATHER_WEIGHT)));
    }

    /** Idempotent by construction: the modifier is replaced, never added a second time. */
    private static void apply(Player player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                             String name, AttributeModifier.Operation operation, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(net.minecraft.resources.ResourceLocation.parse(name));
        if (amount == 0.0) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(
                net.minecraft.resources.ResourceLocation.parse(name), amount, operation));
    }
}
