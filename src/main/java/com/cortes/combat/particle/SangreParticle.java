package com.cortes.combat.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * Una gota de sangre, y el encargado de que pese.
 *
 * <h2>Qué hace que un particula pese</h2>
 *
 * <p>Tres numeros, leidos de las fuentes de 1.21.1 y no de memoria, porque los tres se confunden
 * facilmente:</p>
 *
 * <ul>
 *   <li><b>{@code gravity} es un multiplicador de la caida</b>, no una aceleracion en bloques.
 *       {@code Particle#tick} hace {@code yd -= 0.04 * gravity} por tick. Con 1,0 -- el default --
 *       una gota cae como una pluma; con 3,0 cae como lo que es.</li>
 *   <li><b>{@code hasPhysics} tiene que quedarse en {@code true}</b>, que es su valor por defecto y
 *       el que casi todo el mundo apaga. Con {@code true}, {@code move()} pasa por
 *       {@code Entity.collideBoundingBox} y la gota <em>choca</em> con los bloques.</li>
 *   <li><b>Y el motor se encarga del resto, gratis.</b> {@code Particle} tiene un campo
 *       {@code stoppedByCollision} que se pone en el momento en que la gota queria bajar y no
 *       pudo -- y a partir de ahi {@code move()} <em>no hace nada mas nunca</em>. O sea que la
 *       gota <b>cae, toca el suelo y se queda clavada donde cayo</b>, que es exactamente una
 *       salpicadura, sin una sola linea de codigo de mi lado.</li>
 * </ul>
 *
 * <h2>Y por qué se dibuja con {@code quadSize} y se choca con {@code setSize}</h2>
 *
 * <p>Porque son dos cosas distintas y este proyecto ya pago la confesion una vez entera: el slash
 * declaraba {@code setSize(0.9, 0.9)} buscando un tama&ntilde;o de dibujo y lo unico que movia era
 * la caja de colision, asi que <b>el codigo decia 0,9 y la pantalla no mostraba nada</b>. Acá se
 * ponen las dos, y con el mismo valor -- 0,15 es un cuarto de bloque de caja, chica a proposito,
 * para que la gota no se enganche en el borde de un bloque al caer por una pared.</p>
 *
 * <h2>Render translucido</h2>
 *
 * <p>Porque los ultimos ticks se apagan con alpha. Un sprite opaco no puede desvanecerse, y una
 * gota que desaparece de golpe en el suelo parpadea.</p>
 */
public class SangreParticle extends TextureSheetParticle {

    /**
     * Dieciseis ticks, o sea cuatro quintos de segundo.
     *
     * <p><b>Lo suficiente para caer y para que la salpicadura se lea, corto para que no se
     * acumule.</b> Destripamiento dana cada dos segundos, asi que una pelea deja un goteo -- y un
     * goteo con gotas de un segundo entero se convierte en una mancha permanente en el suelo muy
     * rapido. La gota se cae y se va.</p>
     */
    public static final int LIFETIME = 16;

    /** Un cuarto de bloque. Una gota, no una bandera. */
    public static final float QUAD_SIZE = 0.15F;

    /**
     * Tres veces la caida normal.
     *
     * <p><b>Y el numero no se eligio a ojo: se midio contra la formula.</b> {@code yd} pierde
     * {@code 0.04 * gravity} por tick, o sea 0,12 con este valor; la distancia recorrida a
     * posteriori es aproximadamente {@code 0.02 * gravity * n²}, que con 3,0 son 1,5 bloques en
     * cinco ticks. O sea que una gota disparada desde la cintura de un zombi toca el piso antes de
     * un cuarto de segundo -- que es lo que se siente como peso.</p>
     *
     * <p><b>Y el numero se eligio contra la formula y no a ojo.</b> {@code Particle#tick} hace
     * {@code yd -= 0.04 * gravity} por tick, asi que la distancia recorrida a posteriori es
     * aproximadamente {@code 0.02 * gravity * n²}. Con 3,0 eso da <b>1,5 bloques en cinco
     * ticks</b>, que es la altura de un cuerpo: la gota toca el piso antes de un cuarto de segundo,
     * y los once ticks que le quedan de vida se pasan <em>clavada ahi</em>, que es la
     * salpicadura. Con la gravedad normal de {@code Particle}, 1,0, el mismo recorrido toma nueve
     * ticks -- la mitad de la vida volando, que se lee como una chispa.</p>
     *
     * <p><b>Corregido a las fuentes, y esto decia otra cosa:</b> la primera version de este
     * javadoc afirmaba que {@code move()} solo resolvia la colision por debajo de 3,0 bloques por
     * tick y que una gravedad mas alta haria <em>atravesar</em> el suelo. <b>Eso era falso</b>:
     * {@code MAXIMUM_COLLISION_VELOCITY_SQUARED} es {@code Mth.square(100.0)}, o sea cien bloques
     * por tick, y no hay gravedad razonable que lo toque. El guard que lo decia en el test mordio
     * por el motivo equivocado y me obligo a leer la constante -- que es la unica razon por la que
     * la mentira no llego a un playtest.</p>
     */
    public static final float GRAVITY = 3.0F;

    /**
     * Arrastre del aire, casi nulo.
     *
     * <p>El default de {@code Particle} es 0,98 y a una gota le frena visiblemente en tres ticks.
     * 0,99 la deja caer a la velocidad que la gravedad le dio, que es la lectura correcta: el agua
     * no frena en el aire.</p>
     */
    public static final float AIR_DRAG = 0.99F;

    /** Los ultimos ticks se apagan. Sin esto la gota desaparece de golpe y parpadea. */
    public static final int FADE_TICKS = 4;

    private final SpriteSet sprites;

    public SangreParticle(ClientLevel level, double x, double y, double z,
                          double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        // hasPhysics NO se toca: true es su valor por defecto y es la mitad del peso. Apagarlo deja
        // la gota flotando hasta que se le acabe la vida, que es una chispa y no una gota.
        this.quadSize = QUAD_SIZE;
        this.setSize(QUAD_SIZE, QUAD_SIZE);
        this.gravity = GRAVITY;
        this.friction = AIR_DRAG;
        this.setLifetime(LIFETIME);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        // Lineal y no al cuadrado como el humo de Purified: el humo se disuelve y la gota se apaga.
        // Un cuadrado aqui la dejaria opaca casi toda su vida y se iria toda en el ultimo tick,
        // que es exactamente el parpadeo de arriba con otro dibujo.
        int left = this.lifetime - this.age;
        if (left < FADE_TICKS) {
            this.setAlpha(Math.max(0.0F, (float) left / (float) FADE_TICKS));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Translucido porque lleva alpha. Opaco ignoraria el fade y dibujaria un cuadrado rojo
        // duro hasta el ultimo frame.
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
