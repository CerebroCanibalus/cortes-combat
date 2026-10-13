package com.cortes.combat.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * El temblor de una flecha que pasa al lado.
 *
 * <h2>Un escaneo cada tres ticks, y no un {@code getEntities} por tick</h2>
 *
 * <p>Es el único disparador de FX que necesita lógica nueva, y la forma barata de hacerlo mal es
 * evidente: <b>un escaneo por tick de todos los proyectiles cercanos</b>. El proyecto ya tiene la
 * lección escrita con el reactor -- algo que corre cada tick y nunca se libera termina costando
 * <em>247 sonidos simultáneos</em> -- así que esto escanea <b>cada tres ticks</b>.</p>
 *
 * <p>Y tres alcanza: una flecha cruza alrededor de tres bloques por tick, así que un escaneo cada
 * tres ve la trayectoria en tramos de nueve bloques y el radio de zumbido son dos. El único caso
 * que se puede escapar es una flecha que entra y sale del radio entero dentro de tres ticks, o sea
 * una a más de un bloque por tick <b>y</b> pasando exactamente por el medio -- y esa flecha ya
 * pegó.</p>
 *
 * <h2>El aviso es de la cámara y no del jugador</h2>
 *
 * <p>El temblor va <b>local y no por payload</b>, a diferencia de los otros dos disparadores. La
 * razón es que la pregunta es del observador: "¿me pasó cerca <em>a mí</em>?" depende de dónde está
 * cada cliente, y el servidor tendría que mandar la flecha a todos para que cada uno mida lo suyo.
 * La flecha ya está en cada cliente: <b>el dato está donde se usa</b>.</p>
 */
public final class ArrowWhizz {

    /** Cada cuántos ticks se mira. Ver el javadoc: no es un número elegido por gusto. */
    public static final int SCAN_INTERVAL = 3;

    /** Hasta dónde se buscan proyectiles. Más lejos del radio de zumbido, para no perder el borde. */
    public static final double SCAN_REACH = 6.0D;

    /**
     * El aviso. Un grado.
     *
     * <p><b>Subió de 0,3, que era invisible.</b> La caída cuadrática por distancia ya lo baja a poco
     * más de la mitad a un bloque, así que el número de partida tiene que ser lo bastante grande
     * para sobrevivirla -- y una flecha que pasa al lado tiene que <em>sobresaltar</em>, no
     * insinuarse.</p>
     */
    public static final float DEGREES = 1.0F;

    /** Corto, porque una flecha que pasa es un instante. */
    public static final int TICKS = 4;

    private static int cooldown;

    private ArrowWhizz() {
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = SCAN_INTERVAL;

        Vec3 eye = minecraft.player.getEyePosition();
        AABB box = minecraft.player.getBoundingBox().inflate(SCAN_REACH);
        List<Projectile> near = minecraft.level.getEntitiesOfClass(Projectile.class, box);

        // El vector "derecha" del observador, para saber de qué lado pasa. Se arma con el yaw y no
        // con la mirada entera: el lado es una pregunta horizontal, y con pitch la proyección
        // cambiaría según dónde esté mirando el jugador.
        double yaw = Math.toRadians(minecraft.player.getYRot());
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);

        for (Projectile projectile : near) {
            Vec3 relative = eye.subtract(projectile.position());
            Vec3 velocity = projectile.getDeltaMovement();
            if (!WhizzGeometry.whizzes(relative.x, relative.y, relative.z,
                    velocity.x, velocity.y, velocity.z)) {
                continue;
            }
            double[] point = WhizzGeometry.closestPoint(relative.x, relative.y, relative.z,
                    velocity.x, velocity.y, velocity.z);
            double side = WhizzGeometry.side(point[0], point[2], rightX, rightZ);
            // **Un solo aviso por escaneo.** Diez flechas en el mismo tick son una ráfaga, no diez
            // sustos, y el ojo no puede leer diez direcciones a la vez.
            CameraShake.localImpulse(projectile.getX(), projectile.getY(), projectile.getZ(),
                    DEGREES, TICKS, side >= 0.0D ? 1.0F : -1.0F);
            return;
        }
    }

    /** Un cambio de mundo o de jugador: el contador no tiene que arrastrarse. */
    public static void reset() {
        cooldown = 0;
    }
}
