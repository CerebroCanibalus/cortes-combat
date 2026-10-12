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
 * El Firestone enorme de Mungus: una bola de fuego <b>que se mueve como una flecha</b>.
 *
 * <h2>Por que hereda de {@code LargeFireball} y no de {@code ThrowableProjectile}</h2>
 *
 * <p>Porque lo que el encantamiento quiere es una explosion con fuego que rompe bloques y cuyo
 * dano se le atribuye a quien la tiro, y {@code LargeFireball} ya hace las tres cosas -- la regla
 * del proyecto es extender antes de inventar, y reescribir eso seria reimplementar tres cosas para
 * equivocarse en una.</p>
 *
 * <h2>Por que hereda, pero deja de moverse como una bola de fuego</h2>
 *
 * <p><b>Esto es lo que el playtest reporto como "da tirones y se ve raro", y la causa esta en
 * {@code AbstractHurtingProjectile#tick}.</b> La bola de fuego vanilla <b>no vuela</b>: se
 * <b>auto-acelera</b>. Su constructor la lanza a {@code 0,1} bloques por tick -- casi quieta -- y
 * cada tick hace</p>
 *
 * <pre>{@code v = (v + v̂ · 0.1) · 0.95}</pre>
 *
 * <p>que es una aceleracion a lo largo de su <b>propia direccion</b>. El resultado es que arranca
 * arrastrandose y termina a unos <b>1,9 bloques por tick</b>: la velocidad se multiplica por
 * <b>diecinueve</b> a lo largo del vuelo. Y como la gravedad dobla la direccion mientras vuela, la
 * auto-aceleracion empuja siempre <em>hacia donde la curva ya va</em>, asi que el arco se cierra
 * sobre el piso en vez de ser una parabola.</p>
 *
 * <p><b>Y una flecha no hace nada de eso.</b> {@code AbstractArrow} es <b>arrastre constante mas
 * gravedad</b> -- {@code v·0,99} y un numero que se resta -- asi que su velocidad solo baja, la
 * trayectoria es una parabola limpia, y las dos mitades (cliente y servidor) integran la misma
 * funcion monotona. Eso es lo que se pide aca: <b>el modelo de la flecha</b>.</p>
 *
 * <h2>Las tres cosas que hay que tocar, y la que casi se pasa</h2>
 *
 * <ol>
 *   <li><b>{@code accelerationPower = 0}.</b> Apaga la auto-aceleracion: lo que queda es
 *       {@code v·getInertia()}, arrastre puro.</li>
 *   <li><b>{@code getInertia() = 0,99}</b>, la de la flecha, y no el {@code 0,95} de la bola de
 *       fuego -- un arrastre suave deja un vuelo largo y parejo.</li>
 *   <li><b>Una velocidad de salida de verdad.</b> Con la aceleracion en cero, lanzarla a
 *       {@code 0,1} la dejaria flotando: el constructor la deja en {@code 0,1} por el
 *       {@code accelerationPower}, asi que hay que darle la suya.</li>
 * </ol>
 *
 * <p><b>Y la que casi se pasa: {@code accelerationPower} se pone en LOS DOS constructores.</b> El
 * cliente no usa el constructor que dispara -- lo construye el paquete de spawn, con el constructor
 * {@code (EntityType, Level)} -- asi que un numero puesto solo del lado del servidor deja al cliente
 * simulando <em>otra</em> trayectoria, que es el bug que este archivo entero trata de evitar. Hay un
 * paquete que sincroniza ese campo ({@code ClientboundProjectilePowerPacket}), pero llega despues de
 * que el cliente ya tickeo el primer frame, y no hay ninguna razon para depender de el.</p>
 *
 * <h2>Y tiene tipo propio</h2>
 *
 * <p>Un proyectil <b>lo simula tambien el cliente</b>. Con {@code EntityType.FIREBALL} el cliente
 * crearia una bola de vanilla y las dos copias se separarian; con su propio tipo las dos mitades
 * crean <b>esta</b> clase y las dos leen estos numeros.</p>
 */
public class MungusShell extends LargeFireball implements net.minecraft.world.entity.projectile.ItemSupplier {

    /**
     * La velocidad con la que sale, en bloques por tick.
     *
     * <p>Es el numero que la bola de fuego <b>no tiene</b>: vanilla la lanza a {@code 0,1} y confia
     * en la auto-aceleracion para que llegue. Con la auto-aceleracion apagada, esta es la unica que
     * hay, y {@code 0,85} es una flecha de arco a media carga -- cruza unos cuarenta bloques con el
     * arrastre de abajo.</p>
     */
    private static final double LAUNCH_SPEED = 0.85D;

    /**
     * Cuanto se le resta a la velocidad vertical por tick.
     *
     * <p><b>0,004, y el playtest pidio "que caiga un poco menos".</b> La primera version usaba
     * {@code 0,03} -- la caida de una bola de nieve, que aterrizaba en los pies del que disparaba --
     * y la segunda {@code 0,006}, que ya era un arco. Esta es un tercio menos que esa: la curva se
     * ve y sigue siendo una curva, pero apuntar apenas arriba alcanza a lo lejos.</p>
     */
    private static final double GRAVITY_PER_TICK = 0.004D;

    /**
     * La inercia, y es la de la flecha.
     *
     * <p>{@code 0,95} es la de la bola de fuego: frena un cinco por ciento por tick, que sobre un
     * vuelo de cuarenta ticks es perder el ochenta y cinco por ciento de la velocidad. {@code 0,99}
     * es lo que usa {@code AbstractArrow} y lo que hace que una flecha siga siendo una flecha al
     * final del arco.</p>
     */
    private static final float INERTIA = 0.99F;

    /**
     * Cero, y es <b>el</b> numero de esta clase.
     *
     * <p>Es el que apaga la auto-aceleracion. Ver la nota de la clase: con {@code 0,1} la velocidad
     * se multiplica por diecinueve y el arco se cierra sobre el piso.</p>
     */
    private static final double ACCELERATION_POWER = 0.0D;

    /** La que usa el cliente al crear la entidad del paquete de spawn. Ver la nota de la clase. */
    public MungusShell(EntityType<? extends LargeFireball> type, Level level) {
        super(type, level);
        this.accelerationPower = ACCELERATION_POWER;
    }

    /** La que usa el servidor al disparar. */
    public MungusShell(Level level, LivingEntity shooter, Vec3 direction, int explosionPower) {
        super(level, shooter, direction, explosionPower);
        this.accelerationPower = ACCELERATION_POWER;
        // **La velocidad de salida, que vanilla deja en 0,1.** El constructor de arriba la puso en
        // `direccion · 0,1` (por `assignDirectionalMovement`) y con la aceleracion apagada esa seria
        // toda la que hay: la bola saldria flotando.
        Vec3 unit = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D)
                : direction.normalize();
        this.setDeltaMovement(unit.scale(LAUNCH_SPEED));
        // **Y `hasImpulse`, para que el servidor mande este vector en el primer tick.**
        // `setDeltaMovement` no avisa a nadie; el paquete de spawn lleva el movimiento que el
        // `ServerEntity` capturo al crear la entidad, y marcar el impulso es lo que hace que el
        // primer tick tambien lo mande. Sin esto el cliente arranca con el vector viejo.
        this.hasImpulse = true;
    }

    /**
     * El tipo propio, <b>y esta es la linea que arregla los tirones.</b>
     *
     * <h2>Que pasa si no esta</h2>
     *
     * <p>{@code LargeFireball} tiene dos constructores, y <b>el que dispara pisa el tipo</b>:</p>
     *
     * <pre>{@code
     * public LargeFireball(Level level, LivingEntity owner, Vec3 movement, int explosionPower) {
     *     super(EntityType.FIREBALL, owner, movement, level);   // <-- nuestro tipo, a la basura
     *     this.explosionPower = explosionPower;
     * }
     * }</pre>
     *
     * <p>No hay overload que acepte un {@code EntityType} <em>y</em> un dueno <em>y</em> una potencia, asi
     * que heredar el disparo significa heredar el tipo de vanilla. Y como el paquete de spawn lleva
     * {@code getType()} y no la clase, <b>el cliente creaba una bola de fuego vanilla y el servidor
     * simulaba esto</b>: dos fisicas distintas sobre la misma entidad. La copia del cliente se
     * corregia contra la del servidor en cada paquete de posicion, y eso es exactamente lo que el
     * playtest describio como "tirones raros, es un problema de sincronizacion, la fisica se siente
     * correcta" -- porque la posicion que se ve ES la del servidor, y lo que salta es la distancia
     * entre las dos copias.</p>
     *
     * <p><b>Y no era sutil: el cliente simulaba una bola SIN gravedad y CON auto-aceleracion</b>
     * ({@code accelerationPower 0,1}, inercia 0,95) mientras el servidor simulaba una con gravedad y
     * sin auto-aceleracion. Las dos se separaban {@code 0,1} por tick al cuadrado y la correccion
     * llegaba cada dos ticks. Poner la gravedad en los dos lados -- el arreglo anterior -- no podia
     * alcanzar, porque el cliente nunca ejecutaba esta clase.</p>
     *
     * <h2>Por que se arregla sobreescribiendo y no pasando el tipo</h2>
     *
     * <p>Porque <b>no hay constructor de vanilla que deje las dos cosas</b>: {@code Fireball} tiene uno
     * que acepta el tipo y el dueno, pero no la potencia, y {@code explosionPower} es privado y sin
     * setter. Las dos salidas eran: reescribir {@code onHit} para tener potencia propia, o devolver el
     * tipo desde el acceso. La segunda es una linea y no duplica el reventon -- y la regla del
     * proyecto es extender antes de inventar.</p>
     *
     * <p><b>Es seguro porque {@code Entity#getType()} no es final</b>, y porque los unicos lugares de
     * vanilla que leen el campo crudo en vez del acceso son dos y los dos dan igual: las dimensiones
     * ({@code FIREBALL} mide 1,0 por 1,0 y este tambien -- ver {@code ModEntities}) y un tag de dano por
     * caida que a un proyectil no le importa. Todo lo demas -- el paquete de spawn, el guardado, el
     * tracking, el intervalo de actualizacion -- pasa por {@code getType()}.</p>
     *
     * <p><b>Y el sintoma de que esto vuelva a faltar es silencio.</b> El tipo de vanilla existe, la
     * entidad se crea, no hay error en el log: lo unico que se ve es una bola que tiembla.</p>
     */
    @Override
    public EntityType<?> getType() {
        return com.cortes.combat.registry.ModEntities.MUNGUS_SHELL.get();
    }

    /**
     * La inercia propia, y es la mitad del arreglo. Ver {@link #INERTIA}.
     *
     * <p>{@code AbstractHurtingProjectile#tick} la lee de aca, asi que sobreescribirla es lo que
     * cambia el arrastre de bola de fuego por el de flecha <b>sin tocar la clase de vanilla</b>.</p>
     */
    @Override
    protected float getInertia() {
        return INERTIA;
    }

    @Override
    public void tick() {
        // **La gravedad va ANTES de `super.tick()`, y no es un detalle de estilo.**
        //
        // `AbstractHurtingProjectile#tick` lee `getDeltaMovement()` DENTRO del metodo: de ahi saca
        // la posicion a la que avanza y de ahi recalcula el vector. Empujar hacia abajo DESPUES de
        // `super` seria mover una bola que ya se movio recto, y el resultado seria un tiron por
        // tick en vez de un arco. Empujando antes, el vector que `super` lee ya viene cayendo.
        //
        // **Y va en los DOS lados.** Un proyectil lo simula tambien el cliente, asi que un
        // `if (!isClientSide)` aca deja al cliente volando recto: su posicion se corrige contra la
        // del servidor en cada paquete y eso, mas que un arco, se lee como una bola que tiembla.
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, motion.y - GRAVITY_PER_TICK, motion.z);
        super.tick();
    }

    /**
     * Lo que el renderer dibuja, que es lo que hace que esto se vea.
     *
     * <p>Una Firestone entera, del mismo item que la municion del arcabuz: el proyectil es lo que
     * la recamara escupe, y dibujar otra cosa seria mentir sobre que costo el disparo. Va por
     * {@link net.minecraft.world.entity.projectile.ItemSupplier} para poder usar el renderer de
     * items tirados que ya existe en vez de escribir uno.</p>
     *
     * <p>Y la potencia de la explosion se la pasa el arcabuz: {@code MUNGUS_POWER} vive en
     * {@link CombatEnchantRules} como todo el balance del proyecto.</p>
     */
    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.FIRE_CHARGE);
    }
}
