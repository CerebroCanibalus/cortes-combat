package com.cortes.combat.platform;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.enchant.FrenzyState;
import com.cortes.combat.guard.GuardStamp;
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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The 1.20.1 body of the combat-state facade. Same 42 methods as the shared twin, which the
 * 1.21.1 leg compiles instead of this file.
 *
 * <p><b>One provider, served under one capability, holding the same 21 states.</b> 1.20.1 has no
 * attachments: the mechanism here is the capability system Forge 47 ships ({@code Capability} +
 * {@code ICapabilityProvider} + {@code LazyOptional}), attached once per entity under
 * {@code cortescombat:state} and invalidated on unload through the event's own listener. The
 * states themselves are the shared records -- the mechanism changed, the data did not, which is
 * the entire bet this facade was built on. (⌒‿⌒)</p>
 *
 * <p><b>Death and disk mirror 1.21.1 exactly, and the mirror is explicit.</b> Of the 21
 * attachments, {@code CHARGE} is the only one with {@code copyOnDeath} and ten carry a codec, so
 * the clone handler copies exactly one field and the serializer writes exactly ten keys. If a
 * future attachment gains {@code copyOnDeath} or a codec over there, the matching line has to be
 * added here by hand -- there is no shared constant to read, because the two mechanisms have no
 * common type. That asymmetry is the price of the port and it is paid here, in the open.</p>
 *
 * <p><b>Sync rides the existing payload flow, not a new one.</b> Capabilities do not sync
 * themselves; but nothing here invents syncing, because {@code PlayerStateSync} already pushes
 * the HUD-relevant states to tracking clients every tick and heartbeat, reading through this
 * same facade. A fresh tracker converges within a tick of seeing the player, which is the same
 * bound the 1.21.1 leg already lives with.</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID)
public final class CombatState {

    /** The single capability this file serves. One attach per entity, twenty-one states inside. */
    public static final Capability<StateBag> STATE =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private CombatState() {
    }

    private static StateBag bag(Entity entity) {
        return entity.getCapability(STATE).orElseThrow(() ->
                new IllegalStateException("cortescombat:state missing on " + entity));
    }

    // --- the left button: charge, cooldown, the swing in flight ------------------------------

    public static ChargeState charge(Entity entity) {
        return bag(entity).charge;
    }

    public static void setCharge(Entity entity, ChargeState state) {
        bag(entity).charge = state;
    }

    public static CooldownState cooldown(Entity entity) {
        return bag(entity).cooldown;
    }

    public static void setCooldown(Entity entity, CooldownState state) {
        bag(entity).cooldown = state;
    }

    public static PendingHit pendingHit(Entity entity) {
        return bag(entity).pendingHit;
    }

    public static void setPendingHit(Entity entity, PendingHit hit) {
        bag(entity).pendingHit = hit;
    }

    // --- the guard ---------------------------------------------------------------------------

    public static GuardState guard(Entity entity) {
        return bag(entity).guard;
    }

    public static void setGuard(Entity entity, GuardState state) {
        bag(entity).guard = state;
    }

    public static GuardStamp lastBlock(Entity entity) {
        return bag(entity).lastBlock;
    }

    public static void setLastBlock(Entity entity, GuardStamp stamp) {
        bag(entity).lastBlock = stamp;
    }

    public static GuardFree guardFree(Entity entity) {
        return bag(entity).guardFree;
    }

    public static void setGuardFree(Entity entity, GuardFree free) {
        bag(entity).guardFree = free;
    }

    public static SecondWindWindow secondWindow(Entity entity) {
        return bag(entity).secondWindow;
    }

    public static void setSecondWindow(Entity entity, SecondWindWindow window) {
        bag(entity).secondWindow = window;
    }

    // --- the sabre ---------------------------------------------------------------------------

    public static ParryState parry(Entity entity) {
        return bag(entity).parry;
    }

