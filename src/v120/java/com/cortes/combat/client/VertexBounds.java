package com.cortes.combat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The 1.20.1 body of the measuring consumer. Same min/max, older verbs.
 *
 * <p>1.20.1 spells the position hook {@code vertex(double, double, double)}; the rest is
 * discarded the same way. Un pase con este consumidor devuelve el rango exacto, en el
 * mismo espacio y con los mismos números que va a usar el pase que sí dibuja. (⌒‿⌒)</p>
 */
public final class VertexBounds implements VertexConsumer {

    private float minY = Float.POSITIVE_INFINITY;
    private float maxY = Float.NEGATIVE_INFINITY;

    /** La Y más baja que el modelo escribió, en el mundo. {@code +inf} si no escribió ninguna. */
    public float minY() {
        return this.minY;
    }

    /** La Y más alta que el modelo escribió, en el mundo. {@code -inf} si no escribió ninguna. */
    public float maxY() {
        return this.maxY;
    }

    /**
     * La única que importa: <b>la posición</b>. El resto de las llamadas se tiran.
     */
    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (y < this.minY) {
            this.minY = (float) y;
        }
        if (y > this.maxY) {
            this.maxY = (float) y;
        }
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer normal(float normalX, float normalY, float normalZ) {
        return this;
    }

    @Override
    public void endVertex() {
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
