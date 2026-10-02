import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Writes the lunge status-effect icon.
 *
 * <p>A MobEffect derives its icon from its registry path, so
 * {@code assets/cortescombat/textures/mob_effect/lunge.png} has to exist or the effect renders as a
 * missing texture -- which defeats the entire reason the effect is here. AGENTS.md records the
 * player reporting the lunge's invulnerability as invisible; a missing icon would have been
 * invisible in a new way.
 *
 * <p>Java rather than PowerShell because the PowerShell that ships on Windows is 5.1, which has no
 * {@code ZLibStream} and overflows on {@code [uint32]0xFFFFFFFF}, and a build step that only runs on
 * some machines is a build step somebody will skip.
 *
 * <p>The shape is a thrust: a bright diagonal from the heel to the point, running up and to the
 * right. At eighteen pixels the only thing that reads is the diagonal, so the outline is what carries
 * it -- one dark pixel above and below the lit line, so it survives both the bright HUD and the dark
 * one over a health bar.
 *
 * <p>Run with: {@code java tools/MakeLungeIcon.java}
 */
public final class MakeLungeIcon {

    private static final int SIZE = 18;

    /** The dark outline. Sits above and below the blade so a one-pixel line still reads. */
    private static final int EDGE = 0xFF2A2018;

    /** The lit half of the blade. */
    private static final int BLADE = 0xFFE8DCC4;

    /** The trailing half, so the diagonal has a direction and not just a slope. */
    private static final int SHADOW = 0xFFA89274;

    /** The cross piece at the heel: what makes the diagonal read as a weapon and not a scratch. */
    private static final int GUARD = 0xFFC8B89A;

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

        // The blade runs along x + (SIZE - 1 - y) == SIZE - 2, from the heel at the bottom left to
        // the point at the top right.
        int diagonal = SIZE - 2;
        for (int t = 2; t <= SIZE - 3; t++) {
            int x = t;
            int y = SIZE - 1 - t + 1;
            set(image, x, y, BLADE);
            set(image, x + 1, y + 1, SHADOW);
            set(image, x, y - 1, EDGE);
            set(image, x + 1, y + 2, EDGE);
        }

        // The point, and the heel guard.
        set(image, diagonal + 1, 2, BLADE);
        set(image, diagonal + 1, 1, BLADE);
        set(image, diagonal, 1, EDGE);
        set(image, 3, SIZE - 2, GUARD);
        set(image, 2, SIZE - 3, GUARD);
        set(image, 2, SIZE - 2, EDGE);
        set(image, 3, SIZE - 3, EDGE);

        File out = new File(args.length > 0 ? args[0]
                : "src/main/resources/assets/cortescombat/textures/mob_effect/lunge.png");
        File dir = out.getParentFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("could not create " + dir);
        }
        // The game's atlas reader wants an indexed or RGBA PNG; TYPE_INT_ARGB writes RGBA.
        if (!ImageIO.write(image, "png", out)) {
            throw new IllegalStateException("no PNG writer in this JRE");
        }
        System.out.println("  " + out.getPath() + "  " + out.length() + " bytes");
    }

    private static void set(BufferedImage image, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            return;
        }
        image.setRGB(x, y, argb);
    }
}