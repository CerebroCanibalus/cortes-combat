package com.cortes.combat.block;

import com.cortes.combat.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-evaluates the redstone components a reactor covers, a slice per tick.
 *
 * <h2>The problem it solves</h2>
 *
 * <p>The hook on {@code hasNeighborSignal} makes a component <em>see</em> the reactor's power, but
 * a component only asks that question when something tells it a neighbour changed. Activating a
 * reactor changes no neighbour -- the reactor's own block state flips and nothing else -- so without
 * this scan the lamps inside the radius would stay off until somebody happened to touch them. The
 * scan is what makes "flip the lever and the field lights up" true.</p>
 *
 * <h2>Why it is budgeted and resumable, and not one loop</h2>
 *
 * <p><b>A thirty-charge reactor covers a cube about one hundred and eighty blocks on a side.</b>
 * That is roughly twelve million positions, and a section is four thousand and ninety-six of them,
 * so a single pass over the volume would stall the server for seconds and trip the watchdog. The
 * scan therefore advances a fixed number of positions per tick and remembers where it was, so a big
 * reactor takes a second or two to settle and the tick never notices.</p>
 *
 * <p><b>It skips work in three places.</b> A chunk that is not loaded is skipped whole -- the
 * components in it are not ticking anyway, and the reactor's power is published, so they will see it
 * the moment they load. A section that is all air is skipped whole
 * ({@code LevelChunkSection.hasOnlyAir}). And a position outside the radius is skipped before its
 * block is even read. Only the survivors pay a {@code neighborChanged} call.</p>
 *
 * <h2>What forces the re-evaluation, and why</h2>
 *
 * <p>{@code ServerLevel.neighborChanged(pos, block, fromPos)} -- read out of the 1.21.1 sources, not
 * guessed. {@code ServerLevel} overrides the empty {@code Level} method and hands the call to its
 * {@code NeighborUpdater}; the {@code SimpleNeighborUpdate} path then runs
 * {@code BlockState.handleNeighborChanged} on the block at {@code pos}, which is exactly the block's
 * own {@code neighborChanged}. That is the call a lamp answers by asking
 * {@code level.hasNeighborSignal(pos)} -- the field's outlet: whose answer now comes from the wire the hook feeds. It is the same
 * call vanilla makes when a lever flips, so nothing about the component's own logic is bypassed.</p>
 *
 * <p><b>{@code updateNeighborsAt} is the wrong call and the difference is one word.</b> It notifies
 * the six neighbours <em>of</em> a position that that position changed; the scan needs the block at
 * a position to re-check <em>itself</em>, which is {@code neighborChanged}. Calling the first would
 * light up the blocks around each lamp and leave the lamp dark.</p>
 *
 * <p><b>The scan runs on activate, on deactivate and on a radius change, never every tick.</b> A
 * component placed inside a live reactor needs no scan: placing it runs its own
 * {@code neighborChanged}, which asks about its own wire and gets the right answer. Only a change
 * that no component can observe needs the sweep.</p>
 */
public final class ReactorScan {

    /**
     * Positions examined per server tick, across every pending scan together.
     *
     * <p>Forty thousand is about ten chunk sections. It is a deliberately small slice: the scan is a
     * background settle, not a deadline, and a reactor is expected to be switched on by a lever the
     * player is standing at, so a second of lag is invisible. <b>Adjustable to playtest:</b> lower
     * it if a large reactor is felt as a tick spike, raise it if switching on feels sluggish.</p>
     */
    public static final int POSITIONS_PER_TICK = 40_000;

    /**
     * Neighbour updates fired per server tick, across every pending scan together.
     *
     * <p>A position budget alone is not enough: a dense field of redstone wire can pass the tag
     * filter at almost every position, and each {@code neighborChanged} can cascade through the
     * neighbour updater. This second budget bounds the cascade independently of how many positions
     * were merely examined.</p>
     */
    public static final int UPDATES_PER_TICK = 256;

    /** Server-thread only: {@link #request} and {@link #onServerTick} both run there. */
    private static final List<Scan> PENDING = new ArrayList<>();

    private ReactorScan() {
    }

