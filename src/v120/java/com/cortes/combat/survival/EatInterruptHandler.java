package com.cortes.combat.survival;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.guard.GuardHandler;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/**
 * Taking a hit while uncovered costs you the bite you were halfway through.
 *
 * <h2>The contradiction this resolves, and where it came from</h2>
 *
 * <p>The project's own documents disagreed about this one, and the disagreement was worth settling
 * before writing anything:</p>
 *
 * <ul>
 *   <li>{@code DESIGN.md} section 5, and two blocks of {@code AGENTS.md}: <b>"comer se interrumpe
 *       al recibir daño"</b> &mdash; a decided mechanic.</li>
 *   <li>{@code docs/api-trampas.md}, and two other blocks of {@code AGENTS.md}: <b>"el daño no
 *       interrumpe comer"</b> &mdash; also true, because it is what <em>vanilla</em> does.</li>
 * </ul>
 *
 * <p>Both are right and they are about different things. <b>Verified against the 1.21.1 sources:
 * vanilla does not interrupt eating.</b> {@code Player#hurt} is a hundred and six bytes that check
 * invulnerability, scale, and delegate to {@code super}; {@code LivingEntity#hurt} and
 * {@code actuallyHurt} never call {@code stopUsingItem}. Nothing in the game stops a meal.</p>
 *
 * <p>So the DESIGN row is a mechanic we have to <em>add</em>, and the api-trampas note is a warning
 * about which half of the design to preserve. The user settled it: <b>the hit interrupts only when
 * you are not covered.</b> That satisfies the design row and the separate, older rule that covering
 * and eating at the same time is legal &mdash; a rule whose whole point would be destroyed by an
 * interrupt that fired while the guard was up.</p>
 *
 * <h2>Why {@code LivingDamageEvent.Post} and not the incoming one</h2>
 *
 * <p>Because the question is "did this player actually lose health", and the two events answer
 * different questions. {@code LivingIncomingDamageEvent} fires before the invulnerability window,
 * so a hit that vanilla then throws away because the player was still inside their i-frames would
 * have cost a bite for damage that never happened. {@code Post} fires from
 * {@code LivingEntity#actuallyHurt} once every reduction has been applied &mdash; which is also
 * why a parry that cancels the damage never reaches here.</p>
 *
 * <h2>Server side only, and that is load-bearing</h2>
 *
 * <p>{@link GuardHandler#isGuarding} reads {@code ModAttachments.GUARD_STAMINA}, which is
 * serialized but <b>not</b> synced: the client learns the guard from
 * {@code PlayerCombatState}, not from the attachment. Asking this question on the client would
 * answer "not guarding" for a player who is covering, and the two sides would disagree about the
 * same tick &mdash; the exact failure {@code GuardHandler} already documents for its own right-click
 * hook. So the server decides and the client obeys through the synced
 * {@code DATA_LIVING_ENTITY_FLAGS}, which clears {@code useItem} when it arrives.</p>
 *
 * <h2>What the player actually sees, and the one thing to watch in playtest</h2>
 *
 * <p>The bite restarts rather than staying stopped. {@code stopUsingItem} clears the meal on the
 * server, but vanilla re-arms a held right click every four ticks, so a player holding the button
 * begins chewing again almost immediately &mdash; from zero. That is the intended reading of
 * "interrumpe": <b>each hit costs you everything you had chewed</b>, so eating through a fight
 * never finishes and eating after one does. Whether losing the whole bite on a single stray hit
 * feels right is a playtest question, not a design one, and it is one line here (plus a short
 * refusal window, if it comes to that) to change.</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class EatInterruptHandler {

    private EatInterruptHandler() {
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        // Cheap question first: almost every hit lands on something that is not eating.
        if (!GuardHandler.isEating(player)) {
            return;
        }
        // And this one is the whole design. Covered, the meal continues -- "cubrir y comer es
        // legal", and an interrupt that fired here would make the guard the thing that stops you
        // eating, which is the opposite of what the rule says.
        if (GuardHandler.isGuarding(player)) {
            return;
        }
        player.stopUsingItem();
    }
}
