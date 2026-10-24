package com.cortes.combat.entity;

import com.cortes.combat.enchant.CombatEnchantRules;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The 1.20.1 body of Mungus's shell. Same arrow flight, older fireball.
 *
 * <p>1.20.1 accelerates through the public {@code xPower/yPower/zPower} fields instead of
 * the 1.21.1 {@code accelerationPower}, and the shooting constructor takes the direction
 * split into three doubles. Zeroing the power in <em>both</em> constructors is the same
 * fix the shared twin documents: the client builds from the spawn packet with the
 * {@code (EntityType, Level)} constructor, and a number set only on the server side
 * leaves the client simulating another trajectory. See the shared twin for the full
 * story -- why a fireball base, why 0.85, why the type override, why gravity first.
 * (⌒‿⌒)</p>
 */
public class MungusShell extends LargeFireball implements net.minecraft.world.entity.projectile.ItemSupplier {

    /**
     * La velocidad con la que sale, en bloques por tick.
     */
    private static final double LAUNCH_SPEED = 0.85D;

    /**
     * Cuanto se le resta a la velocidad vertical por tick.
     */
    private static final double GRAVITY_PER_TICK = 0.004D;

    /**
     * La inercia, y es la de la flecha.
     */
    private static final float INERTIA = 0.99F;

    /** La que usa el cliente al crear la entidad del paquete de spawn. */
    public MungusShell(EntityType<? extends LargeFireball> type, Level level) {
        super(type, level);
        this.xPower = 0.0D;
        this.yPower = 0.0D;
        this.zPower = 0.0D;
    }

    /** La que usa el servidor al disparar. */
    public MungusShell(Level level, LivingEntity shooter, Vec3 direction, int explosionPower) {
        super(level, shooter, direction.x, direction.y, direction.z, explosionPower);
        this.xPower = 0.0D;
        this.yPower = 0.0D;
        this.zPower = 0.0D;
        Vec3 unit = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D)
                : direction.normalize();
        this.setDeltaMovement(unit.scale(LAUNCH_SPEED));
        // **Y `hasImpulse`, para que el servidor mande este vector en el primer tick.**
        this.hasImpulse = true;
    }

    /**
     * El tipo propio, y esta es la linea que arregla los tirones. Ver el gemelo compartido.
     */
    @Override
    public EntityType<?> getType() {
        return com.cortes.combat.registry.ModEntities.MUNGUS_SHELL.get();
    }

    /**
     * La inercia propia: {@code AbstractHurtingProjectile#tick} la lee de aca.
     */
    @Override
    protected float getInertia() {
        return INERTIA;
    }

    @Override
    public void tick() {
        // **La gravedad va ANTES de `super.tick()`.** Ver el gemelo compartido: empujar despues
        // seria mover una bola que ya se movio recto. Y va en los DOS lados.
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, motion.y - GRAVITY_PER_TICK, motion.z);
        super.tick();
    }

    /**
     * Lo que el renderer dibuja: una Firestone entera, del mismo item que la municion.
     */
    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }
}
