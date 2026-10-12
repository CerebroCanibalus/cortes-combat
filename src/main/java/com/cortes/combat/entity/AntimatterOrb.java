package com.cortes.combat.entity;

import com.cortes.combat.enchant.CombatEnchantRules;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * El orbe de antimateria que suelta la pala.
 *
 * <h2>Atrae y despues revienta, y ese orden es todo el encantamiento</h2>
 *
 * <p>Un orbe que solo explotara seria una granada: se tira donde esta el enemigo y listo. Lo que
 * lo hace otra cosa es que <b>primero tira de todo lo que este cerca hacia el</b>, y recien
 * despues cobra. Eso convierte un arma de area en un arma de <b>control del espacio</b>: el que
 * esta a cinco bloques viene, y el radio de cuatro lo agarra. Y el que estaba corriendo para
 * salir del radio, con el tiron ya no sale.</p>
 *
 * <p>Los dos radios estan separados a proposito y el de atraccion es <b>mas grande</b>
 * ({@code PULL_RADIUS 5} contra {@code BLAST_RADIUS 4}). Si fueran el mismo, el tiron solo
 * alcanzaria a los que ya iban a ser golpeados y no agregaria nada; con cinco contra cuatro, la
 * franja de un bloque entre los dos radios es exactamente la gente que <b>no</b> iba a recibir el
 * golpe y ahora si.</p>
 *
 * <h2>Y no tiene renderer que dibuje, pero tiene renderer</h2>
 *
 * <p>Misma razon que {@link MobCloud}, y esta escrita alla: un {@code EntityType} sin renderer
 * registrado no es invisible, es un NPE en el cliente. El orbe se ve por las particulas que va
 * dejando y por el estallido del final.</p>
 */
public class AntimatterOrb extends Entity {

    /** Lo que dura como mucho, en ticks. Pasado eso se apaga sin cobrar: un orbe perdido no espera. */
    private static final int LIFETIME = 60;

    /** Cuanto se le tira a lo que esta en el radio de atraccion, por tick de tiron. */
    private static final double PULL_STRENGTH = 0.28D;

    /** Cuantos ticks dura el tiron antes de reventar. */
    private static final int PULL_TICKS = 10;

    /** El dueno, para que el estallido no le pegue a quien lo tiro. */
    private java.util.UUID owner;

    private int age;
    private boolean imploding;

    public AntimatterOrb(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public void setOwner(LivingEntity thrower) {
        this.owner = thrower == null ? null : thrower.getUUID();
    }

    /** Le da el empuje inicial, que es lo que lo hace un proyectil y no una trampa. */
    public void launch(Vec3 velocity) {
        this.setDeltaMovement(velocity);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        ServerLevel server = (ServerLevel) this.level();

        if (++age >= LIFETIME) {
            this.discard();
            return;
        }

        // La cola del orbe. Va cada tick y no cada dos: a la velocidad a la que viaja, un punto
        // cada dos ticks deja huecos que se leen como un parpadeo.
        server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                this.getX(), this.getY() + 0.2, this.getZ(), 2, 0.06, 0.06, 0.06, 0.0);

        if (imploding) {
            pull(server);
            if (age % PULL_TICKS == 0) {
                detonate(server);
            }
            return;
        }

        // **El viaje se mueve a mano y no con `move()` a secas.** `hasPhysics` esta encendido,
        // asi que el motor resuelve la colision; lo que se mira despues es si el movimiento se
        // corto, que es la forma barata de saber que toco algo sin un raycast por tick.
        Vec3 before = this.position();
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        boolean hitSomething = this.horizontalCollision || this.verticalCollision;
        boolean stopped = this.position().distanceToSqr(before) < 1.0E-6;

        AABB box = this.getBoundingBox().inflate(0.4D);
        List<Entity> touched = server.getEntities(this, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());

        if (hitSomething || stopped || !touched.isEmpty()) {
            // **Y no revienta en el instante en que toca: se clava y tira.** Los diez ticks de
            // tiron son el encantamiento; el daño es la consecuencia. Reventar en el contacto
            // haria que el radio de atraccion fuera decorativo.
            imploding = true;
            this.setDeltaMovement(Vec3.ZERO);
            age = Math.max(0, LIFETIME - PULL_TICKS - 1);
        }
    }

    /** Tira de todo lo que este en el radio, hacia el orbe. */
    private void pull(ServerLevel server) {
        AABB box = this.getBoundingBox().inflate(CombatEnchantRules.PALA_ANTIMATERIAL_PULL_RADIUS);
        for (LivingEntity body : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!body.isAlive() || body.isSpectator() || body.isRemoved()) {
                continue;
            }
            if (owner != null && owner.equals(body.getUUID())) {
                continue;
            }
            Vec3 toward = this.position().subtract(body.position());
            if (toward.lengthSqr() < 1.0E-4) {
                continue;
            }
            Vec3 unit = toward.normalize().scale(PULL_STRENGTH);
            // Sin componente vertical: un tiron que levanta al rival lo saca del piso y el
            // estallido de despues lo encuentra en el aire, que es un cambio de posicion y no un
            // control. Se lo trae por el suelo.
            body.push(unit.x, 0.0D, unit.z);
            body.hurtMarked = true;
            server.sendParticles(ParticleTypes.PORTAL,
                    body.getX(), body.getY() + body.getBbHeight() * 0.5, body.getZ(),
                    3, 0.2, 0.2, 0.2, 0.05);
        }
    }

    /** El reventon: daño puro en el radio de golpe, y el que lo tiro queda afuera. */
    private void detonate(ServerLevel server) {
        AABB box = this.getBoundingBox().inflate(CombatEnchantRules.PALA_ANTIMATERIAL_BLAST_RADIUS);
        for (LivingEntity body : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!body.isAlive() || body.isSpectator() || body.isRemoved()) {
                continue;
            }
            // El veto C2 otra vez: un especial que tambien te cobra a vos es un encantamiento que
            // se castiga solo. El que lo tiro queda afuera, como en Sentencia y en la nube.
            if (owner != null && owner.equals(body.getUUID())) {
                continue;
            }
            body.invulnerableTime = 0;
            body.hurt(ownerSource(server), CombatEnchantRules.PALA_ANTIMATERIAL_DAMAGE);
            body.invulnerableTime = com.cortes.combat.attack.AttackTiming.IFRAME_TICKS;
        }
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                this.getX(), this.getY() + 0.3, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                this.getX(), this.getY() + 0.3, this.getZ(), 60, 1.2, 0.8, 1.2, 0.15);
        server.playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.4F, 0.6F);
        this.discard();
    }

    /** El dano sale como ataque del dueno si sigue existiendo, y como magia si no. */
    private net.minecraft.world.damagesource.DamageSource ownerSource(ServerLevel server) {
        if (owner != null) {
            Entity found = server.getEntity(owner);
            if (found instanceof LivingEntity living) {
                return server.damageSources().indirectMagic(this, living);
            }
        }
        return server.damageSources().magic();
    }

    @Override
    protected void defineSynchedData(
            net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // Sin datos sincronizados: el orbe no lleva nada que el cliente necesite. Lo que se ve
        // son particulas, y las manda el servidor como todo lo demas de este mod.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }
}
