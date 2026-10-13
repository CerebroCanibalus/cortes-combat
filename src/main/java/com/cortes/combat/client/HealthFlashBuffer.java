package com.cortes.combat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * El envoltorio que hace que el flash <b>sea una barra de vida</b>: borra el rojo de la parte del
 * cuerpo que ya no tiene vida que representar.
 *
 * <h2>Qué envuelve, y por qué son seis métodos</h2>
 *
 * <p>{@code VertexConsumer} tiene <b>seis métodos abstractos</b> -- {@code addVertex},
 * {@code setColor}, {@code setUv}, {@code setUv1}, {@code setUv2} y {@code setNormal} -- y todo el
 * resto son {@code default} que se apoyan en esos seis. Así que envolverlo es delegar seis cosas y
 * quedarse con una, <b>y eso es lo que hace barato este efecto</b>.</p>
 *
 * <h2>Las dos que importan</h2>
 *
 * <ul>
 *   <li><b>{@code addVertex(x, y, z)}</b> da la posición <b>en espacio de modelo</b>, y la
 *       {@code y} es la <b>altura del vértice</b>. Es el único lugar donde la altura existe: el
 *       overlay, por sí solo, no sabe dónde está el vértice que está pintando.</li>
 *   <li><b>{@code setUv1(u, v)}</b> es el <b>overlay</b>. Es donde se decide si este vértice
 *       flashea o no.</li>
 * </ul>
 *
 * <p>Y se recuerda la última {@code y} vista: el modelo escribe <b>posición y después atributos</b>
 * para el mismo vértice, así que cuando llega el overlay, la altura está en la mano.</p>
 *
 * <h2>Borra, no pinta</h2>
 *
 * <p>El envoltorio <b>no elige el color del flash</b> -- eso lo sigue decidiendo la fila, en
 * {@code DamageFlash}. Lo único que hace es <b>quitarle el overlay a los vértices de abajo</b>,
 * devolviéndolos a {@code NO_OVERLAY}. Esa división es la que deja intacto todo lo que ya funciona:
 * el frame negro del impacto, el blanco de la muerte y el rojo son de la capa de arriba, y acá sólo
 * se decide <b>hasta dónde llegan</b>.</p>
 *
 * <h2>Por qué no puede testearse</h2>
 *
 * <p>Implementa una interfaz de Minecraft, así que <b>no arranca en una JVM pelada</b>. La
 * aritmética -- dónde está la línea -- vive en {@link DamageFlash#barThreshold}, que es pura y sí
 * tiene test. Es la misma línea que el proyecto ya trazó con {@code HitParticles} y
 * {@code HitReadout}.</p>
 */
public final class HealthFlashBuffer implements VertexConsumer {

    private final VertexConsumer delegate;

    /** La altura, en unidades de modelo, por debajo de la cual el cuerpo ya no flashea. */
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
    public VertexConsumer addVertex(float x, float y, float z) {
        this.lastY = y;
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        // **Arriba de la linea no flashea; abajo si.** La herida TREPA: el rojo es lo que le falta al
        // mob, asi que crece desde los pies. Ver DamageFlash#barThreshold, donde tambien esta por que
        // la Y que llega aca no es la del vertice sino la de la entidad -- y por que eso costo un
        // playtest entero.
        if (this.lastY > this.thresholdY) {
            this.delegate.setUv1(this.hiddenOverlay & 0xFFFF, this.hiddenOverlay >> 16);
        } else {
            this.delegate.setUv1(u, v);
        }
        return this;
    }

    // Los otros cuatro: delegan y nada mas. Ver el javadoc sobre por que son solo cuatro.

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        this.delegate.setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        this.delegate.setNormal(normalX, normalY, normalZ);
        return this;
    }
}
