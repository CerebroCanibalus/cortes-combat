package com.cortes.combat.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * Un corazoncito verde que sube: <b>alguien se curó</b>.
 *
 * <h2>Por qué sube, y por qué eso es lo contrario de la sangre</h2>
 *
 * <p>{@code SangreParticle} existe para pesar: {@code gravity 3} con {@code hasPhysics} encendido,
 * así que la gota cae rápido y el motor la clava donde chocó. <b>Esta es su opuesto exacto y por eso
 * se lee sola</b>: {@code gravity} negativa, sin física, y una velocidad hacia arriba que el propio
 * constructor fija. Un cuerpo que se cura <em>sube</em>; es la única partícula del mod cuyo
 * movimiento dice el signo del efecto.</p>
 *
 * <h2>La velocidad que llega del paquete se descarta</h2>
 *
 * <p><b>Y se descarta a propósito, como en {@code MoscaParticle}.</b> El paquete de {@code
 * sendParticles} no puede elegir una dirección: con conteo mayor que cero el cliente calcula la
 * velocidad como {@code gaussian() * maxSpeed} <b>por eje y con el mismo sigma en los tres</b>, o
 * sea que solo hay nube isotrópica. Un corazón que sube necesita el eje Y, y el eje Y no se puede
 * pedir por el paquete -- así que se pide acá adentro y la dispersión de afuera solo decide
 * <em>dónde</em> nace cada uno.</p>
 *
 * <h2>Y son pocos, porque la regeneración es constante</h2>
 *
 * <p>El ciclo de comida del proyecto cura <b>todo el tiempo</b> -- un punto de vida por segundo,
 * para siempre. Una partícula por tick convertiría la pantalla en un acuario y, peor, <b>dejaría de
 * significar</b>: el valor de este efecto está en que aparece cuando pasa algo, y si está siempre
 * encendido es un fondo. Por eso la vida es larga (26 ticks) y el que dispara tiene que ser
 * <b>parco</b> -- el diseño pide una cada ~10 ticks como techo.</p>
 */
public class CurarParticle extends TextureSheetParticle {

    /** Veintiséis ticks: sube despacio y se alcanza a ver el recorrido entero. */
    public static final int LIFETIME = 26;

    /** Nueve centímetros. Un corazón, no una bandera. */
    public static final float QUAD_SIZE = 0.09F;

    /**
     * Gravedad negativa: {@code Particle#tick} hace {@code yd -= 0.04 * gravity}, así que un valor
     * negativo <b>empuja hacia arriba</b> y el corazón acelera en vez de frenarse.
     *
     * <p>Chica a propósito: con la velocidad inicial de abajo alcanza para un ascenso lento y
     * constante, que es lo que se lee como "flotar". Un número grande lo dispara como una burbuja y
     * el efecto se vuelve cómico.</p>
     */
    public static final float GRAVITY = -0.12F;

    /** Poca resistencia, para que el ascenso no se frene antes de tiempo. */
    public static final float FRICTION = 0.98F;

    /** La velocidad inicial hacia arriba, en bloques por tick. Lenta: un corazón no es un chorro. */
    public static final double RISE_SPEED = 0.035D;

    /** Los últimos diez ticks se apagan, para que no se corte de golpe a mitad del ascenso. */
    public static final int FADE_TICKS = 10;

    private final SpriteSet sprites;

    public CurarParticle(ClientLevel level, double x, double y, double z,
                         double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        // La velocidad de afuera se pisa: solo decide dónde nace, no hacia dónde va. Ver el javadoc.
        this.xd *= 0.25D;
        this.yd = RISE_SPEED;
        this.zd *= 0.25D;
        // hasPhysics NO se toca (queda en true, su default): un corazón que atraviesa el techo se ve
        // raro, y con la física encendida el motor lo frena contra el bloque de arriba.
        this.quadSize = QUAD_SIZE;
        this.setSize(QUAD_SIZE, QUAD_SIZE);
        this.gravity = GRAVITY;
        this.friction = FRICTION;
        this.setLifetime(LIFETIME);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        int left = this.lifetime - this.age;
        if (left < FADE_TICKS) {
            this.setAlpha(Math.max(0.0F, (float) left / (float) FADE_TICKS));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
