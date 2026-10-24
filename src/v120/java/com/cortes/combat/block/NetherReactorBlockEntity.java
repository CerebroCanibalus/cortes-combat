package com.cortes.combat.block;

import com.cortes.combat.effect.RevelationEffect;
import com.mojang.logging.LogUtils;
import com.cortes.combat.registry.ModBlockEntities;
import com.cortes.combat.registry.ModBlocks;
import com.cortes.combat.registry.ModEffects;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.sound.CombatSounds;
import com.cortes.combat.sound.ReactorSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.slf4j.Logger;

/**
 * The nether reactor's state: the firestones it holds, the nether star, and the hour clock.
 *
 * <h2>Charge is the slot, not a counter</h2>
 *
 * <p><b>One firestone is one charge, so the charge <em>is</em> the count in slot zero.</b> There is
 * no second number to keep in step: inserting a firestone is charge plus one, consuming one at the
 * hour is charge minus one, and the {@code charged} block state is "is that slot empty". A separate
 * counter beside the inventory would be two truths about one fact, which is the bug this project
 * keeps paying for.</p>
 *
 * <h2>The two caps, and why they are in two different places</h2>
 *
 * <p><b>The slot's limit is forty; the automation wrapper's limit is thirty.</b> The design wants a
 * hopper to fill a reactor to thirty and stop, and a player to be able to push it to forty by hand.
 * The way that is expressed is the block capability: a hopper never sees the inventory, it sees
 * {@link #automationHandler()}, whose {@code insertItem} refuses anything past
 * {@link ReactorCharge#MAX_NORMAL}. The manual right-click calls the inventory directly, so it
 * obeys only the slot limit. One inventory, two doors, two ceilings.</p>
 *
 * <p><b>The cap lives in {@code insertItem} and not in {@code isItemValid}, and that is deliberate.</b>
 * {@code ItemStackHandler.insertItem} does call {@code isItemValid}, so both would work for the
 * direct path -- but the number that matters is "how much is already in there", which
 * {@code isItemValid} is not given. The room left is computed in the wrapper's {@code insertItem}
 * from the current charge, which is the only place the count is visible.</p>
 *
 * <h2>The clock</h2>
 *
 * <p>The hour counter advances only while the block state says active, and the block state says
 * active only while powered and charged. A reactor switched off holds its charge and its clock, so
 * switching it back on resumes rather than restarts -- which is what "the overload is interrupted
 * and resumed" asks for, applied to the normal clock as well. The counter is saved, so a reload
 * does not hand out a free hour.</p>
 */
public class NetherReactorBlockEntity extends BlockEntity {

