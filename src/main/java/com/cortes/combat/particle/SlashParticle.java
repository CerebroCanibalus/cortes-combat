package com.cortes.combat.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.NoSuchElementException;

/**
 * One animated slash, cut out of a single vertical strip.
 *
 * <p>The art is authored as one 32x160 file with five 32x32 frames stacked. That is a normal way
 * to hand over animation and it is <em>not</em> what the particle system reads: in 1.21.1 a
 * description's texture list is the animation itself, one entry per frame, which is why vanilla
 * ships {@code sweep_0} through {@code sweep_7} as seven files. {@code SpriteSet#get(int, int)} is
 * {@code list.get(age * (size - 1) / lifetime)} -- an index walked by age, nothing more.</p>
 *
 * <p>So the strip stays whole and the slicing happens here, in the four UV accessors. They are
 * {@code protected} and not private, which is the only reason this is possible without shipping
 * forty files instead of eight.</p>
 *
 * <p><b>The slice is taken inside the sprite's own bounds, not inside the unit square.</b> The
 * atlas stitches at a fixed size and a 32x160 sprite occupies a sub-rectangle of it, so slicing
 * against 0..1 would draw the wrong fifth of the atlas, which looks like bad art rather than like a
 * bug.</p>
 *
 * <p>No gravity and no physics: a slash is a gesture, and it stays where it was drawn.</p>
 */
public class SlashParticle extends TextureSheetParticle {

    /** Five frames per strip, and every strip in the art is the same height. */
    public static final int FRAMES = 5;

    /**
     * How many ticks the whole strip takes.
     *
     * <p>Seven, so each frame gets a tick and a half. It has to be quick: this is punctuation on
     * a swing that already happened, and a slash that lingers reads as a second event.</p>
     */
    public static final int LIFETIME = 7;

    /**
     * The square the art is drawn on, in blocks across.
     *
     * <p><b>This is {@code quadSize}, not {@code setSize}.</b> {@code Particle#setSize} sets the
     * collision box and leaves {@code quadSize} alone, and {@code renderRotatedQuad} draws from
     * {@code quadSize}, whose default is 0.1 to 0.2. The slash was therefore rendering as a ten
     * centimetre quad for as long as it existed, and calling {@code setSize(0.9, 0.9)} for a size
     * number that read like a size number is exactly how that went unnoticed: the code said 0.9 and
     * the screen said nothing.</p>
     *
     * <p>1.8 is a zombie's height, so the slash is about as big as the thing it landed on and
     * reads as a hit from a few blocks away. It is a square, not a billboard, and it faces the
     * camera.</p>
     */
    private static final float QUAD_SIZE = 1.8F;

    private final SlashParticleOptions options;

    /** One warning per session, not one per particle: a null sprite is per-particle. */
    private static boolean warnedNullSprite;

    public SlashParticle(ClientLevel level, double x, double y, double z,
                         double dx, double dy, double dz, SlashParticleOptions options) {
        super(level, x, y, z, dx, dy, dz);
        this.options = options;
        this.hasPhysics = false;
        this.quadSize = QUAD_SIZE;
        this.setSize(QUAD_SIZE, QUAD_SIZE);
        // The lifetime covers the delay, and the animation is measured against what is LEFT of it
        // rather than against the whole. That is what makes a delayed slash play its five frames
        // in full instead of starting halfway through and cutting out.
        this.setLifetime(SlashTiming.totalTicks(options.delayTicks()));
        // Slows to a stop instead of drifting, so the arc stays where the swing connected.
        this.xd *= 0.1F;
        this.yd *= 0.1F;
        this.zd *= 0.1F;
    }

    /**
 * Holds the sprite back for the delay, then lets the strip run.
 *
 * <p><b>The sprite is restored, and that is the whole bug this method had.</b> It used to set the
 * sprite to {@code null} for the delay and nothing ever put it back: {@link #bindSprite} runs once,
 * at spawn, before the first tick. So a delayed slash was invisible for the whole of its life, and
 * the sabre's second cut was never drawn at all. The playtest could not hear or see it, and the log
 * said both hits landed -- which is exactly the shape of a visual that is not being drawn rather
 * than one that is being drawn too quietly.</p>
 *
     * <p>So the bound sprite is kept and handed back the moment the delay is over. Nulling it was
 * never the right tool: it is the same null that a genuinely missing description produces, and the
 * class already carries a warning for that case, which means a particle holding a null sprite is
 * ambiguous between "not yet" and "never".</p>
     *
     * <p>The position is still integrated throughout, because the engine removes a particle when the
     * age passes the lifetime whether or not it is drawing, and freezing it would leave the second
     * cut hanging in the air next to where the first one landed.</p>
     */
    @Override
    public void tick() {
        super.tick();
        if (this.age <= options.delayTicks()) {
            if (this.sprite != null) {
                this.held = this.sprite;
                this.setSprite(null);
            }
        } else if (this.sprite == null && this.held != null) {
            // The delay is over. Handing the strip back is all it takes; the animation is measured
            // against the ticks that remain, so it starts at its first frame rather than wherever
            // the elapsed time happens to land.
            this.setSprite(this.held);
            this.held = null;
        }
    }