    /**
     * Drops every pending sweep. <b>Called when the server stops, and it has to be.</b>
     *
     * <p>{@code PENDING} is static, so a sweep left over when a world closes would outlive it and
     * keep a reference to a dying {@code ServerLevel}. The next world's ticks would then advance a
     * cursor against a level that is being torn down -- which is the freeze on the "Saving world"
     * screen, because the save is exactly when a pending sweep is still being ticked.</p>
     */
    public static void clear() {
        PENDING.clear();
    }

    /**
     * Queues a sweep centred on a reactor, replacing any sweep already queued for it.
     *
     * <p>One sweep per reactor: a second request for the same position -- a radius change, say --
     * discards the half-finished one and starts fresh, because the old one was walking the old
     * radius.</p>
     */
    public static void request(ServerLevel level, BlockPos center, int radius) {
        BlockPos immutable = center.immutable();
        PENDING.removeIf(scan -> scan.level == level && scan.center.equals(immutable));
        if (radius > 0) {
            PENDING.add(new Scan(level, immutable, radius));
        }
    }

    /**
     * Advances every pending sweep within one shared budget.
     *
     * <p><b>Iterated over a snapshot, with removals by identity afterwards -- and that is not
     * paranoia.</b> {@link Scan#advance} hands a position to {@code neighborChanged}, which can walk
     * all the way back to a reactor: {@code setPowered} -> {@code refreshState} -> {@code request}
     * -> {@code PENDING.removeIf}. Mutating the list under its own iterator turns into a
     * {@code ConcurrentModificationException} in the tick loop and takes the server down with it,
     * which is exactly what the stack trace said, at the {@code remove()} on line 126. A snapshot
     * lets the re-entrant request do its work -- the fresh scan it queues simply starts next tick --
     * while this one finishes the volume it was already walking.</p>
     */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (PENDING.isEmpty()) {
            return;
        }
        Budget budget = new Budget(POSITIONS_PER_TICK, UPDATES_PER_TICK);
        List<Scan> snapshot = new ArrayList<>(PENDING);
        for (Scan scan : snapshot) {
            if (budget.positions <= 0 || budget.updates <= 0) {
                break;
            }
            if (scan.advance(budget)) {
                PENDING.removeIf(pending -> pending == scan);
            }
        }
    }

    /**
     * Re-evaluates a component the moment a player drops it inside a live field.
     *
     * <p><b>The sweep runs on activate, deactivate and radius change -- never on placement.</b> Most
     * components answer for themselves when they land (a lamp asks in {@code getStateForPlacement},
     * a piston in {@code onPlace}), but not all of them do, and "placed after activation and it
     * never lit" was the report. This closes that hole with the same call the sweep makes and at the
     * same cost: one position, and only if the block is in the powerable tag and the field really
     * covers it -- {@link ActiveReactors#covering} gives back the reactor to name as the source, so
     * the update looks exactly like one the sweep fired.</p>
     *
     * <p>{@code EntityMultiPlaceEvent} extends this event, so placing a stack of lamps is covered
     * too. The {@code ServerLevel} check makes the client's copy of the same event a no-op rather
     * than a second answer from a registry that has no authority.</p>
     */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel server)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!event.getPlacedBlock().is(ReactorTags.POWERABLE)) {
            return;
        }
        BlockPos center = ActiveReactors.covering(server, pos);
        if (center == null) {
            return;
        }
        server.neighborChanged(pos, ModBlocks.NETHER_REACTOR.get(), center);
        // A new wire can join the network the moment it lands, and only a rebuild can know that:
        // connectivity is a question about the whole line, not about this block.
        ReactorNetwork.rebuild(server);
    }

    /**
     * The mirror of {@link #onBlockPlace}: a wire broken is a wire that must stop drawing free
     * strength.
     *
     * <p>Player breaks only -- an explosion or a piston do not fire this event, which is the known
     * limit of the refresh scheme: a line destroyed that way keeps its membership until the next
     * state change or placement. Membership is only ever a missing {@code -1}, so the failure is a
     * line that stays strong a little longer, never a line that lies about being zero, and the
     * gate cases all read zero from their sources.</p>
     */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel server)) {
            return;
        }
        // **No tag filter, and that omission is the bug this fixed.** The first version only
        // rebuilt when a POWERABLE block broke -- and the reactor itself is deliberately NOT in
        // that tag (it would sweep itself forever), so breaking the reactor skipped the rebuild,
        // the severed wires kept their membership, and the mixin kept removing their decay with
        // nothing left to feed them: redstone that stays on until the world reloads.
        ReactorNetwork.rebuild(server);
    }

    /** The mutable per-tick allowance shared by every scan. */
    private static final class Budget {
        int positions;
        int updates;

        Budget(int positions, int updates) {
            this.positions = positions;
            this.updates = updates;
        }
    }

    /** One sweep's cursor. Mutated only on the server thread. */
    private static final class Scan {

        private final ServerLevel level;
        private final BlockPos center;
        private final int radiusSq;
        private final int minChunkX;
        private final int maxChunkX;
        private final int minChunkZ;
        private final int maxChunkZ;
        private final int minSectionY;
        private final int maxSectionY;

        private int chunkX;
        private int chunkZ;
        private int sectionY;
        private int cell;
        private boolean done;

        Scan(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = center;
            this.radiusSq = radius * radius;
            this.minChunkX = (center.getX() - radius) >> 4;
            this.maxChunkX = (center.getX() + radius) >> 4;
            this.minChunkZ = (center.getZ() - radius) >> 4;
            this.maxChunkZ = (center.getZ() + radius) >> 4;
            // getMaxSection() is exclusive, so the last valid section is one below it.
            int lowest = Math.max(level.getMinSection(), (center.getY() - radius) >> 4);
            int highest = Math.min(level.getMaxSection() - 1, (center.getY() + radius) >> 4);
            this.minSectionY = lowest;
            this.maxSectionY = highest;
            this.chunkX = minChunkX;
            this.chunkZ = minChunkZ;
            this.sectionY = lowest;
            this.cell = 0;
            this.done = highest < lowest;
        }

        /** Runs until the shared budget is spent or the volume is finished. Returns true when done. */
        boolean advance(Budget budget) {
            while (budget.positions > 0 && budget.updates > 0) {
                if (done) {
                    return true;
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    // **A skipped column still costs the budget.** Without this the loop walks
                    // every unloaded column in a single tick without ever yielding, and a large
                    // reactor's radius is mostly unloaded chunks -- which is the watchdog freeze.
                    budget.positions--;
                    if (nextChunk()) {
                        return true;
                    }
                    continue;
                }
                if (sectionY > maxSectionY) {
                    budget.positions--;
                    if (nextChunk()) {
                        return true;
                    }
                    continue;
                }
                LevelChunkSection section =
                        chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                if (section.hasOnlyAir()) {
                    budget.positions--;
                    sectionY++;
                    cell = 0;
                    continue;
                }

                int baseX = chunkX << 4;
                int baseZ = chunkZ << 4;
                int baseY = sectionY << 4;
                while (cell < 4096 && budget.positions > 0 && budget.updates > 0) {
                    int localX = cell & 15;
                    int localZ = (cell >> 4) & 15;
                    int localY = (cell >> 8) & 15;
                    cell++;
                    budget.positions--;

                    int x = baseX + localX;
                    int y = baseY + localY;
                    int z = baseZ + localZ;
                    int dx = x - center.getX();
                    int dy = y - center.getY();
                    int dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }

                    BlockState state = section.getBlockState(localX, localY, localZ);
                    if (state.is(ReactorTags.POWERABLE)) {
                        level.neighborChanged(new BlockPos(x, y, z),
                                ModBlocks.NETHER_REACTOR.get(), center);
                        budget.updates--;
                    }
                }
                if (cell >= 4096) {
                    sectionY++;
                    cell = 0;
                }
            }
            return done;
        }

        /** Moves to the next chunk column, or reports the sweep finished. */
        private boolean nextChunk() {
            chunkZ++;
            if (chunkZ > maxChunkZ) {
                chunkZ = minChunkZ;
                chunkX++;
            }
            if (chunkX > maxChunkX) {
                done = true;
                return true;
            }
            sectionY = minSectionY;
            cell = 0;
            return false;
        }
    }
}
