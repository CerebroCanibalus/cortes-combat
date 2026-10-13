package com.cortes.combat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Un {@link VertexConsumer} que <b>no dibuja: mide</b>. Guarda la Y más baja y la más alta que el
 * modelo escribe y tira todo lo demás.
 *
 * <h2>Por qué existe</h2>
 *
 * <p>La barra de vida necesita saber <b>dónde empieza y dónde termina el modelo</b> para poner la
 * línea. Las dos versiones anteriores lo dedujeron de datos que no son la geometría dibujada -- la
 * caja de colisión, multiplicada por dieciséis -- y las dos fallaron: la Y de un vértice llega en
 * bloques (el modelo divide por 16 antes de transformar) y encima arrastra un desplazamiento fijo
 * del renderer. Ver {@link DamageFlash#barCut}.</p>
 *
 * <p>∴ se le pregunta al modelo. Un pase con este consumidor devuelve el rango exacto, en el mismo
 * espacio y con los mismos números que va a usar el pase que sí dibuja.</p>
 *
 * <h2>Por qué el pase doble es barato y seguro</h2>
 *
 * <p>Barato: son dos pasadas de vértices <b>sólo mientras un cuerpo está herido</b>, o sea diez
 * ticks por golpe, y el modelo de un mob son unos cientos de vértices.</p>
 *
 * <p>Seguro: {@code renderToBuffer} es delegación pura a {@code ModelPart.render}, que empuja y
 * saca el {@code PoseStack} de forma simétrica (leído en las fuentes: {@code HierarchicalModel} no
 * agrega nada, y {@code PlayerModel} ni lo sobreescribe). Llamarlo dos veces no deja estado.</p>
 *
 * <h2>Por qué no tiene test</h2>
 *
 * <p>Implementa una interfaz de Minecraft, así que <b>no arranca en una JVM pelada</b>. Lo que sí es
 * puro -- qué hacer con los dos extremos -- vive en {@link DamageFlash#barCut}, que tiene test. Es
 * la misma línea que el proyecto trazó con {@code HealthFlashBuffer}.</p>
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
     *
     * <p>Y llega ya transformada al mundo, porque {@code ModelPart} pasa por el
     * {@code addVertex(Matrix4f, ...)} por defecto, que aplica la matriz y delega en esta. Ver el
     * javadoc de la clase.</p>
     */
    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (y < this.minY) {
            this.minY = y;
        }
        if (y > this.maxY) {
            this.maxY = y;
        }
        return this;
    }

    // Los otros cinco: se tiran y devuelven `this`, para no romper la cadena del modelo.

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
        return this;
    }
}
