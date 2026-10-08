package com.cortes.combat.block;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which wire positions are <b>wired</b> to a running reactor, so the wire mixin can drop the
 * per-hop decay on them and nowhere else.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>The redesign's promise is "circuits as long as you like without repeaters" <em>and</em>
 * "connected -- but only as far as the reactor reaches". Redstone's decay is one line,
 * {@code return Math.max(i, j - 1)} in {@code RedStoneWireBlock.calculateTargetStrength}, and
 * {@code j} is the neighbouring wire's stored power. Removing the {@code -1} only for wires that
 * draw from the reactor gives long runs; leaving it everywhere else leaves the rest of the world
 * untouched. Knowing which wires those are is a reachability question, and reachability is what
 * this class computes.</p>
 *
 * <h2>Connected, and inside the radius -- the bound that fixed two reports at once</h2>
 *
 * <p>The walk used to be bounded by connectivity alone, and connectivity is <b>charge-blind</b>:
 * a reactor holding one firestone claimed every wire it could reach, so a fresh one-charge reactor
 * inherited the old ten-charge one's reach ("the cables detect ten charges on a reactor with
 * one"). Worse, membership could stretch far past the radius the sweep covers, so an off-switch
 * notified the wires it could reach and left the far ones -- members, still immune to decay -- at
 * their stored fifteen forever: the reported redstone that stays on until the world reloads.
 * Bounding the walk by the reactor's radius fixes both with one number, because <b>membership then
 * never pokes outside the region the sweep will notify</b>: every member hears the switch,
 * recomputes with vanilla decay restored, and goes dark.</p>
 *
 * <h2>Why a walk, and why the walk is dumb</h2>
 *
 * <p><b>The walk does not simulate redstone, and that is deliberate.</b> It never asks whether a
 * repeater is powered or a torch is on, because the answer does not change what this set is for:
 * membership only removes the {@code -1}, and the {@code -1} is applied to a value the network
 * <em>already</em> has. A gate decides zero-or-not from its sources, so a NOT gate with its torch
 * dark still computes zero here and the gate still works -- which is exactly the property that
 * made {@code max(i, j)} the right choice over "force fifteen for members".</p>
 *
 * <p>It traverses what vanilla lets power through: wire, repeaters, comparators, torches and the
 * redstone block. A lamp does not conduct in vanilla and does not conduct here either -- it is an
 * endpoint, powered by the wire touching it. <b>Known limit:</b> a wire whose only path runs
 * through a solid block (the vertical "wire on top of a block" trick) is not joined, and keeps its
 * decay. That shape costs a repeater until a playtest says it matters.</p>
 *
 * <h2>Server only, and why that is not a compromise</h2>
 *
 * <p>Read the three {@code !level.isClientSide} guards in {@code RedStoneWireBlock}: the wire's
 * level is computed on the server and arrives on the client as a synced block state. There is no
 * second computation to keep in step, so there is no second copy of this set to maintain -- which
 * removed the biggest structural risk the redesign had before a line of it was written.</p>
 *
 * <p>Refreshed on a state change, on a block placed or broken nearby, and on a slow timer as a
 * safety net; cleared when the server stops, because it is static and statics outlive worlds.</p>
 */
public final class ReactorNetwork {

    /** One line per rebuild, because "did the set clear?" is answerable no other way. */
    private static final Logger log = LogUtils.getLogger();

    /**
     * Safety cap on one walk. A base's dust is thousands of nodes, not tens of thousands; the cap
     * exists so a pathological loop of wire cannot park the server in a BFS during a block update.
     * <b>Adjustable to playtest:</b> raise it if a very large build keeps part of its dust decayed.
     */
    public static final int MAX_NODES = 50_000;

    private static final Map<ResourceKey<Level>, Set<BlockPos>> WIRE_BY_LEVEL = new ConcurrentHashMap<>();

    private ReactorNetwork() {
    }

    /** Whether this position draws from some running reactor's network. Hot: one map lookup. */
    public static boolean contains(Level level, BlockPos pos) {
        Set<BlockPos> wire = WIRE_BY_LEVEL.get(level.dimension());
        return wire != null && wire.contains(pos.immutable());
    }

    /**
     * The wire currently being computed, as a yes-or-no, held for the length of one call.
     *
     * <p><b>Not gameplay state, and the distinction is the whole defence.</b> Nothing in the game
     * reads this to decide anything -- it carries one value between a method's own head and its
     * return, because the compiled wire class has no {@code LocalVariableTable} and the position
     * cannot be handed to the redirect any other way. Vanilla does the same thing with
     * {@code RedStoneWireBlock.shouldSignal} for the same reason. It is written and read on the
     * server thread only, which is the only place {@code calculateTargetStrength} runs.</p>
     */
    private static boolean pending;

