package com.cortes.combat.item;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.registry.ArcabuzTier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;

/**
 * The 1.20.1 body of the arcabuz item. Same shape, same numbers, older constructor.
 *
 * <p>1.21.1 carries damage and tempo in the properties ({@code createAttributes}) and the
 * constructor takes only the tier; 1.20.1 takes them in the constructor itself:
 * {@code (tier, damage, speed, properties)}. The numbers are the shared ones from
 * {@link ArcabuzStats} -- 5 damage (an int, as the old signature demands) and the -2.6
 * offset -- so the tooltip and the swing read the same values on both legs. (⌒‿⌒)</p>
 */
public final class ArcabuzItem extends SwordItem {

    private ArcabuzItem(Item.Properties properties) {
        super(ArcabuzTier.INSTANCE, (int) ArcabuzStats.DAMAGE, ArcabuzStats.SPEED_OFFSET, properties);
    }

    public static Item.Properties properties() {
        return com.cortes.combat.platform.WeaponData.tagArcabuz(new Item.Properties()
                .durability(ArcabuzStats.USES));
    }

    /** The one and only way an item of this kind is built. */
    public static ArcabuzItem of(Item.Properties ignored) {
        return new ArcabuzItem(properties());
    }

    /** The profile every other system asks about, which this item fixes at creation. */
    public WeaponProfile profile() {
        return WeaponProfile.ARCABUZ;
    }
}