    public static void setParry(Entity entity, ParryState state) {
        bag(entity).parry = state;
    }

    public static ContragolpeWindow contragolpe(Entity entity) {
        return bag(entity).contragolpe;
    }

    public static void setContragolpe(Entity entity, ContragolpeWindow window) {
        bag(entity).contragolpe = window;
    }

    // --- the ropera --------------------------------------------------------------------------

    public static LungeState lunge(Entity entity) {
        return bag(entity).lunge;
    }

    public static void setLunge(Entity entity, LungeState state) {
        bag(entity).lunge = state;
    }

    public static Set<LivingEntity> lungeHits(Entity entity) {
        return bag(entity).lungeHits;
    }

    public static void setLungeHits(Entity entity, Set<LivingEntity> hits) {
        bag(entity).lungeHits = hits;
    }

    // --- the hammer, the shovel, the sword ----------------------------------------------------

    public static StreakState hammerStreak(Entity entity) {
        return bag(entity).hammerStreak;
    }

    public static void setHammerStreak(Entity entity, StreakState streak) {
        bag(entity).hammerStreak = streak;
    }

    public static StreakState palizaStreak(Entity entity) {
        return bag(entity).palizaStreak;
    }

    public static void setPalizaStreak(Entity entity, StreakState streak) {
        bag(entity).palizaStreak = streak;
    }

    // --- the swords of the duel (gold and copper) --------------------------------------------

    public static Adrenalina adrenalina(Entity entity) {
        return bag(entity).adrenalina;
    }

    public static void setAdrenalina(Entity entity, Adrenalina state) {
        bag(entity).adrenalina = state;
    }

    public static CorteGemelo corteGemelo(Entity entity) {
        return bag(entity).corteGemelo;
    }

    public static void setCorteGemelo(Entity entity, CorteGemelo state) {
        bag(entity).corteGemelo = state;
    }

    public static FrenzyState frenzy(Entity entity) {
        return bag(entity).frenzy;
    }

    public static void setFrenzy(Entity entity, FrenzyState state) {
        bag(entity).frenzy = state;
    }

    // --- the arcabuz -------------------------------------------------------------------------

    public static boolean arcabuzButton(Entity entity) {
        return bag(entity).arcabuzButton;
    }

    public static void setArcabuzButton(Entity entity, boolean down) {
        bag(entity).arcabuzButton = down;
    }

    public static ChargeState specialCharge(Entity entity) {
        return bag(entity).specialCharge;
    }

    public static void setSpecialCharge(Entity entity, ChargeState state) {
        bag(entity).specialCharge = state;
    }

    // --- survival and falling ----------------------------------------------------------------

    public static SurvivalState survival(Entity entity) {
        return bag(entity).survival;
    }

    public static void setSurvival(Entity entity, SurvivalState state) {
        bag(entity).survival = state;
    }

    public static FallDeferral fallDeferral(Entity entity) {
        return bag(entity).fallDeferral;
    }

    public static void setFallDeferral(Entity entity, FallDeferral deferral) {
        bag(entity).fallDeferral = deferral;
    }

    // --- bandwidth bookkeeping ---------------------------------------------------------------

    public static SyncState lastSynced(Entity entity) {
        return bag(entity).lastSynced;
    }

    public static void setLastSynced(Entity entity, SyncState state) {
        bag(entity).lastSynced = state;
    }