    /** Called at the head of the wire's level computation. */
    public static void begin(Level level, BlockPos pos) {
        pending = contains(level, pos);
    }

    /** Whether the wire in flight should stop losing a unit per hop. */
    public static boolean pendingNoDecay() {
        return pending;
    }

    /** Called on the way out, whatever happened: the scratch must not outlive one call. */
    public static void end() {
        pending = false;
    }

    /**
     * Recomputes this level's whole wired region from scratch, from every running reactor.
     *
     * <p>From scratch rather than incrementally: the walk is bounded by {@link #MAX_NODES} and runs
     * on events that a player causes a few times a minute, so the simple version is both correct
     * and unmeasurable. One reactor is the usual case; several share the same budgeted pass.</p>
     */
    public static void rebuild(ServerLevel level) {
        Set<BlockPos> wire = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        List<ActiveReactors.Entry> all = ActiveReactors.entries(level);
        StringBuilder registryDetail = new StringBuilder("[");
        // Reactors are few and their positions are already published; a stale entry is dropped by
        // the lit check below rather than by consulting block entities.
        for (ActiveReactors.Entry entry : all) {
            BlockPos origin = entry.pos();
            boolean active = level.getBlockState(origin).getValue(NetherReactorBlock.ACTIVE);
            // The detail that turns "0 cables" into an answer: zero because nothing is registered
            // (removed) reads nothing like zero because one is registered but not lit (state never
            // flipped), and the bare count could not tell those apart.
            registryDetail.append(origin.toShortString()).append(" radio=")
                    .append(entry.radius()).append(active ? " ACTIVO" : " apagado").append("; ");
            if (!active) {
                continue;
            }
            // **The radius is the bound, and it is one number for two bugs.** Connectivity alone
            // is charge-blind: one firestone would claim the run a hundred had earned. And
            // membership outside the radius is membership the sweep can never notify when the
            // reactor switches off, which is how a severed line held its stored fifteen forever.
            long radiusSquared = (long) entry.radius() * entry.radius();
            Deque<BlockPos> queue = new ArrayDeque<>();
            for (Direction direction : Direction.values()) {
                BlockPos start = origin.relative(direction);
                if (within(origin, start, radiusSquared)) {
                    queue.add(start);
                }
            }
            Set<BlockPos> seen = new HashSet<>();
            int budget = MAX_NODES;
            while (!queue.isEmpty() && budget-- > 0) {
                BlockPos pos = queue.poll().immutable();
                if (!seen.add(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(cursor.set(pos));
                if (state.is(Blocks.REDSTONE_WIRE)) {
                    wire.add(pos);
                }
                if (!conducts(state)) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (!seen.contains(next) && within(origin, next, radiusSquared)) {
                        queue.add(next);
                    }
                }
            }
        }
        if (wire.isEmpty()) {
            WIRE_BY_LEVEL.remove(level.dimension());
        } else {
            WIRE_BY_LEVEL.put(level.dimension(), wire);
        }
        // **The instrument this feature was missing, now saying who and from where.** Whether the
        // set clears on a lever and on a break is the whole question behind "the redstone stays on
        // forever", and a bare count hides the two ways it can read zero: no reactor registered
        // (removed) versus one registered but not lit (state never flipped). One line per real
        // state change, same deal as `[sonido-reactor]`.
        log.info("[red] rebuild en {}: {} cables (reactores visibles con radio) {}",
                level.dimension().location(), wire.size(), registryDetail.append(']'));
    }

    /**
     * Squared distance from the reactor, the same integer test {@code ActiveReactors} uses for
     * coverage: one definition of "inside the field", so membership and coverage cannot drift.
     */
    private static boolean within(BlockPos origin, BlockPos pos, long radiusSquared) {
        long dx = pos.getX() - origin.getX();
        long dy = pos.getY() - origin.getY();
        long dz = pos.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    /**
     * Whether power crosses this block in vanilla, which is the whole rule of the walk.
     *
     * <p>Wire, repeater, comparator, torch and the redstone block: everything that carries a signal
     * onward. Components are absent on purpose -- a lamp is where a line ends, and letting the walk
     * through it would weld two unrelated circuits together through somebody's light.</p>
     */
    private static boolean conducts(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE)
                || state.is(Blocks.REPEATER)
                || state.is(Blocks.COMPARATOR)
                || state.is(Blocks.REDSTONE_TORCH)
                || state.is(Blocks.REDSTONE_WALL_TORCH)
                || state.is(Blocks.REDSTONE_BLOCK);
    }

    /**
     * Forgets this level. <b>Called when the server stops</b>, next to the other two statics: a set
     * keyed by dimension would otherwise be inherited by the next world loaded in this process, and
     * the wires of a world that no longer exists would quietly stop decaying.
     */
    public static void clear() {
        WIRE_BY_LEVEL.clear();
    }
}
