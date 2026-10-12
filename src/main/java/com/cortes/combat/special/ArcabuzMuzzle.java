package com.cortes.combat.special;

import java.util.Random;

/**
 * Where the arcabuz's flash, its smoke and its fire come out, as numbers a test can open.
 *
 * <p>Pure, for the reason {@code ArcabuzShot} is: <b>a number nobody can load is a number nobody
 * checks</b>, and every one of these was wrong once.</p>
 *
 * <h2>Why the flash was coming out of the camera</h2>
 *
 * <p>It was spawned at {@code eye + look * 1.7}, and that point is <b>on the view axis</b> -- dead
 * centre, precisely where the crosshair is, precisely where the camera is. Seven tenths of a block
 * further along it still puts the flash at the middle of the screen, and a bright particle in the
 * middle of the screen reads as the screen flickering. The number was retuned twice (0.6, then
 * 1.7) and both times the complaint stayed the same, because <b>the distance was never the
 * problem: the two missing components were.</b></p>
 *
 * <p>A gun held in first person is down and to the right of the eye, and its barrel points along
 * its own axis rather than along the look vector. So the muzzle is described here as three offsets
 * against the <em>camera's</em> basis -- forward, right and up -- rather than as one distance along
 * one direction. Retuning it is now three numbers that can be read independently.</p>
 *
 * <h2>Why the first three are not read from the model's vertex</h2>
 *
 * <p>Because a particle cannot be spawned from inside the item renderer, and that was checked
 * rather than assumed: {@code GameRenderer#renderItemInHand} builds the pose stack it hands to
 * {@code renderHandsWithItems} with only the <em>inverse projection</em> in it, and the camera
 * rotation lives in the modelview stack instead. So there is nowhere inside the renderer to put a
 * particle.</p>
 *
 * <p><b>What that argument did not say is that the two halves cannot be separated in time.</b> The
 * matrix can be read during the draw and used later on the client's own tick, which is what
 * {@code ItemViewModel#drawnFrame} and {@link #FUSE_RIGHT} do -- and that is why the fuse stopped
 * coming out of the camera while the muzzle flash, which is still three numbers against the
 * camera's basis, has the same weakness it always had.</p>
 *
 * <p>A first person muzzle flash is a view space effect in any engine, and this one is placed in
 * view space against the camera basis. It is a number tuned to the drawn weapon rather than a
 * measurement of it -- and saying so is the point, because the alternative is a number that claims
 * to be a measurement and is not.</p>
 */
public final class ArcabuzMuzzle {

    /**
     * How far in front of the eye the barrel tip is, in blocks.
     *
     * <p>About one, not one and a half: this is measured from the <em>camera</em> and the drawn
     * musket is twenty four units long in a sixty four unit texture, so its own length is a third
     * of the gun's height and the barrel ends well short of arm's length in front of the eye.</p>
     */
    public static final double FORWARD = 1.05;

    /**
     * How far to the right of the eye the barrel is, in blocks. **The missing half of the bug.**
     *
     * <p>Positive is to the right, because that is the hand the gun is in. Roughly a quarter of a
     * block: the hand sits about there in the vanilla first person pose and the gun's bore is a
     * little inboard of the knuckles.</p>
     */
    public static final double RIGHT = 0.30;

    /**
     * How far below the eye the barrel is, in blocks. **The other missing half.**
     *
     * <p>Negative is down, because the drawn weapon is carried at hip height and the bore points
     * slightly up off it. About a fifth of a block.</p>
     */
    public static final double UP = -0.18;

    /**
     * El culatazo: cuanto empuja el disparo al que dispara, en velocidad.
     *
     * <p><b>0,25 es "ligeramente" medido, no elegido.</b> {@code push} suma a la velocidad y un
     * jugador en el piso frena a razon de 0,6 por tick, asi que el desplazamiento total es
     * {@code 0,25 / (1 - 0,6)} = <b>unos sesenta centimetros</b>: se siente el empujon y no te saca de
     * donde estabas parado. Es un tercio del impulso base de un lunge y la mitad de un empuje de
     * martillo, que es la escala que el juego ya tiene para "te movio un poco".</p>
     */
    public static final double RECOIL = 0.25;

    /** Y Mungus empuja el doble. Ver {@code Arcabuz#recoil}. */
    public static final double MUNGUS_RECOIL_FACTOR = 2.0;

