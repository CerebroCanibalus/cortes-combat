package com.cortes.combat.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
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
 * The nether reactor: the mod's first block entity, and an energy core rather than a machine.
 *
 * <h2>Two states, and the difference between them</h2>
 *
 * <p><b>{@code charged} is "there is at least one firestone in it"; {@code active} is "it is charged
 * and a redstone signal reaches it".</b> The texture follows {@code charged} -- a reactor holding
 * firestone shows the lit core, an empty one the dead core -- and the emissive overlay follows
 * {@code active}. That split is what makes the two facts readable at a glance: a reactor with
 * firestone but no lever looks alive and does nothing, which is the difference the player has to be
 * able to see.</p>
 *
 * <h2>Activation is redstone, and it is read where redstone is read</h2>
 *
 * <p>A lever beside the block toggles it. {@code neighborChanged} is the hook: when the lever
 * flips, the world fires a neighbour update here and this reads
 * {@code level.hasNeighborSignal(pos)} and tells the block entity. Off means no firestone is spent
 * and no overload runs -- which is why the block entity, not the block, owns the clock.</p>
 *
 * <h2>Light, and the one NeoForge hook that makes it per-position</h2>
 *
 * <p>Vanilla's light is a property of the block state, and this block's light depends on how many
 * firestones it holds -- which is block entity data, not a state. NeoForge's
 * {@code IBlockExtension.getLightEmission(state, level, pos)} is exactly the seam for that: it is
 * given the level and the position, so it can read the block entity's charge, and
 * {@code hasDynamicLightEmission} tells the engine the value is not a pure function of the state.
 * The static {@code lightLevel} still names a value for the charged state, so any code path that
 * asks the state alone gets a sensible answer instead of zero.</p>
 *
 * <p><b>The overlay is emissive through {@code emissiveRendering}, and that is a per-state flag.</b>
 * NeoForge has no per-model full-bright, so the whole block renders at full brightness while
 * {@code active}, base texture and overlay together. That is acceptable here because "active" is
 * precisely "the reactor is glowing": the base face's bright core reading as lit is the intended
 * look, and the translucent overlay adds the soft bloom on top of it. <b>If the active face ever
 * reads as washed out, the fix is to darken the base texture, not to reach for a hook that does not
 * exist.</b></p>
 */
public class NetherReactorBlock extends BaseEntityBlock {

    /**
     * The lever's trace, and why it exists: "the lever does not switch it off" has exactly two
     * causes -- no switch touching the block (by design: the switch has to touch the reactor), or
     * a switch touching it whose state never reaches {@code neighborChanged} -- and silence cannot
     * tell them apart. One line per neighbour update *that finds a switch nearby*; no line means
     * no switch is touching the reactor.
     */
    private static final Logger log = LogUtils.getLogger();

    /** At least one firestone is held. Drives which base texture is drawn. */
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");

