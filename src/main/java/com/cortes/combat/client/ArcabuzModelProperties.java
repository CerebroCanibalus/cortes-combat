package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.registry.ModComponents;
import com.cortes.combat.registry.ModItems;
import java.util.Optional;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * How the arcabuz picks which of its five fuse frames to draw.
 *
 * <h2>Why this file exists at all</h2>
 *
 * <p><b>A texture animation cannot do this, and 1.21.1 has no way to make it.</b> The gun is one
 * 64x320 strip of five frames -- the unlit one and four of the fuse burning down -- and a
 * {@code .mcmeta} runs it at whatever rate it is told to, freely, forever. The playtest reported
 * the weapon as "bugged, it has no textures", and the reason was exactly that: the fuse was
 * burning down on its own in the inventory whether or not a shot had been fired, and nothing in
 * the game could ask it to stop.</p>
 *
 * <p><b>So the strip was cut into five files</b> (the original is untouched and still on disk) and
 * the model picks between them with a predicate. This class is what feeds that predicate.</p>
 *
 * <h2>The API, verified rather than remembered</h2>
 *
 * <p><b>{@code ItemPropertyUtils} and {@code assignIndex} do not exist in 1.21.1.</b> They were the
 * 1.20 names, and the first version of this file imported one of them and would not have compiled.
 * Probing the class name with javac found four candidates that all did not exist, and the real one
 * turned out to be {@link ItemProperties} with {@code register(Item, ResourceLocation,
 * ItemPropertyFunction)} and a function shaped
 * {@code float call(ItemStack, ClientLevel, LivingEntity, int)}.</p>
 *
 * <p>There is <b>no {@code assignIndex}</b>: the model does not store a number, the function
 * <em>computes</em> one every frame from the stack. So the value has to live on the stack, which is
 * why {@link ModComponents#ARCABUZ_FUSE} is a data component and not a field here. A client field
 * would be right on one screen and wrong on every other client that can see the gun.</p>
 */
public final class ArcabuzModelProperties {

    /** The predicate name the five models listen on. Namespaced, like every property. */
    public static final ResourceLocation FUSE = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "fuse");

    private ArcabuzModelProperties() {
    }

    /**
     * Registers the property. Client side, once, at startup.
     *
     * <p><b>Reads the component and nothing else.</b> No clock, no client tick counter, no
     * interpolation: the frame is whatever the server last put on the stack, so the gun a player
     * sees is the gun every other player sees.</p>
     */
    public static void register() {
        ItemProperties.register(ModItems.ARCABUZ.get(), FUSE, (stack, level, entity, seed) -> {
            Integer frame = stack.get(ModComponents.ARCABUZ_FUSE.get());
            return frame == null ? 0 : frame;
        });
    }
}