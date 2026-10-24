package com.cortes.combat.entity;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.guard.GuardHandler;
import com.cortes.combat.registry.ModEntities;
import com.cortes.combat.weapon.SweepingEdge;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * La onda de filo: el espadazo que dispara un golpe a carga completa con Sweeping Edge.
 *
 * <h2>Por qué es una entidad y no una partícula</h2>
 *
 * <p>El dueño lo decidió y la razón es la distancia: la onda recorre hasta doce bloques, y a esa
 * distancia lo que se ve tiene que ser <b>algo que está ahí</b>. Las partículas de este mod
 * funcionan para el tajo del golpe, que vive diez ticks en la cara del jugador; no para un
 * proyectil que el rival tiene que poder ver venir, esquivar y -- si está cubierto -- devolver.
 * Además la colisión importa: <b>una pared frena la onda</b>, y eso sólo lo puede decidir algo con
 * una posición de verdad.</p>
 *
 * <h2>Lo que dibuja</h2>
 *
 * <p>Su modelo propio: un plano con el arte del dueño, agrandado con el nivel por
 * {@code SlashWaveRenderer}. Antes de que ese modelo existiera se dibujaba con el renderer de vanilla
 * sobre una flecha, que era un placeholder declarado. El placeholder se fue entero, {@code
 * ItemSupplier} y su {@code getItem()} incluidos, porque un componente que ya nadie lee es la
 * forma exacta de un bug que este proyecto ya pagó.</p>
 *
 * <h2>Las reglas del vuelo</h2>
 *
 * <ul>
 *   <li><b>Atraviesa</b> hasta {@link SweepingEdge#pierce(int)} enemigos, uno por uno, y cada uno
 *       cobra la porción del daño cargado que el nivel fija.</li>
 *   <li><b>Saltea al objetivo del golpe de melé</b> ({@code skipId}): la onda es el seguimiento del
 *       tajo <em>más allá</em> del cuerpo, no un segundo golpe al mismo.</li>
 *   <li><b>La pared la frena</b>, con un raycast por tick contra los colisionadores.</li>
 *   <li><b>Una guardia la devuelve</b>: quien la bloquea no la cobra, y la onda vuelve -- y desde
 *       ese momento <b>puede herir a quien la tiró</b>, que es la decisión del dueño.</li>
 *   <li><b>Empuja muy poco</b> ({@link SweepingEdge#PUSH}): la onda corta.</li>
 * </ul>
 */
public class SlashWave extends Entity {

    private static final EntityDataAccessor<Integer> DATA_LEVEL =
            SynchedEntityData.defineId(SlashWave.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_REFLECTED =
            SynchedEntityData.defineId(SlashWave.class, EntityDataSerializers.BOOLEAN);

    /** Quién la tiró. Se guarda por UUID y no por referencia: la entidad puede descargarse. */
    @Nullable
    private UUID throwerId;

    /** El daño que lleva esta onda, ya multiplicado por la porción del nivel. */
    private float damage;

    /** El objetivo que el golpe de melé ya cobró, para no cobrarlo dos veces. */
    private int skipId;

    /** Los que ya cobraron, para no cobrar dos veces al mismo en un tick que se solape. */
    private final Set<Integer> alreadyHit = new HashSet<>();

    private int hits;

    public SlashWave(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * Dispara una onda.
     *
     * <p>El daño entra ya calculado -- es la porción del daño <em>cargado</em> que
     * {@code CombatEngine} resolvió para este golpe -- para que la onda no tenga una segunda
     * opinión sobre cuánto pega una espada.</p>
     */
    public static void fire(ServerLevel level, Player thrower, Vec3 from, Vec3 direction,
                            int enchantLevel, float damage, int skipId) {
        SlashWave wave = new SlashWave(ModEntities.SLASH_WAVE.get(), level);
        wave.throwerId = thrower.getUUID();
        wave.damage = damage;
        wave.skipId = skipId;
        wave.setPos(from.x, from.y, from.z);
        wave.setDeltaMovement(direction.normalize().scale(SweepingEdge.SPEED));
        wave.entityData.set(DATA_LEVEL, SweepingEdge.clampLevel(enchantLevel));
        level.addFreshEntity(wave);
    }

    public int enchantLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    public boolean reflected() {
        return this.entityData.get(DATA_REFLECTED);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_LEVEL, 1);
        entityData.define(DATA_REFLECTED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Thrower")) {
            this.throwerId = tag.getUUID("Thrower");
        }
        this.damage = tag.getFloat("Damage");
        this.skipId = tag.getInt("Skip");
        this.entityData.set(DATA_LEVEL, tag.getInt("Level"));
        this.entityData.set(DATA_REFLECTED, tag.getBoolean("Reflected"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.throwerId != null) {
            tag.putUUID("Thrower", this.throwerId);
        }
        tag.putFloat("Damage", this.damage);
        tag.putInt("Skip", this.skipId);
        tag.putInt("Level", enchantLevel());
        tag.putBoolean("Reflected", reflected());
    }

    /** No se puede clickear ni golpear: es un efecto con posición, no un blanco. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        Vec3 from = this.position();
        Vec3 to = from.add(this.getDeltaMovement());

        // **La pared primero.** Un raycast contra colisionadores: la onda muere donde moriría
        // cualquiera, y el aire, el pasto y el agua la dejan pasar por la misma razón por la que
        // dejan pasar el tajo (`ClipContext.Block.COLLIDER`).
        HitResult blocked = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blocked.getType() != HitResult.Type.MISS) {
            this.discard();
            return;
        }

        this.setPos(to.x, to.y, to.z);

        // **El rastro.** Partículas de bruja, del lado del servidor para que las vean todos: la
        // onda es una cosa que el rival tiene que poder leer.
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.WITCH, to.x, to.y, to.z, 4, 0.12, 0.12, 0.12, 0.01);
        }

        this.sweep(from, to);

        if (this.tickCount > SweepingEdge.lifeTicks(enchantLevel())) {
            this.discard();
        }
    }

    /**
     * Cobra lo que la caja barrida toca, en orden de cercanía.
     *
     * <p>La caja es el segmento recorrido este tick inflado por el ancho del tajo, así que a un
     * bloque por tick no hay agujeros entre ticks: lo que la onda pasó por encima, lo cobra.</p>
     */
    private void sweep(Vec3 from, Vec3 to) {
        double half = SweepingEdge.width(enchantLevel()) * 0.5;
        AABB swept = new AABB(from, to).inflate(half);
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, swept)) {
            if (this.hits >= SweepingEdge.pierce(enchantLevel())) {
                return;
            }
            if (this.alreadyHit.contains(target.getId())) {
                continue;
            }
            if (target == this.level().getEntity(this.skipId) && !reflected()) {
                // El golpe de melé ya lo cobró: la onda es lo que sigue de largo.
                continue;
            }
            if (!reflected() && this.throwerId != null && this.throwerId.equals(target.getUUID())) {
                continue;
            }
            // **La guardia la devuelve.** El que bloquea no la cobra, y desde acá la onda puede
            // herir a quien la tiró -- la decisión del dueño, y el contrajuego que la vuelve justa.
            if (target instanceof Player blocking && GuardHandler.isGuarding(blocking)) {
                reflect();
                return;
            }
            this.alreadyHit.add(target.getId());
            this.hits++;
            applyTo(target);
        }
    }

    private void applyTo(LivingEntity target) {
        Entity thrower = this.throwerId == null ? null : ((ServerLevel) this.level()).getEntity(this.throwerId);
        target.hurt(thrower == null
                        ? this.damageSources().magic()
                        : this.damageSources().indirectMagic(this, thrower),
                this.damage);
        Vec3 push = this.getDeltaMovement().normalize().scale(SweepingEdge.PUSH);
        target.push(push.x, 0.05, push.z);
        target.hurtMarked = true;
    }

    /** La onda vuelve por donde vino, y cambia de bando. */
    private void reflect() {
        this.setDeltaMovement(this.getDeltaMovement().scale(-1.0));
        this.entityData.set(DATA_REFLECTED, true);
        // **Y el cliente se entera de que dio vuelta.** El modelo se orienta con el movimiento, y
        // `setDeltaMovement` no sincroniza nada: sin esto la onda que vuelve se dibuja mirando para
        // adelante -- el mismo objeto al revés, justo en el único momento en que su dirección es la
        // información que importa.
        this.hurtMarked = true;
        // Los que ya cobraron no cobran de nuevo al volver: es la misma onda.
        this.hits = 0;
        this.alreadyHit.clear();
    }

    /**
     * El id con el que esta entidad se registra, expuesto para el log de arranque.
     *
     * <p>Existe porque una entidad sin registro es un fallo silencioso hasta que alguien dispara:
     * el log lo dice al cargar.</p>
     */
    public static String describe() {
        return CortesCombat.MODID + ":slash_wave";
    }
}
