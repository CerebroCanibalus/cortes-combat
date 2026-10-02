package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Hooks the four providers into datagen.
 *
 * <p>Registered on the <b>mod</b> bus with {@link EventBusSubscriber}, and that is not a detail:
 * {@code GatherDataEvent} only fires on the mod bus, so the game bus would silently never call
 * this and {@code runData} would produce an empty folder with no error anywhere. A provider that
 * never runs and a provider that was never registered look identical from the outside, which is
 * what makes this the kind of mistake worth a comment.</p>
 *
 * <p>The {@code Bus.MOD} argument is explicit for the same reason: a class with no
 * {@code @SubscribeEvent} method throws when it is registered, and adding a fifth provider to the
 * wrong bus would take the whole mod down at construction rather than at datagen.</p>
 */
@EventBusSubscriber(modid = CortesCombat.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CortesDataEvents {

    private CortesDataEvents() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        // Tags first, in the sense that recipes read them: the order here does not matter to the
        // generator, but a reader needs to see that the recipes depend on the tags to believe the
        // three can be reasoned about together.
        // The tags provider is built by hand rather than through createProvider, because it needs
        // the existing-file helper -- the thing that lets it see tag files already on disk and
        // refuse to overwrite them -- and no createProvider overload takes three arguments. The
        // other three are one-liners and stay as lambdas.
        event.addProvider(new CortesItemTagProvider(
                event.getGenerator().getPackOutput(),
                event.getLookupProvider(),
                event.getExistingFileHelper()));

        // createProvider alone, and the nesting is the whole point of this comment.
        //
        // createProvider(..) already calls addProvider internally, so
        //     event.addProvider(event.createProvider(X::new))
        // registers the same provider twice. The second registration is rejected by name, and the
        // error is the least legible one in datagen:
        //
        //     IllegalStateException: Duplicate provider: cortescombat Recipes
        //
        // It names the provider, so it reads as "two providers with the same name were declared"
        // -- and the actual cause is one provider declared once and registered twice. The only
        // reason it was ever visible is that the first attempt inherited RecipeProvider's final
        // name "Recipes" and collided with NeoForge's own, which hid the double registration
        // behind a different error entirely.
        event.createProvider(CortesRecipeProvider::new);
        // The two nugget conversions. Separate from the catalogue because these are not catalogue
        // entries: vanilla has no copper nugget at all, so the recipes exist to make our own item
        // obtainable rather than to add a weapon.
        event.createProvider(NuggetRecipes::new);
        // And the firestone, the arcabuz's only ingredient. Same reason as the nuggets and one
        // more: a provider whose job is "loop the catalogue" cannot hold a two ingredient
        // conversion without the loop growing an if, and the catalogue's whole value is that it
        // has none.
        event.createProvider(FirestoneRecipes::new);
        event.createProvider(CortesLanguageProvider::new);

        // Built by hand for the existing-file helper, the same as the tags provider: no
        // createProvider overload takes it, and without it the model provider overwrites the six
        // hand-drawn halberd models on every run.
        event.addProvider(new CortesModelProvider(
                event.getGenerator().getPackOutput(),
                event.getExistingFileHelper()));
    }
}
