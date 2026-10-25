package com.cortes.combat.platform;

import com.cortes.combat.enchant.FrenzyState;
import com.cortes.combat.guard.GuardStamp;
import com.cortes.combat.registry.ModAttachments;
import com.cortes.combat.state.Adrenalina;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.ContragolpeWindow;
import com.cortes.combat.state.CooldownState;
import com.cortes.combat.state.CorteGemelo;
import com.cortes.combat.state.FallDeferral;
import com.cortes.combat.state.GuardFree;
import com.cortes.combat.state.GuardState;
import com.cortes.combat.state.LungeState;
import com.cortes.combat.state.ParryState;
import com.cortes.combat.state.PendingHit;
import com.cortes.combat.state.SecondWindWindow;
import com.cortes.combat.state.StreakState;
import com.cortes.combat.state.SyncState;
import com.cortes.combat.survival.SurvivalState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Every piece of combat state this mod keeps on a player, named once, here.
 *
 * <h2>Why this class exists</h2>
 *
 * <p><b>Because "where the state lives" is the single most version-coupled decision in the mod, and
 * it used to be spelled at every call site.</b> There were one hundred and twelve of them -- sixty
 * on reads, forty-eight on writes -- across eighteen files, and every one said
 * {@code player.getData(ModAttachments.CHARGE.get())}: the storage mechanism, in the middle of a
 * damage calculation. That is fine as long as the mechanism never changes, and it changed four
 * releases after this mod was written: NeoForge replaced {@code AttachmentType} with the capability
 * system it had before, and a port to 1.20.1 would have rewritten all one hundred and twelve.</p>
 *
 * <p>Asked from here the question is <em>what</em> and not <em>how</em>: {@code CombatState.charge(player)}.
 * The answer is an attachment in 1.21.1 and will be a capability anywhere else, and the difference
 * lands in this file.</p>
 *
 * <h2>The three rules this class obeys, and why each is a rule</h2>
 *
 * <p><b>One, every method delegates and none of them computes.</b> A facade that derived a value
 * would be a second source of truth for a number the state already owns, and this project has the
 * receipt: {@code canCharge = false} was written as a special case and the fix was to ask the
 * component instead. A method here that does arithmetic is a bug wearing a layering argument.</p>
 *
 * <p><b>Two, nothing is cached and nothing is copied.</b> These are called in the middle of a tick,
 * sometimes several times per tick, so they return the state object itself -- the same reference the
 * attachment holds. Copying here would be a per-tick allocation dressed as cleanliness, and caching
 * would be stale the moment anything wrote through the other door.</p>
 *
 * <p><b>Three, no Minecraft type crosses in either direction.</b> Every parameter and every return
 * value below is one of the mod's own records, except the {@link Entity} being asked about. That is
 * the same line {@code item/tooltip} drew around {@code TooltipLine} and the same one that lets the
 * mod's rules be tested in a bare JVM with no game on the classpath. (⌒‿⌒)</p>
 *
 * <p>The alternative to a class like this is a generic {@code get(Entity, AttachmentType<T>)}, which
 * reads neat and buys nothing: the call site would still name the attachment registry, which is the
 * part that does not survive a version. (￣ω￣)</p>
 */
public final class CombatState {

    private CombatState() {
    }

    // --- the left button: charge, cooldown, the swing in flight ------------------------------

    /** Charge accumulated while the attack button is held. Predicted on the client, owned by the server. */
    public static ChargeState charge(Entity entity) {
        return entity.getData(ModAttachments.CHARGE.get());
    }

    public static void setCharge(Entity entity, ChargeState state) {
        entity.setData(ModAttachments.CHARGE.get(), state);
    }

    /** The permission to attack. Server-authoritative. */
    public static CooldownState cooldown(Entity entity) {
        return entity.getData(ModAttachments.COOLDOWN.get());
    }

    public static void setCooldown(Entity entity, CooldownState state) {
        entity.setData(ModAttachments.COOLDOWN.get(), state);
    }

