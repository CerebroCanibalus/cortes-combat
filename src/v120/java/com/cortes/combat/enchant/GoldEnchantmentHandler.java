package com.cortes.combat.enchant;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.CortesCombat;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;

/**
 * The 1.20.1 wiring of gold's nine. Same drops, same mark, same tick pass, older APIs.
 *
 * <ul>
 *   <li>The mark's damage multiply lands on {@code LivingHurtEvent} (pre-armour, with
 *       {@code setAmount}) -- the spike's assignment for damage mods, and the same number
 *       the incoming event carried: life steal still heals from the pre-armour amount.</li>
 *   <li>Enchantment levels read straight off the stack
 *       ({@code EnchantmentHelper.getItemEnchantmentLevel}): 1.20.1 enchantments are real
 *       registry objects, so the shared twin's holder-identity problem does not exist
 *       here and the helper stays two lines.</li>
 *   <li>Attribute modifiers key off name-derived UUIDs: 1.20.1 removes by UUID, and the
 *       operations are called {@code ADDITION}/{@code MULTIPLY_TOTAL}.</li>
 *   <li>The tick gates on {@code END} and reads {@code event.player}.</li>
 * </ul>
 * (⌒‿⌒)
 */
public final class GoldEnchantmentHandler {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, CortesCombat.MODID);

    /** The mark; one effect, one timer, and the multiplier is read from the effect. */
    public static final RegistryObject<MobEffect> DEATH_MARKED =
            EFFECTS.register("death_marked",
                    () -> new MobEffect(MobEffectCategory.HARMFUL, 0xFFC94A) {
                    });

    private static final int MARK_TICKS = 100;

    /**
     * The drops midas on a tool turns to gold. <b>An explicit list and not a tag or a predicate
     * over hardness</b>: vanilla's own ore set answers it by naming nine items, where a hardness
     * rule would turn a netherrack into gold.
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

    /** The level of one of ours on the stack. Real registry objects: identity just works. */
    private static int level(ItemStack stack, RegistryObject<Enchantment> enchantment) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment.get(), stack);
    }

    /** A fresh roll source per table, so two kills in the same tick do not correlate. */
    private static Random roll(ServerLevel level) {
        return new Random(level.random.nextLong());
    }

    /**
     * A kill, and the drop table comes out of it.
     *
     * <p><b>Only a player's weapon, and only a living victim.</b> A skeleton that died in a
     * fall is not a kill the player made.</p>
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity living = victim.getKillCredit();
        if (!(living instanceof Player player)) {
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
     * <p><b>Not a mutation of the existing drops.</b> The event owns the list, and pushing
     * onto it is the contract.</p>
     */
    private static void addStack(java.util.Collection<net.minecraft.world.entity.item.ItemEntity> drops, LivingEntity victim,
                                 ItemStack stack) {
        drops.add(new net.minecraft.world.entity.item.ItemEntity(victim.level(), victim.getX(),
                victim.getY(), victim.getZ(), stack));
    }

    /** Miner's frenzy, written on every block and read by the attribute pass. */
    @SubscribeEvent
    public static void onBreakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        int frenzy = level(player.getMainHandItem(), ModEnchantments.MINER_FRENZY);
        if (frenzy <= 0) {
            return;
        }
        int now = (int) level.getGameTime();
        int ceiling = GoldEnchantRules.frenzyMaxStacks(frenzy);
        FrenzyState current = CombatState.frenzy(player).asOf(now);
        FrenzyState grown = current.broken(now, ceiling);
        CombatState.setFrenzy(player, grown);
        // **Una linea cada cinco bloques, y existe porque "no lo noto" no se contesta mirando.**
        if (grown.stacks() > current.stacks()
                && grown.stacks() % GoldEnchantRules.FRENZY_LOG_EVERY == 0) {
            com.cortes.combat.CortesCombat.LOGGER.info("[frenzy] stacks={} bonus={}%",
                    grown.stacks(),
                    Math.round(GoldEnchantRules.frenzyBonus(frenzy, grown.stacks()) * 100.0F));
        }
    }

    /**
     * The four that happen to a block's drops: golden hands, midas on a tool, the phantom block
     * and the golden harvest.
     *
     * <p>1.20.1 has no drop-list event (neither {@code BlockDropsEvent} nor the older
     * {@code HarvestDropsEvent} exist in 47.4.28 -- verified against the mapped jar), so this
     * hooks {@code BreakEvent} and replicates the three vanilla calls it preempts
     * ({@code removeBlock}, the drop roll, {@code popExperience}), with the xp amount read
     * through the same {@code getExpDrop} hook vanilla reads. Cancelling first would leave a
     * list as a corpse nobody reads; here there is no list yet, only the decision to make
     * vanilla's or ours.</p>
     *
     * <p><b>Cancelled only when one of the four fires.</b> An unenchanted tool returns before
     * touching anything and vanilla proceeds exactly as without the mod.</p>
     */
    @SubscribeEvent
    public static void onBlockDrops(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        net.minecraft.core.BlockPos pos = event.getPos();
        net.minecraft.world.level.block.state.BlockState state = event.getState();
        ItemStack tool = player.getMainHandItem();

        int hands = level(tool, ModEnchantments.GOLDEN_HANDS);
        int harvest = level(tool, ModEnchantments.GOLDEN_HARVEST);
        int phantom = level(tool, ModEnchantments.PHANTOM_BLOCK);
        int midas = level(tool, ModEnchantments.MIDAS_TOOL);
        if (hands <= 0 && harvest <= 0 && phantom <= 0 && midas <= 0) {
            return;
        }

        net.minecraft.world.level.block.entity.BlockEntity blockEntity = level.getBlockEntity(pos);
        net.minecraft.world.level.block.Block block = state.getBlock();
        java.util.List<ItemStack> drops =
                net.minecraft.world.level.block.Block.getDrops(state, level, pos, blockEntity, player, tool);

        // **The shovel first, and it is the only one that does not care what was broken.**
        // Golden hands is a shovel enchantment because the shovel is the tool that clears ground:
        // a trench over lava, a shaft over a drop, a beach under water. Anywhere the botin would
        // fall into the world, it falls into the inventory instead.
        if (hands > 0) {
            for (ItemStack stack : drops) {
                player.getInventory().placeItemBackInInventory(stack);
            }
            replaceBreak(level, block, state, pos, tool);
            event.setCanceled(true);
            return;
        }

        // **The hoe: the field comes back and the second level pays double.** Replanting reads
        // the state the event holds, and writing age zero is all of it.
        if (harvest > 0
                && state.getBlock() instanceof net.minecraft.world.level.block.CropBlock) {
            if (GoldEnchantRules.harvestReplants(harvest)
                    && state.hasProperty(net.minecraft.world.level.block.CropBlock.AGE)) {
                event.setCanceled(true);
                level.removeBlock(pos, false);
                level.setBlock(pos,
                        state.setValue(net.minecraft.world.level.block.CropBlock.AGE, 0),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
                if (GoldEnchantRules.harvestDoubles(harvest)) {
                    drops = doubled(drops);
                }
                drops.forEach(stack ->
                        net.minecraft.world.level.block.Block.popResource(level, pos, stack));
                popVanillaXp(level, block, state, pos, tool);
                return;
            }
            if (GoldEnchantRules.harvestDoubles(harvest)) {
                event.setCanceled(true);
                level.removeBlock(pos, false);
                doubled(drops).forEach(stack ->
                        net.minecraft.world.level.block.Block.popResource(level, pos, stack));
                popVanillaXp(level, block, state, pos, tool);
                return;
            }
        }

        // **Phantom block, and the experience is left alone on purpose:** vanilla halves it per
        // level of fortune, so an item that doubled the block AND the levels would turn every
        // shaft into a level ten.
        if (GoldEnchantRules.toolDropsTwice(phantom, roll(level))) {
            event.setCanceled(true);
            level.removeBlock(pos, false);
            doubled(drops).forEach(stack ->
                    net.minecraft.world.level.block.Block.popResource(level, pos, stack));
            popVanillaXp(level, block, state, pos, tool);
            return;
        }

        // **Midas on a tool: the drop is replaced, not added to.** A coal vein that becomes gold
        // is worth mining; a coal vein that also pays in gold would be a different game -- one
        // that pays in gold for what it came for anyway.
        if (midas <= 0) {
            return;
        }
        Random random = roll(level);
        if (!GoldEnchantRules.toolMidasTurned(midas, random)) {
            return;
        }
        int bonus = GoldEnchantRules.toolMidasBonusNuggets(midas);
        boolean turned = false;
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack stack : drops) {
            if (!ORES.contains(stack.getItem())) {
                out.add(stack);
                continue;
            }
            turned = true;
            out.add(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, stack.getCount()));
            if (bonus > 0) {
                out.add(new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET, bonus));
            }
        }
        if (!turned) {
            return;
        }
        event.setCanceled(true);
        level.removeBlock(pos, false);
        out.forEach(stack ->
                net.minecraft.world.level.block.Block.popResource(level, pos, stack));
        popVanillaXp(level, block, state, pos, tool);
    }

    /**
     * The block gone with no drops, which is what every custom path starts from: vanilla's own
     * {@code removeBlock} plus vanilla's own xp, and nothing else.
     */
    private static void replaceBreak(ServerLevel level, net.minecraft.world.level.block.Block block,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.core.BlockPos pos, ItemStack tool) {
        level.removeBlock(pos, false);
        popVanillaXp(level, block, state, pos, tool);
    }

    /** Vanilla's xp for this break, or nothing: the destroy path only pops when it is positive. */
    private static void popVanillaXp(ServerLevel level, net.minecraft.world.level.block.Block block,
            net.minecraft.world.level.block.state.BlockState state,
            net.minecraft.core.BlockPos pos, ItemStack tool) {
        int fortune = net.minecraft.world.item.enchantment.EnchantmentHelper
                .getItemEnchantmentLevel(
                        net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, tool);
        int silk = net.minecraft.world.item.enchantment.EnchantmentHelper.hasSilkTouch(tool) ? 1 : 0;
        int xp = state.getExpDrop(level, level.random, pos, fortune, silk);
        if (xp > 0) {
            block.popExperience(level, pos, xp);
        }
    }

    /** Copies every drop once, which is the whole of a doubled block. */
    private static java.util.List<ItemStack> doubled(java.util.List<ItemStack> drops) {
        java.util.List<ItemStack> out =
                new java.util.ArrayList<>(drops.size() * 2);
        for (ItemStack stack : drops) {
            out.add(stack);
            out.add(stack.copy());
        }
        return out;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        // **The mark multiplies for everyone, and that is the point of a mark.**
        if (victim.hasEffect(DEATH_MARKED.get())) {
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
            victim.addEffect(new MobEffectInstance(DEATH_MARKED.get(), MARK_TICKS, echo - 1,
                    false, true, true));
        }
    }

    /**
     * Wind and feather weight, both attributes and therefore both recomputed, not stacked.
     *
     * <p><b>On the tick rather than on the change event.</b> Recomputing from the equipment
     * every tick is one pass over six slots; a modifier applied once and forgotten becomes a
     * permanent buff the day an enchantment arrives without the item changing.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (!(player.level() instanceof ServerLevel)) {
            return;
        }
        apply(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                CortesCombat.MODID + ":wind_walker", AttributeModifier.Operation.MULTIPLY_TOTAL,
                GoldEnchantRules.windMovementBonus(level(player.getMainHandItem(),
                        ModEnchantments.WIND_WALKER)));
        // **An offset and not a ratio on this one, because vanilla's own is an offset.**
        apply(player, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                CortesCombat.MODID + ":feather_weight", AttributeModifier.Operation.ADDITION,
                GoldEnchantRules.featherAttackSpeedOffset(level(player.getMainHandItem(),
                        ModEnchantments.FEATHER_WEIGHT)));
    }

    /**
     * Miner's frenzy, applied where 1.20.1 computes dig speed.
     *
     * <p>1.20.1 has no block-break-speed attribute (it arrived in 1.21), so the frenzy does
     * not live on the tick pass here: {@code BreakSpeed} carries the computed speed and a
     * setter, and the same stacks read from the same attachment multiply it in place.
     * The write side (the break event above) is untouched.</p>
     */
    @SubscribeEvent
    public static void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        // PlayerEvent narrows getEntity() to Player: no hay nada que preguntar.
        Player player = event.getEntity();
        int frenzyLevel = level(player.getMainHandItem(), ModEnchantments.MINER_FRENZY);
        if (frenzyLevel <= 0) {
            return;
        }
        FrenzyState frenzy = CombatState.frenzy(player)
                .asOf((int) player.level().getGameTime());
        double bonus = GoldEnchantRules.frenzyBonus(frenzyLevel, frenzy.stacks());
        if (bonus == 0.0) {
            return;
        }
        event.setNewSpeed((float) (event.getNewSpeed() * (1.0 + bonus)));
    }

    /** Idempotent by construction: the modifier is replaced, never added a second time. */
    private static void apply(Player player,
                              net.minecraft.world.entity.ai.attributes.Attribute attribute,
                              String name, AttributeModifier.Operation operation, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        instance.removeModifier(id);
        if (amount == 0.0) {
            return;
        }
        instance.addPermanentModifier(new AttributeModifier(id, name, amount, operation));
    }
}
