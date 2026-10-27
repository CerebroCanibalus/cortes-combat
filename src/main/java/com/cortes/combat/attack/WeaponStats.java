package com.cortes.combat.attack;

import com.cortes.combat.platform.WeaponData;

import com.cortes.combat.platform.CombatState;
import com.cortes.combat.platform.WeaponAttr;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.KnockbackProfile;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.AutoProfile;
import com.cortes.combat.item.WeaponProfileTags;
import com.cortes.combat.item.WeaponTables;
import com.cortes.combat.weaponpack.ResolvedWeapon;
import com.cortes.combat.weaponpack.WeaponPackSpec;
import com.cortes.combat.weaponpack.WeaponPackStore;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.CooldownState;
import com.cortes.combat.state.GuardState;
import com.cortes.combat.state.ParryState;
import com.cortes.combat.state.StreakState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a weapon's behaviour off whatever the player is holding.
 *
 * <p>Four sources, in priority order, and the order is the contract:
 * <ol>
 *   <li>Our data components, when the item has them. That covers our own weapons and any
 *       mod that adopted our API.</li>
 *   <li>Anything the player wrote in the override config (step 12 of the plan; the call site
 *       is left for that step and currently falls through).</li>
 *   <li>Automatic derivation from the item's own attributes and vanilla tags, so a foreign
 *       weapon works without any code of ours. This is the compat layer.</li>
 *   <li>Defaults, which for an empty hand means bare hands.</li>
 * </ol>
 *
 * <p>No gameplay number is hardcoded in the combat code: it all arrives through here.</p>
 */
public final class WeaponStats {

    private final WeaponProfile profile;
    private final ReachProfile reach;
    private final ChargeProfile charge;
    private final SpecialType special;
    private final KnockbackProfile knockback;

    private WeaponStats(WeaponProfile profile, ReachProfile reach, ChargeProfile charge,
                        SpecialType special, KnockbackProfile knockback, ReachProfile shotReach) {
        this.profile = profile;
        this.reach = reach;
        this.charge = charge;
        this.special = special;
        this.knockback = knockback;
        this.shotReach = shotReach;
    }

    public WeaponProfile profile() {
        return profile;
    }

    public ReachProfile reach() {
        return reach;
    }

    public ChargeProfile charge() {
        return charge;
    }

    public SpecialType special() {
        return special;
    }



    /** How hard this weapon pushes, and how much it lifts. */
    public KnockbackProfile knockback() {
        return knockback;
    }

    private final ReachProfile shotReach;

    /**
     * The cone a weapon's <b>right button</b> hits with.
     *
     * <p><b>Falls back to the melee cone, so it always answers something usable.</b> Thirty-one
     * weapons have no second cone, and a caller asking "where does this weapon's right button
     * reach" should not have to branch on whether the weapon happens to be the arcabuz. For those,
     * this is {@link #reach()}; for the arcabuz it is six and a half blocks at thirty two
     * degrees, and {@link #reach()} is a two point six block bayonet.</p>
     *
     * <p>Which is exactly why the two are separate and not one cone with a modifier: a caller that
     * wanted the left button could not tell them apart afterwards.</p>
     */
    public ReachProfile shotReach() {
        return shotReach == null || shotReach.isNone() ? reach : shotReach;
    }

    /** Whether this weapon has a cone of its own for its right button. */
    public boolean hasOwnShotCone() {
        return shotReach != null && !shotReach.isNone();
    }

    /**
     * The cone the player is shown, which is the shot's when there is one.
     *
     * <p><b>Written down as its own question because "the reach" is two questions.</b> The melee
     * cone is a fact about a hit; this is a fact about what the brackets around the crosshair are
     * measuring. On thirty-one weapons they are the same number, which is why nobody noticed they
     * were two numbers until one weapon had both.</p>
     *
     * <p>And the shot wins, because a player holding a loaded arcabuz is aiming the gun: the
     * crosshair has to describe where the blast will go, or the player is aiming with a marker
     * that lies about the thing they are about to do. The bayonet's short cone is still what the
     * left click uses, and it still decides what a click can hit -- the marker simply stops
     * describing it.</p>
     */
    public ReachProfile displayedReach() {
        return hasOwnShotCone() ? shotReach : reach;
    }

