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

    /**
     * Los numeros viven en {@code CombatEnchantRules} y no aca, como todo el balance del proyecto.
     *
     * <p>Y son los que pidio el playtest: un viaje <b>lento y largo</b> que tira y lastima
     * <em>mientras viaja</em>, y una <b>implosion</b> de seis ticks con un tiron violento al final.
     * La version anterior hacia lo contrario -- el orbe cruzaba el alcance en un tercio de segundo
     * y solo tiraba despues de clavarse, o sea que el tiron del camino no existia.</p>
     */
    private static final double TRAVEL_PULL_RADIUS =
            CombatEnchantRules.PALA_ANTIMATERIAL_TRAVEL_PULL_RADIUS;

    /** El dueno, para que el estallido no le pegue a quien lo tiro. */
    private java.util.UUID owner;

    private int age;
    private boolean imploding;

    /** Los ticks que lleva tragando. Ver {@code PALA_ANTIMATERIAL_IMPLODE_TICKS}. */
    private int implodeTicks;

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
        age++;

        // La cola del orbe. Va cada tick y no cada dos: a la velocidad a la que viaja, un punto
        // cada dos ticks deja huecos que se leen como un parpadeo.
        server.sendParticles(ParticleTypes.REVERSE_PORTAL,
                this.getX(), this.getY() + 0.2, this.getZ(), 2, 0.06, 0.06, 0.06, 0.0);

        if (imploding) {
            // **La implosion son SEIS ticks de tiron fuerte, y despues el golpe.** Un tiron de un
            // solo tick es un teleport: el cuerpo aparece pegado al orbe y el jugador no lee "me
            // chupo", lee "se tecleo". Los seis ticks son lo que separa una implosion de una
            // explosion, y ver `PALA_ANTIMATERIAL_IMPLODE_TICKS`.
            pull(server, CombatEnchantRules.PALA_ANTIMATERIAL_PULL_RADIUS,
                    CombatEnchantRules.PALA_ANTIMATERIAL_IMPLODE_PULL);
            implodeTicks++;
            if (implodeTicks >= CombatEnchantRules.PALA_ANTIMATERIAL_IMPLODE_TICKS) {
                detonate(server);
            }
            return;
        }

        if (age >= CombatEnchantRules.PALA_ANTIMATERIAL_LIFETIME_TICKS) {
            // **Se apaga sin cobrar, y eso lo separa de clavarse.** Un orbe que no toco nada en
            // veintiocho bloques es un tiro al aire, y cobrarlo a esa distancia seria un ataque sin
            // punteria. Un orbe perdido no espera.
            this.discard();
            return;
        }

        // **El viaje tira y lastima, y esa es la mitad del encantamiento.**
        //
        // Un orbe que solo explotara al final seria una granada con dibujo. Lo que lo hace otra
        // cosa es que **arrastra mientras va**: el que esta a tres bloques viene, y el que queria
        // correrse descubre que el orbe lo sigue alcanzando. Antes esto no pasaba -- el orbe cruzaba
        // el alcance en un tercio de segundo y el tiron solo existia despues de clavarse.
        pull(server, TRAVEL_PULL_RADIUS, CombatEnchantRules.PALA_ANTIMATERIAL_TRAVEL_PULL);
        if (age % CombatEnchantRules.PALA_ANTIMATERIAL_TRAVEL_DAMAGE_TICKS == 0) {
            bite(server);
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
            // **Y no revienta en el instante en que toca: se clava y empieza a tragar.** Los seis
            // ticks de la implosion SON el encantamiento; el dano es la consecuencia. Reventar en el
            // contacto haria que el radio de atraccion fuera decorativo.
            imploding = true;
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * Tira de todo lo que este en el radio, hacia el orbe, con la fuerza que le digan.
     *
     * <p>Un solo metodo para los dos tirones y no dos copias: el del viaje es suave y ancho y el de
     * la implosion es fuerte y violento, y lo unico que cambia entre ellos son los dos argumentos.
     * Dos bucles con las mismas cuatro guardas de "es un cuerpo que se puede mover" es como uno de
     * los dos se olvida del dueno.</p>
     */
    private void pull(ServerLevel server, double radius, double strength) {
        AABB box = this.getBoundingBox().inflate(radius);
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
            Vec3 unit = toward.normalize().scale(strength);
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

    /**
     * El mordisco del viaje: poco y seguido, para que el arrastre se sienta.
     *
     * <p><b>Va separado del tiron a proposito.</b> El tiron es posicion y esto es vida, y comparten
     * el radio pero no el reloj: tirar todos los ticks esta bien -- es lo que hace que el cuerpo
     * venga -- y pegar todos los ticks seria una ametralladora. Cada
     * {@code PALA_ANTIMATERIAL_TRAVEL_DAMAGE_TICKS} ticks, y el numero lo lleva la regla.</p>
     */
    private void bite(ServerLevel server) {
        AABB box = this.getBoundingBox().inflate(TRAVEL_PULL_RADIUS);
        for (LivingEntity body : server.getEntitiesOfClass(LivingEntity.class, box)) {
            if (!body.isAlive() || body.isSpectator() || body.isRemoved()) {
                continue;
            }
            if (owner != null && owner.equals(body.getUUID())) {
                continue;
            }
            body.invulnerableTime = 0;
            body.hurt(ownerSource(server), CombatEnchantRules.PALA_ANTIMATERIAL_TRAVEL_DAMAGE);
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
        // **Volumen 4 y no 1,4, porque el volumen ES el alcance.**
        //
        // En Minecraft el radio audible de un sonido sale de `SoundEvent#getRange(volume)`, que
        // multiplica el rango base por el volumen: con 1,4 el reventon del orbe se escuchaba a unos
        // veintidos bloques y el playtest lo reporto como "muy bajo y con muy poco rango". Con 4 son
        // unos sesenta y cuatro -- el mismo numero que usa la detonacion del reactor -- asi que el
        // golpe mas caro del catalogo se anuncia desde donde se tiene que anunciar.
        //
        // El pitch se queda en 1: subir el volumen no pide tocar el tono, y el archivo ya es del
        // usuario.
        server.playSound(null, this.getX(), this.getY(), this.getZ(),
                com.cortes.combat.sound.CombatSounds.event(
                        com.cortes.combat.sound.ModSounds.SHOVEL_ORB_EXPLOSION),
                net.minecraft.sounds.SoundSource.PLAYERS, 4.0F, 1.0F);
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
