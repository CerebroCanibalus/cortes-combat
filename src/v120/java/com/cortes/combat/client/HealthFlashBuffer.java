package com.cortes.combat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * The 1.20.1 body of the health-flash wrapper. Same bar, older vertex verbs.
 *
 * <p>1.20.1 spells the two hooks {@code vertex(double, double, double)} and
 * {@code overlayCoords(int, int)}, and wants {@code endVertex},
 * {@code defaultColor} and {@code unsetDefaultColor} implemented too. The rule is
 * untouched: above the line the overlay goes back to {@code NO_OVERLAY}, below it the
 * vertex keeps what the flash row decided. (⌒‿⌒)</p>
 */
public final class HealthFlashBuffer implements VertexConsumer {

    private final VertexConsumer delegate;

    /** La Y del MUNDO por encima de la cual el cuerpo ya no flashea. Sale del rango medido. */
    private final float thresholdY;

    /** El overlay que se usa abajo de la línea: {@code NO_OVERLAY}, o sea "sin flash". */
    private final int hiddenOverlay;

    /** La altura del vértice que se está escribiendo. Ver el javadoc: el orden del modelo. */
    private float lastY;

    public HealthFlashBuffer(VertexConsumer delegate, float thresholdY, int hiddenOverlay) {
        this.delegate = delegate;
        this.thresholdY = thresholdY;
        this.hiddenOverlay = hiddenOverlay;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        this.lastY = (float) y;
        this.delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        if (this.lastY > this.thresholdY) {
            this.delegate.overlayCoords(this.hiddenOverlay & 0xFFFF, this.hiddenOverlay >> 16);
        } else {
            this.delegate.overlayCoords(u, v);
        }
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        this.delegate.color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        this.delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        this.delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float normalX, float normalY, float normalZ) {
        this.delegate.normal(normalX, normalY, normalZ);
        return this;
    }

    @Override
    public void endVertex() {
        this.delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        this.delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        this.delegate.unsetDefaultColor();
    }
}