    /** Powered and charged. Drives the emissive overlay and the clock. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /**
     * A nether star is loaded, whether its fuse is running or the water already cut it. Drives which
     * <em>pair</em> of textures is drawn: the star has its own core art and its own bloom, both the
     * same size as the normal ones, so the blockstate swaps the pair rather than stacking a second
     * cube in the cell -- two full cubes in one cell are a depth fight, not a texture. The swap is
     * written with AND, which the format supports: {@code Selector.Deserializer.getCondition} builds
     * an {@code AndCondition} from more than one key in {@code when}.
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
     * block.</b> The class exists for blocks whose look comes from a {@code BlockEntityRenderer}
     * (chests, signs, banners), so it suppresses the baked model -- and this block has no renderer
     * and is drawn entirely by its blockstate. Without this override the reactor places as an
     * invisible box that still has collision and light, which reads as "broken" rather than as
     * "missing": the whole feature looks like it does nothing.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * The reactor as a redstone source: fifteen while it runs, nothing when it does not.
     *
     * <p><b>The redesign's first half, and the half that does not need a network.</b> The reactor is
     * no longer a field that answers questions about other positions -- it is a block that answers
     * for itself, exactly like a redstone block: whatever touches it reads fifteen through the
     * vanilla path ({@code getSignal}), and every component downstream behaves the way Minecraft
     * always behaved. The lever still rules it, because {@code ACTIVE} is <em>lit</em>, which already
     * means "charged and not switched off".</p>
     *
     * <p><b>Weak power on purpose.</b> A repeater set against the reactor reads this method directly
     * and works; a solid block in between does not carry it, because carrying through a block is
     * strong power ({@code getDirectSignal}) and vanilla's own redstone block does not do that
     * either. Adding it is two lines if a playtest asks for it.</p>
     */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(ACTIVE);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(NetherReactorBlock::new);
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
     * <p>Falls back to the charged state's static value when there is no block entity yet -- during
     * world generation or the first tick of a placement, when the emission is still being computed.
     * That is a provisional answer for one tick, not a second source of truth.</p>
     */
    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            return ReactorCharge.lightFor(reactor.charge());
        }
        return state.getValue(CHARGED) ? ReactorCharge.lightFor(1) : 0;
    }

    /** The charge is not a state, so the engine has to ask per position. */
    @Override
    public boolean hasDynamicLightEmission(BlockState state) {
        return true;
    }

    /**
     * The manual feed: a firestone, or the nether star.
     *
     * <p>On the client this only reports whether the click should swing; the server is the one that
     * writes. A full reactor refuses, so the click falls through to whatever the item would do
     * otherwise rather than being silently swallowed.</p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // **The water bucket, and it is checked before anything else.** While a nether star is
        // counting down this is the only way out, and it has to be recognised here -- in the block's
        // own use, before the bucket's vanilla water placement ever gets a turn -- or the click
        // would pour water and leave the reactor running.
        // **Both overloads drown.** The star was the only one for a long time, but a firestone fuse
        // counting down is the same emergency with the same exit: pour water on it, the fuse drops
        // whole and the reactor is left holding at most the normal ceiling (30 -- the excess
        // firestones are dropped, they are not silently destroyed).
        if (stack.is(Items.WATER_BUCKET)
                && (reactor.overload() == ReactorOverload.Kind.STAR
                || reactor.overload() == ReactorOverload.Kind.FIRESTONE)) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel server) {
                boolean cut = reactor.overload() == ReactorOverload.Kind.STAR
                        ? reactor.cancelStar(server, player, stack)
                        : reactor.cancelFirestone(server, player, stack);
                if (cut) {
                    return ItemInteractionResult.SUCCESS;
                }
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return reactor.accepts(stack)
                    ? ItemInteractionResult.SUCCESS
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server && reactor.insertFromHand(player, stack)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** The lever, and anything else that changes a neighbour's signal. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos fromPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, fromPos, movedByPiston);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            boolean switchNow = ActiveReactors.switchSignal(server, pos);
            // Only prints when a switch is touching the reactor (or one was read as on): silence
            // means "nothing in the SWITCH tag touches this block", which is the design's own
            // answer and not a failure -- see the field's javadoc.
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
            // **Both overloads drown, and a flowing cell is enough.** The bucket above is the
            // deliberate version; this is the same rule for water that arrived on its own, for
            // the star and for the firestone alike: neither overload a player can leave running
            // by accident once there is water in the cell.
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState,
                           boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            reactor.setPowered(server, ActiveReactors.switchSignal(server, pos));
        }
    }

    /** Breaking the block spills its firestones; the block itself is the loot table's business. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof NetherReactorBlockEntity reactor) {
            reactor.dropContents(server);
            // The sweep lives here and not in the block entity's setRemoved, because this is the one
            // hook that knows the block was actually replaced -- and it asks no question about chunk
            // state, which is the question that used to hang a save.
            reactor.onBlockRemoved(server);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
