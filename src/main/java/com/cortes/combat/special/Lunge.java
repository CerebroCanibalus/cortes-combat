package com.cortes.combat.special;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.attack.CombatEngine;
import com.cortes.combat.attack.Knockback;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.particle.SlashParticles;
import com.cortes.combat.registry.ModEffects;
import com.cortes.combat.sound.CombatSounds;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.state.LungeState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The server's half of a lunge: it moves nobody, and it judges.
 *
 * <h2>The authority model</h2>
 *
 * <p>The client throws the player. This class cannot do it and stopped trying: a velocity the
 * server sets is ignored outright for a player, because the player's position is driven by the
 * movement report the client sends every tick. Teleporting around that worked -- {@code
 * teleportRelative} is the one call that moves a player -- and produced ten to thirty discrete
 * corrections per lunge, which the playtest called jerks.</p>
 *
 * <p>So the client moves, and this decides whether the lunge counted. It does not ask first. The
 * movement report already arrived: by the time this runs, the server knows exactly where the player
 * went and how far that is from where the lunge began. The measurement is the permission.</p>
 *
 * <p>Concretely, every tick: the displacement is checked against the tier's ceiling, the space the
 * body actually passed through is swept, and everything in it is hit. A client that lied about the
 * move gets no damage and no invulnerability -- <b>but it does still slide</b>, because nothing can
 * undo it. That is the deliberate price of having no rollback: an honest player never notices, and a
 * dishonest one gets free movement rather than free invulnerability, which is the difference between
 * a sloppy client and a broken game.</p>
 *
 * <h2>The hit volume</h2>
 *
 * <p>Vanilla's, read out of the sources: {@code LivingEntity#checkAutoSpinAttack} sweeps
 * {@code before.minmax(after)}, the volume between the last two ticks' positions. Not a box drawn in
 * front of the player at the press, which is what this used to be, and which meant a ropera could
 * swing at everything four blocks ahead while standing safely behind cover. The design says the step
 * is the price and it is paid in position; a hit volume that ignored where the player went charged
 * them for nothing.</p>
 *
 * <p>Unlike vanilla this does not stop at the first thing it touches. Riptide's {@code break} is a
 * one hit and out; a thrust that goes through a line is the whole character of the ropera, and
 * multiplying by hit order is where the escalating damage lives.</p>
 */
public final class Lunge {

    /** Matches the anti-double-hit window the normal attack uses. */
    private static final int IFRAME_TICKS = 10;

    /**
     * The invulnerability a lunge holds for while it is in flight, as opposed to on the press.
     *
     * <p><b>Why this is not the same number as {@link LungeDamage#INVULNERABLE_TICKS}:</b> the whole
     * lunge is already invulnerable either way, so the press value is a formality that only matters
     * if something hurts the player on the frame they committed. This one is what makes the dodge
     * legible, and the two are different lengths on purpose.</p>
     *
     * <p>What the playtest found is the reason this is written down at all. Vanilla's
     * {@code LivingEntity#beginTick} decrements {@code invulnerableTime} <em>before</em> the server
     * tick event our ticker runs on, so re-arming it to six every tick pins it at five for the whole
     * dash: an invisible, total, uninterruptible dodge. And then the lunge touches a mob, which sets
     * its {@code invulnerableTime} to zero, which stops us re-arming, and the player is <b>halfway
     * through a dash that was never a dodge</b> -- the complaint that arrived as "I don't think it
     * makes me invulnerable" and as "it doesn't do damage" at the same time.</p>
     *
     * <p>Twelve keeps a full window for the length of the run, so nothing the game does to the
     * player mid-lunge can shorten it. It is not a big number: the price is the button, which costs
     * eight swings, and the price of the i-frames is that they only exist while somebody is paying
     * it.</p>
     */
    private static final int LUNGE_IFRAME_TICKS = 12;

