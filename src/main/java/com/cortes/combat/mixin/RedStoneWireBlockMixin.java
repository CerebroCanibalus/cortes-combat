package com.cortes.combat.mixin;

import com.cortes.combat.block.ReactorNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The decay, removed one hop at a time on the wires that draw from a running reactor.
 *
 * <h2>The one line this is about</h2>
 *
 * <p>{@code RedStoneWireBlock.calculateTargetStrength} ends in
 * {@code return Math.max(i, j - 1)}, where {@code i} is what non-wire neighbours report and
 * {@code j} is the neighbouring wire's stored power. <b>The {@code -1} is the entire reason a
 * fifteen-block line dies.</b> Returning {@code max(i, j)} instead keeps the network at full
 * strength and changes nothing else: gates read zero-or-not from their sources, so a NOT gate with
 * its torch dark still computes zero and still works.</p>
 *
 * <h2>Why a redirect and an ordinal, and why the number is not a guess</h2>
 *
 * <p>There are <b>four</b> {@code Math.max} calls in that method -- three inside the neighbour loop
 * and one at the return -- counted with {@code javap -c} against the compiled class, where the
 * return one is ordinal 3 at bytecode offset 178, three bytes before the {@code ireturn}. Redirect
 * only that one; redirecting the loop's would add a phantom unit of strength per neighbour.</p>
 *
 * <h2>Why the position travels through a static</h2>
 *
 * <p><b>Because the class has no {@code LocalVariableTable}</b> -- {@code javap -l} reports zero of
 * them -- so {@code Locals.CAPTURE_FAILHARD}, the obvious way to read {@code i} and {@code j} at the
 * return, cannot work: it would have thrown when the game loaded the mixin, after every test had
 * passed. Instead the head stashes the position, the redirect asks about it, the return clears it.
 * The scratch lives in {@link ReactorNetwork}, not here, and it is not gameplay state: it exists
 * for the length of one call, exactly like vanilla's own {@code shouldSignal} field, and the method
 * it guards runs on the server thread only -- the three {@code !level.isClientSide} guards in this
 * class say so.</p>
 *
 * <h2>Why the rebuild hangs on {@code onRemove} and not on the break event</h2>
 *
 * <p><b>Timing is the whole seam.</b> NeoForge fires the break event ({@code fireBlockBreak})
 * <em>before</em> {@code removeBlock}, so a rebuild there sees the wire still in the world and
 * keeps the severed downstream half a member -- immune to decay with nothing feeding it, a line
 * that stays lit until the world reloads. {@code onRemove} runs inside {@code setBlock}, after the
 * wire is gone and before the neighbour cascade starts, so every wire that recomputes next does it
 * with correct membership and goes dark. It also fires for every cause -- player, explosion,
 * piston -- where the break event fires only for players.</p>
 */
@Mixin(RedStoneWireBlock.class)
public class RedStoneWireBlockMixin {

    /** Remembers which wire is being computed, so the redirect can ask about it. */
    @Inject(method = "calculateTargetStrength", at = @At("HEAD"))
    private void cortescombat$begin(Level level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        ReactorNetwork.begin(level, pos);
    }

    /**
     * {@code Math.max(i, j - 1)} -- ordinal 3, the return -- becomes {@code max(i, j)} when the wire
     * belongs to a running reactor's network. The second argument arrives already decremented, so
     * adding the unit back <em>is</em> the removal of the decay, with no approximation in it.
     */
    @Redirect(method = "calculateTargetStrength",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I", ordinal = 3))
    private int cortescombat$withoutDecay(int first, int second) {
        return ReactorNetwork.pendingNoDecay() ? Math.max(first, second + 1) : Math.max(first, second);
    }

    /** Clears the scratch whatever the method did on the way out. */
    @Inject(method = "calculateTargetStrength", at = @At("RETURN"))
    private void cortescombat$end(CallbackInfoReturnable<Integer> cir) {
        ReactorNetwork.end();
    }

    /**
     * A severed line loses its membership the moment the wire leaves the world -- see the class
     * javadoc for why this runs here and not on the break event. Server only: the walk reads
     * {@code ServerLevel}, and the client's copy of a removal has no network to rebuild.
     */
    @Inject(method = "onRemove", at = @At("TAIL"))
    private void cortescombat$rebuildOnRemoval(BlockState state, Level level, BlockPos pos,
                                               BlockState newState, boolean isMoving,
                                               CallbackInfo ci) {
        if (state.is(newState.getBlock())) {
            // **`onRemove` fires on every server-side `setBlockState`** -- LevelChunk:274 calls it
            // unconditionally when not client-side, so for a wire that means *every power change*.
            // A forty-wire cascade then rebuilt the region forty times in five milliseconds (6201
            // `[red]` lines in one session), because a wire changing 14 -> 15 is not a wire leaving
            // the world. Only a block actually replaced is a connectivity change.
            return;
        }
        if (level instanceof ServerLevel server) {
            ReactorNetwork.rebuild(server);
        }
    }
}
