package com.cortes.combat.weaponpack;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Nivel B: los ítems que un weapon pack <em>crea</em>, registrados en el {@code RegisterEvent}
 * de {@code Registries.ITEM} -- la única ventana que existe antes del freeze.
 *
 * <p><b>El timing es la mitad del diseño.</b> El constructor de {@code @Mod} corre <em>antes</em>
 * de los {@code RegisterEvent}, así que {@link WeaponPackStore} ya tiene los packs parseados
 * cuando este handler pregunta. Y los dos lados corren el mismo mod con el mismo
 * {@code config/}, así que los ids coinciden sin que nadie sincronice nada.
 *
 * <p><b>El ítem creado no lleva componente {@code weapon_profile}:</b> su origen de verdad es la
 * capa 2, y {@code WeaponStats.readProfile} ya la consulta antes que los tags. Poner el
 * componente sería escribir en dos lugares la misma verdad -- el patrón que el handoff prohibió
 * para los stacks y vale igual para los items.
 *
 * <p><b>Sin daño declarado nace con los de una espada de hierro</b> (createAttributes(IRON, 3,
 * -2.4)), que es un default razonable y documentado; si el pack declara {@code damage} o
 * {@code attack_speed}, {@link WeaponPackAttributes} -- la última en correr -- lo pisa.
 * El nombre va por {@code CUSTOM_NAME}, porque {@code Item.Properties} de 1.21.1 no tiene
 * {@code displayName} (verificado con javap): es el mismo canal de defaults de los componentes.
 */
@Mod.EventBusSubscriber(modid = CortesCombat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WeaponPackItems {

    /**
     * Los creados con {@code display.tab} vacío o {@code cortescombat:combat} -- los que la
     * pestaña muestra. Una tab ajena queda fuera con warning: sólo podemos poblar la nuestra
     * sin caer en un {@code BuildCreativeModeTabContentsEvent} por cada pestaña del juego.
     */
    private static final List<Item> CREATED = new CopyOnWriteArrayList<>();

    private WeaponPackItems() {
    }

    public static List<Item> created() {
        return CREATED;
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!Registries.ITEM.equals(event.getRegistryKey())) {
            return;
        }
        for (WeaponPackSpec pack : WeaponPackStore.packs()) {
            for (WeaponPackSpec.Entry entry : pack.weapons()) {
                if (entry.creates() == null) {
                    continue;
                }
                registerOne(event, pack.origin(), entry);
            }
        }
    }

    private static void registerOne(RegisterEvent event, String origin,
                                    WeaponPackSpec.Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.creates());
        if (id == null) {
            CortesCombat.LOGGER.warn("[weaponpack] {} :: creates '{}' no es un id válido -- no se "
                    + "crea nada", origin, entry.creates());
            return;
        }
        if (BuiltInRegistries.ITEM.containsKey(id)) {
            CortesCombat.LOGGER.warn("[weaponpack] {} :: el ítem '{}' ya existe -- no se crea "
                    + "nada (¿otra mod tiene ese id?)", origin, entry.creates());
            return;
        }

        // El resolver contra TODOS los packs, con tags en falso: un ítem que todavía no existe
        // no puede estar en ningún tag, y su propia entrada matchea por id (specificity 2).
        ResolvedWeapon resolved = PackResolver.resolve(entry.creates(), tag -> false,
                WeaponPackStore.packs());
        if (resolved == null || resolved.profile() == null) {
            CortesCombat.LOGGER.warn("[weaponpack] {} :: '{}' se crea pero NO va a pelear: sin "
                    + "archetype ni profile resoluble. Pone archetype en la entrada.", origin,
                    entry.creates());
        }

        String tab = entry.display() == null ? null : entry.display().tab();
        // **Both spellings of ours**: the guide says `tab: cortescombat` and rejecting the
        // player's own example with a warning is teaching them the wrong string.
        boolean ours = tab == null || tab.equals(CortesCombat.MODID + ":combat")
                || tab.equals(CortesCombat.MODID);
        if (!ours) {
            CortesCombat.LOGGER.warn("[weaponpack] {} :: display.tab '{}' no está soportada: sólo "
                    + "cortescombat:combat. El ítem existe y funciona, pero no aparece en una "
                    + "pestaña -- usa /give.", origin, tab);
        }

        Item item = build(entry);
        event.register(Registries.ITEM, id, () -> item);
        if (ours) {
            CREATED.add(item);
        }
        CortesCombat.LOGGER.info("[weaponpack] ítem creado: {} -- su icono va en "
                        + "assets/{}/models/item/{}.json + textura (template en "
                        + "resourcepacks/cortescombat-weapon-pack/)", entry.creates(),
                id.getNamespace(), id.getPath());
    }

    private static Item build(WeaponPackSpec.Entry entry) {
        // 1.20.1 has neither per-item attributes in the properties nor a name component:
        // the numbers arrive through the attribute event (see WeaponPackAttributes) and the
        // name through an override, which is what a renamed stack reads as.
        Item.Properties properties = new Item.Properties().stacksTo(1);
        String displayName = entry.display() != null ? entry.display().name() : null;
        if (displayName == null) {
            return new Item(properties);
        }
        return new Item(properties) {
            @Override
            public Component getName(net.minecraft.world.item.ItemStack stack) {
                return Component.literal(displayName);
            }
        };
    }
}