    // --- the mechanism: attach, serialize, copy on death -------------------------------------

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        StateBag bag = new StateBag();
        event.addCapability(ResourceIds.rl("state"), bag);
        event.addListener(bag.self::invalidate);
    }

    /**
     * What survives death, decided by the 1.21.1 side: {@code CHARGE} is the only attachment
     * with {@code copyOnDeath}. Everything else comes back as its default, which is the
     * guard-loses-its-points-on-respawn rule the playtest asked for, not an omission.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        bag(event.getEntity()).charge = bag(event.getOriginal()).charge;
    }

    /**
     * All 21 states, with the 1.21.1 defaults. The ten with a codec serialize under their
     * registry key; the rest live and die with the session, exactly like their twins.
     */
    static final class StateBag implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final LazyOptional<StateBag> self = LazyOptional.of(() -> this);

        ChargeState charge = ChargeState.EMPTY;
        GuardState guard = GuardState.EMPTY;
        GuardStamp lastBlock = GuardStamp.NONE;
        CooldownState cooldown = CooldownState.READY;
        StreakState hammerStreak = StreakState.EMPTY;
        ParryState parry = ParryState.READY;
        ContragolpeWindow contragolpe = ContragolpeWindow.NONE;
        LungeState lunge = LungeState.IDLE;
        Set<LivingEntity> lungeHits = new LinkedHashSet<>();
        FrenzyState frenzy = FrenzyState.EMPTY;
        SyncState lastSynced = SyncState.NEVER;
        boolean arcabuzButton = false;
        FallDeferral fallDeferral = FallDeferral.NONE;
        PendingHit pendingHit = PendingHit.NONE;
        ChargeState specialCharge = ChargeState.EMPTY;
        SecondWindWindow secondWindow = SecondWindWindow.NONE;
        GuardFree guardFree = GuardFree.READY;
        Adrenalina adrenalina = Adrenalina.READY;
        CorteGemelo corteGemelo = CorteGemelo.READY;
        SurvivalState survival = SurvivalState.NEW;
        StreakState palizaStreak = StreakState.EMPTY;

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            if (cap == STATE) {
                return self.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.put("charge", encode(ChargeState.CODEC, charge));
            tag.put("guard_stamina", encode(GuardState.CODEC, guard));
            tag.put("guard_last_block", encode(GuardStamp.CODEC, lastBlock));
            tag.put("cooldown", encode(CooldownState.CODEC, cooldown));
            tag.put("hammer_streak", encode(StreakState.CODEC, hammerStreak));
            tag.put("parry", encode(ParryState.CODEC, parry));
            tag.put("lunge", encode(LungeState.CODEC, lunge));
            tag.put("last_synced", encode(SyncState.CODEC, lastSynced));
            tag.put("fall_deferral", encode(FallDeferral.CODEC, fallDeferral));
            tag.put("paliza_streak", encode(StreakState.CODEC, palizaStreak));
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            charge = decode(ChargeState.CODEC, tag, "charge", ChargeState.EMPTY);
            guard = decode(GuardState.CODEC, tag, "guard_stamina", GuardState.EMPTY);
            lastBlock = decode(GuardStamp.CODEC, tag, "guard_last_block", GuardStamp.NONE);
            cooldown = decode(CooldownState.CODEC, tag, "cooldown", CooldownState.READY);
            hammerStreak = decode(StreakState.CODEC, tag, "hammer_streak", StreakState.EMPTY);
            parry = decode(ParryState.CODEC, tag, "parry", ParryState.READY);
            lunge = decode(LungeState.CODEC, tag, "lunge", LungeState.IDLE);
            lastSynced = decode(SyncState.CODEC, tag, "last_synced", SyncState.NEVER);
            fallDeferral = decode(FallDeferral.CODEC, tag, "fall_deferral", FallDeferral.NONE);
            palizaStreak = decode(StreakState.CODEC, tag, "paliza_streak", StreakState.EMPTY);
        }

        private static <T> net.minecraft.nbt.Tag encode(com.mojang.serialization.Codec<T> codec, T value) {
            return codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow(false, s -> {
            });
        }

        private static <T> T decode(com.mojang.serialization.Codec<T> codec, CompoundTag tag,
                String key, T fallback) {
            if (!tag.contains(key)) {
                return fallback;
            }
            return codec.parse(NbtOps.INSTANCE, tag.get(key)).result().orElse(fallback);
        }
    }
}
