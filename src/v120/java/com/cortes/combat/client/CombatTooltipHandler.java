package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.tooltip.TooltipColor;
import com.cortes.combat.item.tooltip.TooltipLine;
import com.cortes.combat.item.tooltip.WeaponTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * The 1.20.1 twin of the tooltip replacement. Same seven lines, older removal.
 *
 * <p>1.20.1 has no skip event: by the time the tooltip is a list, vanilla's two lines are
 * already rendered strings. They are removed by matching their shape instead of their
 * text -- a {@code attribute.modifier.*} key whose arguments name the attack damage or
 * attack speed attribute -- which is the closest this version gets to "skip by modifier
 * id" and, like that, does not break the day somebody renames a language file. Only
 * fighters lose the two lines; tools keep vanilla's, exactly like the shared twin. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class CombatTooltipHandler {

    /**
     * Vanilla's damage modifier id, read out of `Item.<clinit>` bytecode: the field is
     * protected on 1.20.1.
     */
    private static final UUID BASE_ATTACK_DAMAGE_UUID =
            UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");

    /**
     * Vanilla's speed modifier id, same source as above.
     */
    private static final UUID BASE_ATTACK_SPEED_UUID =
            UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");

    /**
     * The player's base {@code ATTACK_DAMAGE}, before any item.
     *
     * <p>A named constant rather than a literal in the sum, because "why is there a one here" is
     * the question a reader asks, and the answer -- it is vanilla's fist damage -- is worth ten
     * words.</p>
     */
    private static final double BASE_ATTACK_DAMAGE = 1.0;

    /** The player's base {@code ATTACK_SPEED}: five ticks between swings, no weapon. */
    private static final double BASE_ATTACK_SPEED = 4.0;

    private CombatTooltipHandler() {
    }

    /**
     * Whether this stack gets our block at all.
     *
     * <p><b>Not a block.</b> Every item resolves to some profile, and printing weapon lines under
     * every stack would be wallpaper. {@code NONE} is the profile that exists precisely because
     * "we do not recognise it" must not mean "treat it as a sword".</p>
     */
    private static boolean showsTooltip(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        WeaponProfile profile = WeaponStats.of(stack).profile();
        // **A tool counts too now.** It is not a weapon and it must not print weapon lines, but its
        // category and durability are facts a player wants. The split lives in
        // {@code WeaponTooltip#linesFor}.
        return com.cortes.combat.item.WeaponKinds.fights(profile)
                || com.cortes.combat.core.weapon.WeaponCategory.isTool(profile);
    }

    /** And print ours in vanilla's place. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!showsTooltip(stack)) {
            return;
        }

        boolean fights = com.cortes.combat.item.WeaponKinds.fights(
                WeaponStats.of(stack).profile());
        List<Component> tooltip = event.getToolTip();
        if (fights) {
            // Hide vanilla's two numbers for exactly the items whose numbers we replace.
            // A pickaxe has neither a block nor a replacement for vanilla's lines: it keeps
            // them, so it goes on saying what a pickaxe says about itself.
            tooltip.removeIf(CombatTooltipHandler::isVanillaCombatLine);
        }

        WeaponStats stats = WeaponStats.of(stack);
        List<TooltipLine> lines = WeaponTooltip.linesFor(
                stats.profile(),
                (float) (BASE_ATTACK_DAMAGE + baseModifier(stack, Attributes.ATTACK_DAMAGE,
                        BASE_ATTACK_DAMAGE_UUID)),
                intervalTicks(stack, stats.profile()),
                stats.reach(),
                stats.charge(),
                stats.special(),
                stats.knockback(),
                com.cortes.combat.item.WeaponTables.maxTargets(stats.profile()),
                stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage(),
                stats.hasOwnShotCone(),
                fights,
                enchantmentNumbers(event, stack));

        if (lines.isEmpty()) {
            return;
        }
        if (!endsInBlank(tooltip)) {
            tooltip.add(Component.empty());
        }
        for (TooltipLine line : lines) {
            tooltip.add(Component.translatable(line.key(), line.args().toArray())
                    .withStyle(formatFor(line.color())));
        }
    }

    /**
     * Whether this list line is one of vanilla's two combat numbers.
     *
     * <p>Shape, not text: a translatable {@code attribute.modifier.*} key with an argument
     * naming the attack damage or attack speed attribute. Locale-proof and rename-proof,
     * like the skip-by-id it stands in for.</p>
     */
    private static boolean isVanillaCombatLine(Component line) {
        if (!(line.getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        if (!contents.getKey().startsWith("attribute.modifier.")) {
            return false;
        }
        for (Object arg : contents.getArgs()) {
            if (arg instanceof Component name && isAttackAttributeName(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAttackAttributeName(Component name) {
        if (!(name.getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        String key = contents.getKey();
        return key.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE
                        .getDescriptionId())
                || key.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED
                        .getDescriptionId());
    }

    /**
     * Los tres números que el tooltip imprime y que un encantamiento puede mover.
     */
    private static com.cortes.combat.item.tooltip.EnchantmentNumbers enchantmentNumbers(
            ItemTooltipEvent event, ItemStack stack) {
        // Sin patron instanceof: getEntity() ya es Player (nullable) y el patron contra un
        // supertipo no compila en JDK 17 aunque el 21 lo deje pasar. Null-check directo.
        net.minecraft.world.entity.player.Player user = event.getEntity();
        if (user == null) {
            return com.cortes.combat.item.tooltip.EnchantmentNumbers.base();
        }
        return new com.cortes.combat.item.tooltip.EnchantmentNumbers(
                com.cortes.combat.special.Arcabuz.reloadTicks(stack, user),
                com.cortes.combat.enchant.CombatEnchantRules.parryCooldown(
                        com.cortes.combat.state.ParryState.COOLDOWN_TICKS,
                        com.cortes.combat.enchant.CombatEnchantments.level(
                                user.level().registryAccess(), stack,
                                com.cortes.combat.enchant.CombatEnchantments.STEEL_REFLEX)),
                com.cortes.combat.enchant.CombatEnchantRules.streakWindow(
                        com.cortes.combat.enchant.CombatEnchantments.level(
                                user.level().registryAccess(), stack,
                                com.cortes.combat.enchant.CombatEnchantments.RHYTHM_OF_IRON)));
    }

    /**
     * The {@code ChatFormatting} for a colour, in one place.
     */
    private static ChatFormatting formatFor(TooltipColor color) {
        return switch (color) {
            case DAMAGE -> ChatFormatting.RED;
            case CHARGED -> ChatFormatting.DARK_RED;
            case SPEED -> ChatFormatting.YELLOW;
            case RANGE -> ChatFormatting.AQUA;
            case SIZE -> ChatFormatting.GRAY;
            case SPECIAL -> ChatFormatting.GOLD;
            case DURABILITY -> ChatFormatting.GREEN;
            case DURABILITY_LOW -> ChatFormatting.RED;
            case CATEGORY -> ChatFormatting.BLUE;
            case NOTE -> ChatFormatting.DARK_GRAY;
        };
    }

    /**
     * Ticks between swings for this weapon, tempo included.
     */
    private static int intervalTicks(ItemStack stack, WeaponProfile profile) {
        double attackSpeed = BASE_ATTACK_SPEED
                + baseModifier(stack, Attributes.ATTACK_SPEED, BASE_ATTACK_SPEED_UUID);
        return AttackTiming.scaleFor(attackSpeed, WeaponStats.tempoFor(profile));
    }

    /**
     * What this item alone adds to an attribute, in the main hand.
     *
     * <p>Through the item's own modifier map, matched by UUID: an enchantment's modifier has
     * its own id, so filtering keeps the exclusion and reads the event-swapped numbers for
     * the maza (whose damage the rebalance changes when attributes are asked for).</p>
     */
    private static double baseModifier(ItemStack stack,
            net.minecraft.world.entity.ai.attributes.Attribute attribute, UUID id) {
        double total = 0.0;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(attribute)) {
            if (id.equals(modifier.getId())
                    && modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                total += modifier.getAmount();
            }
        }
        return total;
    }

    /**
     * Whether the tooltip already ends in one or more blank lines.
     */
    private static boolean endsInBlank(List<Component> tooltip) {
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            if (!tooltip.get(i).getString().isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