    /**
     * The flash: how many particles, and how far apart they are spread.
     *
     * <p>Three and a twentieth of a block. One particle is a dot, and a dot at the muzzle of a
     * black powder gun is a spark rather than a report; three loose over a very small volume is
     * what reads as a flash. The spread is small because the flash is <em>at the barrel</em> and a
     * wide one puts it back into being an explosion, which is the thing {@code ArcabuzShot}'s
     * javadoc says the damage is not.</p>
     */
    public static final int FLASH_COUNT = 3;

    public static final double FLASH_SPREAD = 0.02;

    /**
     * The smoke: how much of it, and how fast it leaves.
     *
     * <p><b>Eighteen, and this is the number the request was really about.</b> A black powder shot
     * produces a great deal of smoke and it is most of what the player sees: the flash is over in a
     * frame and the cloud is still there a second later. One particle for it would be a puff, and a
     * puff on a gun that just killed four things reads as a decoration.</p>
     *
     * <p>Eighteen over a much wider volume than the flash and with real velocity, so the cloud
     * expands away from the muzzle instead of sitting on it. Smoke does not stop being visible
     * when the flash does, which is the whole reason this weapon's report lasts longer than its
     * light.</p>
     */
    public static final int SMOKE_COUNT = 18;

    public static final double SMOKE_SPREAD = 0.09;

    /** How fast the smoke leaves the barrel, in blocks a tick. */
    public static final double SMOKE_SPEED = 0.12;

    /**
     * How much lava a shot throws at each thing it hit.
     *
     * <p>Four along the line from the muzzle to the target. It is a streak and not an explosion:
     * what a player reads is "that fire went <em>there</em>", so the particles have to lie between
     * the two points, and four is enough to draw a line and few enough not to be a wall.</p>
     */
    /**
     * The smoke that comes off the fuse while it burns.
     *
     * <p><b>Smoke, and not sparks, and that is the playtest's word.</b> It was {@code ELECTRIC_SPARK}:
     * a blue-white electric arc is what aenchanted thing throws, and a match on a flintlock throws
     * a grey thread of smoke that hangs. Sparks were also the wrong <em>shape</em> -- they leave the
     * point and go, and what the player needs to see is that the fuse is lit for a second.</p>
     *
     * <p><b>Six is now the count of puffs, not the count of sparks.</b> The old reason for six was
     * "one spark is invisible", which was a fact about an electric spark's one-frame life and is not
     * a fact about smoke: smoke persists, so the count is about how much of a thread there is
     * rather than about being seen at all.</p>
     */
    public static final int FUSE_SMOKE_COUNT = 6;

    /** How far off the fuse tip each puff starts, in blocks. */
    public static final double FUSE_SMOKE_SPREAD = 0.012;

    /** How fast the smoke leaves the fuse, in blocks per tick. */
    public static final double FUSE_SMOKE_SPEED = 0.05;

    /**
     * Donde esta la mecha, <b>en el marco del item</b>: a la derecha, arriba y adelante del punto
     * donde la mano pone el arma.
     *
     * <p><b>Estos tres son los unicos numeros SIN VERIFICAR del archivo</b>, y su propio texto lo
     * admite: la conversion de unidades del modelo a este marco no se pudo derivar de las fuentes,
     * asi que quedaron elegidos a ojo. El marco en el que se usan, en cambio, sale de la matriz de
     * pose que el renderer entrega -- y ese marco es lo que se esta investigando ahora mismo.</p>
     *
     * <p><b>NO se tocan todavia, y esa es la decision.</b> El playtest reporto "la particula de humo
     * sale de en medio de la pantalla", y en medio de la pantalla es la camara: {@code fuseTip} arma
     * la posicion como {@code ojo + adelante + derecha + arriba}, asi que <b>una matriz cuyo
     * desplazamiento salga cero deja las tres contribuciones en nada</b> y el humo nace sobre la
     * lente. Es el mismo sintoma que ya tuvo la version anterior con un offset fijo en el marco de la
     * camara -- o sea que hay al menos dos causas posibles y se ven identicas.</p>
     *
     * <p>Antes de mover un solo numero hace falta saber cual es, y eso lo contesta el log, que ya
     * imprime la posicion: la linea {@code [arcabuz/mecha]} ahora imprime tambien la traslacion de
     * la matriz y la distancia al ojo. <b>Distancia al ojo menor a 0,2 es la camara; alrededor de
     * 0,7 es el arma.</b> Un tercer ancla inventada sin ese dato seria la tercera suposicion de la
     * misma serie.</p>
     */
    public static final double FUSE_RIGHT = -0.15;

    /** Como referencia: cuanto sube la mecha desde la raiz del item, en su marco. */
    public static final double FUSE_UP = 0.10;

