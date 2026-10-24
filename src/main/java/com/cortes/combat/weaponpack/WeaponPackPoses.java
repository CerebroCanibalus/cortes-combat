package com.cortes.combat.weaponpack;

import com.cortes.combat.pose.WeaponPose;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The pack poses the client knows: {@code assets/&lt;ns&gt;/poses/&lt;id&gt;.json}, by full id.
 *
 * <p>Poses are client assets (a resource pack is the only place the game loads them from), so
 * they load on the client's resource reload and not with the YAML in {@code config/}: the
 * stats travel in the pack file both sides read, the animation travels in the pack both sides
 * wear. The YAML names the pose ({@code animation.pose}); this is the shelf it is taken
 * from. (⌒‿⌒)</p>
 *
 * <p>Two maps and not one: the files are keyed by where they were found
 * ({@code mymod:chef_knife}), and {@link WeaponPose#id()} says what the file claims to be.
 * A file that disagrees with its own address loads under the address -- the game looks poses
 * up by reference, not by self-declaration -- and says so, because a silent rename is how a
 * pose stops matching and nobody knows which of the two names is lying.</p>
 */
public final class WeaponPackPoses
        implements net.minecraft.server.packs.resources.PreparableReloadListener {

    public static final WeaponPackPoses INSTANCE = new WeaponPackPoses();

    /** By full id ({@code mymod:chef_knife}), replaced whole on every resource reload. */
    private static volatile Map<String, WeaponPose> poses = Map.of();

    private WeaponPackPoses() {
    }

    /** The pose file for this id, or {@code null} when no resource pack ships one. */
    public static WeaponPose get(String rawId) {
        return poses.get(rawId);
    }

    /** How many pose files the last reload read. For the debug readout. */
    public static int size() {
        return poses.size();
    }

    @Override
    public CompletableFuture<Void> reload(
            net.minecraft.server.packs.resources.PreparableReloadListener.PreparationBarrier barrier,
            net.minecraft.server.packs.resources.ResourceManager manager,
            net.minecraft.util.profiling.ProfilerFiller preparations,
            net.minecraft.util.profiling.ProfilerFiller reload,
            Executor background, Executor game) {
        return CompletableFuture.supplyAsync(() -> readAll(manager), background)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(found -> poses = Map.copyOf(found), game);
    }

    /** All pose files the enabled packs ship right now, failures left out and named. */
    static Map<String, WeaponPose> readAll(
            net.minecraft.server.packs.resources.ResourceManager manager) {
        Map<String, WeaponPose> found = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceLocation,
                net.minecraft.server.packs.resources.Resource> entry : manager
                .listResources("poses", path -> path.getPath().endsWith(".json")).entrySet()) {
            String id = entry.getKey().getNamespace() + ":"
                    + entry.getKey().getPath().substring("poses/".length(),
                            entry.getKey().getPath().length() - ".json".length());
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(),
                    StandardCharsets.UTF_8)) {
                DataResult<WeaponPose> parsed = WeaponPose.CODEC.parse(JsonOps.INSTANCE,
                        com.google.gson.JsonParser.parseReader(reader));
                if (parsed.error().isPresent()) {
                    problems.add(id + " :: " + parsed.error().get().message());
                    continue;
                }
                WeaponPose pose = parsed.result().orElseThrow(() -> new IllegalStateException(
                        id + " :: sin resultado ni error, que no debería pasar"));
                if (!pose.id().equals(id)) {
                    problems.add(id + " dice ser '" + pose.id() + "' -- manda el archivo");
                }
                found.put(id, pose);
            } catch (RuntimeException | java.io.IOException e) {
                problems.add(id + " :: no se pudo leer: " + e.getMessage());
            }
        }
        for (String problem : problems) {
            com.cortes.combat.CortesCombat.LOGGER.warn("[weaponpack] pose {}", problem);
        }
        return found;
    }
}
