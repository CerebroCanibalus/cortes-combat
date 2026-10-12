package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.mojang.serialization.Codec;
import com.cortes.combat.core.weapon.AmmoProfile;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.viewmodel.ViewModelProfile;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The four data components that carry every gameplay number.
 *
 * <p>Kept as four separate components rather than one fat one on purpose: a foreign mod can
 * change the reach of an item without also having to restate, and potentially break, its
 * charge settings. See ARCHITECTURE.md section 2.</p>
 */
public final class ModComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CortesCombat.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WeaponProfile>> WEAPON_PROFILE =
            component("weapon_profile", WeaponProfile.TYPE);

    /**
     * Which of the arcabuz's five fuse frames the gun is showing, 0 to 4.
     *
     * <p><b>A data component and not a client field, and that is the whole design.</b> The frame has
     * to be the same on every client that can see the gun, and the only value that is guaranteed
     * to be is one the server wrote and the network carried.</p>
     *
     * <p>It exists because a texture <em>animation</em> cannot do this. The drawn art is one
     * 64x320 strip of five frames and a {@code .mcmeta} runs it at whatever rate it is told to,
     * freely, forever -- so the gun's fuse burned down on its own in the inventory whether or not
     * anything was happening. 1.21.1 gives a mod no way to choose a frame of an animated texture,
     * so the strip was cut into five files and the model picks one with a predicate on this
     * component.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>>
            ARCABUZ_FUSE = component("arcabuz_fuse", Codec.INT);

    /**
     * <b>Todo el estado de este arcabuz</b>: la recamara, la mecha y la recarga.
     *
     * <h2>En el stack y no en el jugador, y esa es la cura de los tres bugs del arma</h2>
     *
     * <p>Esto nacio como un attachment del <em>jugador</em> -- {@code ArcabuzState} entero, con la
     * recamara adentro -- y ese unico hecho produjo tres sintomas que se reportaron por separado y
     * son el mismo: el segundo arcabuz salia cargado, el segundo terminaba la recarga del primero, y
     * el segundo disparaba una mecha que habia arrancado en el primero. <b>Cambiar de arma heredaba
     * el estado del otro</b>, porque el estado no era del arma.</p>
     *
     * <p>Los tres arreglos que se intentaron antes -- reconciliar en el press, reconciliar en el
     * tick, y anotar contra que arma iba todo -- fallaron uno por uno, y el ultimo <em>empeoro</em>
     * las cosas. La razon es que los tres eran parches sobre el mismo error: <b>estado del arma
     * guardado en el jugador</b>. Con el estado en el stack no hay nada que heredar, y no hay
     * reconciliacion, ni comparacion de identidad, ni reset. Los tres desaparecen.</p>
     *
     * <p>Es lo que hace vanilla con la ballesta ({@code CHARGED_PROJECTILES} vive en el stack) y con
     * el tridente. Y tiene un premio que no era el objetivo: un arma guardada en un cofre <b>conserva
     * su estado</b>, porque viaja con el item.</p>
     *
     * <h2>El costo, que es escribir el componente</h2>
     *
     * <p>Escribir un componente sobre el stack que la mano sostiene reemplaza el {@code ItemStack} del
     * cliente, lo que dispara la animacion de cambio de item de vanilla y mueve el arma sesenta
     * centimetros mientras se asienta. Es el mismo precio que ya pagaba la recamara, y por eso el
     * estado guarda el <b>tick absoluto de fin</b> en vez de un contador: se escribe <b>cuatro veces
     * por ciclo de disparo</b> y nunca entre medio. Un contador obligaria a escribirlo cada tick, que
     * es el bug que el proyecto ya pago con la animacion de la mecha.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.cortes.combat.state.ArcabuzState>>
            ARCABUZ = component("arcabuz", com.cortes.combat.state.ArcabuzState.CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ReachProfile>> REACH_PROFILE =
            component("reach_profile", ReachProfile.CODEC);

    /**
     * The cone a weapon's <b>right button</b> hits with, which only the arcabuz has.
     *
     * <p>Separate from {@link #REACH_PROFILE} and not a second field inside it, for the reason
     * {@code WeaponTables}' second table gives: the two cones answer two different questions, and
     * a melee profile carrying "and also this" is a profile whose name no longer says what it
     * is. A weapon with none of this carries {@link ReachProfile#NONE} rather than leaving the
     * component off, because an absent component reads as a bug and an explicit NONE reads as a
     * decision.</p>
     *
     * <p>Same type and same codec as {@code REACH_PROFILE} on purpose: the shape is the same
     * thing, and reusing the record means a datapack author writes one kind of number.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ReachProfile>> SHOT_REACH =
            component("shot_reach", ReachProfile.CODEC);

    /**
     * Qué gasta el arma al disparar.
     *
     * <p><b>La última decisión de gameplay que quedaba escrita a mano en Java.</b> El ítem que
     * consumía el arcabuz estaba clavado en {@code firestoneSlot} contra {@code ModItems.FIRESTONE},
     * así que un pack no podía hacer un arma que gaste otra cosa sin editar Java. Ahora es un
     * dato, y la munición por default sigue siendo la Firestone -- un default que cambiara el
     * comportamiento de las armas existentes sería un regalo o una trampa.</p>
     *
     * <p>Ver {@link AmmoProfile} sobre por qué el costo no es negociable desde el YAML.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AmmoProfile>> AMMO_PROFILE =
            component("ammo_profile", AmmoProfile.CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChargeProfile>> CHARGE_PROFILE =
            component("charge_profile", ChargeProfile.CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpecialType>> SPECIAL =
            component("special", SpecialType.TYPE);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GuardProfile>> GUARD_PROFILE =
            component("guard_profile", GuardProfile.CODEC);

    /**
     * The viewmodel of an item: a list of tracks, resolved once and evaluated per frame.
     *
     * <p>A fifth component, and separate on purpose. The other four are gameplay numbers that
     * the server resolves and that decide damage, reach and cooldown. This one is pure
     * presentation, only the client resolves it, and folding it into one of the others would
     * mean every combat system in the mod had to carry a field it never reads. Presentation data
     * that a server-side change could invalidate is the thing worth keeping apart.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ViewModelProfile>> VIEWMODEL_PROFILE =
            component("viewmodel_profile", ViewModelProfile.CODEC);

    /**
     * How hard the item pushes when it hits.
     *
     * <p>Its own component because the shove is per weapon and the hit is not. A sword, a
     * warhammer and a block in the hand were all pushing identically while the other four
     * components described the same three numbers, and a player who noticed could not change it
     * because there was nowhere to put the value.</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<KnockbackProfile>> KNOCKBACK_PROFILE =
            component("knockback_profile", KnockbackProfile.CODEC);

    private ModComponents() {
    }

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> component(
            String name, com.mojang.serialization.Codec<T> codec) {
        return DATA_COMPONENT_TYPES.register(name, () -> DataComponentType.<T>builder()
                .persistent(codec)
                .networkSynchronized(ByteBufCodecs.fromCodec(codec))
                .build());
    }
}
