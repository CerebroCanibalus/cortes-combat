package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Pone nuestras recetas en el libro de crafteo de quien entra, sin depender de un mundo nuevo.
 *
 * <h2>El problema que resuelve</h2>
 *
 * <p><b>El libro se puebla por el_packet_, no por el mundo.</b> Vanilla manda un
 * {@code ClientboundRecipePacket} cuando se <em>concede</em> un avance, y en un mundo donde las 32
 * recetas ya estaban completas no hay concesión nueva: el libro queda vacío y no se arregla hasta
 * que se de verdad pase algo. Por eso el sintoma se lee como "hay que empezar un mundo nuevo", y no
 * lo es.</p>
 *
 * <p>La cadena real, leída de 21.1.250:</p>
 * <pre>
 * PlayerList:212  → ServerRecipeBook#sendInitialRecipeBook  →  ClientboundRecipePacket.State.INIT
 * ServerPlayer:1351 → public int awardRecipes(Collection&lt;RecipeHolder&lt;?&gt;&gt;)
 * ServerRecipeBook:26 → addRecipes(...)  →  State.ADD
 * </pre>
 *
 * <p><b>{@code awardRecipes} es público y no depende del mundo.</b> Eso es toda la solución: una
 * llamada en el login, y el libro tiene las recetas aunque el jugador lleve meses jugando ahí.</p>
 *
 * <h2>Por qué no hace falta nada más</h2>
 *
 * <p><b>{@code addRecipes} es idempotente por su propia cuenta.</b> Empieza con
 * {@code !this.known.contains(resourcelocation) && !recipeholder.value().isSpecial()}, así que
 * llamarlo en cada login no duplica nada: la segunda vez no encuentra ninguna nueva y no manda
 * paquete.</p>
 *
 * <p>Y <b>el {@code known} se persiste</b> en el NBT del jugador
 * ({@code ServerRecipeBook#RECIPE_BOOK_TAG}), de modo que la primera vez que entra queda,y de ahí en
 * más ya las tiene aunque el mundo cambie.</p>
 *
 * <p><b>El trigger {@code CriteriaTriggers.RECIPE_UNLOCKED}</b> lo dispara vanilla por cada receta
 * nueva, así que los criterios de "craftear X" no se rompen: al contrario, se cumple el que faltaba.</p>
 *
 * <h2>Lo que NO cubre, y está dicho</h2>
 *
 * <p>Un {@code /reload} en el server. {@code RecipesUpdatedEvent} existe pero es
 * {@code net.neoforged.neoforge.client.event}: es del cliente, y no hay equivalente server-side. Un
 * reload no pierde el {@code known} porque vive en el NBT, así que el libro sobrevive; lo que un
 * reload sí puede hacer es dejar de conocer recetas <em>nuevas</em> hasta el próximo login, que para
 * un mundo de producción no es un caso que merezca un handler propio.</p>
 *
 * <p>Y <b>las recetas que ya estaban en el mundo y nunca se hierarchizaron</b> ({@code isSpecial})
 * no entran por esta puerta, porque es vanilla filtrando, no nós. Ninguna nuestra lo es.</p>
 */
public final class RecipeBookPopulation {

    private RecipeBookPopulation() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // **El filtro es por namespace del id de la receta, no por el item que produce.** La receta
        // es nuestra si su id es nuestro, y el id es lo que la identifica en el mundo; mirar el
        // resultado sería preguntar por el síntoma.
        List<RecipeHolder<?>> ours = player.server.getRecipeManager().getRecipes().stream()
                .filter(holder -> CortesCombat.MODID.equals(holder.id().getNamespace()))
                .toList();

        if (ours.isEmpty()) {
            // Sin recetas no hay nada que mandar, y `awardRecipes` con una lista vacía no hace daño
            // pero tampoco dice por qué no pasó nada.
            return;
        }

        player.awardRecipes(ours);
    }
}