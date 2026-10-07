package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.attack.AttackTiming;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.tooltip.TooltipColor;
import com.cortes.combat.item.tooltip.TooltipLine;
import com.cortes.combat.item.tooltip.WeaponTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Replaces every weapon's tooltip with the numbers this mod actually runs on.
 *
 * <h2>Two events, and the order they matter in</h2>
 *
 * <p>{@code GatherSkippedAttributeTooltipsEvent} removes vanilla's own two lines -- attack damage
 * and attack speed -- <em>before</em> they are written, and {@code ItemTooltipEvent} appends ours
 * after everything else. Two events rather than one because the first has to happen upstream:
 * by the time the tooltip list is a list, those lines are already rendered strings and the only
 * way to drop them would be to recognise them by their text, which is a guess that breaks the day
 * somebody renames a language file.</p>
 *
 * <p><b>Skipping by modifier id, not by slot group and not by all.</b> Every weapon declares
 * exactly two modifiers with the ids {@code minecraft:base_attack_damage} and
 * {@code minecraft:base_attack_speed}, and those are the two this tooltip replaces. Skipping the
 * whole main-hand group would also take the enchantments' lines with it -- a Sharpness sword
 * would stop saying what Sharpness does -- and skipping nothing leaves the player looking at
 * "+7 attack damage" directly above a "Daño: 7" that means something else.</p>
 *
 * <h2>Why this is not an {@code appendHoverText}</h2>
 *
 * <p>Because it has to reach vanilla's weapons. {@code appendHoverText} is a method on
 * {@code Item}, so it only ever runs for items whose class is ours, and a diamond sword would
 * keep vanilla's tooltip next to nothing. A sword in this game reaches three point six blocks
 * through a thirty five degree cone and charges for half again as much damage, and the player
 * hovering that sword is owed those facts.</p>
 *
 * <h2>Why the damage is the item's own and not the player's</h2>
 *
 * <p>A tooltip has no player to be honest about. Reading {@code ATTACK_DAMAGE} off the entity
 * would print whatever the reader happens to be wearing and drinking, which turns a fact about
 * the weapon into a fact about the moment. So this reads the player's own base of 1.0 and the
 * item's own modifier, and nothing else -- no armour, no potions, no effects. The same
 * arithmetic {@code ArcabuzStats} already does for the arcabuz.</p>
 */
