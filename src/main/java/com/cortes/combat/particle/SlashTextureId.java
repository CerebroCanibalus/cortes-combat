package com.cortes.combat.particle;

/**
 * What the YAML's {@code texture.slash} names, translated to the sprite id the atlas knows.
 *
 * <p>The template writes a <em>file path</em> ({@code mymod:textures/particle/slash/chef.png})
 * because that is what a packmate sees on disk, but the particle atlas stitches by
 * <em>sprite id</em> ({@code mymod:slash/chef}): the directory {@code textures/particle/} is
 * the atlas prefix and {@code .png} is not part of any id. This function is the whole
 * translation, and it is pure so a typo'd path fails a unit test instead of a playtest.
 * (⌒‿⌒)</p>
 *
 * <p>Three shapes in, one id out:</p>
 * <ul>
 *   <li>A file path ({@code ns:textures/particle/....png}) -- the template's shape -- loses
 *       the prefix and the extension.</li>
 *   <li>A bare id ({@code ns:pipe}) -- already a sprite id -- passes through untouched.</li>
 *   <li>A bare name ({@code pipe}) -- one of ours -- lands in our namespace.</li>
 * </ul>
 */
public final class SlashTextureId {

    private static final String PARTICLE_PREFIX = "textures/particle/";

    private SlashTextureId() {
    }

    /**
     * The sprite id for a YAML {@code texture.slash} value, never null and never blank.
     *
     * @param raw what the pack wrote; null or blank means "no custom sprite"
     * @return the id to draw, or {@code null} when there is nothing custom to draw
     */
    public static String resolve(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        int colon = value.indexOf(':');
        if (colon < 0) {
            return com.cortes.combat.CortesCombat.MODID + ":" + value;
        }
        String namespace = value.substring(0, colon);
        String path = value.substring(colon + 1);
        if (path.startsWith(PARTICLE_PREFIX)) {
            path = path.substring(PARTICLE_PREFIX.length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
        }
        return namespace + ":" + path;
    }
}