    public static WeaponStats of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return bareHands();
        }
        // **La capa 2 resuelta una vez y preguntada por todos los campos.** El orden de cada
        // número es el mismo en todas partes: componente > pack > tabla del perfil. Un solo
        // resolve por `of()` y no uno por campo, porque el fast-path del store es una lectura
        // y el resolver completo es un par de listas cortas -- barato, pero no regalado.
        ResolvedWeapon pack = WeaponPackStore.resolve(stack);
        WeaponProfile profile = readProfile(stack, pack);

        ChargeProfile charge = WeaponData.chargeProfile(stack);
        if (charge == null) {
            charge = WeaponTables.charge(profile);
            if (pack != null) {
                // El pack declara la puerta y los números de la carga; lo que no declara sigue
                // siendo del molde -- bono y ticks son la profundidad del mecanismo, no decoración.
                charge = new ChargeProfile(
                        pack.canCharge() != null ? pack.canCharge() : charge.canCharge(),
                        pack.chargeBonus() != null ? pack.chargeBonus() : charge.bonus(),
                        pack.fullChargeTicks() != null ? pack.fullChargeTicks()
                                : charge.fullChargeTicks());
            }
            // **El tier (el ore del nombre) escala la carga de las ajenas.** Solo el numero que
            // sigue viniendo del molde: si el pack declaro bonus o ticks, el pack manda y el
            // tier no toca ese campo. El dano NO esta aqui: el arma ajena ya trae el suyo por
            // material, y pisaarlo seria pelear contra el balance del otro mod. (μ_μ)
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            AutoProfile.OreTier tier = AutoProfile.OreTier.of(id.getPath());
            if (AutoProfile.isForeign(id.getNamespace()) && tier.delta() != 0.0F) {
                charge = new ChargeProfile(charge.canCharge(),
                        pack == null || pack.chargeBonus() == null
                                ? AutoProfile.tieredChargeBonus(charge.bonus(), tier)
                                : charge.bonus(),
                        pack == null || pack.fullChargeTicks() == null
                                ? AutoProfile.tieredChargeTicks(charge.fullChargeTicks(), tier)
                                : charge.fullChargeTicks());
            }
        }

        ReachProfile reach = WeaponData.reachProfile(stack);
        if (reach == null) {
            ReachProfile base = WeaponTables.reach(profile);
            // Distancia y ángulos son parámetros SEPARADOS (decisión del General): el pack puede
            // poner sólo la distancia y dejar el cono del molde, o al revés, campo por campo.
            reach = new ReachProfile(
                    pack != null && pack.reachDistance() != null ? pack.reachDistance()
                            : base.distance(),
                    pack != null && pack.cone() != null && pack.cone().horizontal() != null
                            ? pack.cone().horizontal() : base.horizontalAngle(),
                    pack != null && pack.cone() != null && pack.cone().vertical() != null
                            ? pack.cone().vertical() : base.verticalAngle());
        }

        SpecialType special = WeaponData.special(stack);
        if (special == null) {
            special = pack != null && pack.special() != null ? pack.special()
                    : WeaponTables.special(profile);
        }

        KnockbackProfile knockback = WeaponData.knockbackProfile(stack);
        if (knockback == null) {
            KnockbackProfile base = WeaponTables.knockback(profile);
            if (pack != null && pack.knockback() != null) {
                // **Los techos viven en KnockbackProfile y el pack no los mueve**: clamp contra
                // MAX_HORIZONTAL / MAX_VERTICAL como cualquier otro número de este sistema.
                float horizontal = pack.knockback().horizontal() != null
                        ? pack.knockback().horizontal() : base.horizontal();
                float vertical = pack.knockback().vertical() != null
                        ? pack.knockback().vertical() : base.vertical();
                knockback = new KnockbackProfile(
                        net.minecraft.util.Mth.clamp(horizontal, 0.0F,
                                KnockbackProfile.MAX_HORIZONTAL),
                        net.minecraft.util.Mth.clamp(vertical, 0.0F,
                                KnockbackProfile.MAX_VERTICAL));
            } else {
                knockback = base;
            }
        }

        ReachProfile shotReach = WeaponData.shotReach(stack);
        if (shotReach == null) {
            WeaponPackSpec.ShotReach declared = pack == null ? null : pack.shot();
            // El segundo cono, resuelto por las mismas capas que todos los demás números:
            // componente, pack, fila del perfil, NONE. **El nombre del componente es el mismo
            // que el del campo que alimenta** -- reach_profile es la bayoneta, shot_reach es el
            // cañón -- y eso vale más un comentario que un test, porque una pareja intercambiada
            // haría el click izquierdo del arcabuz de seis bloques y nada más lo notaría.
            shotReach = declared != null
                    ? new ReachProfile(
                            declared.distance() != null ? declared.distance()
                                    : WeaponTables.shotReach(profile).distance(),
                            declared.horizontal() != null ? declared.horizontal()
                                    : WeaponTables.shotReach(profile).horizontalAngle(),
                            declared.vertical() != null ? declared.vertical()
                                    : WeaponTables.shotReach(profile).verticalAngle())
                    : WeaponTables.shotReach(profile);
        }

        return new WeaponStats(
                profile,
                reach,
                charge,
                special,
                knockback,
                shotReach);
    }

    private static WeaponStats bareHands() {
        return new WeaponStats(
                WeaponProfile.UNARMED,
                WeaponTables.reach(WeaponProfile.UNARMED),
                WeaponTables.charge(WeaponProfile.UNARMED),
                SpecialType.NONE,
                WeaponTables.knockback(WeaponProfile.UNARMED),
                ReachProfile.NONE);
    }

    private static WeaponProfile readProfile(ItemStack stack, ResolvedWeapon pack) {
        WeaponProfile explicit = WeaponData.weaponProfile(stack);
        if (explicit != null) {
            return explicit;
        }
        // **La capa 2 entre la API de Java y nuestros tags.** Un pack manda sobre el molde del
        // tag: la regla que el packmate aprende en una línea es "el YAML manda, el tag molde es
        // el fallback", y este es exactamente el punto donde esa frase se vuelve código.
        if (pack != null && pack.profile() != null) {
            return pack.profile();
        }
        return deriveProfile(stack);
    }

    /**
     * The compat layer, and the mod's own classification in front of it.
     *
     * <p><b>Ours first, vanilla second, and the order is the maintainability.</b> This method
     * used to ask only vanilla's tags, which meant five copper tools of ours entered our own
     * combat system through {@code #minecraft:swords} and its four siblings -- tags in a
     * namespace we do not own, that we happened to write into ourselves. Tags merge
     * additively, which is what makes them work, and the same mechanism lets a datapack
     * declare one with {@code "replace": true} and take our items with it: no error, no
     * warning, the copper sword simply stops fighting. {@link WeaponProfileTags} is the door we
     * own. The vanilla tags stay, below ours, because they are the compat layer for a mod that
     * never heard of us.</p>
     *
     * <p>Anything unrecognised is not a weapon. Falling back to the sword here was the bug behind
     * "every object I hit with can charge": a dirt block has no tag, derived to a sword, and
     * inherited a sword's charge profile. An unknown item is an unknown item, and the honest
     * answer for an unknown item is that it is not a weapon.</p>
     */
    private static WeaponProfile deriveProfile(ItemStack stack) {
        // **El tridente, por instancia y no por tag.** El tag `enchantable/trident` es de
        // 1.21.1: en 1.20.1 no existe, y el diagnostico de arranque lo canto --
        // `Trident=NONE(NO PELEA)` en el server de verdad. `TridentItem` existe en las dos
        // patas y es la clase que `Items.TRIDENT` instancia, asi que la puerta es una sola, y
        // de paso cubre a un mod que extienda `TridentItem` en la 1.20.1. Las lanzas ajenas
        // llegan por otro camino (arquetipo `spear` -> TRIDENT, en el pack). (⌒‿⌒)
        if (stack.getItem() instanceof net.minecraft.world.item.TridentItem) {
            return WeaponProfile.TRIDENT;
        }
        for (Map.Entry<TagKey<net.minecraft.world.item.Item>, WeaponProfile> entry
                : CORTES_PROFILE_TAGS.entrySet()) {
            if (stack.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        for (Map.Entry<TagKey<net.minecraft.world.item.Item>, WeaponProfile> entry
                : VANILLA_TAGS.entrySet()) {
            if (stack.is(entry.getKey())) {
                return refineForeignSwordTag(stack, entry.getValue());
            }
        }
        return WeaponProfile.NONE;
    }

    /**
     * **Compat automatica (2026-10-27): el tag de espada identificado y corregido.** Lo unico
     * que llego por un tag de VANILLA con namespace ajeno deja de heredar la espada completa y
     * se clasifica por sus atributos (dano y velocidad, escala del tooltip -- ver
     * {@link AutoProfile}). El resto: los nuestros, los de minecraft y los que llegaron por
     * otros tags siguen con el valor del tag, porque ese tag lo lleno alguien que tuvo una
     * opinion. Exclusivo el SWORD: el tag espada es el de la herencia ciega -- "casi cualquier
     * arma de mod se declara en #minecraft:swords" -- y ningun otro tag de vanilla es abuso
     * por falso defecto: se lo lleno alguien con una opinion. (μ_μ)
     */
    private static WeaponProfile refineForeignSwordTag(ItemStack stack, WeaponProfile tagProfile) {
        if (tagProfile != WeaponProfile.SWORD
                || !AutoProfile.isForeign(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(stack.getItem()).getNamespace())) {
            return tagProfile;
        }
        WeaponAttr.Hand hand = WeaponAttr.of(stack);
        return AutoProfile.refineForeignSword(hand.damage(), hand.speed());
    }

    /**
     * Ours: one tag per mould, built by walking the enum.
     *
     * <p><b>There is no list of fourteen names here to forget.</b> The loop below fills this
     * map from {@code WeaponProfile.values()}, so a profile added later gets its key the moment
     * it is declared. What is left to a human is the file in
     * {@code data/cortescombat/tags/item/profile/}, and that is what the test checks.</p>
     *
     * <p>A {@code LinkedHashMap} and not a {@code HashMap} because iteration order is the only
     * reason a first match wins, and an order nobody chose is an order nobody can read. The
     * keys are disjoint by construction -- one tag per profile -- so the order does not change
     * an answer today; it is here so that <em>when</em> it does, the file says who wins.</p>
     */
    private static final Map<TagKey<net.minecraft.world.item.Item>, WeaponProfile>
            CORTES_PROFILE_TAGS = new LinkedHashMap<>();

    /** Vanilla's tags, asked only when ours answer nothing. See {@link #deriveProfile}. */
    private static final Map<TagKey<net.minecraft.world.item.Item>, WeaponProfile>
            VANILLA_TAGS = new HashMap<>();

    static {
        for (WeaponProfile profile : WeaponProfile.values()) {
            CORTES_PROFILE_TAGS.put(WeaponProfileTags.profileTag(profile), profile);
        }
        VANILLA_TAGS.put(itemTag("swords"), WeaponProfile.SWORD);
        VANILLA_TAGS.put(itemTag("axes"), WeaponProfile.AXE);
        VANILLA_TAGS.put(itemTag("pickaxes"), WeaponProfile.PICKAXE);
        VANILLA_TAGS.put(itemTag("shovels"), WeaponProfile.SHOVEL);
        // **There is no `#maces` and there is no `#tridents` in 1.21.1.** Verified in the sources:
        // `ItemTags` declares SWORDS, AXES, HOES, PICKAXES and SHOVELS and declares no MACES or
        // TRIDENTS at all; the only mace and trident tags the game publishes are the enchantable
        // ones. A `TagKey` for a path nothing fills simply never matches, so those two lines
        // resolved every maza and every trident to NONE -- and NONE does not fight. The playtest
        // reported both as full vanilla, with a green build and no error anywhere, because a tag
        // that matches nothing is not an error: it is a silent no.
        //
        // The enchantable tag is also the better question on its own terms. It is "whatever the
        // game treats as a mace for its combat enchantment", which is a capability, so another
        // mod's mace is one line away from working instead of being a name somebody has to know.
        VANILLA_TAGS.put(itemTag("enchantable/mace"), WeaponProfile.MACE);
        VANILLA_TAGS.put(itemTag("enchantable/trident"), WeaponProfile.TRIDENT);
    // The hoe, which vanilla makes a penalty: its offsets run 0, -1, -2, -3, -4 by material, so a
    // netherite hoe removes three points of damage and a diamond one swings four times a second.
    // The tag was already missing, which meant a hoe resolved to NONE and did not fight at all.
    VANILLA_TAGS.put(itemTag("hoes"), WeaponProfile.HOE);
    }

    private static TagKey<net.minecraft.world.item.Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(path));
    }

    /**
     * Ticks between attacks for whoever is holding this, straight from the attack speed
     * attribute. Reading it off the entity rather than the stack means equipment, potions and
     * other attribute sources are accounted for automatically.
     *
     * <p>The vanilla sword lands on 12 or 13 ticks, which is a hair over half a second and
     * reads as sluggish the moment a fight has more than one enemy in it. The per-profile
     * multiplier is how that gets tuned: a data row, not a branch, so the next weapon that
     * needs a different tempo is a number here instead of code.</p>
     */
    public static int attackInterval(LivingEntity holder) {
        return attackInterval(holder, WeaponStats.of(holder.getMainHandItem()).profile());
    }

    /**
     * The tempo multiplier for a profile, which is the third of the four numbers behind an attack
     * interval and the only one that used to be unreachable from outside.
     *
     * <p>Exposed because a tooltip that printed vanilla's untouched attribute would be printing
     * a number this table then changes: the axe's real tempo is 0.85 and its real interval is not
     * the one its item declares. A readout that skips this step is a readout of a weapon that
     * does not exist.</p>
     *
     * <p><b>Pero un multiplicador por debajo de uno FRENA, y el nombre dice lo contrario.</b>
     * Multiplica la velocidad de ataque y no los ticks, asi que el 0.85 del hacha son veinticuatro
     * ticks contra los veinte de vanilla. Es exactamente como la espada paso un round entera siendo
     * frenada por una fila cuyo comentario la llamaba mas rapida.</p>
     */
    public static float tempoFor(WeaponProfile profile) {
        return TEMPO.getOrDefault(profile, 1.0F);
    }

    /** As above, for a profile whose tempo is already known. */
    public static int attackInterval(LivingEntity holder, WeaponProfile profile) {
        double base = holder.getAttributeValue(Attributes.ATTACK_SPEED);
        int interval = AttackTiming.scaleFor(base, TEMPO.getOrDefault(profile, 1.0F));
        // Haste (prisa minera) reduce el cooldown duro adicionalmente.
        // El Haste vanilla ya sube ATTACK_SPEED (+10%/nivel), que ya reduce el intervalo base.
        // Este multiplicador aplica la reduccion extra sobre el cooldown duro.
        int hasteLevel = hasteLevelOf(holder);
        double multiplier = AttackTiming.hasteCooldownMultiplier(hasteLevel);
        return Math.max(1, (int) Math.round(interval * multiplier));
    }

    /** Nivel de Haste del holder (0 si no tiene el efecto). Publico: el balance de la carga
     * lo lee el cliente (prediccion) y el server (dano) por la misma puerta. */
    public static int hasteLevelOf(LivingEntity holder) {
        net.minecraft.world.effect.MobEffectInstance effect = holder.getEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED);
        if (effect == null) {
            return 0;
        }
        return effect.getAmplifier() + 1;
    }

    /** Per-profile interval multiplier. 1.0 is vanilla's own tempo, unmodified. */
    private static final Map<WeaponProfile, Float> TEMPO = new HashMap<>();

    static {
        // **La espada ya no esta en esta tabla, y la fila que estaba es la razon de este parrafo.**
        //
        // Decia `TEMPO.put(WeaponProfile.SWORD, 0.85F)` con un comentario que afirmaba *"0.85 puts
        // it at 10"* -- y ese 0.85 hacia exactamente lo contrario. `scaleFor` MULTIPLICA la
        // velocidad de ataque por el tempo, asi que un valor por debajo de uno FRENA: 1.6 * 0.85 es
        // 1.36, que es `ticksFor(1.36)` = quince ticks, dos MAS que los trece de vanilla. El mismo
        // numero esta en el hacha unas lineas mas abajo, donde el comentario dice *"slowed to twenty
        // four"* y tiene razon -- o sea que la tabla era coherente y **el unico renglon que
        // discordaba con su propia nota era el de la espada.**
        //
        // La leccion no es el signo: es que un comentario que explica POR QUE una rareza es correcta
        // es exactamente donde hay que ir a mirar si la rareza lo es. La espada estuvo un round
        // entero mas lenta que vanilla, con el comentario diciendo que era mas rapida.
        //
        // El playtest pidio que bajara un quince por ciento, y quince por ciento de quince ticks es
        // 12.75, que no es un entero. Asi que la fila se **borra** en vez de retunearse, y la espada
        // cae en el tempo de su propio item: DOCE ticks -- `20.0 / 1.6` da 12,4999... en punto
        // flotante, que es el numero real de vanilla y no el trece que la nota de al lado sugeria.
        // Son un veinte por ciento menos que los quince que venia corriendo, o sea un poco mas de lo
        // que el pedido decia, y es el numero honesto: el otro candidato es trece, que sigue siendo
        // mas lento que vanilla y que habria que escribir con un tempo de 0.98 -- "cuatro por ciento
        // mas lenta" para conseguir "un poco mas rapida", que es la misma mentira al reves.
        // The dagger is NOT in this table. It was, at 0.7, and the entry was dead: there was no
        // dagger item for it to apply to, so nothing had ever been measured against it. The
        // moment the item existed, the entry started doing the opposite of what it looked like:
        // a dagger declaring 2.6 attacks per second would have been multiplied down to 1.82 and
        // swung every eleven ticks instead of eight, which is slower than a sword.
        //
        // So the tempo for the five new families lives on the item's own ATTACK_SPEED, where the
        // number a reader finds in WeaponFamily is the number that runs. This table is for
        // retuning vanilla's weapons, whose attribute Mojang owns and we cannot change.
        // Bare hands are the fastest thing in the game, and the only entry here above 1.0.
        //
        // The player's base attack speed is vanilla's 4.0, which is already five ticks, so
        // "fastest" cannot mean leaving this alone: at 1.0 the fists tie with a dagger and lose
        // to nothing. 1.25 puts them on four ticks, and the only thing that keeps that from
        // being strictly better than a weapon is that a punch does one damage.
        TEMPO.put(WeaponProfile.UNARMED, 1.25F);

        // The axe, slowed to twenty four ticks from vanilla's twenty.
        //
        // Vanilla's axe is attack speed 1.0, which is twenty ticks -- already the slowest
        // thing in the catalogue by one tick against the warhammer's nineteen. That is not
        // "bastante lento", it is a rounding error, and a player cannot feel a difference
        // between nineteen and twenty.
        //
        // 0.85 puts it at twenty four, which is the slowest swing in the game by five ticks
        // and a fifth above the bare hand's four. The axe is the weapon whose base is
        // deliberately weak -- seven damage in a second and a fifth is less than half the
        // ropera's throughput -- and a base like that only reads as a choice if the rhythm
        // is also a cost. At twenty ticks it was a slightly worse sword; at twenty four it
        // is an axe.
        TEMPO.put(WeaponProfile.AXE, 0.85F);
    }

    /**
     * Whether this weapon can charge at all.
     *
     * <p>Asked by the input code before it opens a swing and by the viewmodel before it shows a
     * wind-up, so a block in the hand does not get a charge bar and does not look like it is
     * winding up. A single lookup rather than a check of the resolved profile's boolean, because
     * the caller has a stack and not a profile and building one to read one boolean would be
     * silly.</p>
     */
    /**
     * Whether the held item can charge, resolved the same way every other stat is.
     *
     * <p>It read {@code WeaponTables.charge(profile)} instead, which skips the component layer and
     * the override file and asks the profile's default directly. So a mod that puts a real charge
     * profile on the ropera -- a component, through the compat path this project promises --
     * got an item the game had already decided cannot charge. The answer that survives a
     * component is the one that goes through {@link #of(ItemStack)} like every other question
     * about a weapon.
     */
    public static boolean canCharge(ItemStack stack) {
        return WeaponStats.of(stack).charge().canCharge();
    }

    public static CooldownState cooldownOf(Player player) {
        return CombatState.cooldown(player);
    }

    public static ChargeState chargeOf(Player player) {
        return CombatState.charge(player);
    }

    public static GuardState guardOf(Player player) {
        return CombatState.guard(player);
    }

    public static StreakState streakOf(Player player) {
        return CombatState.hammerStreak(player);
    }

    public static ParryState parryOf(Player player) {
        return CombatState.parry(player);
    }

    /**
     * The reach table, for the places that need to see all of it.
     *
     * <p>{@code /cc stats} prints one weapon; the tag provider and the tests walk all thirteen.
     * The map handed back is unmodifiable, because a caller that could write to it would be a
     * second, invisible copy of this table -- which is the bug this class was rewritten to
     * remove.</p>
     */
    public static Map<WeaponProfile, ReachProfile> defaultReach() {
        return WeaponTables.reachTable();
    }
}
