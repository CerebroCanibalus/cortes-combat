package com.cortes.combat.client;

import com.cortes.combat.special.LungeDamage;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * The lunge's movement, on the client, and it is one call.
 *
 * <h2>Why there is no loop here any more</h2>
 *
 * <p>This class used to set the player's velocity every tick from a decaying curve, which took
 * <em>three</em> attempts and a table of numbers to build, and which was still the wrong shape. It
 * set the velocity rather than adding to it so the deceleration was the curve instead of an
 * accident of accumulation, and because it owned the velocity outright the player had nothing to
 * steer with -- the first version was reported as "if I go backwards it still pulls me forward", and
 * the fix was a hand-rolled blend of the camera into the committed direction, which then turned the
 * lunge into a flight.</p>
 *
 * <p>Vanilla's riptide has none of that. It is:</p>
 *
 * <pre>{@code
 * player.push(f2, f3, f4);
 * player.startAutoSpinAttack(20, 8.0F, stack);
 * if (player.onGround()) player.move(MoverType.SELF, new Vec3(0.0, 1.2, 0.0));
 * }</pre>
 *
 * <p>One impulse, the engine's own physics, and the engine's own air control. Nothing here fights
 * the player for the controls, so the steering the blend was trying to fake is simply what happens:
 * hold a direction and the lunge leans there, hold back and it fights you. The movement also stays
 * honest with the collision, which is why a wall stops a lunge for free -- and it could not before,
 * because a position the server sets is not a position anything collides with.</p>
 *
 * <h2>Why the client is the one that moves</h2>
 *
 * <p>It cannot be the server. A velocity the server sets is ignored outright for a player, because
 * the player's position is driven by the movement report the client sends every tick, which is
 * authoritative. That was verified in vanilla's sources and it is why this was ever a teleport --
 * and why the teleport version was ten to thirty discrete jerks.</p>
 *
 * <p>So the client moves and the server judges. The server does not need permission to be given to
 * it: the movement report already told the server where the player went, and {@code Lunge} compares
 * that against the tier's ceiling and simply stops honouring the lunge if it is further than the
 * impulse could account for.</p>
 */
public final class LungeMotion {

    private LungeMotion() {
    }

    /**
     * Throws the player. Once.
     *
     * <p>The direction is the look vector flattened to the ground plane, so a lunge cannot gain
     * height: a step that can climb is a mobility tool, and a mobility tool is a way out of a
     * fight. Looking straight up throws you straight forward, which is the same rule the old
     * version applied and the same one vanilla's riptide applies by normalising a look vector whose
     * pitch can be anything.</p>
     *
     * <p>Additive, because that is what {@code push} is -- {@code setDeltaMovement(delta.add(x, y,
     * z))}, two lines, verified in the sources. So a lunge taken at a run goes further than one
     * taken from standing still, which is the same relationship every knockback in the game has and
     * is inside the ceiling the server checks against.</p>
     *
     * <h2>Retirada: quién decide la direccion y por qué aca</h2>
     *
     * <p>Que el lunge salga para atras cuando el jugador esta agachado con Retirada es
     * <b>una diferencia de un signo</b>, y se toma en este archivo porque <b>quien empuja es el
     * cliente</b>: el servidor no puede mover a un jugador, que es la razon entera de que este
     * metodo exista. Los dos lados leen el mismo hecho -- el agachado, sincronizado, y el
     * encantamiento, en el stack que ambos tienen -- y cada uno actua sobre su mitad. El cliente da
     * el impulso; el servidor resuelve el golpe del frente y despues deja que la distancia
     * recorrida haga el resto del trabajo.</p>
     */
    public static void begin(LocalPlayer player, int materialRank, boolean retreating) {
        if (player == null || player.isSpectator() || !player.isAlive()) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() < 1.0E-6) {
            flat = new Vec3(0.0, 0.0, 1.0);
        }
        flat = flat.normalize();

        double impulse = LungeDamage.impulseFor(materialRank);
        // Retirada: la mirada sigue al enemigo y el cuerpo sale al reves. Por eso el signo se aplica
        // al impulso y no a la mirada -- voltearse para huir seria regalar la espalda, que es
        // exactamente lo que este encantamiento existe para no hacer. Y es una unica diferencia:
        // el salito, el impulso y el techo que el servidor mide son los mismos, y direccion-
        // agnosticos, asi que una marcha atras no necesita una segunda tabla de numeros.
        double sign = retreating ? -1.0 : 1.0;
        player.push(flat.x * impulse * sign, 0.0, flat.z * impulse * sign);

        // The hop, and only from the ground: mid-air it would be a second jump nobody asked for.
        if (player.onGround()) {
            player.move(MoverType.SELF, new Vec3(0.0, LungeDamage.HOP_HEIGHT, 0.0));
        }
    }
}