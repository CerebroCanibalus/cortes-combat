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
        // **And the block tags, which are a second provider and not a second method.** A tags
        // provider is parameterised by one registry, so the block tags cannot live in the item one.
        event.addProvider(new CortesBlockTagProvider(
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
        // And the arcabuz, the thirty-first item and the last one that had no way to be made. It
        // is a weapon and not an ingredient, so it is the one non-catalogue recipe that does carry
        // an advancement: a player who never sees this entry never learns the gun exists, which is
        // exactly what happened for as long as the grid was missing.
        event.createProvider(ArcabuzRecipes::new);
        // And the copper gear: five tools and four armour pieces, the set vanilla 1.21.1 lacks.
        //
        // **No advancements, and that is a decision rather than an omission.** RecipeBookPopulation
        // filters by namespace and puts every recipe of ours in the book when a player logs in, so
        // the gear reaches a survival player without nine more advancement files -- which is the
        // reason the nuggets and the firestone carry none either.
        event.createProvider(CopperGearRecipes::new);
        // And the firestone block: nine firestones up and nine back down, the first block this mod
        // has ever added. Its own provider for the reason every non-catalogue recipe has one -- it
        // is not a weapon and it is not an ingredient, and neither of the loops that own those can
        // hold it without growing an if.
        event.createProvider(FirestoneBlockRecipes::new);
        // And the nether reactor: the first machine and the first block entity, whose grid is a
        // one-off shape and whose recipe carries an advancement like every other discovery.
        event.createProvider(NetherReactorRecipes::new);
        // And the block's loot table, which is the first file this mod writes under `loot_table/`.
        // A block with no loot table drops nothing at all, silently, so the file that says "it
        // drops itself" is not optional.
        event.addProvider(new FirestoneBlockLoot(event.getGenerator().getPackOutput()));
        // The reactor's loot table, the same shape one block over.
        event.addProvider(new NetherReactorLoot(event.getGenerator().getPackOutput()));
        // Two language files, as two providers. The English one is not optional: en_us is what
        // Minecraft runs in until a player opens the options menu, so a mod with only es_es
        // shows every item's translation key to anyone who has not done that.
        //
        // **Y son OCHO archivos, porque el juego trae siete espanoles y ninguno es un alias de
        // otro.** La pila de idiomas de Minecraft es [seleccionado, en_us] -- no hay un paso
        // intermedio por es_es -- asi que un jugador en es_mx, es_ar, es_cl, es_ec, es_uy o es_ve
        // veia las cadenas de vanilla en su variante y LAS NUESTRAS EN INGLES. Un mod en espanol
        // que se ve en ingles para la mayoria de los que hablan espanol.
        //
        // Un archivo por codigo porque cada idioma ES un archivo en el cliente. Lo que no se hace
        // es tipear la traduccion siete veces: sale del mismo provider, con el mismo contenido, y
        // el provider decide el idioma por el prefijo del codigo.
        event.addProvider(new CortesLanguageProvider(event.getGenerator().getPackOutput(), "en_us"));
        for (String spanish : ModLocales.SPANISH) {
            event.addProvider(
                    new CortesLanguageProvider(event.getGenerator().getPackOutput(), spanish));
        }

        // Built by hand for the existing-file helper, the same as the tags provider: no
        // createProvider overload takes it, and without it the model provider overwrites the six
        // hand-drawn halberd models on every run.
        event.addProvider(new CortesModelProvider(
                event.getGenerator().getPackOutput(),
                event.getExistingFileHelper()));
    }
}
