package com.cortes.combat.entity;

import com.cortes.combat.registry.ModEntities;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * El Firestone enorme de Mungus: una bola de fuego <b>que cae</b>.
 *
 * <h2>Por que hereda de {@code LargeFireball} y no de {@code ThrowableProjectile}</h2>
 *
 * <p>Porque lo que el encantamiento quiere es una explosion con fuego que rompe bloques y cuyo
 * dano se le atribuye a quien la tiro, y {@code LargeFireball} ya hace las tres cosas -- la regla
 * del proyecto es extender antes de inventar, y reescribir eso seria reimplementar tres cosas para
 * equivocarse en una. Lo unico que se le agrega es <b>la gravedad, que es lo que no tenia</b>.</p>
 *
 * <h2>La gravedad va ANTES de {@code super.tick()}, y no es un detalle de estilo</h2>
 *
 * <p>{@code AbstractHurtingProjectile#tick} lee {@code getDeltaMovement()} <b>dentro</b> del
 * metodo: de ahi saca la posicion a la que avanza y de ahi recalcula el vector con su inercia y su
 * aceleracion. Empujar hacia abajo <b>despues</b> de {@code super} seria mover una bola que ya se
 * movio recto y el resultado seria un tiron por tick, no un arco. Empujando antes, el vector que
 * {@code super} lee ya viene cayendo, la posicion avanza con la caida y la inercia sigue la curva
 * en vez de pelearla.</p>
 *
 * <h2>Y tiene tipo propio, que es la mitad de la correccion</h2>
 *
 * <p>Un proyectil <b>lo simula tambien el cliente</b>. Si esto fuera un {@code LargeFireball}
 * comun con la gravedad agregada por el servidor, el cliente crearia una bola recta desde el
 * paquete de spawn y las dos copias se separarian a razon de {@code 0.03} por tick al cuadrado:
 * unos seis bloques en veinte ticks, o sea una bola que tiembla y explota en otro lado. Con su
 * propio {@code EntityType} las dos mitades crean <b>esta</b> clase y las dos caen igual.</p>
 */
public class MungusShell extends LargeFireball implements net.minecraft.world.entity.projectile.ItemSupplier {

    /**
     * Cuanto se le resta a la velocidad vertical por tick.
     *
     * <p>{@code 0.03} es el mismo numero que {@code ThrowableProjectile#getGravity}, y es el que
     * el jugador ya conoce: es la caida de una perla de ender, una bola de nieve o un huevo. Un
     * arco que se aprende una vez y sirve para todas las cosas que se tiran.</p>
     */
    private static final double GRAVITY_PER_TICK = 0.03D;

    public MungusShell(EntityType<? extends LargeFireball> type, Level level) {
        super(type, level);
    }

    public MungusShell(Level level, LivingEntity shooter, Vec3 direction, int explosionPower) {
        super(level, shooter, direction, explosionPower);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide) {
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(motion.x, motion.y - GRAVITY_PER_TICK, motion.z);
        }
        super.tick();
    }

    /**
     * Lo que el renderer dibuja, que es lo que hace que esto se vea.
     *
     * <p>Una Firestone entera, del mismo item que la municion del arcabuz: el proyectil es lo que
     * la recamara escupe, y dibujar otra cosa seria mentir sobre que costo el disparo. Va por
     * {@link net.minecraft.world.entity.projectile.ItemSupplier} para poder usar el renderer de
     * items tirados que ya existe en vez de escribir uno.</p>
     */
    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }
}
