package com.cortes.combat.block;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every active reactor, by dimension, so a redstone query can answer "is this position powered by
 * one?" without walking the world.
 *
 * <h2>The shape, and why it is a static registry</h2>
 *
 * <p><b>The query is a hot path and it is not a block's method.</b> The hook is on
 * {@code SignalGetter.hasNeighborSignal}, which the game calls from every redstone component and
 * from the wire, on both logical sides, several times a tick. A block entity cannot answer it -- the
 * question is asked about a <em>different</em> position than the reactor -- so the reactor has to
 * publish its coverage somewhere the query can reach in a couple of instructions. This is that
 * somewhere.</p>
 *
 * <p><b>The fast path is the whole design.</b> The normal world has no active reactor at all, and
 * {@link #covers} answers that with one volatile read of {@link #total} and returns before touching
 * a map or a list. That is the cost paid by every redstone tick in every world that never builds
 * one of these.</p>
 *
 * <h2>Reads are lock-free, writes are serialised</h2>
 *
 * <p>The list per dimension is a {@link CopyOnWriteArrayList} and the map a
 * {@link ConcurrentHashMap}: an iteration sees a stable snapshot, so a redstone query running on
 * the server thread can never be corrupted by a reactor activating on the same thread in the middle
 * of the scan. Mutations take a small lock so {@link #total} -- the fast-path flag -- is exact
 * rather than approximate; the write path runs a handful of times per reactor, the read path
 * millions, so the asymmetry is the right one.</p>
 *
 * <p><b>Both logical sides register.</b> The server is the authority and is the side that matters
 * for redstone; the client keeps its own copy so a render or a prediction that asks the same
 * question gets the same answer. They are populated from the block entity on load and on every
 * state change, and the client's comes with the synced charge.</p>
 */
public final class ActiveReactors {

    private static final Logger log = LogUtils.getLogger();

    /** One active reactor: where it is and how far it reaches. */
    private record Reactor(BlockPos pos, int radius) {
    }

    private static final Object LOCK = new Object();

    private static final Map<ResourceKey<Level>, List<Reactor>> BY_LEVEL = new ConcurrentHashMap<>();

    /** The fast-path flag: how many active reactors exist anywhere. Zero is the normal case. */
    private static final AtomicInteger TOTAL = new AtomicInteger();

    private ActiveReactors() {
    }

    /** Publishes a reactor, replacing any previous entry for the same position. */
    public static void add(Level level, BlockPos pos, int radius) {
        if (radius <= 0) {
            remove(level, pos);
            return;
        }
        BlockPos immutable = pos.immutable();
        synchronized (LOCK) {
            List<Reactor> list = BY_LEVEL.computeIfAbsent(level.dimension(),
                    key -> new CopyOnWriteArrayList<>());
            list.removeIf(reactor -> reactor.pos().equals(immutable));
            list.add(new Reactor(immutable, radius));
            TOTAL.set(countAll());
        }
    }

    /** Removes a reactor. Idempotent: removing one that is not there is a no-op. */
    public static void remove(Level level, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        synchronized (LOCK) {
            List<Reactor> list = BY_LEVEL.get(level.dimension());
            if (list == null) {
                return;
            }
            boolean removed = list.removeIf(reactor -> reactor.pos().equals(immutable));
            if (list.isEmpty()) {
                BY_LEVEL.remove(level.dimension());
            }
            TOTAL.set(countAll());
            if (removed) {
                // **Who removed it is the whole question.** The regression "the signal dips during
                // an overload and the toggle fixes it" showed up in [red] as ONE rebuild where the
                // reactor being fed was simply absent between two consecutive feeds -- gone and
                // back within ~100 ms, with no lever event and no break. Every caller of remove is
                // a different bug, and this line names the one that ran. Removes are rare, so the
                // stack walk costs nothing.
                StackTraceElement caller = new Throwable().getStackTrace()[1];
                log.info("[red] reactor {} dado de baja en {} -- por {}",
                        immutable, level.dimension().location(),
                        caller.getClassName() + "." + caller.getMethodName());
            }
        }
    }

    /**
     * Whether any active reactor in this level covers this position.
     */
    public static boolean covers(Level level, BlockPos pos) {
        return covering(level, pos) != null;
    }

    /**
     * <p>The reactor covering this position, or {@code null}. <b>The one implementation of the distance
     * test</b>, shared with {@link #covers} so that "is this covered" and "who covers this" can never
     * drift into two copies of the same arithmetic -- and the answer a field update has to name as
     * its source, the way the sweep already does.</p>
     *
     * <p>Euclidean distance, block to block: the reactor's own cell is the centre and a position is
     * covered when it is within {@code radius} blocks. The comparison is done on squared integers,
     * so there is no square root on the hot path.</p>
     */
    public static BlockPos covering(Level level, BlockPos pos) {
        if (TOTAL.get() == 0) {
            return null;
        }
        List<Reactor> list = BY_LEVEL.get(level.dimension());
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (Reactor reactor : list) {
            // **A reactor never covers its own cell.** It is not a redstone component, and a
            // reactor that counted itself as covered would answer its own hasNeighborSignal with
            // true: it would latch itself on the first neighbour update and no lever could ever
            // switch it back off. The sweep's tag already excludes it for the same reason.
            if (pos.equals(reactor.pos())) {
                continue;
            }
            double dx = pos.getX() - reactor.pos().getX();
            double dy = pos.getY() - reactor.pos().getY();
            double dz = pos.getZ() - reactor.pos().getZ();
            double r = reactor.radius();
            if (dx * dx + dy * dy + dz * dz <= r * r) {
                return reactor.pos();
            }
        }
        return null;
    }

    /**
     * Forgets every reactor. <b>Called when the server stops.</b> The registry is static and keyed
     * by dimension, so entries from a world that closed would otherwise be inherited by the next
     * world loaded in the same process -- a reactor that is not there, powering blocks that are
     * not in its field.
     */
    /** One active reactor as the walk reads it: where it is and how far it reaches. */
    public record Entry(BlockPos pos, int radius) {
    }

    /**
     * Every active reactor in this level <b>with its radius</b>, for a rebuild of the wired region.
     *
     * <p><b>The radius travels with the position because the walk is bounded by it.</b>
     * Connectivity alone is charge-blind: a one-firestone reactor would claim every wire it can
     * reach, so a fresh one-charge reactor inherited the old ten-charge one's reach -- the reported
     * bug where "the cables detect ten charges" on a reactor holding one. The radius is the same
     * number the sweep uses, so membership and the sweep's reach are the same shape, which is what
     * lets an off-switch or a break notify every member exactly once.</p>
     *
     * <p>A fresh list rather than the live one on purpose: {@link ReactorNetwork#rebuild} walks for
     * a while, and a reactor activating mid-walk must not be seen half-registered. The copy is a
     * handful of references and the walk it feeds runs on events a player causes a few times a
     * minute.</p>
     */
    public static List<Entry> entries(Level level) {
        List<Reactor> list = BY_LEVEL.get(level.dimension());
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>(list.size());
        for (Reactor reactor : list) {
            out.add(new Entry(reactor.pos(), reactor.radius()));
        }
        return out;
    }

    public static void clear() {
        synchronized (LOCK) {
            BY_LEVEL.clear();
            TOTAL.set(0);
        }
    }

    /** For diagnostics and tests: the number of active reactors across every level. */
    public static int total() {
        return TOTAL.get();
    }

    /**
     * Whether a control <em>touching</em> the reactor is switched on, which is the only thing that
     * turns it off.
     *
     * <p><b>The switch is a control, not a signal, and that is the whole design.</b> The field feeds
     * redstone wire, so a wire beside the reactor reports fifteen -- its own power coming back as a
     * command. Asking {@code hasNeighborSignal} here would switch the reactor off with its own field;
     * the sweep would then report the wire to zero and switch it back on: a toggle every tick. Telling
     * the two apart is impossible, because a stored fifteen from the field and one from a lever are
     * the same number. So this does not ask "is there a signal" at all -- it asks "is one of my
     * neighbours a control a player can flip", and only {@link ReactorTags#SWITCH} can answer that. A
     * wire, a repeater or a comparator can be in the field's grip and never in the tag.</p>
     *
     * <p>Vanilla's own six-way loop, spelled out: the block at {@code pos.relative(d)}, then
     * {@code getSignal} on it. <b>No hook of ours is consulted on the way</b> -- the only one left is
     * on {@code getBestNeighborSignal}, which this never calls -- so the answer can only be the real
     * world.</p>
     */
    public static boolean switchSignal(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos here = pos.relative(direction);
            if (level.getBlockState(here).is(ReactorTags.SWITCH)
                    && level.getSignal(here, direction) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int countAll() {
        int sum = 0;
        for (List<Reactor> list : BY_LEVEL.values()) {
            sum += list.size();
        }
        return sum;
    }
}
