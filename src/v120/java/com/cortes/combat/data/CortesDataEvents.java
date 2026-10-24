package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The 1.20.1 wiring of datagen. Same providers, older handshake.
 *
 * <p>Forge 1.20.1 has no {@code createProvider} helper: providers register through
 * {@code generator.addProvider(includeServer, instance)}, built by hand. The tags
 * provider additionally needs the block-tag lookup, so the block provider is built first
 * and its {@code contentsGetter()} is handed over -- the standard 1.20.1 wiring. Every
 * provider twin below writes the 1.20.1 shapes ({@code recipes/}, {@code advancements/},
 * {@code FrameType}, hand JSON), and nothing here names a format. (⌒‿⌒)</p>
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CortesDataEvents {

    private CortesDataEvents() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var lookup = event.getLookupProvider();
        var helper = event.getExistingFileHelper();

        CortesBlockTagProvider blockTags =
                new CortesBlockTagProvider(output, lookup, helper);
        generator.addProvider(event.includeServer(), blockTags);
        generator.addProvider(event.includeServer(), new CortesItemTagProvider(
                output, lookup, blockTags.contentsGetter(), helper));

        generator.addProvider(event.includeServer(), new CortesRecipeProvider(output, lookup));
        generator.addProvider(event.includeServer(), new NuggetRecipes(output, lookup));
        generator.addProvider(event.includeServer(), new FirestoneRecipes(output, lookup));
        generator.addProvider(event.includeServer(), new ArcabuzRecipes(output, lookup));
        generator.addProvider(event.includeServer(), new CopperGearRecipes(output, lookup));
        generator.addProvider(event.includeServer(), new FirestoneBlockRecipes(output, lookup));
        generator.addProvider(event.includeServer(), new NetherReactorRecipes(output, lookup));
        generator.addProvider(event.includeServer(),
                new FirestoneBlockLoot(output));
        generator.addProvider(event.includeServer(), new NetherReactorLoot(output));

        generator.addProvider(event.includeClient(),
                new CortesLanguageProvider(output, "en_us"));
        for (String spanish : ModLocales.SPANISH) {
            generator.addProvider(event.includeClient(),
                    new CortesLanguageProvider(output, spanish));
        }

        generator.addProvider(event.includeClient(),
                new CortesModelProvider(output, helper));
    }
}
