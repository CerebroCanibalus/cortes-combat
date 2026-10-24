package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * The 1.20.1 wiring of the recipe-book backfill. Same login award, older manager.
 *
 * <p>1.20.1 has no {@code RecipeHolder}: the manager hands out bare recipes and
 * {@code awardRecipes} takes them directly (the id-based door is the separate
 * {@code awardRecipesByKey}). The filter is still the recipe id's namespace, because the
 * recipe is ours if its id is ours. Idempotent and persisted the same way -- see the
 * shared twin for why each property matters. (⌒‿⌒)</p>
 */
public final class RecipeBookPopulation {

    private RecipeBookPopulation() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // **El filtro es por namespace del id de la receta, no por el item que produce.**
        List<Recipe<?>> ours = player.server.getRecipeManager().getRecipes().stream()
                .filter(recipe -> CortesCombat.MODID.equals(recipe.getId().getNamespace()))
                .toList();

        if (ours.isEmpty()) {
            return;
        }

        player.awardRecipes(ours);
    }
}
