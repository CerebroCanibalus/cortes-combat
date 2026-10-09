package com.cortes.combat.item;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The mod's own tags, one per weapon mould: {@code #cortescombat:profile/<id>}.
 *
 * <p><b>Every profile has one, including the empty ones.</b> Fourteen values in the enum,
 * fourteen files in {@code data/cortescombat/tags/item/profile/}, and the ones with no member
 * yet -- {@code none}, {@code unarmed}, {@code mace}, {@code trident} -- are generated empty on
 * purpose. An empty tag is a tag that exists, and the file is the path somebody writes into
 * later. A missing file is a mould with no door, which is the failure this whole class is here
 * to make impossible: {@code WeaponProfileTagsTest} walks the enum and checks the file is
 * there, so adding a profile without its tag fails the build instead of shipping a mould
 * nobody can select.</p>
 *
 * <h2>Read order, and why ours comes first</h2>
 *
 * <p>{@code WeaponStats.deriveProfile} asks these tags <b>before</b> the vanilla ones, and the
 * order is the whole point:</p>
 *
 * <ol>
 *   <li><b>the {@code weapon_profile} data component</b> -- set on the thirty-one items of ours,
 *       so they never reach a tag at all;</li>
 *   <li><b>{@code #cortescombat:profile/<id>}</b> -- the mod's own classification, written by our
 *       datagen, holding the five copper tools today;</li>
 *   <li><b>the vanilla tags</b> -- the compat layer, still there and still first for a foreign
 *       mod that never heard of us;</li>
 *   <li><b>{@link WeaponProfile#NONE}</b> -- the honest answer for everything else.</li>
 * </ol>
 *
 * <p>Vanilla last, not absent: removing it would send every weapon of every mod we have never
 * heard of back to vanilla combat with no message, and "we do not recognise it" is a worse
 * answer than "we guessed sword". Keeping it costs nothing because our own items are matched
 * by a layer above it.</p>
 *
 * <h2>What the ordering buys us even with no third party involved</h2>
 *
 * <p>Because {@code #cortescombat:profile/none} is read before {@code #minecraft:swords}, an
 * item can be opted <em>out</em> by tag without editing anything of ours. Nobody needs that
 * today. It is worth writing down anyway, because it is a property of the read order rather
 * than a feature, and a property of the read order is free -- a feature would have to be
 * maintained.</p>
 *
 * <h2>Why the wrapper exists at all</h2>
 *
 * <p>{@link WeaponProfile#tagPath()} is the pure half: a {@code String}, reachable from a bare
 * JVM, walkable by a test. This class is the half that needs the game, and it does one thing
 * -- turn that path into a key. Two classes rather than one for the same reason
 * {@code CortesTier.repairTag} exists: a test that can reach the naming is the only thing that
 * catches a rename.</p>
 */
public final class WeaponProfileTags {

    private WeaponProfileTags() {
    }

    /** The tag that selects {@code profile}'s mould. */
    public static TagKey<Item> profileTag(WeaponProfile profile) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, profile.tagPath()));
    }
}
