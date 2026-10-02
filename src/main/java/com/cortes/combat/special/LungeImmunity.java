package com.cortes.combat.special;

import com.cortes.combat.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The lunge's immunity, enforced by the status effect it leaves behind.
 *
 * <h2>Why there are two mechanisms</h2>
 *
 * <p>{@code invulnerableTime} is re-armed every tick of a lunge and {@code LivingEntity#hurt} refuses
 * anything while it is positive, which on its own is a complete dodge. It was also reported as
 * unreliable -- "sometimes it fails, especially when I am not going so fast" -- and slow is exactly
 * when the lunge runs its full twenty tick window and is therefore exposed to everything that touches
 * the player's damage state during it.</p>
 *
 * <p>So the effect is a second, independent path to the same rule: while it is on the entity, the
 * damage is cancelled at the event. The field and the effect can each be zeroed by whatever knocked
 * it over, and the other one still holds. <b>Two mechanisms with one intent is not tidiness, and it
 * is not being superstitious either</b> -- a dodge that is sometimes a dodge is a button that teaches
 * the player the rules only apply when the game feels like it.</p>
 *
 * <p>What the effect buys on top of that is legibility: {@code invulnerableTime} has no icon and no
 * way to be seen, and "I do not notice it" was the playtest's own words about a mechanic that
 * cannot announce itself.</p>
 *
 * <h2>Why the event and not a damage modifier</h2>
 *
 * <p>{@code LivingIncomingDamageEvent} fires at line 1165 of {@code LivingEntity#hurt} and the
 * {@code invulnerableTime > 10} check is at 1190, so the event runs <b>first</b>. Cancelling there
 * returns before the game's own bookkeeping, which is what the parry wanted too: a hit that was
 * refused leaves no trace and no cooldown.</p>
 *
 * <p>Registered on the game bus, and the class is registered explicitly in the mod's constructor
 * because a class with no {@code @SubscribeEvent} method on that bus aborts the mod's construction.</p>
 */
public final class LungeImmunity {

    private LungeImmunity() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.hasEffect(ModEffects.LUNGE)) {
            return;
        }
        event.setCanceled(true);
    }
}