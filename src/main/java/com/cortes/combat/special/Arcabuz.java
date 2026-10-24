package com.cortes.combat.special;

import com.cortes.combat.platform.WeaponData;

import com.cortes.combat.platform.CombatPackets;

import com.cortes.combat.platform.CombatState;

import com.cortes.combat.attack.AttackCooldown;
import com.cortes.combat.attack.Knockback;
import com.cortes.combat.attack.WeaponStats;
import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.enchant.CombatEnchantments;
import com.cortes.combat.core.CombatFeedback;
import com.cortes.combat.core.weapon.AmmoProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.net.ArcabuzShotPayload;
import com.cortes.combat.net.ModNetworking;
import com.cortes.combat.particle.SlashParticles;
import com.cortes.combat.reach.Reach;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.sound.CombatSounds;
import com.cortes.combat.sound.ModSounds;
import com.cortes.combat.state.ArcabuzState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The arcabuz's right button: light the fuse, or load the gun.
 *
 * <h2>One button, two meanings, decided by what is already in it</h2>
 *
 * <p>This is the same idiom the whole combat system is built on -- one press meaning two things
 * -- applied to a different axis. The attack button means "hit" or "hit harder" depending on how
 * long it was held; this one means "load" or "fire" depending on whether a firestone is in the
 * chamber. Both readings come out of {@link ArcabuzState}, and the state is the only thing that
 * decides, which is why the decision is a test rather than a hope.</p>
 *
 * <h2>Why the press does not fire</h2>
 *
 * <p>The press lights a fuse and spends the firestone. The shot is twenty ticks later, resolved
 * by {@link #tick} from the server's own clock. That split is the whole design -- a musket is not
 * a weapon whose damage happens when you click -- and it is also the third time this project has
 * had to write the "a state that outlives the button that made it" rule down. The two earlier
 * ones were the ropera's discarded charge and the parry's swallowed countdown, and both were a
 * caller asking a question the state could have answered itself.</p>
 *
 * <p>So a second press while the fuse burns is refused, and refused <em>without</em> resetting
 * the countdown. See {@link ArcabuzState#canLight()}.</p>
 *
 * <h2>What the shot costs, and what it does not</h2>
 *
 * <ul>
 *   <li><b>One firestone</b>, spent at the light rather than at the shot, so a gun with a live
 *       fuse cannot be topped up behind the player's back.</li>
 *   <li><b>One second of standing still</b>, which is the delay and is not a cooldown.</li>
 *   <li><b>The attack cooldown, on the reload only.</b> That is the decision requirement 6 asks
 *       to be justified, and it is the halberd's argument verbatim: a special with its own timer
 *       stacks with the swing cooldown, and then "what does this button cost" is two numbers.
 *       Loading is not free and it is not instant, and the one number the player already knows
 *       how to read is the bracket around the crosshair.</li>
 * </ul>
 *
 * <p>Lighting the fuse deliberately does <b>not</b> touch the cooldown. It already costs a
 * firestone and a second of doing nothing, and paying a third price would make the most
 * expensive button in the game also the one with the most arithmetic on it. The result is a
 * rhythm of about a second and a half per shot -- half walking, half loading -- which is what a
 * black powder weapon is.</p>
 */
public final class Arcabuz {

    /**
     * Matches the anti-double-hit window the normal attack uses.
     *
     * <p>Cleared before <em>every</em> hit in the loop rather than once before it, for the same
     * reason {@code CombatEngine#apply} does: the window is one full second in vanilla, ten of
     * our targets would collapse into one hit if it were not re-armed between them, and "a blast
     * that hit four things because three of them were immune" reads as the weapon being broken
     * rather than as vanilla working.</p>
     */
    private static final int IFRAME_TICKS = 10;

    private Arcabuz() {
    }

    /**
     * Resolves one right-button press on the arcabuz.
     *
     * <p>Exactly one of two things happens, and the state decides which. Nothing else: no third
     * branch, no "if it is somehow both", because the state cannot be both
     * ({@code loaded && fuseTicks > 0} is refused by both gates at once).</p>
     */
    public static void resolve(ServerPlayer player, ServerLevel level, WeaponStats stats) {
        // **El estado es del ARMA, y se lee de la mano.**
        //
        // Aca vivia `state.withChamber(chamber(player))`, una reconciliacion entre el attachment del
        // jugador y el stack -- porque el estado estaba en el jugador y el stack era la verdad sobre
        // la recamara. Con el estado en el stack esa reunion no existe: <b>lo que dice el arma ES lo
        // que hay</b>, y no hay segundo lugar del que pueda discrepar. Ver `ModComponents#ARCABUZ`
        // para los tres bugs que esa reunion produjo.
        net.minecraft.world.item.ItemStack gun = player.getMainHandItem();
        ArcabuzState state = state(gun);
        long now = level.getGameTime();

        if (state.loaded() && !state.isBurning() && !state.isReloading()) {
            // The firestone is gone on this tick, before the fuse has moved one step. That is the
            // rule the whole state exists for and putting it here makes it true by construction
            // rather than by the order of two statements further down.
            // **El largo de la mecha, calculado una vez y usado en los dos lugares.**
            //
            // El estado del servidor y el paquete tienen que decir el mismo numero, porque uno cuenta
            // los ticks hasta el disparo y el otro los ticks que la animacion tiene que dibujar. Con
            // Mecha Corta son quince o diez en vez de veinte, y una constante en cualquiera de los dos
            // seria una mecha que dispara a los veinte con la animacion terminada a los diez.
            int fuse = CombatEnchantRules.fuseTicks(CombatEnchantments.level(
                    player.level().registryAccess(), player.getMainHandItem(),
                    CombatEnchantments.MECHA_CORTA), ArcabuzState.FUSE_TICKS);
            // **Mungus: la carga al doble.** Es la otra mitad de su precio, y la que importa de
            // verdad: el tiro mas fuerte del juego cuesta diez Firestones -- que se juntan -- y
            // **el doble de tiempo con el arma apuntada y sin poder hacer otra cosa**. La municion
            // se consigue; esos dos segundos largos son los que el rival te ve venir.
            if (CombatEnchantments.level(player.level().registryAccess(), player.getMainHandItem(),
                    CombatEnchantments.MUNGUS) > 0) {
                fuse = (int) (fuse * CombatEnchantRules.MUNGUS_CHARGE_FACTOR);
            }
            // **La mecha y la recamara se escriben en la MISMA escritura y sobre el MISMO stack.**
            //
            // Antes eran dos lugares: `light()` iba al attachment y el `remove(ARCABUZ_CHAMBER)` al
            // stack, y de ahi salia el `ARCABUZ_GUN` que anotaba contra que arma iban. Ahora hay un
            // solo objeto de estado y no hay nada que pueda quedar apuntando a otra arma.
            WeaponData.setArcabuzState(gun, state.light(now, fuse));
            // **loaded = true, and that one word is the whole of "apunta hacia abajo".** The
            // firestone is spent from the CHAMBER's point of view at the shot, not at the light:
            // the ball is still in the barrel while the fuse burns, so the loaded pose stays for
            // the whole second. Sending false here made the gun point at the floor until the shot
            // went off, and two channels moving opposite ways over that second is what read as a bob.
            ModNetworking.sendArcabuzFuse(player, true, fuse, true, 0);
            CombatSounds.play(level, player, WeaponProfile.ARCABUZ, ModSounds.Kind.FUSE_LIT);
            trace(player, state, "mecha encendida, Firestone gastada");
            return;
        }

        // **En creativo la recarga no pide la Firestone, y SOLO en creativo.**
        //
        // Es la unica excepcion a "el arma cobra lo que dice cobrar", y la razon es que en creativo
        // el jugador ya tiene acceso infinito a la municion por otra via -- saca un stack del menu
        // cuando quiera -- asi que exigirla no hace el arma mas cara: hace que probarla cueste dos
        // pasos en vez de uno. Lo que NO cambia es el tiempo: la recarga sigue durando lo que dura y
        // la mecha sigue ardiendo. Un creativo instantaneo si seria un arma distinta.
        //
        // El modo se pregunta por `isCreative` y no por `isSpectator` tambien: un espectador no
        // deberia disparar nada, y `canReload` ya lo corta antes por otras vias.
        boolean freeAmmo = player.isCreative();
        if (state.canReload() && !freeAmmo && ammoSlot(player, player.getMainHandItem()) < 0) {
            // **The fifth refusal, and the one that had no line of its own.** The reload used to
            // open on any press of an empty gun, so holding the button for two and a half seconds
            // drew the entire reload motion -- the lean that reads as the loaded pose -- and then
            // spent itself on nothing. The player was shown a reload that could not happen, which
            // is the same class of lie as the model drawing a charged musket over an empty one, and
            // it is fixed the same way: refuse it on the press instead of discovering it fifty
            // ticks later, which costs the player half a second of their time to learn.
            LOG.info("[arcabuz] press RECHAZADO: sin Firestone, la recarga no arranca");
        } else if (state.canReload()) {
            // **The firestone is NOT spent on this press, and that is a change.**
            //
            // It used to be: load() took one here, so a player who pressed and then let go had
            // been charged for a gun they never loaded. The press now only opens the reload and
            // the firestone is spent in tick(), on the tick the gun actually becomes loaded.
            // Letting go costs nothing, which is the only thing "I let go" can mean.
            //
            // The price the reload used to pay -- the attack cooldown, and the reload sound --
            // moved with it, to the tick that finishes.
            //
            // **Lo que NO hace falta declarar es el boton.** Un press ES la evidencia de que el boton
            // esta abajo, asi que la recarga no depende de que el paquete de "apretado" haya llegado
            // antes: el `tick` pregunta por el boton del jugador, que es donde vive, y esta recarga
            // solo avanza si lo esta.
            int reloadTotal = reloadTicks(gun, player);
            WeaponData.setArcabuzState(gun, state.startReload(now, reloadTotal));
            // **The press sound is NOT played here, and its absence is the fix.**
            //
            // It used to be, and this is the line that has to go for the reload to be able to
            // stop sounding. `ServerLevel#playSound` hands the sound to `PlayerList#broadcast` and
            // then forgets about it -- the server keeps no list of live sounds -- so from here the
            // only way to end it would be a stop with nothing to name. A reload the player abandoned
            // would keep grinding to the end of the file.
            //
            // **The client starts it instead**, in `ArcabuzFuseState.tick`, off the same count that
            // drives the motion, and cuts it on the tick the count is no longer running. It follows
            // the player too, which this path could not: the packet carries coordinates, so the
            // sound stayed where it was played from.
            //
            // The press is still the moment it sounds at -- the count arrives here, on the press --
            // so what is lost is only the server's inability to stop it.
            trace(player, state, "recarga empezada (" + reloadTotal + " ticks)");
            // **And this packet, which is the whole of "no hay forma de saber que estoy
            // recargando".** The server said nothing at all on the press: the RELOAD channel had a
            // track on the arcabuz's profile and no signal from anywhere to move it, so the gun sat
            // in its unloaded pose for two and a half seconds with nothing on screen. The count
            // travels rather than a flag so the motion is exactly as long as the reload.
            ModNetworking.sendArcabuzFuse(player, false, 0, false, reloadTotal);
        }

        // A press mid-fuse, or a press that restarted nothing, is what a cooldown looks like.
        // **This else is the fourth of the five silent refusals and it is here to stop being one.**
        // It is the only branch that runs on every press the player makes at a gun that cannot
        // do what they asked, and it says which of the three conditions stopped it.
        else {
            LOG.info("[arcabuz] press RECHAZADO: cargado={} quemando={} recargando={}",
                    state.loaded(), state.isBurning(), state.isReloading());
        }
    }
    /**
     * The server's copy of the right mouse button.
     *
     * <p>Written from the client's own reconciliation rather than from a release event, because the
     * release is not reliable: GLFW drops it when the window loses focus with a button down, and a
     * flag that only moves on events leaves the reload waiting for a release that is never coming.
     * It is cleared with everything else on login and logout for the same reason the charge and the
     * guard are: it is a button, and a button still set from a connection that no longer exists is
     * state nothing in the game is producing.</p>
     */
    public static void setHeld(ServerPlayer player, boolean held) {
        boolean before = CombatState.arcabuzButton(player);
        if (before == held) {
            return;
        }
        CombatState.setArcabuzButton(player, held);
        // **The log line is not decoration: it is one of the five refusals.** A reload that does not
        // advance and a reload that was never started look identical from outside, and this is the
        // cheapest half of telling them apart.
        LOG.info("[arcabuz] held={} (antes {})", held, before);
    }

    /**
     * Where the player has a firestone, and spends one if they do.
     *
     * <p><b>The ungated line in here is not debug output -- it is the reason this method can be
     * diagnosed at all.</b> The reload path has five places that can refuse: the press never
     * arrives, the state refuses it, the button flag never turns up, the timer never finishes, and
     * the firestone is not there. Four of the five were silent, and a silent refusal reads as
     * "the weapon is broken" rather than as "the player had nothing to load it with".
     * {@code /cc input} exists for the same reason on the attack button.</p>
     *
     * <p>Out of the whole inventory and not out of the hand. The hand has to hold the gun or the
     * gun would not be held, and the recipe for a firestone is two of them out of a gunpowder and
     * a glowstone, so it is a stack a player carries rather than something they click.</p>
     *
     * <p><b>Called when the reload completes, not when it starts</b>, and that difference is a
     * design decision: a reload the player abandoned must not cost anything, or letting go of the
     * button reads as being charged for nothing.</p>
     *
     * <h3>The index, which is the bug this method used to have</h3>
     *
     * <p>It used to be {@code contains(probe)} followed by {@code removeItem(probe)}. The first
     * works -- it compares with {@code isSameItemSameComponents} -- and the second <b>removes
     * nothing at all</b>, because {@code Inventory#removeItem(ItemStack)} is written for "forget
     * this exact stack I just took out" and tests {@code slot == stack}, which for an
     * {@code ItemStack} is <em>reference identity</em>. A freshly built probe is never the instance
     * sitting in a slot, so the comparison never matched, the method returned {@code void} having
     * removed nothing, and the gun loaded for free.</p>
     *
     * <p>The index is the flattened one: {@code Inventory#getItem(int)} and
     * {@code Inventory#removeItem(int, int)} both walk {@code compartments} subtracting sizes, so
     * the read and the write use one loop and cannot disagree about what a number means. Read out
     * of the sources rather than assumed, because the two walking differently is exactly the bug
     * the paragraph above is about.</p>
     */
    private static boolean load(ServerPlayer player) {
        ItemStack weapon = player.getMainHandItem();
        AmmoProfile ammo = declaredAmmo(player, weapon);
        int found = ammoSlot(player, weapon);
        // **En creativo no hace falta tenerla, y no se cobra.** Ver `resolve`: el modo no abarata el
        // TIEMPO de la recarga, solo el paso de conseguir el item. Fuera de creativo esto sigue
        // siendo la quinta negativa silenciosa y sigue devolviendo `false`.
        boolean freeAmmo = player.isCreative();
        if (found < 0 && !freeAmmo) {
            trace(player, state(player.getMainHandItem()),
                    "sin munición (" + ammo.itemId() + ")");
            return false;
        }
        // **La cantidad la dice el componente y no el codigo.** Antes era `removeItem(found, 1)`,
        // y con `count` en el perfil un arma puede gastar mas de una por disparo -- que es como
        // se balancea un arma que dispara de a tres sin que el escaneo del inventario tenga que
        // saber nada de eso. El piso lo pone `AmmoProfile.of`, que nunca deja bajar de uno.
        if (found >= 0) {
            player.getInventory().removeItem(found, ammo.count());
        }
        // **No escribe el estado.** El pago y la carga son dos cosas y las hace el mismo tick, pero
        // en dos lineas: esta cobra la Firestone, y el `tick` que llama pone `finished()` en el
        // componente del arma. Cuando la recamara era un segundo componente y una bandera en el
        // attachment, "cobre y no cargo" era un estado que el jugador no podia leer; ahora es
        // imposible por construccion, porque el que cobra no escribe estado y el que escribe estado
        // es el mismo que cobra.
        return true;
    }

    /** El estado del arcabuz que hay en ese stack. <b>Ausente = un arma recien salida de la caja.</b> */
    private static ArcabuzState state(ItemStack gun) {
        ArcabuzState found = WeaponData.arcabuzState(gun);
        return found == null ? ArcabuzState.EMPTY : found;
    }

    /**
     * The first slot holding a firestone, or -1. <b>Reads and never writes.</b>
     *
     * <p><b>Extracted rather than copied, and the reason is that this question gets asked twice.</b>
     * It used to be asked once, fifty ticks after the player pressed, by {@link #load}. So a player
     * with an empty inventory held the button for two and a half seconds, watched the whole reload
     * motion run -- which leans the weapon down, which is the pose that reads as *loaded* -- and
     * then nothing happened. The gun spent half a second of the player's time drawing a reload it
     * was never going to perform, and told them it was loaded at no point in the process, only
     * animated like it.</p>
     *
     * <p>So the same question is now asked on the press, and the answer is what decides whether the
     * reload starts. This is the same rule the file already states about paying: <b>cheaper to say
     * nothing happened</b>, and the cheapest nothing is the one that costs the player no time.</p>
     *
     * <p>One search, two callers, and the two cannot disagree about where the ammunition is --
     * which is the bug shape the paragraph above on {@link #load} is entirely about.</p>
     */
    private static int ammoSlot(ServerPlayer player, ItemStack weapon) {
        AmmoProfile ammo = declaredAmmo(player, weapon);
        net.minecraft.world.item.Item wanted = resolveAmmo(ammo.itemId());
        if (wanted == net.minecraft.world.item.Items.AIR) {
            LOG.warn("[arcabuz] municion declarada como '{}' no resuelve a ningun item: el arma "
                    + "no va a poder disparar. Revisa el campo `ammo` del pack o del datapack.",
                    ammo.itemId());
            return -1;
        }
        Inventory inventory = player.getInventory();
        int size = inventory.getContainerSize();
        int found = -1;
        int total = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(wanted)) {
                if (found < 0) {
                    found = slot;
                }
                total += stack.getCount();
            }
        }
        LOG.info("[arcabuz] buscar municion {}: {} en {} slots, {} disponibles",
                ammo.itemId(), found < 0 ? "NO" : "slot " + found, size, total);
        return found;
    }

    /**
     * Qué gasta esta arma, y no está clavado en el codigo.
     *
     * <p><b>El componente manda y la Firestone es el default</b>, que es exactamente lo que el
     * mod ya hacia antes de que esto existiera. Un arma sin componente se comporta identica a
     * ayer, y eso es la mitad de la razon del default: las 31 armas que ya estaban no pueden
     * cambiar de precio por un refactor.</p>
     *
     * <h2>Rey de las Moscas cambia la munición, y esa es su identidad entera</h2>
     *
     * <p>Un arcabuz con ese encantamiento gasta <b>carne podrida</b>, que sobra en cualquier
     * cofre, y no una Firestone, que hay que craftear. Sin esto el encantamiento era el mismo
     * disparo con otro resultado -- cuesta lo mismo y hay que fabricar lo mismo -- y lo único que
     * cambiaba era un detalle visual. <b>Lo que lo hace una elección es que el precio baja y lo
     * que devuelve cambia de forma.</b></p>
     *
     * <p>Y va <b>antes</b> de leer el componente, no despues: un arma que declarara su propia
     * munición y además tuviera el encantamiento gasta la del encantamiento, porque es la que el
     * jugador eligió poner y la que el tooltip promete.</p>
     */
    private static AmmoProfile declaredAmmo(ServerPlayer player, ItemStack weapon) {
        // **Mungus primero, y no es un detalle de orden: es el que mas gasta.** Diez Firestones
        // por disparo, contra la una de siempre -- y eso es lo que lo convierte en un tiro de
        // asedio y no en un arma. `AmmoProfile` ya llevaba el `count` desde que se creo, asi que
        // aca no hay nada nuevo: es el mismo mecanismo con otro numero.
        if (CombatEnchantments.level(player.level().registryAccess(), weapon,
                CombatEnchantments.MUNGUS) > 0) {
            return AmmoProfile.of(com.cortes.combat.registry.ModItems.FIRESTONE.getId().toString(),
                    CombatEnchantRules.MUNGUS_FIRESTONES);
        }
        if (CombatEnchantments.level(player.level().registryAccess(), weapon,
                CombatEnchantments.REY_DE_LAS_MOSCAS) > 0) {
            return AmmoProfile.of(ROTTEN_FLESH_AMMO, 1);
        }
        AmmoProfile declared = WeaponData.ammoProfile(weapon);
        return declared == null ? AmmoProfile.firestone() : declared;
    }

    /** Lo que gasta el arcabuz con Rey de las Moscas. Vanilla, y sobra en todos lados. */
    private static final String ROTTEN_FLESH_AMMO = "minecraft:rotten_flesh";

    /**
     * Traduce el id declarado a un item, una sola vez y en el unico sitio que tiene el registro a
     * mano.
     *
     * <p>Devuelve {@code AIR} -- y no {@code null} -- para lo que no resuelve, porque eso es lo
     * que el registro devuelve y compararlo contra un nulo seria una segunda convencion. El
     * llamador es quien avisa.</p>
     */
    private static net.minecraft.world.item.Item resolveAmmo(String itemId) {
        try {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse(itemId));
        } catch (RuntimeException idRoto) {
            LOG.warn("[arcabuz] el id de municion '{}' no es un ResourceLocation valido",
                    itemId, idRoto);
            return net.minecraft.world.item.Items.AIR;
        }
    }

    /**
     * One tick: the fuse counts down, the reload fills, and whichever of them finished pays for
     * itself.
     *
     * <p>Nothing is decided here. {@link ArcabuzState#step()} answers every question -- does the
     * state move, does the shot go off, did the reload finish -- and this writes what it is handed.
     * The parry's ticker used to decide, and the decision it made ("the window is closed, nothing
     */
    public static void tick(ServerPlayer player) {
        // **Aca vivian `shouldSnuff`, `ARCABUZ_GUN` y la reconciliacion, y los tres eran el mismo
        // error visto tres veces: estado del ARMA guardado en el JUGADOR.**
        //
        // `shouldSnuff` preguntaba "¿dejo de haber un arcabuz en la mano?" -- y dos arcabuces pasan
        // ese filtro sin que nadie se entere. `ARCABUZ_GUN` anotaba contra que arma iba todo, y
        // dependia de que el tick llegara antes que el paquete del press, que no es algo que se
        // pueda pedir. La reconciliacion comparaba dos copias de la misma pregunta.
        //
        // **Con el estado en el componente del stack no hay nada que comparar ni que resetear:** lo
        // que se lee es del arma que esta en la mano, por construccion. Un arma que no esta en la
        // mano no se tickea, asi que su mecha no dispara y su recarga no avanza. Ver
        // `ModComponents#ARCABUZ` y `ArcabuzState` para el porque completo.
        net.minecraft.world.item.ItemStack gun = player.getMainHandItem();
        if (!gun.is(ModItems.ARCABUZ.get())) {
            return;
        }
        ArcabuzState state = state(gun);
        if (state.isEmpty()) {
            // Un arma quieta no hace nada, y no escribir nada es lo que mantiene el componente
            // callado: cada escritura reemplaza el stack del cliente y mueve el arma.
            return;
        }
        long now = player.level().getGameTime();

        // **Sin `step()`, sin conteo y sin escritura por tick.**
        //
        // Todo lo que hacia falta era un reloj: el estado guarda el tick del mundo en que termina la
        // mecha y el que termina la recarga, asi que decidir es comparar y avanzar no existe. Eso es
        // lo que permite que el estado viva en el componente del arma sin pagar la animacion de
        // cambio de item, que era el precio que obligaba a tenerlo en el jugador -- y tenerlo en el
        // jugador es de donde salieron los tres bugs. Ver `ModComponents#ARCABUZ`.
        if (state.dueToFire(now)) {
            // **El disparo sale del arma en la mano, y ya no puede salir de otra.** La mecha esta en
            // el componente del stack: un arma que no esta en la mano no se tickea, asi que su mecha
            // no dispara aca. Antes esto necesitaba un `shouldSnuff` y una comparacion de identidad
            // para preguntar "¿esta es la que encendio?"; ahora la pregunta no existe.
            WeaponData.setArcabuzState(gun, state.afterShot());
            // El cliente tiene que bajar la mecha: la pose cargada dura lo que duro la combustion.
            ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
            if (player.level() instanceof ServerLevel server) {
                fire(player, server);
            }
            return;
        }

        if (!state.isReloading()) {
            return;
        }

        boolean button = CombatState.arcabuzButton(player);
        if (!button) {
            // **La cancelacion, y es la mitad que el cliente no puede deducir solo.**
            //
            // Su cuenta corre en su reloj, asi que el que suelta a mitad tiene al servidor
            // cancelando mientras al cliente le quedan veinte ticks por dibujar: el arma seguiria
            // bajando el cañon y volveria sola, por una recarga que ya no existe.
            LOG.info("[arcabuz] recarga CANCELADA (boton soltado en {} de {})",
                    state.reloadTotal() - state.reloadLeft(now), state.reloadTotal());
            WeaponData.setArcabuzState(gun, state.cancelReload());
            ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
            return;
        }

        if (!state.dueToLoad(now) || !(player.level() instanceof ServerLevel server)) {
            return;
        }

        LOG.info("[arcabuz] recarga TERMINADA, cobrando la Firestone");
        // **The price, paid the tick the gun is actually loaded.**
        //
        // If the player cannot pay, the gun goes back to empty rather than becoming loaded: a gun
        // that reports itself loaded and then cannot light is the one state of this weapon a player
        // cannot read, because the model would draw a charged musket over a gun with nothing in it.
        // Cheaper to say nothing happened.
        //
        // The cooldown is here rather than on the press because this is where the reload is really
        // paid for. Half a second of holding a button is not a swing, and the bracket around the
        // crosshair is the only number the player already knows how to read.
        if (load(player)) {
            // **Cobrar y cargar son la misma linea, y sobre el mismo objeto.** Cuando la recamara era
            // un componente aparte de una bandera en el attachment del jugador, "cobre y no cargo"
            // era un estado alcanzable que el jugador no podia leer.
            WeaponData.setArcabuzState(gun, state.finished());
            AttackCooldown.consume(player, AttackCooldown.intervalFor(player));
            CombatSounds.play(server, player, WeaponProfile.ARCABUZ, ModSounds.Kind.LOADED);
            ModNetworking.sendArcabuzFuse(player, false, 0, true, 0);
            trace(player, state, "cargada");
        } else {
            WeaponData.setArcabuzState(gun, state.cancelReload());
            ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
        }
    }



    /**
     * The shot: everything in the cone, up to ten of them, each one set on fire.
     *
     * <p><b>The cone is the weapon's own.</b> {@link Reach#inCone} with {@code stats.reach()},
     * no widened version and no separate profile, because the design says the cone is reused
     * <em>for</em> this weapon: the same eye origin, the same angles, the same leash a target's
     * size has earned. A shotgun with its own wider cone would be a second set of numbers to keep
     * in step with this one, and the client would have to resolve both.</p>
     */
    /**
     * El culatazo: empuja al que disparo, hacia atras y por el suelo.
     *
     * <p><b>Hacia atras y no arriba.</b> El retroceso de un arma de verdad levanta el cañon; al
     * jugador no lo levanta, y un empuje vertical lo saca del piso y lo deja en el aire despues de
     * cada disparo -- que se lee como un glitch y no como un arma pesada. Se empuja por el suelo,
     * que es lo que se siente como "me movio".</p>
     *
     * <p>Mungus empuja el doble: es el tiro mas caro del catalogo, y si algo tiene que sentirse como
     * un cañon de asedio es ese.</p>
     */
    private static void recoil(ServerPlayer player, int mungus) {
        Vec3 look = player.getLookAngle().normalize();
        double push = ArcabuzMuzzle.RECOIL
                * (mungus > 0 ? ArcabuzMuzzle.MUNGUS_RECOIL_FACTOR : 1.0D);
        // `push` suma a la velocidad y `hurtMarked` es lo que manda esa velocidad al cliente: sin la
        // segunda linea el empuje existe en el servidor y el jugador no lo ve, que es la forma en que
        // un retroceso se siente como un teleport al siguiente paquete de posicion.
        player.push(-look.x * push, 0.0D, -look.z * push);
        player.hurtMarked = true;

        // **Y el culatazo en la camara, que es la mitad que el empuje no puede dar.**
        //
        // El `push` mueve el cuerpo, y mover el cuerpo no se siente como un disparo: se siente como
        // que alguien te empujo. Lo que dice "esto fue un arcabuz" es que la vista salte, y para eso
        // esta CameraShake, que ya tiene el decaimiento y las dos frecuencias.
        //
        // **Se SUMA al empuje, no lo reemplaza** -- son dos lecturas distintas del mismo hecho, y
        // el diseno pide las dos: el cuerpo retrocede y la mira salta.
        //
        // Mungus sacude el doble y no las cuatro veces del empuje: cuatro serian tres grados y pico,
        // que es mas que el reventon del orbe. Un disparo pesado tiene que sentirse pesado, no
        // tirarte la camara al piso.
        com.cortes.combat.fx.Shake.to(player, player.getX(), player.getY(), player.getZ(),
                RECOIL_SHAKE_DEGREES * (mungus > 0 ? MUNGUS_SHAKE_FACTOR : 1.0F),
                RECOIL_SHAKE_TICKS);
    }

    /**
     * El temblor del disparo: dos grados, cinco ticks.
     *
     * <p><b>Subió de 0,8 porque el playtest no lo vio.</b> Acá el impulso nace en la posición del
     * jugador, así que la caída por distancia no lo toca -- pero 0,8 grados en cinco ticks sigue
     * siendo menos que la mitad de lo que se siente. Un arcabuz es un arma que te sacude; dos grados
     * lo dicen y no descolocan la puntería.</p>
     */
    private static final float RECOIL_SHAKE_DEGREES = 2.0F;

    /** Y Mungus el doble. Ver la nota del metodo: las cuatro del empuje serian demasiado aca. */
    private static final float MUNGUS_SHAKE_FACTOR = 2.0F;

    private static final int RECOIL_SHAKE_TICKS = 5;

    private static void fire(ServerPlayer player, ServerLevel level) {
        WeaponStats stats = WeaponStats.of(player.getMainHandItem());
        // The item could have changed in the twenty ticks the fuse burned -- and the left click
        // still works during those ticks, so it really can. Refusing is the honest answer for
        // something that is no longer a gun: the shot is what the player did, and paying it with
        // a different weapon's numbers would be a damage number nobody chose.
        if (stats.special() != SpecialType.FUSEE) {
            trace(player, state(player.getMainHandItem()),
                    "el item ya no es un arcabuz, disparo perdido");
            return;
        }

        // **El disparo llego hasta aca.** Incondicional, al tope, y con LOG y no `trace`: un
        // diagnostico que debe contestar la pregunta no puede estar tras el flag de /cc trace --
        // la primera version estuvo ahi y el log salio mudo. Una linea por disparo, y esa es una
        // frecuencia que un log aguanta.
        LOG.info("[arcabuz] DISPARO rey_de_las_moscas={} item={} municion={}",
                CombatEnchantments.level(level.registryAccess(), player.getMainHandItem(),
                        CombatEnchantments.REY_DE_LAS_MOSCAS),
                player.getMainHandItem().getItem().getDescriptionId(),
                declaredAmmo(player, player.getMainHandItem()).itemId());

        // **Mungus: un Firestone enorme, y es un proyectil de verdad.**
        //
        // Va antes que Rey de las Moscas porque los dos son "el disparo hace otra cosa" y un arma
        // no puede tener los dos -- el orden decide cual gana si un datapack los pusiera juntos, y
        // gana el que mas cuesta.
        //
        // **Y reusa `LargeFireball` de vanilla en vez de una entidad propia.** La regla del
        // proyecto es extender antes de inventar, y esta bola ya trae todo lo que el diseño pide:
        // viaja con gravedad, explota al tocar, prende fuego, rompe bloques y **el daño se le
        // atribuye a quien la tiro** -- que es la mitad de por que un proyectil es un proyectil.
        // Escribir una entidad propia seria reimplementar las cinco cosas y equivocarse en una.
        //
        // Lo unico que se le cambia es la potencia, que es el numero del encantamiento: 12 son
        // tres TNT juntas, un crater que rompe piedra y tierra pero no obsidiana.
        int mungus = CombatEnchantments.level(level.registryAccess(), player.getMainHandItem(),
                CombatEnchantments.MUNGUS);
        // **El culatazo, y va ACA y no al final.**
        //
        // Un disparo empuja al que dispara hacia atras, y es lo unico que el jugador siente del
        // retroceso: el arma no se mueve sola en primera persona sin algo que la respalde. Se aplica
        // antes de las dos ramas de abajo porque las dos salen con `return` -- al final del metodo no
        // correria nunca, y el sintoma seria "el culatazo no funciona con Mungus ni con las moscas",
        // que es justo las dos que mas lo deberian sentir.
        recoil(player, mungus);
        if (mungus > 0) {
            Vec3 aim = player.getEyePosition()
                    .add(player.getLookAngle().normalize().scale(ArcabuzMuzzle.FORWARD));
            // **Nuestra entidad y no `LargeFireball`, y la diferencia es que esta cae.** Ver
            // `MungusShell`: la bola de vanilla no tiene gravedad ninguna, y con tipo propio el
            // cliente simula la misma clase en vez de una bola recta que se separa del arco.
            com.cortes.combat.entity.MungusShell shell =
                    new com.cortes.combat.entity.MungusShell(
                            level, player, player.getLookAngle().normalize(),
                            CombatEnchantRules.MUNGUS_POWER);
            shell.setPos(aim.x, aim.y - 0.3, aim.z);
            level.addFreshEntity(shell);
            // El estallido de la boca, igual que el de las moscas: un disparo que no deja nada en
            // el aire es un sonido, y este es el mas caro del juego.
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    aim.x, aim.y, aim.z, 20, 0.2, 0.2, 0.2, 0.03);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    aim.x, aim.y, aim.z, 16, 0.15, 0.15, 0.15, 0.05);
            // **El disparo, y con el pitch mas bajo del proyecto.** La rama no sonaba nada -- ni el
            // disparo normal, que vive mas abajo y esta rama saltea con su `return`. El oido lee
            // "grande" antes de que el ojo vea el crater, y ese es el aviso que el encantamiento
            // debe: si el sonido fuera el de siempre, el tiro mas caro del juego sonaria a tiro.
            level.playSound(null, player, CombatSounds.event(ModSounds.ARCABUZ_SHOT),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, ModSounds.MUNGUS_SHOT_PITCH);
            LOG.info("[arcabuz] DISPARO mungus={} potencia={} municion={}",
                    mungus, CombatEnchantRules.MUNGUS_POWER,
                    declaredAmmo(player, player.getMainHandItem()).itemId());
            return;
        }

        // **Rey de las Moscas: el disparo deja una nube en vez de estallar.**
        //
        // Va aca, al tope y antes de resolver el cono, porque no es un bonus sobre el disparo --
        // es OTRA arma. El disparo normal cuesta una Firestone y devuelve daño inmediato sobre
        // diez cuerpos; este cuesta carne podrida y devuelve una nube que lastima por cuatro
        // segundos a todo el que se quede adentro. Los dos no pueden cobrarse juntos: seria el
        // golpe entero y encima el area, que es la forma en que un encantamiento deja de ser una
        // eleccion.
        //
        // El cono igual se consulta: es lo que decide DONDE cae la nube, y sin un objetivo no hay
        // nube que valga un disparo al aire.
        int flies = CombatEnchantments.level(level.registryAccess(), player.getMainHandItem(),
                CombatEnchantments.REY_DE_LAS_MOSCAS);
        if (flies > 0) {
            List<LivingEntity> seen = targetsInCone(player, level, stats.shotReach());

            // **El estallido en la boca del arcabuz, y sale SIEMPRE.** Un disparo que no deja nada
            // en el aire es un sonido: el rayo de las moscas tiene que salir de algun lado y ese
            // lado es el cañon. Humo porque un arcabuz quema polvora, y moscas porque esto no
            // dispara plomo -- y van juntos porque son el mismo evento.
            //
            // **Y va antes de preguntar si hay objetivo.** La primera version lo puso despues del
            // `if (seen.isEmpty()) return`, asi que dispararle al aire no dejaba nada -- que es
            // justo el caso donde el jugador MAS necesita saber que el arma hizo algo, porque el
            // unico feedback que le queda es el humo.
            Vec3 muzzle = player.getEyePosition()
                    .add(player.getLookAngle().normalize().scale(ArcabuzMuzzle.FORWARD));
            com.cortes.combat.particle.MoscaParticles.swarm(
                    level, muzzle.x, muzzle.y, muzzle.z, 20, 0.35D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    muzzle.x, muzzle.y, muzzle.z, 14, 0.15, 0.15, 0.15, 0.02);
            // **Y el sonido, que faltaba y por eso el tiro salia mudo.** El usuario dejo
            // `arcabuz_shot_mosca.ogg` para esto y ningun evento lo nombraba; y como esta rama
            // hace `return`, el `FUSEE` de mas abajo -- el disparo normal -- nunca llegaba. Va aca
            // arriba, con el humo, y no despues del `if (seen.isEmpty())`: dispararle al aire
            // tambien suena, y ese es el caso donde el jugador mas necesita saber que hizo algo.
            level.playSound(null, player, CombatSounds.event(ModSounds.ARCABUZ_SHOT_MOSCA),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

            trace(player, state(player.getMainHandItem()),
                    "rey de las moscas: nivel=" + flies + " objetivos=" + seen.size());
            if (seen.isEmpty()) {
                trace(player, state(player.getMainHandItem()),
                        "rey de las moscas: sin objetivo, la nube no cae");
                return;
            }
            // **TODOS los del cono, y el playtest lo reporto como bug: solo se infestaba uno.**
            //
            // La primera version tomaba `seen.get(0)` -- el cuerpo mejor apuntado -- con el
            // razonamiento de que "un disparo infesta a alguien". Eso convierte un arma de area en
            // un arma de un solo objetivo con mucho ruido: el cono de escopeta ya decide a quien
            // llega, y dejar afuera a los otros siete que el mismo cono acaba de elegir es cobrar un
            // disparo por lo que devuelve un golpe.
            //
            // Y es coherente con el disparo normal, que pega sobre todos los del cono hasta el tope.
            // La nube es la version lenta de eso: en vez de cobrar el dano de una, lo reparte cuatro
            // segundos. Uno por cuerpo, con el cuerpo adentro.
            int infested = 0;
            for (LivingEntity landing : seen) {
                com.cortes.combat.entity.MobCloud cloud = new com.cortes.combat.entity.MobCloud(
                        com.cortes.combat.registry.ModEntities.MOB_CLOUD.get(), level);
                // A la altura de la caja y no a los pies, que es la regla que ya pagan el fogonazo y
                // la sangre: `getY` es el suelo y una nube en el piso es polvo en los tobillos.
                var box = landing.getBoundingBox();
                cloud.setPos((box.minX + box.maxX) * 0.5,
                        (box.minY + box.maxY) * 0.5 - 0.5, (box.minZ + box.maxZ) * 0.5);
                cloud.setOwner(player);
                // **Y se va con el cuerpo.** Sin esto la nube nace donde el enemigo ESTABA y un
                // zombi que da un paso queda fuera: el encantamiento estaria castigando al que se
                // mueve, que es lo contrario de lo que hace un enjambre.
                cloud.setTarget(landing);
                level.addFreshEntity(cloud);
                infested++;
            }
            // **El zumbido, UNA vez y no una por cuerpo.** El usuario lo dejo en
            // `arcabuz_mosca_loop.ogg` y ningun evento lo nombraba. Suena en el cuerpo donde cae la
            // primera nube y no en la boca del arcabuz -- el disparo ya tiene el suyo, y esto es el
            // enjambre. **Una sola vez aunque haya ocho**: ocho copias del mismo archivo en el mismo
            // tick no son ocho enjambres, son un enjambre mal alto, y el motor tiene un tope de
            // handles de sonido que ya costo una ronda de este proyecto.
            LivingEntity first = seen.get(0);
            level.playSound(null, first, CombatSounds.event(ModSounds.ARCABUZ_MOSCA_LOOP),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            trace(player, state(player.getMainHandItem()),
                    "rey de las moscas: infestados=" + infested);
            ModNetworking.sendFeedback(player, CombatFeedback.HIT);
            CombatSounds.play(level, first, WeaponProfile.ARCABUZ, ModSounds.Kind.HIT);
            return;
        }

        // Found and hit are two numbers, and both are on the log line: `encontrados` is what the
        // cone saw and `golpeados` is what it capped to and actually damaged, so the cap biting
        // is legible in the log rather than a number somebody has to reconstruct.
        List<LivingEntity> found = targetsInCone(player, level, stats.shotReach());
        List<LivingEntity> targets = ArcabuzShot.capped(found);
        float damage = ArcabuzShot.damageFor(player.getAttributeValue(Attributes.ATTACK_DAMAGE));

        // **Where the fire leaves from, on the server, for everybody.**
        //
        // **Not the eye any more.** The old call put it at `eye + look * MUZZLE_OFFSET`, which is a
        // point on the view axis -- dead centre, at the crosshair -- and that is the whole reason it
        // read as the screen flickering rather than as a gun going off. Here it only has to be
        // *near* the barrel, because the streaks are a line between two entities and a point a
        // quarter block off the muzzle changes nothing about where the lava appears to come from.
        //
        // The flash itself is gone from this method and is drawn by the client from the camera
        // basis, which is the only place the gun's real position exists.
        //
        // getEyePosition rather than getEyeY with getX and getZ: 1.21.1 has no getEyeX or
        // getEyeZ on Entity, only the whole vector.
        Vec3 muzzle = player.getEyePosition()
                .add(player.getLookAngle().normalize().scale(ArcabuzMuzzle.FORWARD));

        boolean anyLanded = false;
        boolean anyKilled = false;
        int landedCount = 0;
        // **The things that were actually damaged**, as a list, because the fire is thrown at this
        // one and not at the ten the cone found. A target that dodged the blast -- an armoured one
        // that survived it, or one that was already dead when the loop reached it -- must not have
        // a streak drawn at it, because a streak is a claim about what the shot did.
        List<LivingEntity> landed = new ArrayList<>(targets.size());
        for (LivingEntity target : targets) {
            Knockback.apply(player, target, stats.knockback(), 1.0F);
            // Re-armed before every hit, for the reason IFRAME_TICKS' comment gives.
            target.invulnerableTime = 0;
            float before = target.getHealth();
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                target.invulnerableTime = IFRAME_TICKS;
                anyLanded = true;
                landedCount++;
                landed.add(target);
                anyKilled |= before > 0.0F && target.getHealth() <= 0.0F;
                // Vanilla's fire, applied after the hit landed so a target that dies of the blast
                // is not left smouldering. `setRemainingFireTicks` is the whole API on both
                // legs: 1.21.1 spells it `igniteForSeconds` and 1.20.1 `setSecondsOnFire`,
                // and all three set the same counter to `seconds * 20`. The effect is
                // vanilla's own onFire source doing vanilla's one damage every twenty ticks.
                target.setRemainingFireTicks((int) (ArcabuzShot.FIRE_SECONDS * 20.0F));
                // Vanilla's flame particles, at the centre of the box and not at the feet, for the
                // reason SlashParticles' burst comment gives.
                var box = target.getBoundingBox();
                level.sendParticles(ParticleTypes.FLAME,
                        (box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5,
                        (box.minZ + box.maxZ) * 0.5,
                        12, 0.25, 0.35, 0.25, 0.02);
            }
        }

        // **The fire is thrown, and the flash is announced, after the loop and not before it.**
        //
        // After, because the streaks have to point at the things that were actually damaged and the
        // only way to know that is to have finished the loop. Before, they would point at the ten
        // the cone found, which is a different list.
        blast(player, level, muzzle, landed);

        CombatSounds.play(level, player, WeaponProfile.ARCABUZ, ModSounds.Kind.FUSEE);

        // **One feedback packet for the whole blast, and this is the decision.**
        //
        // The halberd's sweep sends one per target, which is two or three identical crosshair
        // flashes in the same tick and reads as one. At ten targets the same loop would be ten
        // packets to say one thing, and a KILL reported by a later target would be overwritten
        // by a HIT from an earlier one. So the shot reports once, and a kill wins over a hit --
        // the same precedence CombatEngine#notifyHit uses for a single swing.
        if (anyLanded) {
            ModNetworking.sendFeedback(player,
                    anyKilled ? CombatFeedback.KILL : CombatFeedback.HIT);
        }

        LOG.info("[arcabuz] {} encontrados={} golpeados={} dano={} quemados={}s",
                player.getName().getString(), found.size(), landedCount, damage,
                ArcabuzShot.FIRE_SECONDS);
    }

    /**
     * Everything inside the cone, **aimed at first** and then nearest.
     *
     * <p><b>The order is the whole of "los de al frente tienen prioridad", and it is the opposite
     * of what this method used to do.</b> It sorted by distance, which is right for a sword and
     * wrong for a gun: a player aiming at a mob six blocks away with a husk four blocks behind it
     * was asking to shoot the far one, and {@link ArcabuzShot#capped} handed them the near one
     * because it was closer. The weapon was doing the wrong thing with a correct cone.</p>
     *
     * <p>So the sort key is the squared deviation from the look axis, which orders exactly the way
     * an angle would and needs no trigonometry. Distance is the tiebreak and not the primary: two
     * things under the crosshair are both aimed at, and the nearer one is the more urgent.</p>
     *
     * @param reach the <em>shot's</em> cone, never the bayonet's
     */
    private static List<LivingEntity> targetsInCone(ServerPlayer player, ServerLevel level,
                                                    ReachProfile reach) {
        List<LivingEntity> found = new ArrayList<>();
        var box = player.getBoundingBox().inflate(reach.distance() + 1.0);
        for (Entity candidate : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (candidate == player || !candidate.isAlive()
                    || candidate.isSpectator() || candidate.isRemoved()) {
                continue;
            }
            if (Reach.inCone(player, candidate, reach)) {
                found.add((LivingEntity) candidate);
            }
        }
        var look = player.getLookAngle();
        found.sort((a, b) -> ArcabuzShot.compareByAimThenDistance(
                aimDeviation(a, player, look), a.distanceToSqr(player),
                aimDeviation(b, player, look), b.distanceToSqr(player)));
        return found;
    }

    /**
     * How far off the look axis a target sits, so it can be ordered without an angle.
     *
     * <p><b>One minus the dot product, measured from the shooter's eye to the target's eye.</b> The
     * dot product of the look direction with a unit vector towards the target is the cosine of the
     * angle between them, so one minus it is zero for dead ahead and grows towards one at the
     * edge. Squaring is left to the caller.</p>
     *
     * <p><b>Both eyes, and not the target's feet.</b> {@code getLookAngle} leaves the player's eye
     * and {@code getPosition()} is at the target's feet, so measuring between them puts every
     * target a little below where the player is pointing. At six blocks that error is small; at the
     * half block the bayonet is for, it is the whole question. The eye is what the player is
     * looking along.</p>
     *
     * <p>A target exactly on the eye returns zero rather than dividing: it is perfectly aimed, and
     * a {@code NaN} in a sort comparator on the server thread is not a thing worth having.</p>
     */
    private static double aimDeviation(LivingEntity target, ServerPlayer player,
                                       net.minecraft.world.phys.Vec3 look) {
        var to = target.getEyePosition().subtract(player.getEyePosition());
        double length = to.length();
        if (length <= 1.0E-6) {
            return 0.0;
        }
        return 1.0 - (to.x * look.x + to.y * look.y + to.z * look.z) / length;
    }

    /**
 * The fire the shot throws, and who gets to see it.
 *
 * <h2>Why the lava is here and the flash is not</h2>
 *
 * <p>Because a line between two entities is in the world and both of its ends are things the server
 * knows. Fire that flies from the barrel to a husk is a thing every player in the fight should see,
 * and a server that draws it draws it once for everybody.</p>
 *
 * <p>The muzzle flash cannot be drawn here at all: the weapon's transform is applied by the client
 * while it renders, so the only point the server can produce is the player's eye -- which is on the
 * view axis, at the crosshair, which is why the flash used to read as the screen flickering. So the
 * flash goes out as {@link ArcabuzShotPayload} and the client draws it from the camera basis. The
 * numbers both halves use are {@link ArcabuzMuzzle}, so a retune of the smoke does not have to be
 * made twice.</p>
 *
 * <p><b>Streaks towards each thing it hit, and scatter when it hit nothing.</b> The streaks lie
 * between the muzzle and the target so the player reads "that fire went <em>there</em>", and the
 * scatter exists because a gun that fires into an empty field and shows nothing leaves the player
 * unable to tell a wasted shot from a laggy one.</p>
 */
private static void blast(ServerPlayer player, ServerLevel level, Vec3 muzzle,
                                 List<LivingEntity> hit) {
        boolean drewAtSomething = ArcabuzMuzzle.drewAtSomething(hit.size());
        int total = ArcabuzMuzzle.lavaCount(hit.size());

        if (drewAtSomething) {
            // **One call per particle along the line, not one call with a big spread.** A
            // `sendParticles` with a distance spreads inside a box around one point, which draws a
            // cloud; what reads as a line is several points walked between the two ends.
            for (LivingEntity target : hit) {
                Vec3 to = target.getBoundingBox().getCenter();
                for (int step = 1; step <= ArcabuzMuzzle.LAVA_PER_TARGET; step++) {
                    double t = step / (double) (ArcabuzMuzzle.LAVA_PER_TARGET + 1);
                    level.sendParticles(ParticleTypes.LAVA,
                            muzzle.x + (to.x - muzzle.x) * t,
                            muzzle.y + (to.y - muzzle.y) * t,
                            muzzle.z + (to.z - muzzle.z) * t,
                            1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        } else {
            // **Uniform over the sphere and not over a box**, for the reason ArcabuzMuzzle says:
            // a cube's bias makes a whiff read as a spray that was blown.
            for (int i = 0; i < total; i++) {
                double[] direction = ArcabuzMuzzle.randomDirection();
                level.sendParticles(ParticleTypes.LAVA,
                        muzzle.x + direction[0] * 0.3,
                        muzzle.y + direction[1] * 0.3,
                        muzzle.z + direction[2] * 0.3,
                        1, direction[0] * 0.15, direction[1] * 0.15, direction[2] * 0.15, 0.0);
            }
        }

        // **And the flash is told to the shooter, who is the only one who can place it.**
        //
        // The client draws it at the real muzzle and the server cannot, so this payload carries no
        // position -- only the ids the blast reached, so the client throws fire at the same things
        // the server damaged rather than at whatever is under the crosshair when it arrives.
        List<Integer> ids = new ArrayList<>(hit.size());
        for (LivingEntity target : hit) {
            ids.add(target.getId());
        }
        CombatPackets.sendToPlayer(player, new ArcabuzShotPayload(List.copyOf(ids)));
    }

    /**
     * Drops any fuse and any chambered firestone, for a session that starts with nothing.
     *
     * <p>Called from the ticker on login for the same reason the charge and the guard are reset
     * there: {@code holding} is a button, and a button still set from a connection that no
     * longer exists is state nothing in the game is producing.</p>
     */
    public static void clear(ServerPlayer player) {
        // **Solo el boton, y esa es la consecuencia del refactor.** El estado del arma vive en el
        // arma: un arcabuz guardado en un cofre conserva su recamara, que es lo que se quiere, y no
        // hay nada del jugador que resetear salvo el boton. Antes esto vaciaba una recamara que podia
        // ser de cualquier arma, y por eso el jugador perdia la carga al reconectar.
        CombatState.setArcabuzButton(player, false);
        ModNetworking.sendArcabuzFuse(player, false, 0, false, 0);
    }

    /**
     * Behind {@code /cc trace}, like every other trace line except the lunge's.
     *
     * <p>A shot is about two lines a minute even in a fight, so it is not worth the exemption,
     * and the fuse is twenty ticks long enough that "it lit but never fired" is the kind of thing
     * only a log can answer.</p>
     */
    /**
     * Los ticks que esta recarga va a tardar, <b>Pólvora Seca incluida</b>.
     *
     * <p><b>Es nuestro encantamiento y no Quick Charge, y la razón está medida.</b> Se intentó
     * reutilizar el de vanilla primero -- extender lo que ya existe antes de inventar -- y el efecto
     * de Quick Charge es <b>−0,25 s por nivel</b>, un número absoluto: sobre la ballesta (1,25 s de
     * base) es un −20 % que se nota, y sobre nuestro arcabuz (2,5 s) es un −10 % que apenas. El
     * playtest lo desmintió con el log ({@code de 50} sin encantamiento, {@code de 40} con Quick
     * Charge II, cuando el diseño pedía {@code 30}). Ver
     * {@link com.cortes.combat.enchant.CombatEnchantRules#reloadTicks}.</p>
     *
     * <p>El servidor, el cliente y el tooltip siguen sacando el número de <em>una</em> aritmética,
     * que era lo bueno del intento anterior y se conserva: esto es el único lugar donde la recarga
     * se calcula, y el piso vive en la parte pura, donde un test lo camina.</p>
     */
    public static int reloadTicks(net.minecraft.world.item.ItemStack gun,
                                  net.minecraft.world.entity.LivingEntity user) {
        int level = user == null
                ? 0
                : com.cortes.combat.enchant.CombatEnchantments.level(user.level().registryAccess(),
                        gun, com.cortes.combat.enchant.CombatEnchantments.DRY_POWDER);
        return com.cortes.combat.enchant.CombatEnchantRules.reloadTicks(
                ArcabuzState.RELOAD_TICKS, level);
    }

    private static void trace(ServerPlayer player, ArcabuzState state, String message) {
        if (com.cortes.combat.CombatTrace.isEnabled()) {
            com.cortes.combat.CombatTrace.server(player.getName().getString(),
                    "[arcabuz] " + message + " (mecha=" + state.isBurning()
                            + " cargada=" + state.loaded() + ")");
        }
    }

    /**
     * One line per blast, ungated.
     *
     * <p>Unlike the trace: this one carries the only number the shot has (how many it found, how
     * many it capped, what it did for each), it is one line per <em>shot</em> rather than per
     * tick, and a shot costs a firestone and a second, so it cannot drown anything.</p>
     */
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("cortescombat");
}
