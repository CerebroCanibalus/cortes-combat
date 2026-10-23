package com.cortes.combat.platform;

import com.cortes.combat.CortesCombat;
import net.minecraft.resources.ResourceLocation;

/**
 * The 1.20.1 body of the resource-name factory. Same contract as the shared twin:
 * {@code cortescombat:path}, in one place.
 *
 * <p>1.20.1 has no {@code fromNamespaceAndPath} (arrived in 1.21) and its public constructor is
 * the normal way to build one. This file compiles only on the 1.20.1 leg, which excludes the
 * shared twin and compiles this instead. (⌒‿⌒)</p>
 */
public final class ResourceIds {

    private ResourceIds() {
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(CortesCombat.MODID, path);
    }
}
