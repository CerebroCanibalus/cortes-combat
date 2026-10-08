package com.cortes.combat.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mod's first block: a block of firestone, and it is unstable.
 *
 * <p><b>It glows and it does not burn.</b> Light ten is the design's number, and the block is
 * deliberately <em>not</em> a fire source: it never lights anything, never spreads flame, and never
 * counts as flammable. It only <em>reacts</em> to the fire around it, which is the difference
 * between a fire hazard and a bomb.</p>
 *
 * <h2>The two reactions, and the different hooks they need</h2>
 *
 * <p><b>Fire detonates it, and the hook is {@code neighborChanged}.</b> When a flame appears or
 * spreads into a cell next to this block, the world fires a neighbour update on it, and the update
 * names the cell that changed. That is immediate and reliable, where {@code randomTick} would add a
 * delay the player reads as "it did not work". {@code onPlace} is checked too, because a block
 * placed next to a flame that was <em>already there</em> gets no neighbour update for it -- the
 * world does not tell a block about the neighbours it inherited.</p>
 *
 * <p><b>Water hardens it, and it is the same two hooks.</b> Water that touches it becomes obsidian
 * -- source or flowing, checked as {@code Blocks.WATER}, which in 1.21 is the one block both of
 * those states are. That is the obsidian farm: a flowing edge becomes obsidian, and the source
 * behind it refills the gap when the obsidian is mined. A source directly against the block hardens
 * too, which is the literal rule, and a farm that keeps its source one cell back still works because
 * the flowing frontier that actually touches the block is what changes.</p>
 *
 * <h2>The recursion, which is the whole of the explosion's difficulty</h2>
 *
 * <p>An explosion of power eight that breaks the block that caused it is a loop, and it has to be
 * closed on purpose. Two facts from the runtime decide how.</p>
 *
 * <p><b>One: a destroyed block is read at destruction time, not at blast time.</b>
 * {@code Explosion.finalizeExplosion} walks the positions it decided to blow up and calls
 * {@code state.onExplosionHit(..)} on <em>the current state</em> of each; a position that is already
 * air is skipped by the very first guard in that method. So the guard chosen here is
 * {@link #detonate}: <b>remove the block first, then explode.</b> The blast cannot find this block,
 * because by the time it looks there is nothing there.</p>
 *
 * <p><b>Two: {@code wasExploded} fires for a block that is already gone.</b> NeoForge's
 * {@code BlockState.onBlockExploded} sets the position to air and then calls {@code wasExploded} --
 * so when another blast catches this block, the block has already been removed by that blast, and
 * exploding again cannot re-catch it. That is why the explosion path calls {@link #explode} directly
 * and the fire path goes through {@link #detonate}: the ban on exploding twice lives with the block
 * that is still standing, and nothing needs to live with the one that is not.</p>
 *
 * <p><b>A {@code ThreadLocal} guard was considered and rejected.</b> It would suppress every
 * explosion started on the same thread, which is exactly how a legitimate chain of these blocks is
 * denied its second link -- and explosions nest on one thread by design (TNT chains, a block hit by
 * a blast that then explodes). The world's own state -- is this block still here -- is the guard
 * that is per-position instead of per-thread, and it is the one that keeps chains and kills the
 * loop.</p>
 *
 * <h2>The one reaction that waits a tick, and why</h2>
 *
 * <p><b>{@code onPlace} does not detonate; it schedules a tick.</b> Removing this block while its
 * own placement is still on the stack is a reentrant write to the very position being written, and
 * the safe place to do that is the next tick. Water is different -- it is a <em>neighbour</em>, and
 * vanilla's own sponge mutates neighbours from {@code onPlace} -- so water hardens at once. A flame
 * that spreads in later goes through {@code neighborChanged}, where there is no placement under
 * it, and detonates immediately.</p>
 */
public class FirestoneBlock extends Block {

    /**
     * Twice a TNT blast.
     *
     * <p>TNT is four; {@code createFire = true} is passed with it, so the crater is also set
     * alight. The design drew two TNT and the number is the design's, not a balance pass.</p>
     */
    public static final float BLAST_POWER = 8.0F;

    public FirestoneBlock(Properties properties) {
        super(properties);
    }

    /**
     * A block placed against fire or water is checked here, and <b>this hook exists for the case a
     * neighbour update cannot cover</b>: the flame or the water was already there before the block
     * was. Flame waits a tick; water is hardened on the spot.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (fireAround(server, pos)) {
            server.scheduleTick(pos, this, 1);
            return;
        }
        hardenWaterAround(server, pos);
    }

    /**
     * The reaction to <em>the cell that changed</em>, and not a scan of all six.
     *
     * <p>Scanning would re-run the water rule on every unrelated neighbour update near a block that
     * is already hardened, and it would make a block with water on several faces write each face
     * again from inside the neighbour update the previous write caused. The changed cell is the
     * only one that can have become fire or water, so it is the only one asked about.</p>
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, movedByPiston);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        BlockState changed = server.getBlockState(fromPos);
        if (changed.is(BlockTags.FIRE)) {
            detonate(server, pos);
            return;
        }
        if (changed.is(Blocks.WATER)) {
            // **The cell that changed, and not the whole column.** Source or flowing are both
            // `minecraft:water` in 1.21, so this one check covers both, and a waterlogged block --
            // whose own state is not water even though its fluid state is -- is left alone.
            harden(server, fromPos);
        }
    }

    /** The deferred half of {@link #onPlace}: a flame that was already there when the block was. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.is(this) && fireAround(level, pos)) {
            detonate(level, pos);
        }
    }

    /**
     * And when another explosion breaks this block, that block detonates in turn.
     *
     * <p><b>{@link #explode} and not {@link #detonate}.</b> By the time this runs the blast has
     * already removed the block -- {@code onBlockExploded} sets the position to air and then calls
     * this -- so there is nothing left to remove and nothing left for this block's own blast to
     * re-catch. Removing again would be a no-op; guarding on "is the block still here" would be a
     * guard that is always false and would silently turn every chain reaction off.</p>
     */
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (level instanceof ServerLevel server) {
            explode(server, pos);
        }
    }

    /**
     * The fire path: <b>remove first, then explode.</b>
     *
     * <p>The guard is the removal itself. The blast is built from the world as it is when the blast
     * runs, and this block is no longer in it, so the blast's own final pass can never find the
     * block that started it and the loop has nowhere to begin. The {@code is(this)} check refuses a
     * second detonation when two neighbour updates arrive in the same tick for a block whose first
     * update already took it away.</p>
     */
    private void detonate(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        level.removeBlock(pos, false);
        explode(level, pos);
    }

    /** Whether a flame is in any of the six cells that touch this one. */
    private static boolean fireAround(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(BlockTags.FIRE)) {
                return true;
            }
        }
        return false;
    }

    /** Every water cell that touches this one becomes obsidian. Water, and not waterlogged blocks. */
    private static void hardenWaterAround(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).is(Blocks.WATER)) {
                harden(level, neighbour);
            }
        }
    }

    /**
     * One harden: the water becomes obsidian and the fizz plays.
     *
     * <p><b>The fizz is vanilla's own event and not a sound-and-particle pair written here.</b>
     * Lava quenching itself on water does the same thing, and it does it with
     * {@code LevelEvent.LAVA_FIZZ} ({@code 1501}, read off {@code LevelEvent} and not typed as a
     * literal). The client's handler for it -- {@code LevelRenderer#levelEvent}, case 1501 --
     * plays {@code SoundEvents.LAVA_EXTINGUISH} and throws eight {@code LARGE_SMOKE} puffs above
     * the block, which is exactly the hiss and the steam wanted here. Reusing it means the fizz is
     * type-tested twice over by vanilla's own code and costs no new asset.</p>
     *
     * <p><b>Server side, so it reaches every eye.</b> {@code ServerLevel.levelEvent} broadcasts a
     * {@code ClientboundLevelEventPacket} to the players within range, so calling it once where the
     * water was is the whole of the effect -- no per-client code, no sound file of ours. And it is
     * emitted <em>only where a block actually turned</em>: this method is reached from the one cell
     * that changed and from the place hook, never from a scan of the six faces, so a neighbour
     * update that brings no water still says nothing.</p>
     */
    private static void harden(ServerLevel level, BlockPos water) {
        level.setBlock(water, Blocks.OBSIDIAN.defaultBlockState(), 3);
        level.levelEvent(LevelEvent.LAVA_FIZZ, water, 0);
    }

    /** One blast, from the block's centre, with fire and with no entity to blame it on. */
    private static void explode(ServerLevel level, BlockPos pos) {
        level.explode(null,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                BLAST_POWER, true, Level.ExplosionInteraction.BLOCK);
    }
}