    /** Firestones in slot zero, the nether star in slot one. */
    public static final int SLOT_FIRESTONE = 0;
    public static final int SLOT_STAR = 1;
    private static final int SLOTS = 2;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        public int getSlotLimit(int slot) {
            // The manual ceiling: forty for the firestones, one for the star.
            return slot == SLOT_FIRESTONE ? ReactorCharge.MAX_OVERLOAD : 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_FIRESTONE) {
                return stack.is(ModItems.FIRESTONE.get());
            }
            if (slot == SLOT_STAR) {
                return stack.is(Items.NETHER_STAR);
            }
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            NetherReactorBlockEntity.this.setChanged();
            if (NetherReactorBlockEntity.this.level instanceof ServerLevel server) {
                NetherReactorBlockEntity.this.refreshState(server);
                // **The trace for "the light does not rise with the charge".** Fires only when the
                // inventory changes, so it costs one line per firestone fed or taken, and it prints
                // the exact number the light engine will read: if this climbs and the screen does
                // not, the curve is fine and the refresh is the liar.
                int light = ReactorCharge.lightFor(NetherReactorBlockEntity.this.charge());
                log.info("[luz] carga={} -> luz {}",
                        NetherReactorBlockEntity.this.charge(), light);
            }
        }
    };

    /**
     * The door automation sees: firestones only, never past thirty, and never the star slot.
     *
     * <p>Read as the block capability's item handler, so a hopper, a dropper or any other mod's
     * pipe reaches this and not the inventory behind it. Extraction is delegated unchanged: the
     * design only says automation stops depositing at thirty, not that it may not pull.</p>
     */
    private final IItemHandler automation = new IItemHandler() {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != SLOT_FIRESTONE || !stack.is(ModItems.FIRESTONE.get())) {
                // Slot one is manual only: a nether star never arrives through a pipe.
                return stack;
            }
            int room = ReactorCharge.MAX_NORMAL - charge();
            if (room <= 0) {
                return stack;
            }
            ItemStack offered = stack.copyWithCount(Math.min(stack.getCount(), room));
            ItemStack leftover = inventory.insertItem(SLOT_FIRESTONE, offered, simulate);
            int inserted = offered.getCount() - leftover.getCount();
            return stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == SLOT_STAR) {
                // The star is manual in both directions: automation neither puts one in nor takes
                // one out. "Only by hand" is about the slot, not just the insert.
                return ItemStack.EMPTY;
            }
            return inventory.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_FIRESTONE && stack.is(ModItems.FIRESTONE.get());
        }
    };

    /** Whether a redstone signal reaches the block. Set by the block; the state is derived from it. */
    private boolean powered;

    /** Whether the block is lit, mirrored into the block state. Kept so the clock can read it cheaply. */
    private boolean active;

    /** Ticks the reactor has been active since the last firestone was spent. Saved. */
    private int hourTicks;

    /**
     * The radius this reactor currently publishes to the registry. <b>Saved, and it only ever
     * grows.</b>
     *
     * <p><b>Why it only grows: a wire the reactor once fed is still a wire the reactor feeds.</b>
     * The regression was "the signal dips when the overload ends, and toggling the switch fixes
     * it": the star publishes a flat hundred and the firestone at forty publishes sixty, and the
     * moment the fuse is cut the radius falls back to the charge's own curve. Every wire beyond
     * the smaller number loses its membership, recomputes with {@code -1}, and since
     * {@code max(i, j)} never hands back a point that was lost, it stays dark until something
     * sweeps the area again. The lever is what such a player found by accident.</p>
     *
     * <p>The lever itself does not go through here: {@code lit} false publishes zero, which is a
     * player's decision and not a shrink. Switching back on restores the mark, so a line that was
     * lit before the lever is lit again after it, at whatever radius the reactor once reached.</p>
     */
    private int publishedRadius;

    /**
     * Which overload is running, if any. Saved, and synced to the client so the camera knows to
     * shake.
     *
     * <p><b>The fuse is not a second counter beside the charge; it is the charge's consequence.</b>
     * It is created when the charge passes the ceiling and it is cleared when the charge falls back
     * or the lever cuts the power, so there is no way to have a fuse without a reason for one. The
     * nether star's is the exception in the other direction: it is created by a hand, not a count,
     * and it is cleared by water alone.</p>
     */
    private ReactorOverload.Kind overload = ReactorOverload.Kind.NONE;

    /** Ticks left on the fuse. Zero when nothing is running. */
    private int fuse;

    /** Ticks since the running loop last sounded, so the 245-tick recording repeats seamlessly. */
    private int loopTicks;

    /**
     * False until {@link #onLoad} has finished, so the act of loading a world does not play the
     * switch-on sound for a reactor that was already on.
     */
    private boolean initialised;

    /** The lit value {@link #refreshState} last saw, for the edge-triggered switch sounds. */
    private boolean lastLit;

    /** Debug only, never saved: a lever the command holds down regardless of the world's signal. */
    private boolean debugActive;

    public NetherReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETHER_REACTOR.get(), pos, state);
    }

    /** The number of firestones held, which is the charge. */
    public int charge() {
        return inventory.getStackInSlot(SLOT_FIRESTONE).getCount();
    }

    /** The handler automation is allowed to see. */
    public IItemHandler automationHandler() {
        return automation;
    }

    public boolean powered() {
        return powered;
    }

    /** Whether this item would be accepted by a manual click, for the client's prediction. */
    public boolean accepts(ItemStack stack) {
        if (stack.is(ModItems.FIRESTONE.get())) {
            return charge() < ReactorCharge.MAX_OVERLOAD;
        }
        if (stack.is(Items.NETHER_STAR)) {
            return inventory.getStackInSlot(SLOT_STAR).isEmpty();
        }
        return false;
    }

    /**
     * The manual right-click: one firestone from the hand, or one nether star.
     *
     * <p>Server only. It writes the inventory directly -- bypassing {@link #automationHandler()} --
     * so a firestone may be pushed past the automation ceiling and up to the slot's own limit, which
     * is the whole point of the two doors.</p>
     */
    public boolean insertFromHand(Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel)) {
            return false;
        }
        if (stack.is(ModItems.FIRESTONE.get())) {
            if (charge() >= ReactorCharge.MAX_OVERLOAD) {
                return false;
            }
            ItemStack leftover = inventory.insertItem(SLOT_FIRESTONE,
                    new ItemStack(ModItems.FIRESTONE.get()), false);
            if (!leftover.isEmpty()) {
                return false;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return true;
        }
        if (stack.is(Items.NETHER_STAR)) {
            if (!inventory.getStackInSlot(SLOT_STAR).isEmpty()) {
                return false;
            }
            // **The star is the overload.** Dropping one in does not "arm" anything later: the fuse
            // starts here, on the same right-click, and from this moment the lever is irrelevant and
            // only water stops it.
            if (!(level instanceof ServerLevel server)) {
                return false;
            }
            inventory.setStackInSlot(SLOT_STAR, new ItemStack(Items.NETHER_STAR));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            startFuse(server, ReactorOverload.Kind.STAR);
            return true;
        }
        return false;
    }

    /**
     * The water bucket: the one way out of a star overload.
     *
     * <p>Server only. It stops the fuse, drops the spent star where the core was and gives the
     * player back an empty bucket for the one they poured. The bucket is not refunded in creative,
     * which is vanilla's behaviour for every bucket and not a special case here.</p>
     */
    public boolean cancelStar(ServerLevel server, Player player, ItemStack bucket) {
        if (overload != ReactorOverload.Kind.STAR) {
            return false;
        }
        endOverload();
        if (player != null && bucket != null && !player.getAbilities().instabuild) {
            bucket.shrink(1);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        }
        consumeStar(server);
        refreshState(server);
        return true;
    }

    /** The neighbour-water path: a flowing cell is enough, and there is no bucket to return. */
    public boolean cancelStar(ServerLevel server) {
        if (overload != ReactorOverload.Kind.STAR) {
            return false;
        }
        endOverload();
        consumeStar(server);
        refreshState(server);
        return true;
    }

    /**
     * The slot's live star dies with its fuse, and the dead star that pops <b>is</b> the exchange.
     *
     * <p>Leaving the live one in the slot paid twice: the reactor held it after the cut, and
     * breaking it afterwards spilled the original too -- dead star AND nether star from a single
     * water-cut, which is the reported "guarda la netherstar, la suelta cuando la rompes".</p>
     */
    private void consumeStar(ServerLevel server) {
        inventory.extractItem(SLOT_STAR, 1, false);
        Block.popResource(server, worldPosition, new ItemStack(ModItems.DEAD_STAR.get()));
    }

    /** The overload running, if any. Read by the block's water check and by the client's shake. */
    public ReactorOverload.Kind overload() {
        return overload;
    }

    /** Ticks left on the fuse. For the debug command's readout. */
    public int fuse() {
        return fuse;
    }

    /** Ticks on the hour clock. For the debug command's readout. */
    public int hourTicks() {
        return hourTicks;
    }

    /** The block reads the signal and tells us; we decide what the state becomes. */
    public void setPowered(ServerLevel server, boolean signal) {
        if (this.powered == signal) {
            return;
        }
        this.powered = signal;
        refreshState(server);
    }

    /**
     * The single place the block state, the light, the registry and the sweep are all brought back
     * into agreement with the inventory and the signal.
     */
    private void refreshState(ServerLevel server) {
        int charge = charge();
        boolean charged = ReactorCharge.isCharged(charge);
        boolean lit = ReactorCharge.isActive(powered || debugActive, charge);

        // **The firestone fuse dies the moment its reason does.** The lever is the way out the
        // design asked for, and the charge falling back below forty is the other one (a firestone
        // taken back out). Either way the fuse is dropped whole, not paused: re-arming starts it
        // from three hundred again, which is what "se resetea, no se reanuda" means. The star's fuse
        // is deliberately not touched here -- a lever has no power over it.
        if (overload == ReactorOverload.Kind.FIRESTONE
                && (!lit || !ReactorOverload.detonates(charge))) {
            overload = ReactorOverload.Kind.NONE;
            fuse = 0;
        }

        boolean wasLit = lastLit;
        this.active = lit;
        lastLit = lit;
        // Edge-triggered, and suppressed for the first evaluation after a load: a reactor that was
        // already on when the world loaded must not announce that it just switched on.
        if (initialised && lit != wasLit) {
            play(server, lit ? ReactorSounds.UP : ReactorSounds.OFF,
                    ReactorOverload.REACTOR_VOLUME, 1.0F);
        }

        BlockState current = getBlockState();
        BlockState next = current
                .setValue(NetherReactorBlock.CHARGED, charged)
                .setValue(NetherReactorBlock.ACTIVE, lit)
                // **The star is a state and not a look-up.** The blockstate picks the texture pair
                // from it, so the overload has to reach the client as a property: the block entity
                // data is synced too, but a model is chosen from the state and from nothing else.
                .setValue(NetherReactorBlock.STAR, overload == ReactorOverload.Kind.STAR);
        if (!next.equals(current)) {
            // Flag 2 only: the state change goes to clients, but the reactor's own state change is
            // not a neighbour update for anything around it -- the sweep is what tells the field.
            server.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
        }

        // The emission may have moved with the charge, so the light is re-evaluated at this cell.
        server.getLightEngine().checkBlock(worldPosition);

        // **The radius only grows, and the reason is one sentence in the regression:** the star
        // publishes a flat hundred and the firestone at forty publishes sixty, so when the fuse is
        // cut the published radius fell back to the charge's curve, every wire past the smaller
        // number lost its membership, recomputed with `-1`, and `max(i, j)` never gives back a
        // point that was lost -- the line stayed dark until a lever was thrown, which swept the
        // area again. A wire this reactor once fed is a wire this reactor feeds.
        //
        // The lever is not a case of this: `lit` false publishes zero, which is a decision rather
        // than a shrink, and switching back on restores the mark in full.
        int newRadius = ReactorOverload.energyRadius(overload, lit, charge);
        if (newRadius > publishedRadius) {
            publishedRadius = newRadius;
        }
        int published = lit ? publishedRadius : 0;
        if (published > 0) {
            ActiveReactors.add(server, worldPosition, published);
        } else {
            ActiveReactors.remove(server, worldPosition);
        }
        // **And the sweep with the same number the registry was given.** A barrido bounded to the
        // charge would stop short of the wires the published radius still owns.
        ReactorScan.request(server, worldPosition, published);
        // **And the wired region with it.** The network is a pure function of the blocks around the
        // reactors and of which of them are lit, so any state change is a reason to recompute it --
        // it is the moment a line joins or leaves the reactor's power.
        ReactorNetwork.rebuild(server);

        setChanged();
        // The block state may not have changed at all (a second firestone is still "charged"), but
        // the charge did, and the client needs it for the light and the overlay. This is what ships
        // the block entity data alongside the state.
        server.sendBlockUpdated(worldPosition, next, next, Block.UPDATE_CLIENTS);
    }

    /**
     * The charge arrives with this packet, and <b>so has to the question "how bright is that
     * now?"</b> -- this override exists for that sentence alone.
     *
     * <p>The client recomputes light itself, and vanilla only re-evaluates a cell on a block state
     * whose light properties changed ({@code LevelChunk.setBlockState} gates its
     * {@code checkBlock} behind {@code hasDifferentLightProperties}, which compares the <em>static</em>
     * emission). A second firestone changes no state -- CHARGED stays true -- so the client kept
     * the first firestone's light forever: "se ilumina un poco al cargar y no sube con más", with
     * the server's own value rising correctly the whole time ({@code [luz] carga=13 -&gt; luz 13}
     * while the screen stayed at one). The charge rides in this packet; the re-evaluation has to
     * ride with it. Without this the light is a glowstone that only ever gets asked once.</p>
     */
    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (!tag.isEmpty()) {
            load(tag);
        }
        if (level != null) {
            level.getChunkSource().getLightEngine().checkBlock(worldPosition);
        }
    }

    /**
     * Nothing is overloading any more, and the fuse is empty. The caller is responsible for the sync.
     *
     * <p><b>And this is where the fuse's recording gets cut and the steam plays.</b> An interrupted
     * overload used to leave its long sample draining in the client's ears -- the reported "falta
     * cortar el sonido cuando interrumpes los procesos de sobrecarga" -- because nothing told the
     * sound engine to stop. The steam is the same cue the star's water-cut got, now shared by every
     * cut: one exit, one sound.</p>
     */
    private void endOverload() {
        overload = ReactorOverload.Kind.NONE;
        fuse = 0;
        if (level instanceof ServerLevel server) {
            stopSound(server, ReactorSounds.LOOP_ON);
            stopSound(server, ReactorSounds.OVERHEAT_FIRESTONE);
            stopSound(server, ReactorSounds.OVERHEAT_NETHERSTAR);
            server.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, SoundEvents.GENERIC_EXTINGUISH_FIRE,
                    SoundSource.BLOCKS, 1.0F, 0.8F);
        }
    }

    /**
     * The firestone's water-cut: fuse dropped whole, back to the normal ceiling, excess firestones
     * <b>dropped and not destroyed</b> -- water ends the emergency, it does not eat the fuel.
     */
    public boolean cancelFirestone(ServerLevel server, Player player, ItemStack bucket) {
        if (overload != ReactorOverload.Kind.FIRESTONE) {
            return false;
        }
        endOverload();
        if (player != null && bucket != null && !player.getAbilities().instabuild) {
            bucket.shrink(1);
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        }
        dropExcessFirestones(server);
        refreshState(server);
        return true;
    }

    /** The neighbour-water path for the firestone, mirroring {@link #cancelStar(ServerLevel)}. */
    public boolean cancelFirestone(ServerLevel server) {
        if (overload != ReactorOverload.Kind.FIRESTONE) {
            return false;
        }
        endOverload();
        dropExcessFirestones(server);
        refreshState(server);
        return true;
    }

    /** Clamps the slot back to {@link ReactorCharge#MAX_NORMAL} after a cut, spilling the rest. */
    private void dropExcessFirestones(ServerLevel server) {
        int extra = charge() - ReactorCharge.MAX_NORMAL;
        if (extra > 0) {
            inventory.extractItem(SLOT_FIRESTONE, extra, false);
            Block.popResource(server, worldPosition,
                    new ItemStack(ModItems.FIRESTONE.get(), extra));
        }
    }

    /**
     * Starts a fuse and plays its warning, once.
     *
     * <p>The single door both overloads go through, so the sound, the sync and the state change
     * cannot disagree about whether a fuse is running. The recording is fifteen seconds and the fuse
     * is fifteen seconds; see {@link ReactorOverload#FUSE_TICKS}.</p>
     */
    private void startFuse(ServerLevel server, ReactorOverload.Kind kind) {
        overload = kind;
        fuse = ReactorOverload.FUSE_TICKS;
        setChanged();
        play(server, kind == ReactorOverload.Kind.STAR
                ? ReactorSounds.OVERHEAT_NETHERSTAR
                : ReactorSounds.OVERHEAT_FIRESTONE, ReactorOverload.REACTOR_VOLUME, 1.0F);
        refreshState(server);
    }

    /**
     * Plays one of the reactor's named sounds at this block. Data-driven: no SoundEvent constant here.
     *
     * <p><b>The log line is the diagnosis, and it is permanent rather than behind {@code /cc trace}.</b>
     * A full sound pool writes no sound name -- only {@code Maximum sound pool size 247 reached} --
     * so the only way to know <em>which</em> event is flooding the pool is to print ours as we ask
     * for it. It costs one line per reactor sound, and a reactor asks every {@code LOOP_INTERVAL}
     * ticks, so a quiet reactor is a quiet log. What it settles: whether the pool is being filled by
     * this block (a {@code lit} that oscillates would show {@code reactor_up}/{@code reactor_off}
     * back to back) or by vanilla's own damage sounds under Revelacion.</p>
     */
    private void play(ServerLevel server, String name, float volume, float pitch) {
        SoundEvent event = CombatSounds.event(name);
        if (event == null) {
            return;
        }
        log.info("[sonido-reactor] '{}' volume={} pitch={}", name, volume, pitch);
        server.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, event, SoundSource.BLOCKS, volume, pitch);
        if (ReactorSounds.OFF.equals(name)) {
            // **An off-switch cannot un-play a sample already in the client's ears.** The loop and
            // the overheat recordings are long, `server.playSound` is fire-and-forget, and the
            // ticker has already stopped feeding them -- so without these packets a reactor that
            // is switched off or broken keeps audibly running until its last sample drains, which
            // is exactly the reported "falta cortar el sonido cuando se rompe o se apaga". The
            // client-side sound engine drops the source by event and category, position agnostic:
            // one reactor going quiet silences its sample everywhere it was playing.
            stopSound(server, ReactorSounds.LOOP_ON);
            stopSound(server, ReactorSounds.OVERHEAT_FIRESTONE);
            stopSound(server, ReactorSounds.OVERHEAT_NETHERSTAR);
        }
    }

    /** Fire-and-forget stop: the client's sound engine drops whatever it holds for this event. */
    private void stopSound(ServerLevel server, String name) {
        ClientboundStopSoundPacket packet =
                new ClientboundStopSoundPacket(ResourceLocation.parse(name), SoundSource.BLOCKS);
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5) <= 64.0D * 64.0D) {
                player.connection.send(packet);
            }
        }
    }

    /** One line per reactor sound, so a pool overflow can finally be attributed to an event. */
    private static final Logger log = LogUtils.getLogger();

    /** The clock: one firestone an hour, only while lit, plus the whole of phase two's show. */
    public void serverTick(ServerLevel server, BlockPos pos, BlockState state) {
        boolean lit = state.getValue(NetherReactorBlock.ACTIVE);

        switch (overload) {
            case STAR -> {
                if (tickStar(server, pos)) {
                    return;
                }
            }
            case FIRESTONE -> {
                if (!lit || !ReactorOverload.detonates(charge())) {
                    // The lever, or a firestone pulled back out. The fuse is dropped whole, so
                    // re-arming starts it from the top -- the design's "se resetea, no se reanuda".
                    endOverload();
                    refreshState(server);
                } else {
                    fuse--;
                    warningParticles(server, pos, true);
                    if (fuse <= 0) {
                        detonateFirestone(server, pos);
                        return;
                    }
                }
            }
            case NONE -> {
                if (lit && ReactorOverload.detonates(charge())) {
                    startFuse(server, ReactorOverload.Kind.FIRESTONE);
                } else if (lit && ReactorOverload.isWarning(charge())) {
                    warningParticles(server, pos, false);
                }
            }
        }

        if (!lit) {
            return;
        }
        if (charge() <= 0) {
            // Belt and braces: the state should already be off, but a clock must never run dry.
            refreshState(server);
            return;
        }
        // **Revelacion: the field does not only carry power, it burns everything hostile in it.**
        // The same radius the wire is answering for -- `publishedRadius` is what went to
        // ActiveReactors -- so the mark and the power always cover the same ground.
        applyRevelation(server, pos, publishedRadius);
        if (overload == ReactorOverload.Kind.NONE) {
            loopTicks++;
            if (loopTicks >= ReactorOverload.LOOP_INTERVAL) {
                loopTicks = 0;
                play(server, ReactorSounds.LOOP_ON, ReactorOverload.REACTOR_VOLUME, 1.0F);
            }
            ambientParticles(server, pos);
        }
        hourTicks++;
        if (hourTicks >= ReactorCharge.HOUR_TICKS) {
            hourTicks = 0;
            // Extraction runs onContentsChanged, which is what brings the state back in step.
            inventory.extractItem(SLOT_FIRESTONE, 1, false);
            setChanged();
        }
    }

    /**
     * The star's countdown, which ignores the lever by design.
     *
     * @return true when the reactor detonated and the caller must stop touching this block entity
     */
    private boolean tickStar(ServerLevel server, BlockPos pos) {
        fuse--;
        if (fuse <= 0) {
            detonateStar(server, pos);
            return true;
        }
        if (fuse % ReactorOverload.FUSE_PARTICLE_INTERVAL == 0) {
            starFuseParticles(server, pos);
        }
        if (fuse % ReactorOverload.RUMBLE_INTERVAL == 0) {
            // The rumble is vanilla's, chosen because it is a low thud with no melody: the star is
            // counting down and the ground has to say so, not a tune. Data-driven sounds are ours;
            // this one is the game's.
            server.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 3.0F, 0.6F);
        }
        return false;
    }

    /**
     * The firestone blast: remove the block, then explode.
     *
     * <p>{@link FirestoneBlock}'s own rule, applied here: a destroyed block is read at destruction
     * time, so removing the reactor first means the blast cannot find the block that caused it and
     * there is no loop to close. The contents are cleared before the removal so a nuclear reactor
     * does not litter the crater with the forty firestones it was fed.</p>
     */
    private void detonateFirestone(ServerLevel server, BlockPos pos) {
        endOverload();
        clearContents();
        setChanged();
        if (!server.getBlockState(pos).is(ModBlocks.NETHER_REACTOR.get())) {
            return;
        }
        server.removeBlock(pos, false);
        server.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                FirestoneBlock.BLAST_POWER, true, Level.ExplosionInteraction.BLOCK);
    }

    /**
     * The star blast: the sound and the wave, and the crater handed to {@link ReactorBlast}.
     *
     * <p><b>The one thing this does not do is call {@code explode}.</b> A point blast of this power
     * is unshippable (see {@link ReactorOverload}), so the crater is an explicit sphere paid for by
     * the tick. What is left here is what a sphere cannot do: the sound, the flash, and the bodies
     * inside the radius. The nuclear recording is played by hand and not through an explosion's
     * sound slot because vanilla caps an explosion's sound packet at sixty-four blocks.</p>
     */
    private void detonateStar(ServerLevel server, BlockPos pos) {
        endOverload();
        clearContents();
        setChanged();
        if (!server.getBlockState(pos).is(ModBlocks.NETHER_REACTOR.get())) {
            return;
        }
        server.removeBlock(pos, false);

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        play(server, ReactorSounds.NUCLEAR_EXPLOSION, ReactorOverload.NUCLEAR_VOLUME, 0.8F);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 4, 2.0, 2.0, 2.0, 0.0);
        server.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
        // **La ceniza del reventon, y la del crater no es esta.** Esta es la columna que levanta la
        // nuke en el instante en que se va; la que cubre el area entera la emite `ReactorBlast` con
        // cada frente de la onda, porque solo el frente sabe lo que ya arranco.
        server.sendParticles(ParticleTypes.ASH, cx, cy, cz, 140, 5.0, 3.0, 5.0, 0.06);
        // **Y el golpe de camara, que es lo unico que le dice al cuerpo "fue una nuke".**
        //
        // Va aca y no en `ReactorShake`: ese es el zumbido CONTINUO del reactor, que dura lo que dura
        // la sobrecarga y viaja por el block entity. Esto es el reventon, es un hecho puntual que no
        // tiene block entity detras -- el bloque se acaba de ir --, y ademas tiene que llegar
        // **mucho mas lejos** que una TNT: `ShakeKind.NUCLEAR` lleva 160 bloques de alcance contra los
        // 40 de una explosion comun, y el doble de amplitud. Ver `Shake`.
        com.cortes.combat.fx.Shake.around(server, cx, cy, cz,
                com.cortes.combat.fx.ShakeKind.NUCLEAR);
        damageAround(server, pos);
        ReactorBlast.request(server, pos, ReactorOverload.STAR_DESTRUCTION_RADIUS);
    }

    /**
     * Brands every hostile thing inside the field with Revelacion, once a second.
     *
     * <p>This decides <em>who</em> wears the mark; the effect does the killing. The box is the cube
     * around the reactor and the sphere test inside it mirrors {@link #damageAround} exactly: a box
     * is cheap to ask the server for, and the corners it over-reaches are thrown away by the same
     * distance check.</p>
     *
     * <p>Once a second, not every tick, because the effect already carries three seconds of mark --
     * re-branding every tick would be a packet storm over a badge that never lapses anyway.</p>
     */
    private static void applyRevelation(ServerLevel server, BlockPos pos, int radius) {
        if (radius <= 0 || server.getGameTime() % RevelationEffect.REFRESH_INTERVAL != 0L) {
            return;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        AABB box = new AABB(cx - radius, cy - radius, cz - radius,
                cx + radius, cy + radius, cz + radius);
        double reach = (double) radius * radius;
        for (Entity entity : server.getEntities((Entity) null, box, Entity::isAlive)) {
            if (!(entity instanceof LivingEntity mob)
                    || !RevelationEffect.isRevelable(mob)
                    || mob.distanceToSqr(cx, cy, cz) > reach) {
                continue;
            }
            mob.addEffect(new MobEffectInstance(
                    ModEffects.revelation(), RevelationEffect.DURATION_TICKS, 0, false, true, true));
        }
    }

    /** Bodies inside the crater: damage falling off with distance, launched outwards and set alight. */
    private static void damageAround(ServerLevel server, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double radius = ReactorOverload.STAR_DESTRUCTION_RADIUS;
        AABB box = new AABB(cx - radius, cy - radius, cz - radius,
                cx + radius, cy + radius, cz + radius);
        for (Entity entity : server.getEntities((Entity) null, box, Entity::isAlive)) {
            double distance = Math.sqrt(entity.distanceToSqr(cx, cy, cz));
            if (distance > radius) {
                continue;
            }
            float closeness = (float) (1.0 - distance / radius);
            entity.hurt(server.damageSources().explosion(null, null),
                    ReactorOverload.STAR_DAMAGE_AT_CENTER * closeness);
            entity.setRemainingFireTicks((int) (ReactorOverload.STAR_FIRE_SECONDS * 20.0F));
            Vec3 away = new Vec3(entity.getX() - cx, entity.getEyeY() - cy, entity.getZ() - cz);
            if (away.lengthSqr() > 1.0E-4) {
                Vec3 push = away.normalize().scale(ReactorOverload.STAR_KNOCKBACK * closeness);
                entity.setDeltaMovement(entity.getDeltaMovement()
                        .add(push.x, push.y + 0.2, push.z));
                entity.hurtMarked = true;
            }
        }
    }

    /** Empties both slots without dropping anything, so a detonation leaves nothing behind. */
    private void clearContents() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    /** The warning band and the firestone fuse, as smoke, steam and fire around the core. */
    private static void warningParticles(ServerLevel server, BlockPos pos, boolean fuse) {
        int interval = fuse
                ? ReactorOverload.FUSE_PARTICLE_INTERVAL
                : ReactorOverload.AMBIENT_PARTICLE_INTERVAL;
        if (server.getGameTime() % interval != 0L) {
            return;
        }
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;
        server.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, fuse ? 6 : 3,
                0.45, 0.35, 0.45, 0.01);
        server.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 3, 0.45, 0.25, 0.45, 0.01);
        server.sendParticles(ParticleTypes.FLAME, cx, cy, cz, fuse ? 4 : 2,
                0.35, 0.25, 0.35, 0.01);
    }

    /** A running reactor's ambient drift: a thin rising glow, not a per-tick packet. */
    private static void ambientParticles(ServerLevel server, BlockPos pos) {
        if (server.getGameTime() % ReactorOverload.AMBIENT_PARTICLE_INTERVAL != 0L) {
            return;
        }
        server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.8,
                pos.getZ() + 0.5, 2, 0.3, 0.35, 0.3, 0.01);
        server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6,
                pos.getZ() + 0.5, 1, 0.3, 0.2, 0.3, 0.0);
    }

    /** The star's countdown, denser than the firestone warning and with magic in it. */
    private static void starFuseParticles(ServerLevel server, BlockPos pos) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.0;
        double cz = pos.getZ() + 0.5;
        server.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 8, 0.6, 0.5, 0.6, 0.02);
        server.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 6, 0.6, 0.4, 0.6, 0.02);
        server.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 6, 0.6, 0.5, 0.6, 0.03);
        server.sendParticles(ParticleTypes.ASH, cx, cy, cz, 4, 0.6, 0.5, 0.6, 0.01);
    }

    /** Drops the contents where the block was, so breaking a charged reactor is not a loss. */
    public void dropContents(Level level) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // The block state is the authority on whether the block is lit; on the client there is no
        // refresh path, so it is read here rather than left at the default false.
        this.active = getBlockState().getValue(NetherReactorBlock.ACTIVE);
        if (level instanceof ServerLevel server) {
            // Recompute the signal from the world: a lever that was thrown while the chunk was
            // unloaded is not in any event this block entity ever saw.
            this.powered = server.hasNeighborSignal(worldPosition);
            refreshState(server);
        }
        // After the first refresh, so loading a world does not play the switch-on sound, and after
        // the data is read on the client, so the shake and the registry see the real overload.
        initialised = true;
        refreshClientRegistry();
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            ActiveReactors.remove(level, worldPosition);
            if (level.isClientSide()) {
                forgotOnTheClient();
            }
            // **Nothing else, and least of all getBlockState.** This runs on BOTH paths: a broken
            // block and a chunk that is merely unloading. The world's chunk state is not a safe
            // question here -- it resolves with requireChunk=true, which is a load asked of a
            // pipeline that is already being torn down, and the "Guardando mundo" freeze is exactly
            // that wait. "The block is gone" is not a thing a block entity can answer about itself
            // while it is being unloaded; the block answers it in onRemove instead.
        }
        super.setRemoved();
    }

    /**
     * The block itself was replaced -- broken, blown up or moved. <b>Not</b> called on a chunk
     * unload, because the block is still there in its data and re-registers on load.
     *
     * <p>The field it was lighting has to be swept so the components it powered turn off, and the
     * registry entry has to go. Called from {@code NetherReactorBlock.onRemove}, which is the one
     * place that knows the difference without asking a question this class cannot answer safely.</p>
     */
    public void onBlockRemoved(ServerLevel server) {
        ActiveReactors.remove(server, worldPosition);
        ReactorScan.request(server, worldPosition, publishedRadius);
        // **This, and not BreakEvent, is the reactor's break rebuild.** NeoForge fires the break
        // event (fireBlockBreak) before removeBlock, so a rebuild there sees this reactor still
        // standing and re-adds every wire as a member; here the block is already gone, the walk
        // runs before the neighbour cascade starts, and the severed network loses its immunity in
        // time for the wires to recompute with vanilla decay restored.
        ReactorNetwork.rebuild(server);
    }

    /**
     * Keeps the client's two side registries in step with what the server sent.
     *
     * <p>The client has no refresh path: it never runs {@code serverTick} and never computes the
     * state, so everything it knows about the overload arrives in {@code loadAdditional} and it has
     * to publish it to the redstone registry and to the camera shake from there. Guarded on
     * {@code isClientSide} so a dedicated server never even calls the method that names a client
     * class -- the reference is behind a second method on purpose, because a method that is invoked
     * is verified, and verifying a client-only reference on a server without {@code Minecraft} on
     * the classpath is a {@code NoClassDefFoundError}.</p>
     */
    private void refreshClientRegistry() {
        if (level == null || !level.isClientSide()) {
            return;
        }
        int radius = ReactorOverload.energyRadius(overload, active, charge());
        if (radius > 0) {
            ActiveReactors.add(level, worldPosition, radius);
        } else {
            ActiveReactors.remove(level, worldPosition);
        }
        publishShake();
    }

    /** The client-only half of {@link #refreshClientRegistry}, never reached on a server. */
    private void publishShake() {
        if (FMLEnvironment.dist.isClient()) {
            com.cortes.combat.client.ReactorShake.observe(level, worldPosition,
                    overload == ReactorOverload.Kind.STAR);
        }
    }

    /** Drops this reactor from the client's shake list. Only ever invoked on a client. */
    private void forgotOnTheClient() {
        if (FMLEnvironment.dist.isClient()) {
            com.cortes.combat.client.ReactorShake.forget(level, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putInt("hour_ticks", hourTicks);
        // The overload is saved so a reload does not hand out a free escape from a running fuse,
        // and it rides in getUpdateTag, which is what tells the client's camera to shake.
        tag.putString("overload", overload.name());
        tag.putInt("fuse", fuse);
        // **The published radius is saved, and it has to be.** A radius that only grows is a fact
        // about the wires in the world, not about the moment: without this, a reload starts the
        // reactor at the charge's curve, every wire beyond it loses its membership, and the
        // regression "the signal dipped and the lever fixed it" comes back after any restart.
        tag.putInt("radius", publishedRadius);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        hourTicks = tag.getInt("hour_ticks");
        overload = readKind(tag.getString("overload"));
        fuse = tag.getInt("fuse");
        publishedRadius = tag.getInt("radius");
        // The client may load this from the update packet rather than from disk, and either way it
        // is the one moment the overload arrives; see refreshClientRegistry.
        refreshClientRegistry();
    }

    /** Parses a saved overload name without throwing on data from an older build. */
    private static ReactorOverload.Kind readKind(String name) {
        for (ReactorOverload.Kind kind : ReactorOverload.Kind.values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return ReactorOverload.Kind.NONE;
    }

    // ------------------------------------------------------------------ the debug hooks

    /**
     * Sets the charge to an exact number, for the debug command.
     *
     * <p>Writes the slot directly, so it can reach forty where a hopper could not. Does not drop or
     * refund anything: this is a test hook and the tester asked for the number.</p>
     */
    public void setCharge(ServerLevel server, int value) {
        int clamped = ReactorCharge.clamp(value);
        inventory.setStackInSlot(SLOT_FIRESTONE, clamped == 0
                ? ItemStack.EMPTY
                : new ItemStack(ModItems.FIRESTONE.get(), clamped));
        setChanged();
        refreshState(server);
    }

    /** Holds the lever down (or lets it go) regardless of the world, for the debug command. */
    public void setDebugActive(ServerLevel server, boolean on) {
        this.debugActive = on;
        refreshState(server);
    }

    /** Adds ticks to the hour clock, so a tester does not wait an hour. */
    public void addClock(ServerLevel server, int ticks) {
        hourTicks += ticks;
        setChanged();
    }

    /**
     * Starts one of the overloads on demand, for the debug command.
     *
     * <p>{@code FIRESTONE} fills to the ceiling and holds the lever down first, because a fuse with
     * no reason would be cleared by the very next refresh; {@code STAR} drops the star in, which is
     * the real path; {@code NONE} ends whatever is running and drops the star back.</p>
     */
    public void forceOverload(ServerLevel server, ReactorOverload.Kind kind) {
        switch (kind) {
            case FIRESTONE -> {
                inventory.setStackInSlot(SLOT_FIRESTONE,
                        new ItemStack(ModItems.FIRESTONE.get(), ReactorCharge.MAX_OVERLOAD));
                debugActive = true;
                startFuse(server, ReactorOverload.Kind.FIRESTONE);
            }
            case STAR -> {
                inventory.setStackInSlot(SLOT_STAR, new ItemStack(Items.NETHER_STAR));
                startFuse(server, ReactorOverload.Kind.STAR);
            }
            case NONE -> {
                endOverload();
                refreshState(server);
            }
        }
    }

    /** The full data, so the client gets the charge it needs for the light and the overlay. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
