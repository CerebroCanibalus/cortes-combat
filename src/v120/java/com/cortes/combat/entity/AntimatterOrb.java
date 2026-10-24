package com.cortes.combat.entity;

import com.cortes.combat.enchant.CombatEnchantRules;

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

        // **El aura, cada tick: un nucleo negro y un halo morado.**
        //
        // Antes esto era una cola de dos `REVERSE_PORTAL` por tick, y el playtest lo describio
        // exacto: "se ve como unas particulas de mierda en el aire". El problema no era el color
        // -- era que un proyectil de particulas finas **no tiene cuerpo**. Un agujero negro tiene
        // que leerse como una cosa que ocupa espacio, asi que el nucleo es `SQUID_INK` denso (tinta
        // de calamar: es la particula negra opaca del juego) y el morado es el halo que lo rodea.
        aura(server);

        if (imploding) {
            // **La implosion son SEIS ticks de tiron fuerte, y despues el golpe.** Un tiron de un
            // solo tick es un teleport: el cuerpo aparece pegado al orbe y el jugador no lee "me
            // chupo", lee "se tecleo". Los seis ticks son lo que separa una implosion de una
            // explosion, y ver `PALA_ANTIMATERIAL_IMPLODE_TICKS`.
            collapse(server);
            pull(server, CombatEnchantRules.PALA_ANTIMATERIAL_PULL_RADIUS,
                    CombatEnchantRules.PALA_ANTIMATERIAL_IMPLODE_PULL);
            implodeTicks++;
            if (implodeTicks >= CombatEnchantRules.PALA_ANTIMATERIAL_IMPLODE_TICKS) {
                detonate(server);
            }
            return;
        }

        if (age >= CombatEnchantRules.PALA_ANTIMATERIAL_LIFETIME_TICKS) {
            // **Al agotar el rango IMPLOSIONA, y eso es un cambio de diseno pedido por el playtest.**
            //
            // Antes se apagaba sin cobrar, con el argumento de que un orbe que no toco nada en
            // veintiocho bloques es un tiro al aire. El argumento era bueno y la consecuencia que
            // traia no: al orbe se lo apunta a un enemigo que se corre, y un orbe que se apaga al
            // final **premia correr sin castigar nada** -- el que se corrio ve el orbe deshacerse y
            // aprende que correr es gratis. Implosionando, el rango es el rango: si estas adentro
            // cuando se acaba, cobras.
            //
            // Es la misma forma de la regla que ya tiene el barrido de la alabarda: la accion paso,
            // y el que estaba en el lugar paga. Lo unico que se conserva del comportamiento viejo
            // es que **no** cobra al que lo tiro.
            imploding = true;
            this.setDeltaMovement(Vec3.ZERO);
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

        // **Solo se clava contra BLOQUES. Tocar un cuerpo ya no lo detona, y ese era el bug.**
        //
        // Antes esto era `hitSomething || stopped || !touched.isEmpty()`, y la tercera condicion
        // peleaba con el encantamiento entero: el orbe **atrae**, asi que el cuerpo que viene
        // empujado entra en la caja de contacto del orbe y lo hacia reventar **a mitad de camino**.
        // El tiron se comia su propio proyectil. El playtest lo reporto exacto: "al atraer enemigos
        // durante la transicion este explota a medio camino".
        //
        // Y es la clase de condicion que se escribe sola: hacia falta una lista de entidades para
        // otra cosa y se la reuso de gatillo por conveniencia. Un gatillo que contradice la mecanica
        // no es un atajo, es otra mecanica escrita por accidente.
        //
        // **El dano de contacto no se pierde**: `bite` ya lo cobra por su cuenta cada
        // `TRAVEL_DAMAGE_TICKS`, con su propio radio. Se saca el gatillo, no el mordisco.
        if (hitSomething || stopped) {
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
        burst(server);
        shake(server);
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

    /** Cuanto se sacude la camara del que lo ve, en grados, y por cuantos ticks. */
    private static final float SHAKE_DEGREES = 1.8F;
    private static final int SHAKE_TICKS = 14;
    /** Hasta donde se siente el temblor, en bloques. Un reventon de este tamano se siente lejos. */
    private static final double SHAKE_REACH = 40.0;

    /**
     * El aura del viaje: <b>un nucleo negro con un halo morado</b>, y eso es el efecto de agujero
     * negro que pidio el playtest.
     *
     * <p>Antes esto era una cola de dos {@code REVERSE_PORTAL} por tick, o sea un proyectil de
     * particulas finas: no tiene cuerpo. Un agujero negro tiene que leerse como una cosa que
     * <b>ocupa espacio</b>, asi que el centro es {@code SQUID_INK} denso -- la tinta de calamar es
     * la unica particula negra y opaca del juego -- y el morado es el halo que lo rodea.</p>
     *
     * <p><b>El giro sale de tener capas a distinta velocidad, no de una particula mas rapida.</b>
     * Tres halos con velocidades 0,06 / 0,14 / 0,02 se desfasan entre si y el ojo lee rotacion;
     * uno solo, por rapido que vaya, se lee como una particula que se escapa.</p>
     */
    private void aura(ServerLevel server) {
        double x = this.getX();
        double y = this.getY() + this.getBbHeight() * 0.5;
        double z = this.getZ();
        server.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 10, 0.30, 0.30, 0.30, 0.0);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 14, 0.62, 0.62, 0.62, 0.06);
        server.sendParticles(ParticleTypes.PORTAL, x, y, z, 8, 0.48, 0.48, 0.48, 0.14);
        server.sendParticles(ParticleTypes.WITCH, x, y, z, 6, 0.78, 0.55, 0.78, 0.02);
    }

    /**
     * La implosion: <b>el anillo se cierra sobre el nucleo</b> durante los seis ticks.
     *
     * <p>Un anillo que aparece y desaparece no dice que te estan chupando; uno que <b>baja de radio
     * a cero</b> lo dice sin una palabra. El radio arranca en dos bloques y medio, que es mas ancho
     * que el aura: el cierre se ve empezar por afuera de donde estaba el orbe.</p>
     */
    private void collapse(ServerLevel server) {
        double x = this.getX();
        double y = this.getY() + this.getBbHeight() * 0.5;
        double z = this.getZ();
        double t = implodeTicks / (double) CombatEnchantRules.PALA_ANTIMATERIAL_IMPLODE_TICKS;
        double radius = 2.4 * (1.0 - t);
        ring(server, ParticleTypes.REVERSE_PORTAL, x, y, z, radius, 20);
        ring(server, ParticleTypes.SQUID_INK, x, y, z, radius * 0.7, 10);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 12, 0.3, 0.3, 0.3, 0.3);
    }

    /**
     * El reventon, en capas.
     *
     * <p>Una explosion es <b>una secuencia y no un fogonazo</b>: el destello, la presion, el cuerpo
     * que se deshace, y los anillos que viajan por el piso. Lo que hace que se lea grande no es mas
     * cantidad de lo mismo, es que cada capa diga una cosa distinta -- y la que le faltaba a la
     * version vieja era la presion, que en Minecraft es <b>una sola particula</b>:
     * {@code SONIC_BOOM} es la unica del juego que se dibuja como onda expansiva. Sin ella, cien
     * particulas moradas son confeti, no una explosion.</p>
     */
    private void burst(ServerLevel server) {
        double x = this.getX();
        double y = this.getY() + 0.3;
        double z = this.getZ();

        // 1. El fogonazo, el destello y la onda.
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.SONIC_BOOM, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

        // 2. La tinta que se deshace: el nucleo negro reventando hacia afuera.
        server.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 130, 1.9, 1.5, 1.9, 0.38);

        // 3. El morado violento. La capa mas rapida es la que da la succion.
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 160, 2.4, 1.7, 2.4, 0.95);
        server.sendParticles(ParticleTypes.PORTAL, x, y, z, 90, 1.8, 1.3, 1.8, 0.75);
        server.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 70, 1.9, 1.4, 1.9, 0.35);
        server.sendParticles(ParticleTypes.WITCH, x, y, z, 60, 2.2, 1.8, 2.2, 0.50);

        // 4. El "algo se rompio": chispas de encantamiento y varas de end que salen rectas.
        server.sendParticles(ParticleTypes.ENCHANT, x, y + 0.5, z, 90, 1.5, 1.1, 1.5, 0.55);
        server.sendParticles(ParticleTypes.END_ROD, x, y, z, 70, 0.9, 0.9, 0.9, 0.85);

        // 5. Y dos anillos APOYADOS EN EL PISO. Un agujero negro no revienta en esfera: la onda
        //    viaja por el suelo, y ahi es donde el jugador mide el tamano de lo que paso.
        ring(server, ParticleTypes.END_ROD, x, y - 0.3, z, 2.4, 40);
        ring(server, ParticleTypes.SOUL_FIRE_FLAME, x, y - 0.3, z, 3.8, 44);
    }

    /** Pone una particula en cada punto de un circulo horizontal. */
    private void ring(ServerLevel server, net.minecraft.core.particles.SimpleParticleType type,
            double x, double y, double z, double radius, int points) {
        if (radius <= 0.01) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double angle = i * (Math.PI * 2.0 / points);
            // Con desvio cero la particula cae exacta en el punto: es la unica forma de dibujar una
            // figura con `sendParticles`, que reparte al azar dentro de una caja y nada mas.
            server.sendParticles(type, x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * El temblor de camara del reventon.
     *
     * <p><b>Un paquete y no un bloque de mundo.</b> El temblor del reactor viaja por el block
     * entity porque un reactor <em>esta</em> en el mundo y persiste; un orbe existe dos segundos y
     * se va, asi que lo suyo es un aviso puntual: donde, cuanto y por cuanto tiempo. El cliente
     * mide su propia distancia y decide cuanto lo sacude, que es la razon del paquete y no de un
     * numero fijo.</p>
     */
    private void shake(ServerLevel server) {
        com.cortes.combat.fx.Shake.around(server, this.getX(), this.getY(), this.getZ(),
                SHAKE_REACH, SHAKE_DEGREES, SHAKE_TICKS);
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
    protected void defineSynchedData() {
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
