package com.cortes.combat.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * Un punto de color: <b>la lectura de un golpe</b>.
 *
 * <h2>Por qué es un punto de 4x4 y no una partícula grande</h2>
 *
 * <p>La versión anterior de este diseño agrandaba el {@code DAMAGE_INDICATOR} de vanilla. El dueño
 * lo rechazó con el argumento correcto: <b>las partículas grandes hacen clutter</b>, y agrandar una
 * no arregla que sea una. La vuelta fue hacerla <em>más chica y más informativa</em>: muchos puntos
 * chicos que se leen como un número, con el color diciendo <em>qué pasó</em> y la cantidad
 * diciendo <em>cuánto</em>.</p>
 *
 * <h2>Los cuatro números, y por qué cada uno</h2>
 *
 * <ul>
 *   <li><b>{@code quadSize} 0,10</b> -- diez centímetros. Es el número que decide el dibujo, y va
 *       acá y <b>también</b> en {@code setSize} porque son dos cosas distintas: este proyecto ya
 *       pagó entero el haberse confundido, cuando el slash declaraba {@code setSize(0.9, 0.9)}
 *       buscando un tamaño de dibujo y lo único que movía era la caja de colisión.</li>
 *   <li><b>{@code gravity} 0,45</b> -- <b>menos de la mitad</b> que la sangre, a propósito. La gota
 *       de {@code SangreParticle} cae con peso porque una gota pesa; un punto de lectura <b>no tiene
 *       que caer</b>, tiene que quedarse donde el ojo lo pueda leer. Con 0,45 se hunde despacio y
 *       acompaña sin irse.</li>
 *   <li><b>{@code friction} 0,88</b> -- frena rápido, así que el salpicón inicial se convierte en un
 *       punto quieto en dos o tres ticks. Es lo que lo hace legible: si sigue viajando, hay que
 *       seguirlo con el ojo.</li>
 *   <li><b>Vida 12 con los últimos 5 apagándose</b> -- lo suficiente para contarlo, corto para que
 *       una ráfaga de golpes no llene la pantalla. <b>Y el fade es lineal</b>, por la misma razón
 *       que en la sangre: un cuadrado dejaría el punto opaco casi toda su vida y se iría entero en
 *       el último tick, que es un parpadeo con otro dibujo.</li>
 * </ul>
 *
 * <h2>El color no está en la textura</h2>
 *
 * <p>El PNG es de 4x4 en escala de grises y el color entra por {@code setColor}, con los tres
 * canales que saca {@link HurtPalette}. Es lo que permite que <b>un solo tipo</b> cubra los quince
 * colores del diseño en vez de quince registros.</p>
 */
public class HurtParticle extends TextureSheetParticle {

    /** Doce ticks: se cuenta de un vistazo y no se acumula en una ráfaga. */
    public static final int LIFETIME = 12;

    /** Diez centímetros. Chico a propósito: es un punto, no un efecto. */
    public static final float QUAD_SIZE = 0.10F;

    /**
     * Menos de la mitad de la gravedad de la sangre.
     *
     * <p>Una gota cae porque pesa; un punto de lectura tiene que quedarse legible. Con 0,45 se hunde
     * lo justo para dar volumen y no se va de cuadro.</p>
     */
    public static final float GRAVITY = 0.45F;

    /** Frena rápido para que el salpicón se vuelva un punto quieto en dos o tres ticks. */
    public static final float FRICTION = 0.88F;

    /** Los últimos ticks se apagan. Lineal, por la misma razón que en la sangre. */
    public static final int FADE_TICKS = 5;

    /** Las dos texturas de {@code particles/hurt.json}: el golpe normal y el golpe pesado. */
    public static final int SPRITE_COUNT = 2;

    /** El índice del pesado. El normal es el cero. */
    private static final int HEAVY_INDEX = 1;

    /** El último índice de la lista, que es lo que hay que pasar como "lifetime". */
    private static final int LAST_SPRITE_INDEX = SPRITE_COUNT - 1;

    private final SpriteSet sprites;

    public HurtParticle(ClientLevel level, double x, double y, double z,
                        double dx, double dy, double dz, SpriteSet sprites,
                        HurtParticleOptions options) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        // **SpriteSet no tiene get(indice), y el proyecto ya lo tenia escrito.** Sus dos accesos
        // son get(age, lifetime), que es list.get(age * (size-1) / lifetime), y get(RandomSource),
        // que es una moneda al aire. El primero recorre la lista como una animacion -- con dos
        // texturas, un golpe pesado saldria como un tap la mitad de las veces -- y el segundo
        // elegiria distinto en cada spawn.
        //
        // Asi que se invierte, igual que en SlashParticle: el indice que queremos entra como "age"
        // y el ultimo indice como "lifetime", y la division cae exacta -- index * (size-1)/(size-1).
        //
        // Y el try/catch es por el caso que ya costo dos lanzamientos: si el JSON de descripcion no
        // cargo, el set esta vacio y get() tira en el hilo de render. Una particula que no dibuja es
        // un bug cosmetico; un crash en el primer golpe no es lo mismo.
        try {
            this.setSprite(sprites.get(options.heavy() ? HEAVY_INDEX : 0, LAST_SPRITE_INDEX));
        } catch (RuntimeException unbound) {
            this.setSprite(null);
        }
        this.setColor(HurtPalette.red(options.rgb()),
                HurtPalette.green(options.rgb()),
                HurtPalette.blue(options.rgb()));
        this.quadSize = QUAD_SIZE;
        this.setSize(QUAD_SIZE, QUAD_SIZE);
        this.gravity = GRAVITY;
        this.friction = FRICTION;
        this.setLifetime(LIFETIME);
    }

    @Override
    public void tick() {
        super.tick();
        int left = this.lifetime - this.age;
        if (left < FADE_TICKS) {
            this.setAlpha(Math.max(0.0F, (float) left / (float) FADE_TICKS));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Translúcido porque lleva alpha: opaco ignoraría el fade y dibujaría un cuadradito duro
        // hasta el último frame.
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