    /** A swing waiting for the cone to agree with the aim it was thrown with. See {@link PendingHit}. */
    public static PendingHit pendingHit(Entity entity) {
        return entity.getData(ModAttachments.PENDING_HIT.get());
    }

    public static void setPendingHit(Entity entity, PendingHit hit) {
        entity.setData(ModAttachments.PENDING_HIT.get(), hit);
    }

    /**
     * A refused press, parked for the length of {@link PressBuffer#WINDOW_TICKS}.
     *
     * <p>It is the game tick the press arrived on, {@link PressBuffer#EMPTY} when nothing is
     * parked. No codec and no {@code copyOnDeath}, by the rule the swing in flight and the
     * fallen deferral already wrote: this is a confirmation in flight, not a state -- nothing
     * reads it across a save or a death, and a press interrupted by either should die rather
     * than resume.</p>
     */
    public static long swingBuffer(Entity entity) {
        return entity.getData(ModAttachments.SWING_BUFFER.get());
    }

    public static void setSwingBuffer(Entity entity, long pressTick) {
        entity.setData(ModAttachments.SWING_BUFFER.get(), pressTick);
    }

    // --- the guard ---------------------------------------------------------------------------

    /** Remaining guard hits. The maximum comes from the item, never from here. */
    public static GuardState guard(Entity entity) {
        return entity.getData(ModAttachments.GUARD_STAMINA.get());
    }

    public static void setGuard(Entity entity, GuardState state) {
        entity.setData(ModAttachments.GUARD_STAMINA.get(), state);
    }

    /** Who was blocked and when, so one swing spends one point. */
    public static GuardStamp lastBlock(Entity entity) {
        return entity.getData(ModAttachments.GUARD_LAST_BLOCK.get());
    }

    public static void setLastBlock(Entity entity, GuardStamp stamp) {
        entity.setData(ModAttachments.GUARD_LAST_BLOCK.get(), stamp);
    }

    /** Guardia Firme: the clock of the block that costs no stamina. See {@link GuardFree}. */
    public static GuardFree guardFree(Entity entity) {
        return entity.getData(ModAttachments.GUARD_FREE.get());
    }

    public static void setGuardFree(Entity entity, GuardFree free) {
        entity.setData(ModAttachments.GUARD_FREE.get(), free);
    }

    /** Segundo Aliento: the window that opens when the guard is broken. */
    public static SecondWindWindow secondWindow(Entity entity) {
        return entity.getData(ModAttachments.SECOND_WINDOW.get());
    }

    public static void setSecondWindow(Entity entity, SecondWindWindow window) {
        entity.setData(ModAttachments.SECOND_WINDOW.get(), window);
    }

    // --- the sabre ---------------------------------------------------------------------------

    /** Parry window and its five-second cooldown. */
    public static ParryState parry(Entity entity) {
        return entity.getData(ModAttachments.PARRY.get());
    }

    public static void setParry(Entity entity, ParryState state) {
        entity.setData(ModAttachments.PARRY.get(), state);
    }

    /** Contragolpe: the offensive window a real block opens. */
    public static ContragolpeWindow contragolpe(Entity entity) {
        return entity.getData(ModAttachments.CONTRAGOLPE.get());
    }

    public static void setContragolpe(Entity entity, ContragolpeWindow window) {
        entity.setData(ModAttachments.CONTRAGOLPE.get(), window);
    }

    // --- the ropera --------------------------------------------------------------------------

    /** The lunge in progress: where the player has been measured to, and how far into it. */
    public static LungeState lunge(Entity entity) {
        return entity.getData(ModAttachments.LUNGE.get());
    }

    public static void setLunge(Entity entity, LungeState state) {
        entity.setData(ModAttachments.LUNGE.get(), state);
    }

    /**
     * The entities this player's last lunge has already hit.
     *
     * <p>Handed back live, deliberately: the only thing that ever happens to it is {@code add}, and
     * it is cleared where the lunge is cleared. Copying it here would put the copy and the state out
     * of step in exactly the place where "already hit" decides damage. (μ_μ)</p>
     */
    public static Set<LivingEntity> lungeHits(Entity entity) {
        return entity.getData(ModAttachments.LUNGE_HITS.get());
    }

