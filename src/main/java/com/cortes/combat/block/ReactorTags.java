package com.cortes.combat.block;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The block tags the nether reactor reads.
 *
 * <h2>Why a tag and not a list in code</h2>
 *
 * <p>The reactor has to know which blocks are worth telling "a neighbour changed", because the
 * alternative -- calling {@code neighborChanged} on every solid block in a ninety-block radius --
 * is millions of no-op updates per scan. A tag is the project's usual answer for a set that is
 * data, not logic.</p>
 *
 * <p><b>It is also the answer that stays correct without us.</b> A list in Java would have to name
 * every redstone component in vanilla and would silently miss the one Mojang adds next, and a mod
 * that adds a component could never join in. A tag can be extended by a datapack and the reactor
 * picks it up with no code: a mod author who wants their own lamp to hear the reactor adds one line
 * to {@code data/theirmod/tags/block/reactor_powerable.json}.</p>
 *
 * <p><b>And the failure is legible in both directions.</b> A missing tag entry is a component that
 * does not react, which is a bug report about one block; a missing Java list entry would be the
 * same bug with no file to point at. The tag provider writes the vanilla set and the
 * {@code ReactorWiringTest} reads the generated file, so an empty or misspelled tag is a red build
 * rather than a reactor that does nothing.</p>
 */
public final class ReactorTags {

    /**
     * Every block that should re-evaluate its redstone when the reactor changes state.
     *
     * <p>Lamps, pistons, doors, gates, trapdoors, dispensers, note blocks, TNT, rails, comparators,
     * repeaters, observers, hoppers, bells, copper bulbs, the crafter, redstone wire, torches,
     * levers and the redstone block: the components that read {@code hasNeighborSignal} or
     * {@code getBestNeighborSignal}. The scan asks this tag before it spends a neighbour update.</p>
     */
    public static final TagKey<Block> POWERABLE = TagKey.create(Registries.BLOCK,
            CortesCombat.rl("reactor_powerable"));

    /**
     * The blocks that may switch the reactor itself off, and <b>the only thing that may</b>.
     *
     * <p>The field feeds redstone wire, so a wire beside the reactor reports fifteen -- which is the
     * reactor's own power coming back as a command. Reading "is there a signal" would therefore
     * switch it off with itself, and the sweep would report the wire to zero and switch it on again:
     * a toggle every tick. That cannot be settled by telling the two apart, because a stored fifteen
     * from the field and one from a lever are the same number. So the switch is not a signal at all:
     * it is a control a player can flip, and only this tag can say which blocks those are. A wire, a
     * repeater or a comparator can be in the field's grip and never in this tag.</p>
     *
     * <p>Levers and the buttons and the pressure plates -- the manual controls. A datapack or a mod
     * extends it the same way as {@link #POWERABLE}.</p>
     */
    public static final TagKey<Block> SWITCH = TagKey.create(Registries.BLOCK,
            CortesCombat.rl("reactor_switch"));

    private ReactorTags() {
    }
}
