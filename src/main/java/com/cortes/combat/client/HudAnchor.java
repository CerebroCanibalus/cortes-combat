package com.cortes.combat.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The one point every HUD element is measured from.
 *
 * <p>It exists because four layers were each computing {@code guiWidth() / 2} and
 * {@code guiHeight() / 2} and then nudging their own offset until the art lined up. That is
 * alignment by coincidence: it works on one GUI scale, on one window size, and drifts the
 * moment either changes, and there is no way to tell from reading the code which number is the
 * idea and which is a fudge factor.</p>
 *
 * <p>So: one canonical point, and every element is an <em>offset from it</em>. A change to the
 * crosshair's art is then a change to the offset that describes the crosshair, and it cannot
 * silently move the brackets or the orbs with it.</p>
 *
 * <p>The origin is the exact centre of the crosshair sprite at rest. That is the natural choice
 * because the crosshair is what the player is aiming with, and it is the one element whose
 * position the player already perceives as "the centre".</p>
 */
public final class HudAnchor {

    private HudAnchor() {
    }

    /** The canonical point. Everything else in the HUD is an offset from here. */
    public static int centreX(GuiGraphics graphics) {
        return graphics.guiWidth() / 2;
    }

    public static int centreY(GuiGraphics graphics) {
        return graphics.guiHeight() / 2;
    }

    /**
     * The crosshair's half-height at rest, which is what the vertical offsets are measured
     * from. Not the grown size: an element that moves when the crosshair grows would be
     * reacting to a hit, and reacting to a hit is the crosshair's job.
     */
    public static int crosshairHalf() {
        return CrosshairSprites.baseSize() / 2;
    }

    /**
     * How far the crosshair actually extends at the current growth.
     *
     * <p>Separate from {@link #crosshairHalf()} on purpose. The resting half-height is where an
     * element <em>sits</em>; this is how far the crosshair <em>reaches</em> right now, and only
     * the two things that must not be overlapped use it.</p>
     */
    public static int crosshairReach() {
        return Math.round(CrosshairSprites.baseSize() * CrosshairFeedback.growth() / 2.0F);
    }
}
