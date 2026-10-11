package com.cortes.combat.entity;

import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.particle.MoscaParticles;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * La nube de moscas que deja un disparo de Rey de las Moscas.
 *
 * <h2>Por qué es una entidad y no un estado con un reloj</h2>
 *
 * <p>Porque <b>está en el mundo y no en nadie</b>. Una nube tiene una posición fija donde cayó el
 * disparo, vive ochenta ticks y se muere sola; un attachment vive en un jugador y un handler
 * tendría que llevar una lista de nubes activas, con su posición, su edad y su limpieza -- que es
 * exactamente lo que un {@code Entity} ya hace gratis: el motor la tickea, la persiste, la
 * arrastra entre dimensiones y la descarta cuando se le acaba la vida.</p>
 *
 * <h2>Sin renderer que dibuje, pero con renderer</h2>
 *
 * <p><b>Un tipo sin renderer registrado no es invisible: es un NPE.</b> Verificado en las fuentes
 * de 1.21.1: {@code EntityRenderDispatcher#shouldRender} y {@code #render} toman
 * {@code getRenderer(entity)} y lo dereferencian <em>en la linea siguiente</em>, sin guarda. El mapa
 * de renderers devuelve {@code null} para lo que nadie registro, y null no es "no dibujar" -- es
 * {@code entityrenderer.shouldRender(...)} sobre un nulo.</p>
 *
 * <p>Así que {@link com.cortes.combat.client.MobCloudRenderer} existe y <b>dibuja exactamente
 * nada</b>: su {@code render} esta vacío. La nube se <em>ve</em> porque el servidor le suelta
 * {@link MoscaParticles} adentro, que es donde esta el arte. El renderer es el precio de no tener
 * un cliente que se cae.</p>
 *
 * <h2>Lo que hace, y las dos mitades</h2>
 *
 * <ul>
 *   <li><b>Moscas, todo el tiempo.</b> Un enjambre chico cada pocos ticks, para que la nube tenga
 *       cuerpo permanente y no un estallido inicial. Las moscas se propulsan solas -- ver
 *       {@code MoscaParticle} --, así que lo único que se les da es donde nacer.</li>
 *   <li><b>Daño, cada diez ticks.</b> Sobre todo lo que este dentro del radio, sin importar si
 *       esta quieto o corriendo: <b>infecta por proximidad</b>, que es lo contrario de un golpe y
 *       lo que hace que sirva para cerrar una puerta.</li>
 * </ul>
 *
 * <h2>"No reinfecta", y qué significa acá</h2>
 *
 * <p>Que <b>la nube no apila nada sobre un cuerpo que ya esta adentro</b>: el daño sale del reloj
 * de la nube y no de un efecto que se reaplica, así que hay una sola verdad -- la nube cobra su
 * tick, y dos nubes superpuestas son dos nubes cobrando cada una la suya, que es la lectura
 * correcta de dos disparos al mismo sitio.</p>
 */
public class MobCloud extends Entity {

    /**
     * La vida de la nube, en ticks.
     *
     * <p>La misma que el efecto declara en {@code CombatEnchantRules#MOSCAS_TICKS}: cuatro
     * segundos. <b>Un solo numero y dos sitios que lo leen</b>, porque si cada uno tuviera el suyo
     * la nube podria durar mas que el daño que promete -- o al reves, que es peor.</p>
     */
    private static final int LIFETIME = CombatEnchantRules.MOSCAS_TICKS;

    /** Cada cuantos ticks suelta un enjambre. Chico, para que la nube este siempre viva. */
    private static final int SWARM_TICKS = 4;

    /**
     * Cuantas moscas trae cada enjambre.
     *
     * <p>Ocho, y el numero se elige contra lo que el cliente puede dibujar y no contra lo que se ve
     * bonito: ochenta ticks de vida, un enjambre cada cuatro, o sea veinte enjambres -- y con ocho
     * moscas cada uno son <b>160 viviendo a la vez en el peor caso</b>. Con dos nubes superpuestas,
     * 320. Es un numero que se puede subir, pero el playtest es quien lo dice y no una intuicion.</p>
     */
    private static final int SWARM_COUNT = 8;

    /** Cada cuantos ticks cobra el daño. El intervalo del diseño. */
    private static final int DAMAGE_TICKS = CombatEnchantRules.MOSCAS_INTERVAL;

    private static final EntityDataAccessor<Integer> DATA_AGE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    MobCloud.class, EntityDataSerializers.INT);

    /** Quien tiro el disparo. La nube no le hace daño, igual que el reventon de Sentencia. */
    private java.util.UUID owner;

    public MobCloud(EntityType<?> type, Level level) {
        super(type, level);
        // Sin fisica y sin choque: una nube no cae y no se estanca contra la primera pared, que es
        // la mitad de su utilidad -- se tira en una puerta y tiene que entrar.
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AGE, 0);
    }

    /** Pone el dueño antes de que entre al mundo, que es el unico momento en que se puede. */
    public void setOwner(LivingEntity thrower) {
        this.owner = thrower == null ? null : thrower.getUUID();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        int age = this.getEntityData().get(DATA_AGE) + 1;
        this.getEntityData().set(DATA_AGE, age);

        if (age >= LIFETIME) {
            this.discard();
            return;
        }

        ServerLevel server = (ServerLevel) this.level();

        // Las moscas primero: aunque el daño no encuentre a nadie, la nube se ve. Un especial que
        // solo hace ruido cuando conecta deja de significar algo cuando lo ves en el aire.
        if (age % SWARM_TICKS == 0) {
            MoscaParticles.swarm(server, this.getX(), this.getY() + 0.5D, this.getZ(),
                    SWARM_COUNT, CombatEnchantRules.MOSCAS_RADIUS * 0.6D);
        }

        if (age % DAMAGE_TICKS != 0) {
            return;
        }
        AABB box = this.getBoundingBox().inflate(CombatEnchantRules.MOSCAS_RADIUS);
        List<LivingEntity> inside = new ArrayList<>(
                server.getEntitiesOfClass(LivingEntity.class, box));
        for (LivingEntity body : inside) {
            if (!body.isAlive() || body.isSpectator() || body.isRemoved()) {
                continue;
            }
            // El dueño queda afuera, que es el veto C2: un especial que tambien te lastima a vos
            // es un encantamiento que se castiga solo, y ya hay dos precedent escritos.
            if (owner != null && owner.equals(body.getUUID())) {
                continue;
            }
            // `magic`, igual que el sangrado: es dano que ignora armadura, y una nube de insectos
            // no deberia depender de si el rival llevo casco.
            body.hurt(body.damageSources().magic(), CombatEnchantRules.MOSCAS_DAMAGE);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.getEntityData().get(DATA_AGE));
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.getEntityData().set(DATA_AGE, tag.getInt("Age"));
        if (tag.hasUUID("Owner")) {
            this.owner = tag.getUUID("Owner");
        }
    }
}
