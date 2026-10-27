package com.cortes.combat.reach;

import com.cortes.combat.core.weapon.ReachProfile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Everything standing in a cone, nearest first.
 *
 * <h2>Why this is its own class and not a method in {@code SpecialHandler}</h2>
 *
 * <p>Because it was one, and the maza needed the same thing for a different reason. The halberd's
 * sweep answers "what did this arc cover"; the maza's strike answers "what did this swing cover",
 * and both questions are <b>the same question</b> &mdash; so one of them has to call the other.</p>
 *
 * <p><b>Copying it would have been the easy wrong answer</b>, and the shape of the copy is the
 * part that costs: a private method that sorts by distance, next to another private method that
 * sorts by distance, is two places where "nearest first" is decided. They agree today and one of
 * them would change the day somebody wanted the sweep to reach further. {@code Reach} already owns
 * the cone test itself and stays pure; this owns the enumeration, which needs a world and so
 * cannot live there.</p>
 *
 * <h2>Why nearest first</h2>
 *
 * <p>So that when the invulnerability window eats the second hit of two overlapping mobs, the one
 * that dies is the one the player was looking at. It is the same reason the sweep sorts: the
 * player's intent decides who pays first, and distance is the only reading of intent the server
 * has that the client cannot forge.
 */
public final class ConeTargets {

    private ConeTargets() {
    }

    /**
     * Every living thing in the cone, nearest first, capped.
     *
     * <p>The cap is applied <em>after</em> the sort and not during the scan, because a cap applied
     * while collecting keeps whichever mobs the world's entity list happened to yield first &mdash;
     * which is not the same set, and is not stable between two players looking at the same crowd.</p>
     *
     * @param level the world to scan
     * @param source the player swinging
     * @param reach the cone, which may be the weapon's or a derived one
     * @param limit how many to return; below one means no cap
     * @return nearest first, never including the source
     */
    public static List<LivingEntity> nearestInCone(Level level, LivingEntity source,
                                                    ReachProfile reach, int limit) {
        List<LivingEntity> found = new ArrayList<>();
        // **Inflated by one on top of the distance**, and that extra block is load-bearing: `inCone`
        // tests the target's bounding box rather than its origin, so an entity standing with its
        // feet just past the limit can still be clipped by the swing.
        AABB box = source.getBoundingBox().inflate(reach.distance() + 1.0);
        // **Ojo, vista y tangentes UNA vez por escaneo.** `getEyePosition` y `getLookAngle`
        // alocan un `Vec3` cada uno, y los dos `Math.tan` del test son el 92% de su costo
        // (medido: 25,5 de 27,8 ns por candidato, ver `ConeMath#prepare`). Un barrido de la
        // alabarda sobre diez cuerpos pagaba veinte tangentes y veinte `Vec3` por uno.
        Vec3 eye = source.getEyePosition();
        Vec3 look = source.getLookAngle();
        ConeMath.Prepared limits = Reach.prepared(reach);
        for (Entity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == source || !candidate.isAlive()
                    || candidate.isSpectator() || candidate.isRemoved()) {
                continue;
            }
            if (Reach.inCone(eye, look, candidate.getBoundingBox(), limits)) {
                found.add((LivingEntity) candidate);
            }
        }
        found.sort((a, b) -> Double.compare(a.distanceToSqr(source), b.distanceToSqr(source)));
        return limit > 0 && found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    /** Every living thing in the cone, nearest first, uncapped. */
    public static List<LivingEntity> nearestInCone(Level level, LivingEntity source,
                                                    ReachProfile reach) {
        return nearestInCone(level, source, reach, 0);
    }

    /**
     * El mas cercano de una lista que <b>ya</b> paso el cono, o {@code null} si no hay ninguno.
     *
     * <h2>Existe porque el cliente y el servidor contestaban distinto</h2>
     *
     * <p>{@link #nearestInCone} ordenaba por cercania y {@code ConeTargeting} elegia por
     * <b>angulo</b>. O sea que las dos mitades de la misma pregunta -- "a quien le pego" -- daban
     * respuestas distintas, y con un arma larga el cliente apuntaba al enemigo de mas atras
     * mientras el servidor le cobraba al de adelante. El playtest lo leyo como un bug de Ensarte
     * ("el cono prefiere al mas lejano") y <b>era de las dos respuestas, no del encantamiento</b>:
     * el mismo par de enemigos en una espada daba el mismo desacuerdo, solo que ahi la diferencia
     * de un bloque no se nota.</p>
     *
     * <p><b>La regla es una sola y es esta: el mas cercano, siempre.</b> El angulo no desempata
     * porque los dos ya pasaron el cono, y entre dos cuerpos que estan dentro de lo que el arma
     * alcanza, el de mas cerca es el que el jugador se va a comer. Un desempate por angulo es
     * volver a tener dos opiniones, con un margen mas chico.</p>
     *
     * <p>Pura y con la distancia como funcion para que se pueda testear sin un mundo: la decision
     * no es de Minecraft, lo que la alimenta si.</p>
     *
     * @param inCone   los candidatos que ya pasaron el filtro del cono
     * @param distance la distancia de cada uno al que pega
     */
    public static <T> T nearest(List<T> inCone, java.util.function.ToDoubleFunction<T> distance) {
        T best = null;
        double bestDistance = Double.MAX_VALUE;
        for (T candidate : inCone) {
            double d = distance.applyAsDouble(candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /** Kept so a caller with a position does not need a {@link BlockPos} import it does not use. */
    static double distanceSquaredTo(LivingEntity from, LivingEntity to) {
        return from.distanceToSqr(to);
    }
}
