package com.cortes.combat.client;

import com.cortes.combat.config.ClientConfig;
import com.cortes.combat.net.CameraShakePayload;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Los temblores de camara <b>puntuales</b>: los que vienen de algo que ya no existe cuando los ves.
 *
 * <h2>Por que separado de {@link ReactorShake}</h2>
 *
 * <p>Porque la vida del temblor no se parece. El del reactor dura lo que dura la sobrecarga y el
 * cliente lo sabe porque el bloque esta ahi, sincronizado; el del orbe dura catorce ticks y su
 * origen se fue del mundo antes de que termine. Meter los dos en la misma clase obligaria a que la
 * lista del reactor acepte entradas sin block entity detras, que es un estado que nada mantiene.</p>
 *
 * <p>Los dos escuchan el mismo evento y los dos <b>suman</b> al pitch y al roll de la camara, asi
 * que se componen solos: un reventon al lado de un reactor sobrecargado sacude mas que cualquiera
 * de los dos por separado, y nadie tuvo que escribir esa regla.</p>
 *
 * <h2>El decaimiento es lo que lo hace un golpe y no un temblor de fondo</h2>
 *
 * <p>La amplitud arranca en el maximo y baja a cero a lo largo de la vida del impulso. Sin eso, un
 * reventon dejaria la camara sacudiendose y despues la cortaria de golpe -- que es lo que se lee
 * como un glitch y no como una explosion.</p>
 */
public final class CameraShake {

    /** Hasta donde se siente un reventon, en bloques. Mas alla no mueve un pixel. */
    private static final double REACH = 40.0;

    /**
     * Frecuencias del sacudon, en radianes por tick.
     *
     * <p><b>Altas a proposito, y la razon es la duracion.</b> El temblor del reactor puede ser lento
     * porque dura minutos y tiene que leerse como un zumbido de fondo; un impulso de catorce ticks
     * con una frecuencia baja se ve como dos o tres tirones y no como un golpe. Dos frecuencias que
     * no son multiplo una de la otra (3,1 y 4,7) para que la figura no se repita dentro del impulso
     * -- con una sola, catorce ticks alcanzan para ver el ciclo y se lee como una animacion.</p>
     */
    private static final float PITCH_HZ = 3.1F;
    private static final float ROLL_HZ = 4.7F;

    /** Los impulsos vivos. Inmutable no: entran por paquete y salen al vencer. */
    private static final List<Impulse> ACTIVE = new ArrayList<>();

    private CameraShake() {
    }

    /** Un temblor en curso: donde, cuanto, y entre que ticks del mundo vive. */
    private record Impulse(ResourceKey<Level> dimension, double x, double y, double z, float degrees,
                           float bornTick, float diesTick, float rollSign) {
    }

    /**
     * Un temblor que nace en el CLIENTE, sin pasar por la red.
     *
     * <p>Existe por un caso concreto: el zumbido de una flecha que pasa al lado. Esa pregunta es del
     * observador -- "me paso cerca <em>a mi</em>" -- asi que el servidor tendria que mandar la flecha
     * a todos para que cada uno mida lo suyo, cuando la flecha <b>ya esta en cada cliente</b>. El
     * dato esta donde se usa.</p>
     *
     * <p>{@code rollSign} es lo unico que un impulso local necesita y uno de red no: el lado por el
     * que paso. Se aplica <b>solo al roll</b> y no al pitch, porque "de la derecha" es una pregunta
     * horizontal y el pitch no tiene lados. Un signo negativo y no un angulo negativo: el filtro de
     * amplitud descarta los negativos, asi que un angulo con signo apagaria el impulso entero.</p>
     */
    public static void localImpulse(double x, double y, double z, float degrees, int ticks,
                                    float rollSign) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float now = minecraft.level.getGameTime();
        ACTIVE.add(new Impulse(minecraft.level.dimension(), x, y, z, degrees,
                now, now + ticks, rollSign));
    }

    /** Llego un aviso del servidor. El reloj es el del mundo, para que todos los frames coincidan. */
    public static void accept(CameraShakePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float now = minecraft.level.getGameTime();
        ACTIVE.add(new Impulse(minecraft.level.dimension(), payload.x(), payload.y(), payload.z(),
                payload.degrees(), now, now + payload.ticks(), 1.0F));
    }

    /** Un cambio de mundo: nada del viejo deberia sobrevivir. Misma forma que {@code ReactorShake}. */
    public static void clear() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ACTIVE.isEmpty() || !ClientConfig.CAMERA_SHAKE.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceKey<Level> here = minecraft.level.dimension();
        float time = minecraft.level.getGameTime() + (float) event.getPartialTick();
        // **Vencidos afuera, y ANTES de dibujar.** Un impulso que se quedo a vivir es una camara que
        // tiembla sola para siempre, y el cliente no tiene de donde sacarlo.
        //
        // **Y el filtro por dimension no es adorno: es la unica defensa que hay.** El reloj de un
        // mundo nuevo arranca de cero, asi que un impulso nacido en el mundo anterior con `dies`
        // en 500.014 no venceria nunca -- la camara temblaria durante siete horas. `clear()` no
        // alcanza como respuesta porque hay que acordarse de llamarlo, y este proyecto ya sabe lo
        // que vale un `clear()` que nadie llama.
        ACTIVE.removeIf(impulse ->
                !here.equals(impulse.dimension()) || time > impulse.diesTick());
        if (ACTIVE.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        float pitch = 0.0F;
        float roll = 0.0F;
        for (Impulse impulse : ACTIVE) {
            double distance = Math.sqrt(camera.distanceToSqr(impulse.x(), impulse.y(), impulse.z()));
            float amplitude = amplitudeAt(distance, impulse.degrees());
            if (amplitude <= 0.0F) {
                continue;
            }
            float life = (impulse.diesTick() - time) / (impulse.diesTick() - impulse.bornTick());
            float energy = amplitude * Math.max(0.0F, life);
            pitch += energy * Mth.sin(time * PITCH_HZ);
            // El signo va aca y no en la amplitud: ver `localImpulse`. Un impulso de red siempre
            // trae 1, asi que su roll no cambia.
            roll += energy * impulse.rollSign() * Mth.cos(time * ROLL_HZ);
        }
        if (pitch != 0.0F || roll != 0.0F) {
            event.setPitch(event.getPitch() + pitch);
            event.setRoll(event.getRoll() + roll);
        }
    }

    /**
     * La amplitud a una distancia, con caida <b>cuadratica</b>.
     *
     * <p>Cuadratica y no lineal por la misma razon que la del reactor: lineal deja el borde del
     * alcance a media fuerza, y ahi un reventon lejano se siente como uno cercano. Al cuadrado, a
     * mitad del alcance ya es un cuarto, y el borde es un susurro.</p>
     */
    private static float amplitudeAt(double distance, float max) {
        if (distance >= REACH) {
            return 0.0F;
        }
        float t = (float) (1.0 - distance / REACH);
        return max * t * t;
    }
}