    public static void setLungeHits(Entity entity, Set<LivingEntity> hits) {
        entity.setData(ModAttachments.LUNGE_HITS.get(), hits);
    }

    // --- the hammer, the shovel, the sword ----------------------------------------------------

    /** Warhammer consecutive-hit escalation. */
    public static StreakState hammerStreak(Entity entity) {
        return entity.getData(ModAttachments.HAMMER_STREAK.get());
    }

    public static void setHammerStreak(Entity entity, StreakState streak) {
        entity.setData(ModAttachments.HAMMER_STREAK.get(), streak);
    }

    /** La serie de la pala. The same record as the hammer's, on its own clock. */
    public static StreakState palizaStreak(Entity entity) {
        return entity.getData(ModAttachments.PALIZA_STREAK.get());
    }

    public static void setPalizaStreak(Entity entity, StreakState streak) {
        entity.setData(ModAttachments.PALIZA_STREAK.get(), streak);
    }

    // --- the swords of the duel (gold and copper) --------------------------------------------

    public static Adrenalina adrenalina(Entity entity) {
        return entity.getData(ModAttachments.ADRENALINA.get());
    }

    public static void setAdrenalina(Entity entity, Adrenalina state) {
        entity.setData(ModAttachments.ADRENALINA.get(), state);
    }

    public static CorteGemelo corteGemelo(Entity entity) {
        return entity.getData(ModAttachments.CORTE_GEMELO.get());
    }

    public static void setCorteGemelo(Entity entity, CorteGemelo state) {
        entity.setData(ModAttachments.CORTE_GEMELO.get(), state);
    }

    /** The gold sword's frenzy. Named after the mod's own state record and not after a colour. (￣▽￣) */
    public static FrenzyState frenzy(Entity entity) {
        return entity.getData(ModAttachments.FRENZY.get());
    }

    public static void setFrenzy(Entity entity, FrenzyState state) {
        entity.setData(ModAttachments.FRENZY.get(), state);
    }

    // --- the arcabuz -------------------------------------------------------------------------

    /** The right button, which is the player's and not the weapon's. */
    public static boolean arcabuzButton(Entity entity) {
        return Boolean.TRUE.equals(entity.getData(ModAttachments.ARCABUZ_BUTTON.get()));
    }

    public static void setArcabuzButton(Entity entity, boolean down) {
        entity.setData(ModAttachments.ARCABUZ_BUTTON.get(), down);
    }

    /** The right button's charge: the anti-materiel shovel today, whatever comes next tomorrow. */
    public static ChargeState specialCharge(Entity entity) {
        return entity.getData(ModAttachments.SPECIAL_CHARGE.get());
    }

    public static void setSpecialCharge(Entity entity, ChargeState state) {
        entity.setData(ModAttachments.SPECIAL_CHARGE.get(), state);
    }

    // --- survival and falling ----------------------------------------------------------------

    /** Los dos relojes de la comida y la curación. See {@link SurvivalState}. */
    public static SurvivalState survival(Entity entity) {
        return entity.getData(ModAttachments.SURVIVAL.get());
    }

    public static void setSurvival(Entity entity, SurvivalState state) {
        entity.setData(ModAttachments.SURVIVAL.get(), state);
    }

    /** A fall's damage, held for the coyote window. See {@link FallDeferral}. */
    public static FallDeferral fallDeferral(Entity entity) {
        return entity.getData(ModAttachments.FALL_DEFERRAL.get());
    }

    public static void setFallDeferral(Entity entity, FallDeferral deferral) {
        entity.setData(ModAttachments.FALL_DEFERRAL.get(), deferral);
    }

    // --- bandwidth bookkeeping ---------------------------------------------------------------

    /** What was last pushed to this client's HUD, so an unchanged state is not resent. */
    public static SyncState lastSynced(Entity entity) {
        return entity.getData(ModAttachments.LAST_SYNCED.get());
    }

    public static void setLastSynced(Entity entity, SyncState state) {
        entity.setData(ModAttachments.LAST_SYNCED.get(), state);
    }
}