public final class CombatTooltipHandler {

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
     * <p><b>Not a block.</b> Every item resolves to some profile -- a dirt block resolves to
     * {@link WeaponProfile#NONE} -- and printing "3 damage, small cone" under every stack in the
     * inventory is how a tooltip becomes wallpaper the player stops reading. {@code NONE} is the
     * profile that exists precisely because "we do not recognise it" must not mean "treat it as a
     * sword", and a tooltip is where that mistake becomes visible on every single item.</p>
     *
     * <p><b>It asks {@link com.cortes.combat.item.WeaponKinds#fights}, not "is it NONE".</b> Those
     * were the same question until the rework decided they are not: a pickaxe has a profile, so it
     * passed, and it printed a reach and a cone for a profile that no longer swings. The tooltip is
     * where this shows -- the player reads a number off an item and believes it.</p>
     *
     * <p><b>Which puts vanilla's two numbers back on the pickaxe</b>, and that is the point rather
     * than a side effect. {@link #onGatherSkippedAttributeTooltips} hides them only for the items
     * whose numbers we replace, so a pickaxe goes back to saying what a pickaxe says. A tool that
     * does not fight should look like the tool it is.</p>
     */
    private static boolean showsTooltip(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return com.cortes.combat.item.WeaponKinds.fights(WeaponStats.of(stack).profile());
    }

    /** Hide vanilla's two numbers for exactly the items whose numbers we are replacing. */
    @SubscribeEvent
    public static void onGatherSkippedAttributeTooltips(GatherSkippedAttributeTooltipsEvent event) {
        if (!showsTooltip(event.getStack())) {
            return;
        }
        event.skipId(Item.BASE_ATTACK_DAMAGE_ID);
        event.skipId(Item.BASE_ATTACK_SPEED_ID);
    }

    /** And print ours in their place. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!showsTooltip(stack)) {
            return;
        }

        WeaponStats stats = WeaponStats.of(stack);
        List<TooltipLine> lines = WeaponTooltip.linesFor(
                stats.profile(),
                (float) (BASE_ATTACK_DAMAGE + baseModifier(stack, Item.BASE_ATTACK_DAMAGE_ID)),
                intervalTicks(stack, stats.profile()),
                stats.reach(),
                stats.charge(),
                stats.special(),
                stats.knockback(),
                com.cortes.combat.item.WeaponTables.maxTargets(stats.profile()),
                stack.getMaxDamage() - stack.getDamageValue(),
                stack.getMaxDamage(),
                stats.hasOwnShotCone());

        List<Component> tooltip = event.getToolTip();
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
     * The {@code ChatFormatting} for a colour, in one place.
     *
     * <p><b>The mapping lives here and the decision lives in {@code TooltipColor}.</b> This is the
     * only line in the whole tooltip that has to name a Minecraft type to say what a colour is, and
     * keeping it to one switch is what lets the entire layer above be checked in a bare JVM --
     * including "is the category blue", which is a question a test can ask of the enum and cannot
     * ask of this.</p>
     *
     * <p>{@code BLUE} for the category is vanilla's own choice, copied: a player has read "Food" in
     * blue since they started playing, so the line reads as the same kind of fact without a legend.
     * {@code RED} for a nearly-broken weapon is the same colour vanilla uses for a curse, which is
     * the right alarm for it.</p>
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
     *
     * <p>Built exactly the way {@code WeaponStats#attackInterval} builds it -- the item's own
     * modifier on the player's base, then the profile's tempo, then the same rounding -- because
     * a tooltip that used its own arithmetic would be a second copy of the swing clock. That is
     * the whole reason the tempo table is reachable from outside now.</p>
     */
    private static int intervalTicks(ItemStack stack, WeaponProfile profile) {
        double attackSpeed = BASE_ATTACK_SPEED + baseModifier(stack, Item.BASE_ATTACK_SPEED_ID);
        return AttackTiming.scaleFor(attackSpeed, WeaponStats.tempoFor(profile));
    }

    /**
     * What this item alone adds to an attribute, in the main hand.
     *
     * <p><b>Through {@code forEachModifier}, and the maza is why this changed.</b> It used to read
     * the {@code ATTRIBUTE_MODIFIERS} component directly, which was correct for every weapon whose
     * numbers are baked at registration -- and wrong for the one whose numbers are not. The maza's
     * damage and speed are swapped by {@code MaceRebalance} through
     * {@code ItemAttributeModifierEvent}, which fires when the attributes are <em>asked for</em>;
     * the component still holds vanilla's five, so the tooltip said "6 de daño" on a weapon that
     * hits for fifteen. The playtest caught it by reading the line and knowing the number was
     * wrong, which is the worst way for a tooltip to fail.</p>
     *
     * <p><b>Matching the two {@code base_} ids still excludes the enchantments.</b>
     * {@code forEachModifier} walks them too -- that was the original reason for reading the
     * component instead -- but a Sharpness modifier has its own id, so filtering by id keeps the
     * exclusion and gains the event's modifiers. The two numbers can no longer disagree with the
     * swing, because they are now read through the same door the swing's {@code getAttributeValue}
     * goes through.</p>
     *
     * <p>The array exists because the callback is a lambda and a lambda cannot assign to a local.
     * There is exactly one element and it is read on the line after.</p>
     */
    private static double baseModifier(ItemStack stack, ResourceLocation id) {
        double[] total = {0.0};
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (id.equals(modifier.id())
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    /**
     * Whether the tooltip already ends in one or more blank lines.
     *
     * <p>Scanning back over the blanks rather than checking only the last element, because that is
     * the case that matters: a weapon with an enchantment modifier keeps vanilla's "When in Main
     * Hand:" header and its blank line, and adding a second one on top is the difference between a
     * tooltip that reads as two blocks and one that reads as a bug.</p>
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