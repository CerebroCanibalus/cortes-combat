package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * The bracket sprite, turned into a list of rectangles it can be drawn as.
 *
 * <h2>Why not just blit it with a translucent tint</h2>
 *
 * <p>That was the first version, and the tint did nothing. {@code GuiGraphics#setColor} does reach
 * {@code RenderSystem#setShaderColor} (verified in the 1.21.1 bytecode) but {@code blit} does not
 * go through the GUI render pipeline: it builds its own {@code Tesselator} pass with
 * {@code DefaultVertexFormat.POSITION_TEX} and calls {@code setShader}, which is a no-op under
 * the pipeline system, so the uniform the tint writes is never the one the bound pipeline
 * samples. The brackets drew, at full strength, at every alpha the playtest tried -- which is
 * what three rounds of "make them more transparent" were chasing.</p>
 *
 * <p>{@code fill} does go through {@code RenderType.gui()}, which is why the bar's alpha worked on
 * the first attempt with the same kind of number.</p>
 *
 * <p>So the brackets are filled instead of blitted. The price is reading the PNG once into runs of
 * horizontally adjacent opaque pixels, which happens the first time the widget draws and never
 * again. What it buys is that both halves of this widget are now drawn by <b>the same call with
 * the same alpha semantics</b>, and that is the property worth having: the bug this class exists
 * to prevent is two halves of one widget quietly disagreeing about how alpha works.</p>
 */
final class BracketMask {

    /**
     * One horizontal run, already placed in the sprite's own pixels.
     *
     * <p>Both columns are carried because the sprite is mirror-symmetric and the right bracket is
     * the left one reflected: one source of truth for the art instead of two files that have to
     * be kept in step.</p>
     */
    record Run(int leftX, int rightX, int y, int width, int alpha) {
    }

    private static final ResourceLocation TEXTURE = CortesCombat.rl("textures/gui/sprites/hud/cooldown_corchetes.png");

    /** Below this there is no ink. 21x15 art with a hard edge, so the test is generous. */
    private static final int ALPHA_FLOOR = 16;

    /**
     * Everything one read of the file produced, kept together.
     *
     * <p>One record rather than three statics derived from each other, because the width cannot be
     * derived from the runs: the ink does not reach the edges of the sprite, so the runs describe
     * a twenty column drawing inside a twenty-three wide file. A width guessed from the ink
     * would have been wrong by exactly the amount the mirror needs to be right, which is the
     * whole bug.</p>
     */
    private record Sprite(List<Run> runs, int width, int height, int peak) {

        static final Sprite MISSING = new Sprite(List.of(), 0, 0, 0);
    }

    private static final Sprite SPRITE = load();

    private static final List<Run> RUNS = SPRITE.runs();
    private static final int WIDTH = SPRITE.width();
    private static final int HEIGHT = SPRITE.height();
    private static final int PEAK = SPRITE.peak();

    /**
     * The sprite's own width, in pixels, or zero if there is no sprite.
     *
     * <p>Read from the file and not written down here, because the version that was written down
     * said twenty-one for a file that is twenty-three wide, and that single stale number was half
     * of the "descentrados" report. Nothing in this class or the layer that draws it is allowed to
     * describe the art; they all measure it.</p>
     */
    static int width() {
        return WIDTH;
    }

    /** The rectangles to fill, in the texture's own pixels. Empty if the texture is missing. */
    static List<Run> runs() {
        return RUNS;
    }

    /** How many pixels tall the sprite is, so the widget can centre it. Zero if it is missing. */
    static int height() {
        return HEIGHT;
    }

    /**
     * Where each bracket's ink sits inside the sprite, as the column its middle falls on.
     *
     * <p><b>This used to be three constants in the layer, and the art changed under them.</b>
     * The sprite was twenty-one wide; it is now twenty-three, and the layer still believed
     * twenty-one, so the right bracket was placed by a mirror offset that was two columns short
     * and the pair sat off centre. The playtest saw "los corchetes están descentrados de la
     * barra" and the cause was a number describing art that had been redrawn.</p>
     *
     * <p>Measuring instead of describing is the fix, and the reason is not that it is tidier: a
     * constant that describes art is a claim about art that anybody may redraw, and this one was
     * wrong the moment the file was replaced.</p>
     *
     * <p>Only the left one is measured. The right one is its exact mirror, because measuring both
     * independently is what put them a pixel apart: four columns of ink have no middle column,
     * both measurements rounded down, and seventeen is not the mirror of four in a twenty-three
     * wide file. The mirror is a fact about the file, not a guess about how it was drawn, and
     * {@link #WIDTH} is the image's own width rather than a constant.</p>
     *
     * @return where each bracket's ink is, or {@link #NO_INK} in both halves if there is none
     */
    static Anchors anchors() {
        int first = Integer.MAX_VALUE;
        int last = Integer.MIN_VALUE;
        for (Run run : RUNS) {
            first = Math.min(first, run.leftX());
            last = Math.max(last, run.leftX() + run.width() - 1);
        }
        if (first > last) {
            return new Anchors(NO_INK, NO_INK);
        }
        int left = middle(first, last);
        return new Anchors(left, ConeGauge.mirrorAnchor(left, WIDTH));
    }

    /**
     * The middle of two columns, rounded down.
     *
     * <p>Rounded so a bracket of even width anchors on its left column. That half pixel is now
     * harmless because the right one is the exact mirror of the result rather than its own
     * measurement: a pair that is symmetric to nothing does not read as centred, whatever the
     * rounding.</p>
     */
    private static int middle(int first, int last) {
        return (first + last) / 2;
    }

    /** Where each bracket's ink sits in the sprite's own columns. */
    record Anchors(int left, int right) {
    }

    /**
     * The strongest alpha anywhere in the sprite, which is what {@link #normalised} divides by.
     *
     * <p>Zero if the sprite is missing or entirely transparent.</p>
     */
    static int peakAlpha() {
        return PEAK;
    }

    /** What {@link #anchors()} says when there is no ink to anchor to. */
    static final int NO_INK = -1;

    /**
     * A run's alpha as a fraction of the sprite's own strongest pixel, in 0..1.
     *
     * <p>This is the same split {@code ConeGauge} already uses for the bar: the art decides the
     * <em>shape</em> and one number decides how present the whole thing is. Here the art's shape
     * happens to be very faint -- the sprite's peak is 38 of 255 -- and multiplying that by an
     * opacity of 0.28 gave eleven. Eleven of 255 is not a bracket, it is a rumour, and three
     * rounds of tuning the number would not have found it because the number was doing what it
     * was told.</p>
     *
     * @return 1 for the strongest pixel in the sprite, less for the softer ones, 0 if there is
     *         no sprite to compare against
     */
    static float normalised(int textureAlpha) {
        if (PEAK <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, textureAlpha / (float) PEAK);
    }

    private BracketMask() {
    }

    private static Sprite load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            return Sprite.MISSING;
        }
        Resource resource = mc.getResourceManager().getResource(TEXTURE).orElse(null);
        if (resource == null) {
            return Sprite.MISSING;
        }
        try (InputStream in = resource.open();
                NativeImage image = NativeImage.read(in)) {
            List<Run> runs = runs(image);
            return new Sprite(runs, image.getWidth(), image.getHeight(), peakOf(runs));
        } catch (IOException e) {
            // A missing bracket texture is not worth a crash and not worth a stack trace in a log
            // that also has real problems in it. The widget simply draws nothing.
            return Sprite.MISSING;
        }
    }

    private static int peakOf(List<Run> runs) {
        int peak = 0;
        for (Run run : runs) {
            peak = Math.max(peak, run.alpha());
        }
        return peak;
    }

    /**
     * Reads the left half and mirrors it.
     *
     * <p>Only the left half, so half the art is half the reads and there is no way for the two
     * halves to disagree because they were read twice. The mirror is exact rather than
     * approximate, because the sprite is symmetric about its centre column by construction.</p>
     */
    private static List<Run> runs(NativeImage image) {
        int width = image.getWidth();
        // The symmetry axis of an odd-width image is its middle column. That column carries no
        // ink, so it is where the walk closes any run that ends against it.
        int axis = (width - 1) / 2;
        List<Run> runs = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            int start = -1;
            int strength = 0;
            for (int x = 0; x <= axis; x++) {
                int alpha = x < axis ? alphaOf(image, x, y) : 0;
                if (alpha >= ALPHA_FLOOR) {
                    if (start < 0) {
                        start = x;
                    }
                    // The strongest pixel of the run, so a soft edge is not averaged into
                    // something dimmer than the ink the artist drew.
                    strength = Math.max(strength, alpha);
                } else if (start >= 0) {
                    // A run covering [start, x) mirrors to [width - x, width - start).
                    runs.add(new Run(start, width - x, y, x - start, strength));
                    start = -1;
                    strength = 0;
                }
            }
        }
        return List.copyOf(runs);
    }

    private static int alphaOf(NativeImage image, int x, int y) {
        return (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
    }
}
