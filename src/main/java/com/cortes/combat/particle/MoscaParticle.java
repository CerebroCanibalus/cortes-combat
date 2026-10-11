package com.cortes.combat.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/**
 * Una mosca revoloteando.
 *
 * <h2>Lo que separa un revoloteo de un ruido</h2>
 *
 * <p>Un revoloteo <b>no es movimiento aleatorio por tick</b>. Eso es estatica: la posicion cambia
 * tanto y tan rapido que el ojo no ve un insecto, ve un parpadeo -- y la partícula se lee como un
 * error de render antes que como un animal. Una mosca hace otra cosa: <b>elige una direccion, se
 * tira ahi, se queda un instante, y cambia de idea.</b></p>
 *
 * <p>Asi que hay dos tiempos, y ninguno es el tick del juego:</p>
 *
 * <ul>
 *   <li><b>El dardo</b> ({@link #DART_TICKS}): cada tres ticks elige hacia donde va. Ese es el
 *       "cambio de idea".</li>
 *   <li><b>El acercamiento</b> ({@link #STEERING}): en vez de poner la velocidad del dardo de una,
 *       se <em>acelera</em> hacia ella. Ese empujón brusco seguido de un tramo mas derecho es
 *       exactamente lo que se ve cuando una mosca da tres metros y se queda quietas -- y es lo
 *       que un lerp suave por tick no puede producir, porque no tiene el momento en que decide.</li>
 * </ul>
 *
 * <h2>Sin gravedad y sin choque, a proposito</h2>
 *
 * <p>{@code gravity} en cero porque una mosca no cae: si cayera, la nube de Rey de las Moscas se
 * apilaria en el piso en dos segundos y seria un charco de insectos. Y {@code hasPhysics} en falso
 * porque <em>si</em> chocara, la nube se estancaria contra la primera pared y no entraria por una
 * cueva -- que es justo donde un tiro de asedio se usa.</p>
 *
 * <p>Eso deja una mosca que atraviesa bloques, que es lo que hace una mosca de verdad.</p>
 *
 * <h2>Sin servidor todavia</h2>
 *
 * <p>Esta partícula <b>existe y esta probada, pero nada la dispara todavia</b>: Rey de las Moscas
 * es el encantamiento que la va a usar y esta declarado con sus numeros en {@code CombatEnchantRules}
 * a la espera de su camino de municion. El spawn helper esta en {@link MoscaParticles} y es una
 * sola llamada para el dia que se conecte.</p>
 */
public class MoscaParticle extends TextureSheetParticle {

    /** Dos segundos y medio. Una nube tiene que durar lo que dura la infeccion, que son cuatro. */
    public static final int LIFETIME = 50;

    /** Un cuarto de bloque: una mosca es un punto, y varios puntos son una nube. */
    public static final float QUAD_SIZE = 0.12F;

    /**
     * Cada cuantos ticks cambia de idea.
     *
     * <p>Tres. Con uno es estatica; con seis es un objeto que vuela en linea recta y dobla, que se
     * lee como un proyectil. Tres es el punto donde todavía se distingue cada cambio.</p>
     */
    public static final int DART_TICKS = 3;

    /**
     * La velocidad a la que se tira.
     *
     * <p>Chica -- 0,18 bloques por tick, o sea unos cuatro bloques por segundo -- porque una nube
     * que se dispersa rapido deja de ser una nube. La mosca tiene que parecer que esta <em>en</em>
     * el lugar, revoloteando, y no que esta de paso.</p>
     */
    public static final float DART_SPEED = 0.18F;

    /**
     * Que tan duro se acelera hacia el dardo.
     *
     * <p>0,65, que es brusco. Es el numero que hace la diferencia entre "revolotea" y "flota":
     * alto es un insecto que cambia de rumbo de golpe, bajo es una hoja en el aire.</p>
     */
    private static final float STEERING = 0.65F;

    /** Las moscas suben y bajan menos que se desplazan: vuelan a la altura que estan. */
    private static final float VERTICAL_BIAS = 0.55F;

    private final SpriteSet sprites;

    private double targetX;
    private double targetY;
    private double targetZ;
    private int sinceDart;

    public MoscaParticle(ClientLevel level, double x, double y, double z,
                         double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        // Arrastre nulo, y no es un descuido: el que frena la mosca es el STEERING hacia el dardo
        // nuevo. Con arrastre los dos se pelearian y el resultado seria un bote a la mitad de la
        // velocidad, que se lee como una particula lenta y no como un animal.
        this.friction = 1.0F;
        this.quadSize = QUAD_SIZE;
        this.setSize(QUAD_SIZE, QUAD_SIZE);
        this.setLifetime(LIFETIME);
        this.setSpriteFromAge(sprites);
        dart();
        // Y se le pega el primer dardo a la velocidad que trajo al nacer, para que no arranque
        // quieta: una mosca que aparece parada y despues se mueve es dos eventos distintos.
        this.xd = targetX;
        this.yd = targetY;
        this.zd = targetZ;
    }

    /** Hacia donde se tira ahora. Simetrico en los tres ejes, y el vertical mas corto. */
    private void dart() {
        targetX = (this.random.nextDouble() - 0.5) * 2.0 * DART_SPEED;
        targetY = (this.random.nextDouble() - 0.5) * 2.0 * DART_SPEED * VERTICAL_BIAS;
        targetZ = (this.random.nextDouble() - 0.5) * 2.0 * DART_SPEED;
        sinceDart = 0;
    }

    @Override
    public void tick() {
        // Antes de super.tick(), porque es lo que decide hacia donde se mueve en ESTE frame. El
        // orden importa: super aplica la gravedad, mueve y despues aplica el arrastre, y una
        // velocidad puesta despues de esa llamada solo llega al movimiento del tick siguiente --
        // que es un lag de un frame por particula y no se nota hasta que hay doscientas.
        if (++sinceDart >= DART_TICKS) {
            dart();
        }

        this.xd += (targetX - this.xd) * STEERING;
        this.yd += (targetY - this.yd) * STEERING;
        this.zd += (targetZ - this.zd) * STEERING;

        super.tick();
        this.setSpriteFromAge(this.sprites);

        // Se apaga al final. Una nube que desaparece de golpe se lee como un error de red.
        int left = this.lifetime - this.age;
        if (left < FADE_TICKS) {
            this.setAlpha(Math.max(0.0F, (float) left / (float) FADE_TICKS));
        }
    }

    /** Los ultimos ticks se apagan. */
    private static final int FADE_TICKS = 8;

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
