package com.cortes.combat.guard;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.GuardState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The guard: what blocks, what it costs, and when it refuses.
 *
 * <p>It hooks {@link LivingShieldBlockEvent} rather than adding a mixin, because that event
 * already is the decision point. NeoForge hands it over with a yes or no that vanilla computed,
 * and {@code getBlocked()} is what the game then obeys.</p>
 *
 * <p><b>The one thing to get right here:</b> the event does not reduce anything. It only
 * reports how much <em>was</em> prevented, and the actual subtraction happens afterwards in
 * {@code DamageContainer#setBlockedDamage}, which does {@code newDamage -= blockedDamage}.
 * Writing zero into it therefore means "nothing was blocked" and lets the whole hit through.
 * The default value is already the full damage, which is what a full block is, so the correct
 * move is to leave it alone.</p>
 *
 * <p><b>The stagger is vanilla's, not ours.</b> Minecraft already has exactly this: an axe
 * striking a raised shield puts the shield on a five second item cooldown, and the vanilla HUD
 * already draws that cooldown. So there is no stun counter in {@link GuardState} and nothing to
 * draw ourselves. Exhaustion adds a cooldown, an axe hit adds a cooldown, and both are read
 * back through {@code isOnCooldown}. {@code ServerItemCooldowns} sends the packet, so this
 * arrives at the client already synced and already rendered.</p>
 */
public final class GuardHandler {

    private GuardHandler() {
    }

    /**
     * Server side of the guard input.
     *
     * <p>A raise is refused while the guard has no points. That is not a special case for the
     * exhausted case: it is the same rule as the one that stops an empty guard from blocking,
     * applied one step earlier. It is also what stops a deadlock, because otherwise a player
     * who held the button down on a fresh shield would be holding an empty guard, would not be
     * regenerating (holding costs the regen) and would never become ready.</p>
     */
    public static void setGuard(ServerPlayer player, boolean raising) {
        GuardState current = player.getData(ModAttachments.GUARD_STAMINA.get());
        // The playtest's order rule: you cover first, you eat after. A raise that arrives while
        // the player is already chewing is refused, which is what makes the order a fact instead
        // of something that happens to fall out of which hand holds what. The guard and the meal
        // can still run at the same time -- they start on the same press and the guard resolves
        // instantly while the food takes 32 ticks -- and this only refuses the second press.
        //
        // One predicate, shared with the item-use refusal below, because "may the guard go up" has
        // to have exactly one answer on the server.
        if (raising && !guardIsUsable(player)) {
            return;
        }
        GuardState next = raising ? current.raise() : current.lower();
        if (next != current) {
            player.setData(ModAttachments.GUARD_STAMINA.get(), next);
        }
        if (raising && next.held() && !current.held()) {
            // Covering abandons a swing that was already open. Same shape as the menu case and
            // the same reason: the player pressed attack, then chose to cover, and the release
            // would land a hit against whatever happened to be in the cone in between. Clearing
            // the charge is enough -- CombatEngine#resolveSwing stops when nothing is open, so
            // this costs no cooldown and the abandoned swing simply never was.
            AttackCooldown.endCharge(player);
        }
    }

    /**
     * Whether the guard is up, which is what stops an attack.
     *
     * <p>The playtest called attacking while covered "un gravísimo error", and the reason it
     * matters is not that it looks wrong. It removes the only cost the guard has. A guard that
     * lets you swing is not a stance, it is a passive buff you hold with a button, and every
     * other decision in the design -- points that are only spent by a blocked hit, a stagger
     * that costs you two seconds -- is priced against a player who has to choose between covering
     * and hitting.</p>
     *
     * <p>It is deliberately the strongest guard in the hands, not a per-item case. A shield in
     * the off hand with a sword in the main one would otherwise block and swing for free, which
     * makes the off hand strictly better than the sword and quietly dissolves the unified guard
     * the design is built on.</p>
     */
    public static boolean isGuarding(Player player) {
        return player.getData(ModAttachments.GUARD_STAMINA.get()).isActive();
    }

    /** The guard the player's items are currently worth, and which item is providing it. */
    public static GuardStats.Resolved bestFor(LivingEntity entity) {
        return GuardStats.best(entity);
    }

    /**
     * Whether the guard is up right now, from the server's own numbers and nothing else.
     *
     * <p>Shared by the two things that have to agree on it: refusing the raise in
     * {@link #setGuard} and refusing the item use below. Two copies of this question is how a
     * client and a server end up with two different opinions about the same tick.</p>
     */
    public static boolean guardIsUsable(Player player) {
        GuardState state = player.getData(ModAttachments.GUARD_STAMINA.get());
        return !state.isExhausted() && !isEating(player);
    }

    /**
     * The server's half of "the shield does not go up without points".
     *
     * <p>Fires on both sides in NeoForge, and only the server branch is here: the client needs
     * the synced stamina rather than the live one, and common code cannot reach it. Both sides
     * run {@link GuardRules#mayStartItemUse}, so the rule is written once and only the number it
     * reads differs.</p>
     *
     * <p>Nothing here is a special case for the empty guard. It is the same refusal as an
     * exhausted one, arriving through vanilla's item use instead of through our own input.</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!GuardRules.mayStartItemUse(GuardStats.of(stack).canGuard(), guardIsUsable(player))) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether the player is halfway through eating or drinking.
     *
     * <p>Ask the item's <em>use animation</em> rather than whether the player is using an item at
     * all. "Using an item" is true for a shield that is blocking, and gating the guard on that
     * would deadlock: a shield would prevent its own guard, and the player could never raise it.
     * The three cases the game itself distinguishes are exactly the three this needs -- {@code
     * BLOCK} is the shield, {@code EAT} and {@code DRINK} are the meal -- so the distinction is
     * read off the game rather than reinvented here.</p>
     *
     * <p>There is no {@code Item#isEdible} in 1.21.1 to ask instead. Verified with {@code javap}
     * against the compiled jar, like everything else in this comment block.</p>
     */
    public static boolean isEating(LivingEntity entity) {
        if (!entity.isUsingItem()) {
            return false;
        }
        UseAnim anim = entity.getUseItem().getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    /** Whether the item being used is a blocking one, which is the only use a guard cancels. */
    public static boolean usedItemBlocks(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().getUseAnimation() == UseAnim.BLOCK;
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity entity = event.getEntity();
        // Ours only, and only the server decides anything.
        if (!(entity instanceof Player player) || player.level().isClientSide) {
            return;
        }

        // Kill the vanilla block first, always. Whatever vanilla thought, this system answers.
        event.setBlocked(false);

        GuardStats.Resolved guard = bestFor(player);
        if (!guard.canGuard()) {
            return;
        }

        // A staggered guard. Vanilla's own axe stagger lands here for the shield, and ours
        // lands here for the sword, through the same check and the same cooldown.
        if (player.getCooldowns().isOnCooldown(guard.item().getItem())) {
            return;
        }

        GuardState state = player.getData(ModAttachments.GUARD_STAMINA.get());
        float blocked = blockedFraction(event.getDamageSource(), player, guard.profile());
        if (!state.isActive() || blocked <= 0.0F) {
            return;
        }

        // **One swing spends one point, and this is where that is decided.**
        //
        // <p>Vanilla resolves the shield at {@code LivingEntity#hurt} line 1164 and does not check
        // the invulnerability window until line 1190. Anything that calls {@code hurt()} twice on
        // one entity in one tick therefore pays twice, and the window will not stop it.</p>
        //
        // <p>We do exactly that: the sabre's charged swing is two cuts by design and the loop in
        // {@code CombatEngine} clears {@code invulnerableTime} before each one so the second cut
        // is not swallowed. Both lands inside a single swing. <b>So a charged sabre took two
        // points off a raised shield, and a third cut would have taken the third</b> &mdash; which
        // is also why the shield sometimes stopped working at all, because at zero stamina
        // {@code isActive()} is false and this method returns without ever setting the damage.</p>
        //
        // <p>The key is the triple and not a boolean, because one attacker's tick is not one
        // strike: a sweep hits four different mobs and each of them has to pay.</p>
        Entity attacker = event.getDamageSource().getEntity();
        long now = player.level().getGameTime();
        GuardStamp last = player.getData(ModAttachments.GUARD_LAST_BLOCK.get());
        int attackerId = attacker == null ? 0 : attacker.getId();
        int targetId = player.getId();
        if (!last.shouldCharge(now, attackerId, targetId)) {
            // Same swing, already paid. The block still stands &mdash; only the point is not
            // spent twice &mdash; so a follow-up cut is still stopped, which is what the player
            // sees as "the shield held".
            event.setBlocked(true);
            event.setBlockedDamage(event.getOriginalBlockedDamage() * (1.0F - blocked));
            event.setShieldDamage(0.0F);
            return;
        }

        GuardState after = state.block(guard.profile().regenTicks());
        player.setData(ModAttachments.GUARD_STAMINA.get(), after);
        player.setData(ModAttachments.GUARD_LAST_BLOCK.get(),
                last.charged(now, attackerId, targetId));

        // **Contragolpe: el bloqueo que costó aguante abre una ventana ofensiva.** Se arma acá y no
        // en la rama de arriba, y esa es la distinción entera del encantamiento: el segundo corte
        // del mismo swing bloquea igual -- la guardia se sostiene -- pero no rearma nada. El premio
        // es por el golpe que se pagó, no por el que ya estaba pagado.
        player.setData(ModAttachments.CONTRAGOLPE.get(),
                com.cortes.combat.state.ContragolpeWindow.armedAt(now));

        event.setBlocked(true);
        // The fraction, not a flag. The event starts with `dmgBlocked` at the FULL incoming
        // damage, which is why the first version's `setBlockedDamage(0)` blocked nothing -- zero
        // means "stop nothing", not "stop all of it". An explosion writes half of the original,
        // and the other half is what keeps a creeper worth walking away from.
        event.setBlockedDamage(event.getOriginalBlockedDamage() * (1.0F - blocked));
        // No durability cost: the shield is not an item that wears out from doing the job it
        // exists for.
        event.setShieldDamage(0.0F);

        // A block is the one event that gets no crosshair growth. The player did not throw
        // anything, so making their crosshair jump would claim credit for a swing they never
        // took; the colour carries it on its own.
        if (player instanceof ServerPlayer server) {
            com.cortes.combat.net.ModNetworking.sendFeedback(server, CombatFeedback.GUARDED);
        }

        if (after.isExhausted()) {
            stagger(player, guard, guard.profile().stunTicks());
        }
        applyAxeStagger(player, guard, event.getDamageSource());
    }

    /**
     * Puts the guard item on cooldown and says so out loud.
     *
     * <p>The sound is not decoration. Without it the only feedback is a number going to zero,
     * which reads as the game ignoring the click, and the player has no way of knowing that the
     * two seconds they are now helpless is a rule and not a bug.</p>
     */
    private static void stagger(Player player, GuardStats.Resolved guard, int ticks) {
        player.getCooldowns().addCooldown(guard.item().getItem(), Math.max(1, ticks));
        // A broken sword and a broken shield are different noises, and the game's own is a wooden
        // shield cracking, which is the wrong material for a blade going down.
        //
        // The guard is one mechanic covering two items, so the sound is chosen by what was actually
        // holding, read from the same resolved pair the cooldown went on. One guard with two
        // sounds, rather than one sound that has to sound right for both.
        boolean shield = guard.item().is(net.minecraft.world.item.Items.SHIELD);
        if (shield) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
            return;
        }
        var event = com.cortes.combat.sound.CombatSounds.event(
                com.cortes.combat.sound.ModSounds.of(null,
                        com.cortes.combat.sound.ModSounds.Kind.GUARD_BROKEN));
        if (event != null) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    event, SoundSource.PLAYERS, 1.0F, 0.9F);
        }
    }

    /**
     * The design's "an axe stuns in one hit", for the sword.
     *
     * <p>It reuses vanilla's own predicate, {@code canDisableShield}, so the list of weapons
     * that do this is whatever the game says it is and not a second list here that drifts. For
     * the shield vanilla has already done this by the time we are called, and adding the
     * cooldown again is idempotent, so the same call is correct for both.</p>
     */
    private static void applyAxeStagger(Player player, GuardStats.Resolved guard,
                                        DamageSource source) {
        Entity origin = source.getEntity();
        if (!(origin instanceof LivingEntity attacker)) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        if (weapon.canDisableShield(guard.item(), attacker, player)) {
            stagger(player, guard, guard.profile().stunTicks());
        }
    }

    /**
     * The warhammer's charged hit, reaching past a guard entirely.
     *
     * <p><b>It empties the whole bar, and it does not ask how many points were left.</b> This is
     * the same relationship a warhammer already has with a shield in vanilla -- one swing and the
     * guard is gone -- made explicit, because ours pays for it: the swing has to be at full charge,
     * which is the whole reason a warhammer can afford to be the slowest weapon in the game.</p>
     *
     * <p><b>Armour does not come into it.</b> A hit that already bypasses armour also bypasses the
     * shield, which vanilla decides for us by nesting {@code bypasses_armor} inside
     * {@code bypasses_shield}; this method is what <em>charges</em> the defender for having put the
     * shield up, which bypasses_shield alone does not do. Without it a warhammer would walk through
     * a raised shield for free, and the guard would be the one thing in the game with no price.</p>
     *
     * <p>Guarded items only. An unarmed player has no bar to empty, and hitting one is what the
     * swing is for.</p>
     *
     * <p>It empties the bar <em>and</em> pays the stagger, because those are one event and the
     * caller should not have to know that this is two steps. The stagger is the item's own vanilla
     * cooldown, so it arrives already synced and already drawn by the vanilla HUD.</p>
     */
    public static void breakGuard(Player player) {
        // **Both hands, not the main one.** The guard has been readable from either hand since it
        // was unified, and reading only the main hand would mean a player covering with a shield in
        // the off hand is invulnerable to the warhammer while a player with the same shield in the
        // main hand is not. `best` is the same call the rest of this file makes.
        GuardStats.Resolved guard = GuardStats.best(player);
        if (!guard.canGuard()) {
            return;
        }
        GuardState state = player.getData(ModAttachments.GUARD_STAMINA.get());
        if (!state.isActive()) {
            return;
        }
        player.setData(ModAttachments.GUARD_STAMINA.get(),
                state.breakGuard(guard.profile().regenTicks()));
        stagger(player, guard, guard.profile().stunTicks());
    }

    /** An explosion takes half its damage through the guard, and still costs a point of stamina. */
    public static final float EXPLOSION_BLOCKED = 0.5F;

    private static final float FULL_BLOCK = 1.0F;

    /**
     * How much of a hit this guard stops: {@code 0} nothing, {@code 0.5} half, {@code 1} all.
     *
     * <p>A fraction and not a boolean because the answer is genuinely two-valued, and the second
     * value is the one the design changed. "An explosion is not something you can face" made the
     * guard worthless against the one thing a player cannot choose to be standing next to, and
     * half is what makes positioning still matter: the guard eats the bigger half and the
     * player eats the rest, and the point of stamina it costs is the same as any other block.</p>
     *
     * <p>Falls, fire, drowning and starvation stay at nothing, and for the design's own reason: a
     * guard is a decision about a fight, and environmental damage is not a fight.</p>
     */
    private static float blockedFraction(DamageSource source, Player player, GuardProfile profile) {
        boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        float fraction = explosion ? EXPLOSION_BLOCKED : FULL_BLOCK;

        // Whatever vanilla already considers unshieldable stays that way. The explosion is exempt
        // because vanilla puts several damage types in both tags and we want the blast judged on
        // our rule, not skipped by one of them.
        if (source.is(DamageTypeTags.BYPASSES_SHIELD) && !explosion) {
            return 0.0F;
        }

        Entity origin = source.getEntity();
        if (origin == player) {
            // The "source" is the victim. Nobody threw this.
            return 0.0F;
        }
        if (origin == null && !explosion) {
            // Fall damage, fire, drowning, starvation. No attacker, and judging the arc on the
            // victim's own position would read as "straight ahead" and block all of it.
            return 0.0F;
        }

        // **Where the threat came from, which is not the same question for a projectile.**
        //
        // A projectile's `getSourcePosition()` is the projectile's own centre at the moment of
        // impact, which is *inside the player it hit*. The arc rule gets a zero length vector,
        // and it accepts those on purpose, so a shot in the back read exactly like a shot in the
        // face. The shooter is the direction that means something, and judging the arc on the
        // shooter is what makes a shield worth holding up against arrows at all.
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile
                && origin != null) {
            return inFront(player, origin.position(), profile) ? fraction : 0.0F;
        }

        // An explosion is judged on where the blast is, because nothing threw it and the blast is
        // the only thing that has a direction at all.
        Vec3 position = source.getSourcePosition();
        if (position == null) {
            return 0.0F;
        }
        return inFront(player, position, profile) ? fraction : 0.0F;
    }

    private static boolean inFront(Player player, Vec3 from, GuardProfile profile) {
        Vec3 look = player.getLookAngle();
        Vec3 self = player.position();
        return GuardRules.isFromFront(
                self.x, self.y, self.z,
                look.x, look.y, look.z,
                from.x, from.y, from.z,
                profile.safeArc());
    }
}