    /**
     * One line per lunge, ungated.
     *
     * <p>Every other trace line in this project sits behind {@code /cc trace}, and that is the right
     * default: a session spams hundreds of swings and a log nobody reads is how a real error gets
     * missed.</p>
     *
     * <p>This one is not gated, for two reasons. It is one line per <em>lunge</em> rather than per
     * tick, and a lunge costs eight swings of downtime, so it is the rarest thing in the game and
     * cannot drown anything. And the number on it -- how far the impulse actually carried the player
     * -- is not computable from the source: it is whatever the engine's friction decided, which is
     * the one thing this whole change was written to stop predicting. Three playtests in this
     * project's history produced nothing because a flag nobody flipped was the difference between
     * a measurement and a guess.</p>
     */
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("cortescombat");

    private Lunge() {
    }

    /**
     * The shape of the state, per tick, for the sessions where something is wrong and the summary
     * line cannot say why.
     *
     * <p>Three fields because there were three hypotheses and no way to tell them apart:</p>
     *
     * <ul>
     *   <li>{@code fase} -- whether the lunge is <b>0</b> (its own {@code tick}, which is what
     *       decides whether the window is open) or <b>1</b> (a state a world load or a sync could
     *       have restored, which is not running at all). A lunge that never advances looks
     *       identical to a lunge that is not being ticked.</li>
     *   <li>{@code dist} -- how far the body has got from the start. If this stops while
     *       {@code vel} is still high, the player is being stopped by something that is not a wall
     *       and the sweep is describing a standstill.</li>
     *   <li>{@code vel} -- horizontal speed right now. The impulse is the only thing this mod
     *       applies, so a lunge that is not moving has had its velocity overwritten and the question
     *       is by what.</li>
     * </ul>
     *
     * <p>Gated, unlike the summary line, because it is twenty lines per lunge.</p>
     */
    private static void trace(LungeState state, ServerPlayer player, double x, double z) {
        if (!com.cortes.combat.CombatTrace.isEnabled()) {
            return;
        }
        com.cortes.combat.CombatTrace.server(player.getName().getString(),
                String.format(java.util.Locale.ROOT,
                        "[lunge] t=%02d fase=%d dist=%.2f vel=%.2f s_x=%.2f s_z=%.2f x=%.2f z=%.2f",
                        state.tick(), state.running() ? 0 : 1, state.travelled(x, z),
                        Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z),
                        state.startX(), state.startZ(), x, z));
    }

    /**
     * Opens a lunge and nothing else.
     *
     * <p>No hit is resolved here, which is the change that matters. The old version built a box of
     * the promised length at the press and damaged everything in it before the player had moved a
     * centimetre. This records where they are now, and {@link #tick} does the rest as the ground
     * actually gets covered.</p>
     *
     * <p>Invulnerability is granted on the press rather than from the first tick so that the dodge
     * covers the tick the player committed on, which is the tick they most need it.</p>
     */
    public static void begin(ServerPlayer player, int materialRank) {
        CombatState.setLunge(player, new LungeState(
                0, player.getX(), player.getZ(), player.getX(), player.getZ(), materialRank));
        player.invulnerableTime = LungeDamage.INVULNERABLE_TICKS;
        // The effect is applied here and never refreshed, so its countdown IS the lunge's countdown:
        // whatever ends the lunge early -- a wall, the ceiling, the window -- the immunity ends with
        // it, and a run that a wall cut short does not leave the player untouchable for the rest of
        // a duration that was set before anyone knew how long the run would be.
        //
        // Ambient and particles are both off. Particles would fire on the player's own position on
        // every one of the twenty ticks, which is the same objection the sabre's parry has: the
        // effect would sit exactly where an incoming hit is supposed to be readable.
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                ModEffects.lunge(), LungeDamage.WINDOW_TICKS, 0, false, false, false));
    }

    /**
     * One tick of a lunge: measure, sweep, hit.
     *
     * <p>Three things can end it early, and each of them is a case where granting anything further
     * would be wrong:</p>
     *
     * <ul>
     *   <li><b>A wall</b> -- {@code horizontalCollision}. Vanilla's own rule
     *       ({@code else if (this.horizontalCollision) autoSpinAttackTicks = 0}), and it works here
     *       for the same reason it works there: the movement is real, so the collision is real.</li>
     *   <li><b>Too far</b> -- the displacement passed the tier's ceiling. Nothing is granted after
     *       this point. The lunge is not rewound, because only the client ever knew where the
     *       player was going.</li>
     *   <li><b>Twenty ticks</b> -- the window closing, same as vanilla's {@code startAutoSpinAttack}
     *       which counts down regardless of how long the player keeps sliding.</li>
     * </ul>
     */
    public static void tick(ServerPlayer player) {
        LungeState state = CombatState.lunge(player);
        if (!state.running()) {
            if (!state.equals(LungeState.IDLE)) {
                clear(player);
            }
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        double x = player.getX();
        double z = player.getZ();
        trace(state, player, x, z);

        // How far the body moved this tick. This is what the window is measured against, and it is
        // the only movement number the server has: the position report the client sends every tick
        // carries x, y, z and onGround, and no velocity at all.
        double step = LungeDamage.travelled(state.lastX(), state.lastZ(), x, z);

        if (player.horizontalCollision) {
            LOG.info("[lunge] rank={} movido={} ticks={} fin=pared",
                    state.materialRank(), fmt(state.travelled(x, z)), state.tick());
            clear(player);
            return;
        }
        if (!state.legal(state.travelled(x, z))) {
            LOG.info("[lunge] rank={} movido={} techo={} ticks={} fin=ilegal",
                    state.materialRank(), fmt(state.travelled(x, z)),
                    fmt(LungeDamage.ceilingFor(state.materialRank())), state.tick());
            clear(player);
            return;
        }
        if (LungeDamage.stopped(step)) {
            // **The window ends when the player stops, not when a counter runs out.**
            //
            // Twenty ticks was nearly double the movement, so half of every lunge was a player
            // standing perfectly still while the sweep went on describing a standstill and the
            // invulnerability kept holding. Now the immunity is exactly as long as the dash, which
            // is the only length the player can perceive as "I was untouchable while I moved".
            LOG.info("[lunge] rank={} movido={} ticks={} fin=detenido",
                    state.materialRank(), fmt(state.travelled(x, z)), state.tick());
            clear(player);
            return;
        }

        // The dodge, held for the whole run. Anything that touches the player's damage state mid-lunge
        // -- a fall, a fire tick, a mob the corridor swept -- resets this field to zero, and because
        // a full window is re-armed every tick, the player is unkillable for the entire lunge or for
        // none of it. A dodge that quietly stops halfway is worse than one that never was.
        player.invulnerableTime = LUNGE_IFRAME_TICKS;

        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        Set<LivingEntity> targets = sweep(level, player, state, x, z, stats);

        // **The damage is resolved on the way in, not on the way out.**
        //
        // It used to be resolved after the loop had built its list, which meant collecting every
        // entity inside a corridor that is rebuilt and re-written on every one of twenty ticks -- a
        // list of ids that grew every frame and went into an attachment, and attachments synchronise
        // to the client. So this was one growing list, serialized and shipped twenty times per dash,
        // for a set of integers the client has no use for.
        //
        // A permanent set per player says the same thing once, keeps its memory outside the state
        // that gets written and synced and saved, and is cleared where the lunge is cleared.
        //
        // **Its presence in the state is also the only explanation this change can offer for the
        // first playtest of it, which reported a lunge that neither did damage nor went
        // invulnerable.** Every run completed and none of them hit anything. That is the symptom of
        // a sweep that never saw a target, and it does not have a cause yet: the per-tick trace now
        // prints the distance, the velocity and the state's own tick, which is what distinguishes
        // "the corridor never covered anything" from "the player was never moving" from "the state
        // never advanced".
        Set<LivingEntity> hit = wasHit(player);
        int index = 0;
        for (LivingEntity target : targets) {
            float damage = LungeDamage.damageFor(
                    (float) player.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), index);
            Knockback.apply(player, target, stats.knockback(), 0.0F);
            target.invulnerableTime = 0;
            // **Estocada Perforante: el golpe del lunge entra por donde la armadura no cubre.**
            // La armadura se baja para ESTE golpe y se devuelve en un `finally`, que es donde vive
            // la decision: una armadura que queda baja para siempre es el bug que este encantamiento
            // no puede dejar atras. Ver `CombatEngine.lungePierce`.
            net.minecraft.world.entity.ai.attributes.AttributeInstance armour = target.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            net.minecraft.world.entity.ai.attributes.AttributeModifier pierced =
                    CombatEngine.lungePierce(player, target);
            if (pierced != null) {
                armour.addTransientModifier(pierced);
            }
            boolean landed;
            try {
                landed = target.hurt(player.damageSources().playerAttack(player), damage);
            } finally {
                if (pierced != null) {
                    armour.removeModifier(pierced);
                }
            }
            if (landed) {
                target.invulnerableTime = IFRAME_TICKS;
                SlashParticles.hit(level, stats.profile(), target, false);
                // The per-target hit, a sixth of a tone down from the weapon's own: a rising series
                // across a group rather than one sound repeated. Vanilla's riptide plays three sounds
                // for one thrust; a lunge that can pass through six things earns six.
                CombatSounds.play(level, target, WeaponProfile.ROPERA, ModSounds.Kind.HIT,
                        1.0F - index * 0.06F);
            }
            hit.add(target);
            index++;
        }

        LungeState next = state.advance(x, z);

        if (!targets.isEmpty()) {
            // Vanilla's crit on top, once for the lunge as a whole and not once per target: the crit
            // is the sound of the movement landing, and a movement lands once.
            ModNetworking.sendFeedback(player, CombatFeedback.HIT);
            level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.9, player.getZ(),
                    12, 0.3, 0.3, 0.3, 0.05);
        }

        if (next.running()) {
            CombatState.setLunge(player, next);
        } else {
            LOG.info("[lunge] rank={} movido={} techo={} ticks={} golpes={}",
                    state.materialRank(), fmt(state.travelled(x, z)),
                    fmt(LungeDamage.ceilingFor(state.materialRank())),
                    LungeDamage.WINDOW_TICKS, targets.size());
            clear(player);
        }
    }

    /** Two decimals: a tenth of a block is the difference between "short" and "about right". */
    private static String fmt(double blocks) {
        return String.format(java.util.Locale.ROOT, "%.2f", blocks);
    }

    /**
     * Everything in the space the body just passed through, nearest first.
     *
     * <p>Nearest first matters twice over: the enemy the player was aiming at is the one that takes
     * the first and therefore the smallest multiplier, and it is the one that survives if an
     * invulnerability window eats a later hit.</p>
     *
     * <p>Already-hit entities are skipped, so a mob the corridor covers on three consecutive ticks
     * is hit once. Vanilla gets that for free by stopping at the first target; this cannot stop,
     * because a lunge that pierces is the design, so it remembers instead -- and the remembering
     * lives on the player, not in the state, because the state is written twenty times per lunge and
     * synchronised to the client every time.</p>
     *
     * <p>Overlapping, so it keeps a LinkedHashSet: the order in which targets were found becomes
     * the order in which they are damaged, and {@code sort} on a set that has lost its order is a
     * no-op that reads like it worked.</p>
     */
    private static Set<LivingEntity> sweep(ServerLevel level, ServerPlayer player, LungeState state,
                                            double x, double z, WeaponStats stats) {
        double[] box = LungeDamage.cube(x, player.getY(), z, stats.reach().distance());
        AABB cube = new AABB(box[0], box[1], box[2], box[3], box[4], box[5]);
        Set<LivingEntity> targets = new LinkedHashSet<>();
        Set<LivingEntity> already = wasHit(player);
        for (Entity candidate : level.getEntities(player, cube)) {
            if (!(candidate instanceof LivingEntity living)
                    || candidate == player
                    || !candidate.isAlive()
                    || candidate.isSpectator()
                    || candidate.isRemoved()
                    || already.contains(living)) {
                continue;
            }
            if (cube.intersects(candidate.getBoundingBox())) {
                targets.add(living);
            }
        }
        List<LivingEntity> ordered = new ArrayList<>(targets);
        ordered.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        return new LinkedHashSet<>(ordered);
    }

    /**
     * The entities this player's last lunge already hit.
     *
     * <p>Entity references rather than ids on purpose. An id is an integer that a mob can stop being
     * the owner of and start being some other mob's, so a set of ids silently hits the wrong entity
     * for as long as the game is running -- the exact hazard that
     * {@code Player#getAttackStrengthScale} carried in this project before it was made static.</p>
     *
     * <p>Empty when the player is not in a lunge, and cleared with the lunge. So the set outlives
     * the dash but not the player.</p>
     */
    private static Set<LivingEntity> wasHit(ServerPlayer player) {
        Set<LivingEntity> wasHit = CombatState.lungeHits(player);
        if (!Lunge.active(player)) {
            wasHit.clear();
        }
        return wasHit;
    }

    /**
     * Retirada: apunta un cuerpo al que el lunge ya golpeo, para que el barrido no lo golpee otra vez.
     *
     * <h2>Por qué existe, y es la parte importante</h2>
     *
     * <p>El cubo que barre el lunge esta <b>centrado en el jugador y mide medio alcance en TODAS las
     * direcciones</b> -- {@code LungeDamage.cube} no tiene nocio de hacia donde mira nadie. Eso
     * significa que <b>una marcha atras barre lo que quedo adelante</b>, que es exactamente donde el
     * jugador acaba de golpear. Sin este conjunto, Retirada cobraria dos veces el mismo cuerpo: una
     * en el press y otra en el primer tick de la marcha.</p>
     *
     * <p>Y la consecuencia es el argumento entero del encantamiento: <b>el golpe del frente no es un
     * bonus, es el golpe del lunge adelantado al press.</b> Por eso su fraccion es 1,0 y no una
     * parte -- no se esta agregando nada, se esta moviendo el cobro de "cuando cubris el suelo" a
     * "cuando te vas". El beneficio que queda no es mas daño: es que <b>el golpe se cobra aunque la
     * retirada la corte una pared</b>, que es justo lo que el lunge normal no puede prometer,
     * porque su golpe depende de que el cuerpo llegue.</p>
     *
     * <p><b>Hay que llamarlo despues de {@link #begin}</b>, y no antes: {@code wasHit} limpia el
     * conjunto mientras el lunge no este activo, asi que un golpe registrado antes de abrir la
     * ventana se borraria en el primer tick sin que nadie lo dijera.</p>
     */
    public static void prehit(ServerPlayer player, LivingEntity target) {
        wasHit(player).add(target);
    }

    /** Whether a lunge is in progress. */
    public static boolean active(ServerPlayer player) {
        return CombatState.lunge(player).running();
    }

    /** Stops the lunge. Called on login, and available for death and world change. */
    public static void clear(ServerPlayer player) {
        CombatState.setLunge(player, LungeState.IDLE);
        CombatState.lungeHits(player).clear();
        // **The immunity is driven by the window and never by its own clock.**
        //
        // The effect is applied once, for twenty ticks, and this line is what makes its life exactly
        // the lunge's life. A wall at tick four used to leave the player untouchable for the sixteen
        // ticks that were left on the timer -- a free dodge tail that nothing had earned and nothing
        // announced, since the effect is the only visible part of the mechanic.
        player.removeEffect(ModEffects.lunge());
    }
}