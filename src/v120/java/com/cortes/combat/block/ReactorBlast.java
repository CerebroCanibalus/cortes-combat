package com.cortes.combat.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The nether star's crater, queued and paid for a slice at a time.
 *
 * <h2>Why it is not one call</h2>
 *
 * <p>A power-120 explosion cannot run in a tick; {@link ReactorOverload} carries the measurement.
 * This is the replacement: an explicit sphere that is walked in cursor order, up to a fixed number
 * of block positions and block removals per tick, remembering where it was between ticks. The
 * crater is identical to a solid sphere of {@link ReactorOverload#STAR_DESTRUCTION_RADIUS}; only
 * the timing differs -- about four seconds of wave instead of one frozen frame.</p>
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><b>It removes blocks without drops and without chaining.</b> A nuclear core vaporises: nothing
 * to pick up, and no firestone caught in it detonates in turn, which is also the recursion guard --
 * the block that caused the blast is already gone and this never calls {@code explode}, so there is
 * no second blast to find it.</p>
 *
 * <p><b>It skips unloaded chunks and empty sections.</b> A crater that reaches past the simulation
 * distance simply stops at the edge of the loaded world; generating chunks to destroy them would be
 * a worse bug than a smaller crater. Empty sections are skipped whole so a blast mostly in the air
 * costs almost nothing.</p>
 *
 * <p><b>It leaves unbreakable blocks standing.</b> {@code defaultDestroyTime() < 0} is bedrock,
 * barriers, portal frames and command blocks -- the blocks the game itself refuses to let an
 * explosion remove.</p>
 */
public final class ReactorBlast {

    /** Server-thread only: {@link #request} and {@link #onServerTick} both run there. */
    private static final List<Blast> PENDING = new ArrayList<>();

    private ReactorBlast() {
    }

    /**
     * Queues a crater centred on a position, replacing any crater already queued for it.
     *
     * <p>One blast per centre: the reactor that would start a second one is gone, so a duplicate
     * request is a debug command firing twice rather than two real explosions, and starting fresh
     * is the right answer either way.</p>
     */
    public static void request(ServerLevel level, BlockPos center, int radius) {
        BlockPos immutable = center.immutable();
        PENDING.removeIf(blast -> blast.level == level && blast.center.equals(immutable));
        if (radius > 0) {
            PENDING.add(new Blast(level, immutable, radius));
        }
    }

    /** Whether a crater is still being carved. For diagnostics and the debug command. */
    public static boolean running() {
        return !PENDING.isEmpty();
    }

    /** Advances every pending crater within one shared budget. */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (PENDING.isEmpty()) {
            return;
        }
        Budget budget = new Budget(ReactorOverload.BLAST_POSITIONS_PER_TICK,
                ReactorOverload.BLAST_REMOVALS_PER_TICK);
        Iterator<Blast> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Blast blast = iterator.next();
            if (blast.advance(budget)) {
                iterator.remove();
            } else {
                blast.emitWave();
            }
        }
    }

    /** The mutable per-tick allowance shared by every crater. */
    private static final class Budget {
        int positions;
        int removals;

        Budget(int positions, int removals) {
            this.positions = positions;
            this.removals = removals;
        }
    }

    /** One crater's cursor. Mutated only on the server thread. */
    private static final class Blast {

        private final ServerLevel level;
        private final BlockPos center;
        private final int radius;
        private final int radiusSq;
        private final long volume;
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
        private long destroyed;
        private boolean done;

        Blast(ServerLevel level, BlockPos center, int radius) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.radiusSq = radius * radius;
            this.volume = Math.max(1L, ReactorOverload.sphereVolume(radius));
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

        /** Runs until the shared budget is spent or the sphere is finished. True when done. */
        boolean advance(Budget budget) {
            while (budget.positions > 0 && budget.removals > 0) {
                if (done) {
                    return true;
                }
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) {
                    if (nextChunk()) {
                        return true;
                    }
                    continue;
                }
                if (sectionY > maxSectionY) {
                    if (nextChunk()) {
                        return true;
                    }
                    continue;
                }
                LevelChunkSection section =
                        chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                if (section.hasOnlyAir()) {
                    sectionY++;
                    cell = 0;
                    continue;
                }

                int baseX = chunkX << 4;
                int baseZ = chunkZ << 4;
                int baseY = sectionY << 4;
                while (cell < 4096 && budget.positions > 0 && budget.removals > 0) {
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
                    if (state.isAir() || state.getBlock().defaultDestroyTime() < 0.0F) {
                        continue;
                    }
                    // **El agua se evapora, y `removeBlock` no la evapora.**
                    //
                    // `Level.removeBlock(pos, false)` escribe `fluidstate.createLegacyBlock()` en la
                    // posicion -- o sea "saca el bloque pero deja el fluido que habia adentro", que es
                    // exactamente lo que hace falta para romper una escalera inundada y exactamente lo
                    // contrario de lo que hace una nuke. Sobre agua es un no-op: la lee, la escribe de
                    // vuelta y la cuenta como destruida, consumiendo presupuesto. Un crater bajo el mar
                    // se labraba y se volvia a llenar solo, sin un solo error en el log.
                    //
                    // Una nuke vaporiza: la fuente y el flujo, los dos. `setBlock` con aire lo hace en
                    // una escritura y ademas borra el bloque mojado entero, que es lo que el crater
                    // pide.
                    BlockPos target = new BlockPos(x, y, z);
                    if (state.getFluidState().isEmpty()) {
                        level.removeBlock(target, false);
                    } else {
                        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                    }
                    destroyed++;
                    budget.removals--;
                }
                if (cell >= 4096) {
                    sectionY++;
                    cell = 0;
                }
            }
            return done;
        }

        /** A ring of smoke at the growing frontier, so the blast reads as a wave and not a pop. */
        void emitWave() {
            double fraction = Math.min(1.0, (double) destroyed / (double) volume);
            double wave = radius * Math.cbrt(fraction);
            if (wave < 0.75) {
                return;
            }
            double cx = center.getX() + 0.5;
            double cy = center.getY() + 0.5;
            double cz = center.getZ() + 0.5;
            level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 30,
                    wave * 0.6, wave * 0.6, wave * 0.6, 0.01);
            // **Y la ceniza, que es lo que queda cuando el humo se va.**
            //
            // El humo es blanco y sube; la ceniza es gris y cae despacio, asi que dos emisiones en el
            // mismo lugar leen como dos cosas distintas: la onda que pasa y lo que deja detras. Y sale
            // con el FRENTE y no del centro -- el frente es lo unico que sabe el area que ya arranco,
            // asi que la nube cubre el crater a medida que crece en vez de quedarse en el medio.
            level.sendParticles(ParticleTypes.ASH, cx, cy, cz, 24,
                    wave * 0.75, wave * 0.45, wave * 0.75, 0.02);
            if ((level.getGameTime() & 3L) == 0L) {
                level.sendParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 2,
                        wave * 0.5, wave * 0.5, wave * 0.5, 0.0);
            }
        }

        /** Moves to the next chunk column, or reports the sphere finished. */
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