    /** Como referencia: cuanto avanza la mecha desde la raiz del item, en su marco. */
    public static final double FUSE_FORWARD = 0.02;

    public static final int LAVA_PER_TARGET = 4;

    /**
     * How much lava a shot throws when it hit nothing.
     *
     * <p><b>Six, in directions nobody chose, and that is the requirement and not an
     * afterthought.</b> A gun that fired into an empty field and showed nothing would leave the
     * player unable to tell a wasted shot from a laggy one, and both of those read as the weapon
     * being broken. Six is fewer than the four-per-target times ten of a good shot, so a full
     * blast still looks like a blast and a whiff still looks like something happened.</p>
     */
    public static final int LAVA_WITHOUT_TARGETS = 6;

    private static final Random RANDOM = new Random();

    private ArcabuzMuzzle() {
    }

    /**
     * Whether a hit count should be drawn as streaks at the things hit, or as scatter.
     *
     * <p>A method rather than an if at the call site, so the "did anything get hit" question has
     * one answer that the streak count and the scatter count both read.</p>
     */
    public static boolean drewAtSomething(int targetsHit) {
        return targetsHit > 0;
    }

    /**
     * The lava this shot throws: {@link #LAVA_PER_TARGET} for each thing hit, or
     * {@link #LAVA_WITHOUT_TARGETS} in directions nobody chose.
     *
     * <p>One list and one branch, because the two cases differ in more than a count: a hit draws
     * streaks <em>towards specific points</em> and a whiff draws scatter in random directions, and
     * returning the count alone would leave the caller to remember that.</p>
     *
     * @param targetsHit how many things the blast actually damaged
     * @return how many lava particles this shot puts into the world
     */
    public static int lavaCount(int targetsHit) {
        return targetsHit > 0
                ? LAVA_PER_TARGET * targetsHit
                : LAVA_WITHOUT_TARGETS;
    }

    /**
     * A random unit direction, for a shot that hit nothing.
     *
     * <p><b>Uniform over the sphere and not over a box.</b> Spreading with
     * {@code random(-1, 1)} per component and leaving it there gives a cube, and a cube has a
     * visible bias: every particle in a whiff drifts the same way along one axis and the scatter
     * reads as a spray that was blown rather than as one that came out of a barrel.</p>
     *
     * <p>Two uniforms and an angle is the cheap way to get there: take the height uniformly and
     * the azimuth uniformly around the circle, and the result is uniform over the sphere with one
     * square root and one sine.</p>
     *
     * @return {@code {x, y, z}}, a unit vector
     */
    public static double[] randomDirection() {
        double y = RANDOM.nextDouble() * 2.0 - 1.0;
        double angle = RANDOM.nextDouble() * Math.PI * 2.0;
        double flat = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        return new double[]{flat * Math.cos(angle), y, flat * Math.sin(angle)};
    }

    /**
     * The flash and the smoke, as two separate lists of spawn instructions.
     *
     * <p>Separated because they are different effects with different lifetimes and different
     * counts, and a caller that drew them from one list would have to re-split it to give them
     * different particles. The record is there so the two cannot be swapped at a call site: a flash
     * of smoke is as wrong as smoke of nothing.</p>
     */
    public record Burst(double[] direction, int count, double spread, double speed) {
    }

    /**
     * The two bursts this shot makes, aimed down the given direction.
     *
     * @param direction a unit vector, which for the arcabuz is the player's look
     */
    /**
     * The one burst the fuse makes when it is struck: smoke, going up.
     *
     * <p>A {@code Burst} like any other, so the client's drawing code has nothing arcabuz-shaped
     * in it: the weapon decides what a burst is and the client only spawns what it is handed,
     * which is the same split {@link #bursts(double[])} already uses for the shot.</p>
     *
     * <p>Up, and that direction is the one thing here that is not a retune: the burst's direction
     * is a unit vector the caller multiplies by the speed, so {@code {0, 1, 0}} is straight up in
     * the world's frame -- which is where a thread of smoke off a match goes.</p>
     */
    public static Burst[] fuseSmoke() {
        return new Burst[]{new Burst(new double[]{0.0, 1.0, 0.0}, FUSE_SMOKE_COUNT,
                FUSE_SMOKE_SPREAD, FUSE_SMOKE_SPEED)};
    }
    public static Burst[] bursts(double[] direction) {
        return new Burst[]{
                new Burst(direction, FLASH_COUNT, FLASH_SPREAD, 0.0),
                new Burst(direction, SMOKE_COUNT, SMOKE_SPREAD, SMOKE_SPEED),
        };
    }
}