    /** The sprite while the delay hides it, so the delay does not destroy it. */
    private net.minecraft.client.renderer.texture.TextureAtlasSprite held;

    /** The animation's own length, which is the strip and not the wait in front of it. */
    private int animationTicks() {
        return SlashTiming.STRIP_TICKS;
    }

    @Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        // Translucent, because the art is a fading slash with soft edges and premultiplied alpha
        // would draw a hard rectangle around it.
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Binds the strip this swing should draw.
     *
     * <p><b>The provider has to do this itself, and that is the whole bug this round.</b>
     * {@code ParticleEngine} calls {@code pickSprite} from exactly one place: the deprecated
     * {@code register(ParticleType, ParticleProvider.Sprite)} overload, which wraps the provider and
     * binds on its behalf. {@code registerSpriteSet} stores the provider and nothing else, so a
     * particle registered that way arrives with {@code sprite == null}.</p>
     *
     * <p>Which means {@code pickSprite} is never called, and a particle that reads its UVs from
     * the sprite draws a quad of nothing. No crash, no warning, no log line: the four UV
     * accessors return zero and the particle is simply invisible, which is the same thing on
     * screen as art that is too faint to see. Two launches went into that.</p>
     *
     * <p><b>The sprite comes from the atlas by id, not from the set by index.</b> The set only
     * spans the eleven strips this mod ships; a weapon pack's sprite lives in another namespace
     * and no index reaches it. The atlas stitches every {@code textures/particle/} PNG of every
     * enabled pack, so a direct lookup draws ours and theirs through the same door -- and a
     * missing id falls back to the sword with a warning naming it, which is the
     * fail-loud half of the weapon pack contract. (⌒‿⌒)</p>
     */
    public void bindSprite(SpriteSet sprites) {
        setSprite(resolveSprite(sprites));
    }

    @Override
    public void pickSprite(SpriteSet sprites) {
        // Bound by WHICH sprite id, not by age.
        //
        // SpriteSet has no get(index). Its two accessors are get(age, lifetime), which is
        // list.get(age * (size - 1) / lifetime), and get(RandomSource), which is a coin flip. The
        // first walks the list as an animation, so with eight weapons in it a slash would
        // slideshow through the whole catalogue; the second would pick a different weapon on every
        // spawn.
        //
        // The atlas lookup below replaced the inverted index trick: it draws any stitched sprite
        // through one door instead of one door per catalogue.
        setSprite(resolveSprite(sprites));
    }

