package com.cortes.combat.guard;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.platform.WeaponData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;

/**
 * Resolves how much guard an item is worth, and which item is providing it.
 *
 * <p>Three layers, in the same spirit as {@code WeaponStats}: the item's own component first,
 * then a sensible default for the vanilla item kinds, then nothing. The last layer is what makes
 * a weapon from another mod do something reasonable without a line of code here, and what makes
 * an item that cannot guard say so instead of inheriting a shield's three points.</p>
 *
 * <p>The item comes back alongside the profile because the stagger is a vanilla item cooldown,
 * and a cooldown belongs to an item. Returning a profile with no idea which stack it describes
 * would mean the handler had to go looking for the stack again and could pick a different one.</p>
 */
public final class GuardStats {

    private GuardStats() {
    }

    /** A profile with the stack it came from, or {@link GuardProfile#NONE} on an empty hand. */
    public record Resolved(GuardProfile profile, ItemStack item) {

        public boolean canGuard() {
            return profile.canGuard();
        }
    }

    public static GuardProfile of(ItemStack stack) {
        GuardProfile component = WeaponData.guardProfile(stack);
        if (component != null) {
            return component;
        }
        // **La capa 2 también acá**: un pack puede darle guarda a un arma que vanilla no tiene
        // (points > 0) o quitarle la espada de la suya. Mismo orden que todo lo demás:
        // componente > pack > lo que el juego dice sin que nadie diga nada.
        com.cortes.combat.weaponpack.ResolvedWeapon pack =
                com.cortes.combat.weaponpack.WeaponPackStore.resolve(stack);
        if (pack != null && pack.guard() != null) {
            GuardProfile base = vanillaDefaultFor(stack);
            com.cortes.combat.weaponpack.WeaponPackSpec.Guard declared = pack.guard();
            return new GuardProfile(
                    declared.points() != null ? declared.points() : base.points(),
                    declared.arcDegrees() != null ? declared.arcDegrees() : base.arcDegrees(),
                    declared.regenTicks() != null ? declared.regenTicks() : base.regenTicks(),
                    declared.stunTicks() != null ? declared.stunTicks() : base.stunTicks());
        }
        // **Playtest 2026-10-26, reporte "todas se muestran con escudo": el fallback estaba
        // contestando la pregunta equivocada.** Con una entrada resuelta (match por id o tag),
        // el default vanilla de "cualquier SwordItem cubre un punto" aplicaba a TODOS los
        // arquetipos ajenos -- dagas, katanas, guadañas -- porque casi todo arma de mod
        // extiende SwordItem. El diseño dice otra cosa: la guardia es escudo o espada
        // ("Ninguna de las cinco familias nuevas cubre"), y la capa de packs ya sabe que
        // esa daga es una DAGGER. Regla nueva: resuelto el pack sin guardia declarada, el
        // item vale lo que dice SU perfil resuelto -- SWORD cubre un punto, el resto nada.
        // Un `guard:` en el YAML sigue mandando encima de todo lo de acá.
        if (pack != null && pack.profile() != null
                && com.cortes.combat.core.weapon.WeaponProfile.SWORD != pack.profile()) {
            return GuardProfile.NONE;
        }
        // **Compat automatica (2026-10-27): la capa final pregunta capa arriba de la suya.**
        // Dos cambios, los dos del mismo reporte ("los escudos de mods no convertidos" y
        // "todo arma ajena hereda espada"):
        //
        // 1. Un escudo vale lo que contesta el CONTRATO y no la clase: EK registra sus
        //    escudos como Item pelado que responde canPerformAction(DEFAULT_SHIELD_ACTIONS),
        //    verificado en su jar. instanceof ShieldItem los dejaba fuera de la aguante, y el
        //    bloqueo lo resolvan las mecanicas vanilla "gratis" -- exactamente lo que el dueño
        //    llamo "no convertidos".
        //
        // 2. Y el resto ya no le pregunta al item su Java, le pregunta al perfil DERIVADO de
        //    WeaponStats: alla ahora se clasifican las armas ajenas por atributos (daga para la
        //    veloz, martillo para la pesada), y el fallback "cualquier SwordItem cubre un punto"
        //    se desincroniza de esa clasificación el mismo día que ella decide daga. Dos cadenas
        //    de instanceof para el mismo hecho es dos lugares donde se rompe una vez.
        if (com.cortes.combat.platform.ShieldSeams.canShieldBlock(stack)) {
            return GuardProfile.SHIELD;
        }
        if (com.cortes.combat.attack.WeaponStats.of(stack).profile()
                == com.cortes.combat.core.weapon.WeaponProfile.SWORD) {
            return GuardProfile.SWORD;
        }
        return GuardProfile.NONE;
    }

    /** The strongest guard the two hands are worth. */
    public static Resolved best(LivingItems items) {
        Resolved main = resolve(items.mainHand());
        Resolved off = resolve(items.offHand());
        return off.canGuard() && off.profile().points() > main.profile().points() ? off : main;
    }

    public static Resolved best(net.minecraft.world.entity.LivingEntity entity) {
        return best(LivingItems.of(entity));
    }

    private static Resolved resolve(ItemStack stack) {
        return new Resolved(of(stack), stack);
    }

    /** What a vanilla item means without anyone saying so in a data file. */
    private static GuardProfile vanillaDefaultFor(ItemStack stack) {
        if (stack.is(Items.SHIELD)) {
            return GuardProfile.SHIELD;
        }
        if (stack.getItem() instanceof SwordItem) {
            return GuardProfile.SWORD;
        }
        return GuardProfile.NONE;
    }

    /** A pair of hands, so the resolver does not need a LivingEntity and stays testable. */
    public record LivingItems(ItemStack mainHand, ItemStack offHand) {

        public static LivingItems of(net.minecraft.world.entity.LivingEntity entity) {
            return new LivingItems(entity.getMainHandItem(), entity.getOffhandItem());
        }
    }
}
