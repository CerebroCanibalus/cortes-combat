package com.cortes.combat.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * The purification plume: the little curl of smoke that lifts off something copper is burning.
 *
 * <p><b>A normal animated sprite, not a strip cut by hand.</b> The art is a single
 * {@code 16x64} PNG -- four {@code 16x16} frames stacked -- and an {@code .mcmeta} beside it
 * declares the animation, so the atlas walks the four frames on its own. That is the difference
 * from {@link SlashParticle}: the slash ships as a {@code 32x160} strip with five frames and cuts
 * its own UVs because its frame count is fixed in code, whereas here the frame is the atlas's
 * job and nothing in Java has to know how many there are. A redraw that adds a fifth frame is a
 * taller PNG and no code at all.</p>
 *
 * <p><b>{@code SpriteSet#get(age, lifetime)} is still how the sprite is read.</b> It is the same
 * call vanilla's {@code SoulParticle} makes every tick. With a one-entry texture list the index
 * is always zero, which is correct: the list is not the animation here, the {@code .mcmeta} is,
 * and the sprite handed back is the one the atlas is cycling.</p>
 *
 * <p><b>{@code quadSize}, not {@code setSize}.</b> {@code setSize} moves the collision box and
 * leaves the drawn quad alone -- the slash rendered as ten centimetres for a whole release
 * because of exactly that. There is no collision here anyway ({@code hasPhysics} is off), so the
 * only number that matters for what is seen is {@code quadSize}.</p>
 */
public class PurifiedParticle extends TextureSheetParticle {

    /**
     * A touch over a second. Long enough to read as a plume rather than a spark, short enough
     * that a puff every half second (the effect's own {@code INTERVAL_TICKS}) does not leave a
     * column of the stuff hanging in the air.
     */
    public static final int LIFETIME = 22;

    /** Small: this is a debuff's tell, not a hit's flare. Roughly a quarter of a zombie. */
    public static final float QUAD_SIZE = 0.55F;

    /** Kept so the sprite can be re-read each tick, exactly as vanilla's soul particle does. */
    private final SpriteSet sprites;

    public PurifiedParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        // A curse lifting off the body drifts upward and through whatever it passes; it does not
        // fall and does not bump into blocks.
        this.hasPhysics = false;
        this.quadSize = QUAD_SIZE;
        this.gravity = -0.03F;
        this.friction = 0.94F;
        this.setLifetime(LIFETIME);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        // Fades to nothing over the back half, so the puff dissolves instead of vanishing on a
        // hard frame. Squared, so it holds its opacity early and lets go at the end.
        float t = (float) this.age / (float) this.lifetime;
        this.setAlpha(1.0F - t * t);
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Translucent: the art has soft edges and an alpha, and opaque would ring it in a hard
        // square.
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
