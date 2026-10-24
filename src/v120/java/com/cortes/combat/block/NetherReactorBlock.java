package com.cortes.combat.block;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The 1.20.1 body of the nether reactor. Same block, older interaction.
 *
 * <ul>
 *   <li>Use is the single {@code use} returning {@code InteractionResult}: the hand's stack
 *       comes from the player, and {@code sidedSuccess} carries the client/server split
 *       the two 1.21.1 constants spelled out.</li>
 *   <li>{@code getRenderShape}, {@code getSignal} and {@code isSignalSource} are
 *       <b>public</b> here -- narrowing them to protected is a compile error, and the
 *       bodies are identical.</li>
 *   <li>No {@code codec()}: 1.20.1 blocks have none, so there is nothing to override.</li>
 * </ul>
 * Everything else (states, ticker, light, lever, removal) is untouched. (⌒‿⌒)
 */
public class NetherReactorBlock extends BaseEntityBlock {

    /**
     * The lever's trace, and why it exists: "the lever does not switch it off" has exactly two
     * causes -- no switch touching the block, or a state that never reaches
     * {@code neighborChanged} -- and silence cannot tell them apart.
     */
    private static final Logger log = LogUtils.getLogger();

    /** At least one firestone is held. Drives which base texture is drawn. */
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");

    /** Powered and charged. Drives the emissive overlay and the clock. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /**
     * A nether star is loaded, whether its fuse is running or the water already cut it. Drives
     * which <em>pair</em> of textures is drawn.
     */
    public static final BooleanProperty STAR = BooleanProperty.create("star");

    public NetherReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(CHARGED, false)
                .setValue(ACTIVE, false)
                .setValue(STAR, false));
    }

    /**
     * <b>{@code BaseEntityBlock} answers {@code INVISIBLE} by default, and that is the whole
     * block.</b> Without this override the reactor places as an invisible box that still has
     * collision and light. See the shared twin for the full note.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * The reactor as a redstone source: fifteen while it runs, nothing when it does not.
     * Weak power on purpose, like vanilla's redstone block.
     */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(ACTIVE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGED, ACTIVE, STAR);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetherReactorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickingLevel, pos, tickingState, blockEntity) -> {
            if (blockEntity instanceof NetherReactorBlockEntity reactor
                    && tickingLevel instanceof ServerLevel server) {
                reactor.serverTick(server, pos, tickingState);
            }
        };
    }

    /**
     * The light, read from the block entity's charge.
     *
     * <p>1.20.1 names the same hook through {@code IForgeBlock} (with the state, like the
     * shared twin) and has no {@code hasDynamicLightEmission}: the engine re-asks
     * per position, which is enough because the block entity pushes a light check
     * whenever the charge moves (see the entity). Falls back to the charged state's
     * static value when there is no block entity yet -- during world generation or the
     * first tick of a placement. That is a provisional answer for one tick, not a second
     * source of truth.</p>
     */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            return ReactorCharge.lightFor(reactor.charge());
        }
        return state.getValue(CHARGED) ? ReactorCharge.lightFor(1) : 0;
    }

    /**
     * The manual feed: a firestone, or the nether star.
     *
     * <p>On the client this only reports whether the click should swing; the server is the one
     * that writes. A full reactor refuses, so the click falls through to whatever the item
     * would do otherwise rather than being silently swallowed.</p>
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor)) {
            return InteractionResult.PASS;
        }
        // **The water bucket, and it is checked before anything else.** While an overload is
        // counting down this is the only way out. Both overloads drown.
        if (stack.is(Items.WATER_BUCKET)
                && (reactor.overload() == ReactorOverload.Kind.STAR
                || reactor.overload() == ReactorOverload.Kind.FIRESTONE)) {
            if (level.isClientSide()) {
                return InteractionResult.sidedSuccess(true);
            }
            if (level instanceof ServerLevel server) {
                boolean cut = reactor.overload() == ReactorOverload.Kind.STAR
                        ? reactor.cancelStar(server, player, stack)
                        : reactor.cancelFirestone(server, player, stack);
                if (cut) {
                    return InteractionResult.sidedSuccess(false);
                }
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return reactor.accepts(stack)
                    ? InteractionResult.sidedSuccess(true)
                    : InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && reactor.insertFromHand(player, stack)) {
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    /** The lever, and anything else that changes a neighbour's signal. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, movedByPiston);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            boolean switchNow = ActiveReactors.switchSignal(server, pos);
            StringBuilder switches = new StringBuilder();
            for (Direction d : Direction.values()) {
                BlockPos here = pos.relative(d);
                if (server.getBlockState(here).is(ReactorTags.SWITCH)) {
                    switches.append(d).append('=').append(server.getSignal(here, d)).append(' ');
                }
            }
            if (switches.length() > 0 || switchNow) {
                log.info("[palanca] reactor {} vecinos=[{}] switch={} (por {})",
                        pos.toShortString(), switches.toString().trim(), switchNow, neighborBlock);
            }
            reactor.setPowered(server, switchNow);
            // **Both overloads drown, and a flowing cell is enough.**
            if ((reactor.overload() == ReactorOverload.Kind.STAR
                    || reactor.overload() == ReactorOverload.Kind.FIRESTONE)
                    && server.getBlockState(fromPos).is(Blocks.WATER)) {
                if (reactor.overload() == ReactorOverload.Kind.STAR) {
                    reactor.cancelStar(server);
                } else {
                    reactor.cancelFirestone(server);
                }
            }
        }
    }

    /** A block placed beside a live lever gets no neighbour update for it, so it reads on placement. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            reactor.setPowered(server, ActiveReactors.switchSignal(server, pos));
        }
    }

    /** Breaking the block spills its firestones; the block itself is the loot table's business. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            reactor.dropContents(server);
            reactor.onBlockRemoved(server);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