    /**
     * The strip for this swing, from the particle atlas by id.
     *
     * @param sprites the old set-index door, kept as the last fallback so a session where the
     *                atlas lookup itself fails still draws what it drew before this round
     */
    private TextureAtlasSprite resolveSprite(SpriteSet sprites) {
        java.util.function.Function<ResourceLocation, TextureAtlasSprite> atlas =
                Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_PARTICLES);
        // `tryParse` and not the constructor: the one-arg constructor is gone from 1.21 on and
        // the two-arg one is private there, while `tryParse` reads on both legs and answers null
        // instead of throwing on the render thread. (μ_μ)
        String raw = SlashTextureId.resolve(options.sprite());
        ResourceLocation id = raw != null ? ResourceLocation.tryParse(raw) : null;
        if (id != null && !isMissing(atlas.apply(id), id)) {
            return atlas.apply(id);
        }
        if (id != null) {
            warnMissingSprite(id.toString());
        }
        ResourceLocation fallbackId =
                com.cortes.combat.platform.ResourceIds.rl(SlashSprite.FALLBACK);
        TextureAtlasSprite fallback = atlas.apply(fallbackId);
        if (!isMissing(fallback, fallbackId)) {
            return fallback;
        }
        // The atlas itself is broken past this point; the set-index door is what used to run.
        return setSpriteByIndex(sprites);
    }

    /**
     * Whether the atlas substituted its missing texture: a sprite whose stitched name is not
     * what was asked for.
     *
     * <p>Asked instead of naming the missing sprite, because the missing sprite's own class
     * does not exist on both legs ({@code MissingTextureAtlasSprite} is gone from 1.21 on)
     * while "give me what I asked" holds everywhere. (￣ε￣＠)</p>
     */
    private static boolean isMissing(TextureAtlasSprite sprite, ResourceLocation asked) {
        return sprite == null || !sprite.contents().name().equals(asked);
    }

    /** The pre-atlas door, kept as the floor: an unbound set draws nothing, never crashes. */
    private TextureAtlasSprite setSpriteByIndex(SpriteSet sprites) {
        if (sprites == null) {
            return null;
        }
        int lastIndex = SlashSprite.SPRITES.size() - 1;
        int index = SlashSprite.indexOf(options.sprite());
        warnIfUnbound(sprites, index, lastIndex);
        try {
            return sprites.get(Math.min(index, lastIndex), Math.max(1, lastIndex));
        } catch (RuntimeException unbound) {
            // An unbound set means the description JSON did not load. A slash that draws nothing is
            // a cosmetic bug; a NullPointerException on the render thread twenty frames later is
            // not the same thing.
            return null;
        }
    }

    /**
     * How far through the strip the animation is, 0..1.
     *
     * <p>Clamped because the frame index is derived from it and a particle whose age somehow
     * exceeded its life would otherwise index past the end.</p>
     */
    private float frameProgress() {
        // Measured from the end of the delay, against the strip's own length. Using the particle's
        // full lifetime here would make a delayed slash start its animation already a third of the
        // way through, which is a slash that fades before it is fully drawn.
        int elapsed = this.age - options.delayTicks();
        int lifetime = animationTicks();
        if (lifetime <= 0 || elapsed <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, (float) elapsed / (float) lifetime);
    }

    private int frame() {
        return Math.min(FRAMES - 1, (int) (frameProgress() * FRAMES));
    }

    private float sliceHeight() {
        if (sprite == null) {
            return 0.0F;
        }
        return (super.getV1() - super.getV0()) / (float) FRAMES;
    }

    // Every accessor tolerates a missing sprite. They have to: a particle with no sprite is a
    // degenerate quad, and one that dereferences null is a client crash.

    @Override
    protected float getU0() {
        return sprite == null ? 0.0F : super.getU0();
    }

    @Override
    protected float getU1() {
        return sprite == null ? 0.0F : super.getU1();
    }

    @Override
    protected float getV0() {
        // Frame zero at the sprite's own top edge. If the animation ever plays bottom to top
        // instead, this and getV1 are the two lines to swap, and nothing else.
        return sprite == null ? 0.0F : super.getV0() + sliceHeight() * frame();
    }

    @Override
    protected float getV1() {
        return sprite == null ? 0.0F : getV0() + sliceHeight();
    }

    /**
     * Warns once per session about a sprite id the atlas never stitched.
     *
     * <p>A slash whose sprite is null is an invisible quad, and an invisible quad looks exactly
     * like art that is too faint to see -- the two are indistinguishable from the outside, and two
     * launches went into guessing between them. The name in the message is the full sprite id, so
     * a pack author sees which file the game never saw: a wrong namespace reads as a pack problem
     * and a missing PNG as an art problem, which is the whole diagnosis in one line. (¬‿¬)</p>
     */
    private static final java.util.Set<String> warnedSprites = new java.util.HashSet<>();

    private static void warnMissingSprite(String spriteId) {
        if (!warnedSprites.add(spriteId)) {
            return;
        }
        org.slf4j.LoggerFactory.getLogger("cortescombat")
                .warn("[slash] sin sprite '{}': no esta cocido en el atlas de particulas -- "
                        + "revisa el PNG en textures/particle/ y el id en texture.slash. "
                        + "Se dibuja el generico.", spriteId);
    }

    private static void warnIfUnbound(SpriteSet sprites, int index, int lastIndex) {
        if (warnedNullSprite) {
            return;
        }
        try {
            if (sprites.get(Math.min(index, lastIndex), Math.max(1, lastIndex)) != null) {
                return;
            }
        } catch (RuntimeException e) {
            // An unbound set throws rather than returning null. Also worth saying out loud.
            if (warnedNullSprite) {
                return;
            }
        }
        warnedNullSprite = true;
        org.slf4j.LoggerFactory.getLogger("cortescombat")
                .warn("[slash] sin sprite en el indice {} de {}: assets/cortescombat/particles/"
                        + "slash.json no lo trajo, o el sprite no esta en textures/particle/ "
                        + "(singular). La particula no se va a ver.",
                        index, lastIndex + 1);
    }

    /**
     * The sprite this particle draws, for the debug readout.
     *
     * <p>Public only so a command can name it. Reading it back out of the UV accessors would be
     * arithmetic on a lie.</p>
     */
    public String spriteName() {
        return options.sprite();
    }
}