package com.cortes.combat.platform;

import com.cortes.combat.CortesCombat;
import net.minecraft.resources.ResourceLocation;

/**
 * The one way this mod names anything of its own: {@code cortescombat:path}.
 *
 * <p><b>It is a one-line method and it earns its keep.</b> The factory that builds a
 * {@link ResourceLocation} is the cheapest example of the thing that makes a version bump
 * expensive: {@code ResourceLocation.fromNamespaceAndPath} does not exist before 1.21 and
 * {@code new ResourceLocation} is private from 1.21 on, so a mod that spells the factory inline
 * pays the change once per call site. There were sixty-seven of those across forty-seven files,
 * and every one of them was the same edit.</p>
 *
 * <p>So the spelling lives here and nowhere else, and {@code PortabilityGuardsTest} fails the
 * build if it comes back. <b>An abstraction without a guard is a suggestion</b>, and this
 * project has watched a suggestion rot three times already
 * ({@code RemoteMotion.cooldownRatio}, the orphan icon listener, the {@code BREAK} track).</p>
 *
 * <p>Note it takes the path and not the namespace: every resource this mod names is in its own
 * namespace, and the two call sites that spelled {@code "cortescombat"} by hand were exactly the
 * two that would have survived a modid rename silently.</p>
 *
 * <p><b>Two bodies, one contract.</b> This file is the 1.21.1 body; the 1.20.1 body lives at the
 * same path under {@code src/v120} and spells the constructor 1.20.1 still has. The 1.20.1 leg
 * excludes this file and compiles that one instead (see the leg's source set wiring).</p>
 */
public final class ResourceIds {

    private ResourceIds() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, path);
    }
}
