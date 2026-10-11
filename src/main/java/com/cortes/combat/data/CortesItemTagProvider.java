package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.item.WeaponProfileTags;
import com.cortes.combat.registry.CortesTier;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.registry.ModTools;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The tags the recipes depend on.
 *
 * <p>They exist because the recipes read tags rather than named items, and a recipe that reads a
 * tag nothing defines is a recipe a player can never craft. So the members are generated here,
 * beside the recipes that need them, and the two cannot drift apart.</p>
 *
 * <ul>
 *   <li><b>{@code #minecraft:<tier>_tool_materials}</b> for six of the seven tiers. 1.21.1 ships
 *       exactly <em>one</em> of these -- {@code stone_tool_materials} -- and the other six do not
 *       exist, so this provider writes them. See {@link #materialTags()} for the whole story; the
 *       short version is that a recipe reading a tag nothing publishes is a recipe nobody can
 *       craft, and nothing anywhere says so.</li>
 *   <li><b>{@code #minecraft:swords}</b> gets all thirty, so anything asking "is this a blade" gets
 *       a truthful answer. Our own derivation reads this tag, so it is also the safety net behind
 *       the explicit profile component.</li>
 * </ul>
 */
public class CortesItemTagProvider extends IntrinsicHolderTagsProvider<Item> {

    /**
     * The five-argument constructor, and the mod id is the reason.
     *
     * <p>Vanilla already registers a tags provider for {@code minecraft:item}, and NeoForge's
     * duplicate check rejects a second one whose name collides -- which the three-argument
     * constructor does, because it defaults the mod id to {@code "vanilla"}. The symptom is an
     * IllegalStateException during {@code runData} that reads
     * {@code "Duplicate provider: Tags for minecraft:item mod id vanilla"} and names neither this
     * mod nor this provider, so it is worth the comment that it does not mean what it says.</p>
     *
     * <p>The mod id is the provider's <em>name</em>, and it is not what decides where a tag file
     * lands: the output directory follows the {@code TagKey}'s namespace, so a
     * {@code minecraft:swords} tag is written to {@code data/minecraft/tags/item/swords.json} and a
     * {@code cortescombat:...} tag to {@code data/cortescombat/tags/item/...}. Both are correct --
     * tags from several packs merge -- but it is worth writing down because it is not what the
     * parameter looks like it does.</p>
     */
    public CortesItemTagProvider(PackOutput output,
                                 CompletableFuture<HolderLookup.Provider> registries,
                                 ExistingFileHelper existingFileHelper) {
        // The provider needs a Function<Item, ResourceKey<Item>> -- the registry *key*, which is
        // what a tag file names an entry by. getKey returns a ResourceLocation instead, and
        // getResourceKey returns the Optional, so orElseThrow is what unwraps it.
        super(output, Registries.ITEM, registries,
                item -> BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow(),
                CortesCombat.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        materialTags();
        enchantClasses();
        copperToolsFight();
        profileTags();
        categoryTags();
    }

    /**
     * The copper tools into the vanilla tags the derivation reads.
     *
     * <p><b>Without this the copper axe and the copper sword would not fight at all.</b>
     * {@code WeaponStats.deriveProfile} classifies an item purely by which vanilla tag it sits in,
     * so an item of ours in no tag resolves to {@code NONE} -- and {@code NONE} does not fight. The
     * copper tools are ordinary {@code AxeItem}s and {@code SwordItem}s, but "an axe" to the game
     * is a member of {@code #minecraft:axes}, and a new axe starts outside it.</p>
     *
     * <p>Vanilla's own tags merge with ours, so this adds our five to the vanilla five rather than
     * replacing them. The pickaxe is here for consistency and not because it fights: it resolves to
     * {@code PICKAXE}, which is deliberately the one profile that does not.</p>
     */
    private void copperToolsFight() {
        tag(ItemTags.SWORDS).add(ModTools.COPPER_SWORD.get());
        tag(ItemTags.AXES).add(ModTools.COPPER_AXE.get());
        tag(ItemTags.PICKAXES).add(ModTools.COPPER_PICKAXE.get());
        tag(ItemTags.SHOVELS).add(ModTools.COPPER_SHOVEL.get());
        tag(ItemTags.HOES).add(ModTools.COPPER_HOE.get());
    }

    /**
     * The mod's own classification: one {@code #cortescombat:profile/<id>} per mould, holding
     * every item of ours that uses it.
     *
     * <h2>What this is for</h2>
     *
     * <p><b>Maintainability of the mod, and nothing else.</b> {@code WeaponStats.deriveProfile}
     * used to ask only vanilla's tags, so five copper tools of ours entered our own combat
     * system through {@code #minecraft:swords} and its four siblings -- tags in a namespace we
     * do not own, that this very file wrote into. Tags merge additively, which is what makes
     * them work, and the same mechanism lets a datapack declare one with
     * {@code "replace": true} and drop every member, ours included. That is not hypothetical
     * behaviour to guard against a hypothetical pack: it is the difference between the copper
     * sword fighting and silently not fighting, decided in a file that has nothing to do with
     * this mod.</p>
     *
     * <p>These tags are ours, so the answer cannot be replaced by a pack that never heard of
     * {@code cortescombat}, and they are read <b>before</b> the vanilla ones. The vanilla tags
     * stay where they were, below these, because they are the compat layer for a weapon of
     * another mod.</p>
     *
     * <h2>Why the five copper tools are the point and the thirty are the safety net</h2>
     *
     * <p>{@code ModTools} sets no {@code weapon_profile} component -- the copper sword is an
     * ordinary {@code SwordItem}, and "an axe" to the game is a member of {@code #minecraft:axes}
     * which a new axe starts outside of. <b>So the tag is the only thing that tells the game
     * the copper tools fight at all</b>, which is what {@link #copperToolsFight()}'s javadoc
     * says from the other side: without those five lines in the vanilla tags they resolve to
     * {@code NONE} and swing like vanilla.</p>
     *
     * <p>The thirty catalogue weapons <em>do</em> carry the component, so the component wins and
     * the tag is never reached for them. They are in the file anyway, for two reasons: the file
     * is then the canonical list of "which items of ours use which mould", which is the thing a
     * future contributor comes here looking for; and it is a safety net for the day a
     * registration path forgets {@code .component(WEAPON_PROFILE, ...)}, which is the shape of
     * bug this project has already paid for four times -- a component in place and nothing
     * reading it, or the reverse.</p>
     *
     * <h2>Every profile gets a file, including the four with no member</h2>
     *
     * <p><b>Unlike {@link #materialTags()}, where calling {@code tag(...)} unconditionally was
     * a bug, here it is the specification.</b> {@code none}, {@code unarmed}, {@code mace} and
     * {@code trident} have no item of ours yet, and they get an empty file on purpose: an empty
     * tag is a tag that exists, and the file is the path a future entry writes into. A missing
     * file is a mould with no door, and {@code WeaponProfileTagsTest} walks the enum to check
     * every one is there -- so adding a profile to the enum without adding its items fails the
     * build instead of shipping a mould nobody can select.</p>
     */
    private void profileTags() {
        Map<WeaponProfile, List<Item>> ours = new EnumMap<>(WeaponProfile.class);
        // The thirty, by family: each family names the profile it belongs to, so the item's
        // mould comes from the family and never from this file.
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                ours.computeIfAbsent(family.profile(), p -> new ArrayList<>())
                        .add(ModItems.weapon(family.itemName(material)).get());
            }
        }
        // The five copper tools, by hand. **This is the only place that assigns them a
        // profile**, and that is exactly why the tag exists: there is no field on ModTools to
        // copy, so there is no second copy to drift from.
        ours.computeIfAbsent(WeaponProfile.SWORD, p -> new ArrayList<>())
                .add(ModTools.COPPER_SWORD.get());
        ours.computeIfAbsent(WeaponProfile.AXE, p -> new ArrayList<>())
                .add(ModTools.COPPER_AXE.get());
        ours.computeIfAbsent(WeaponProfile.PICKAXE, p -> new ArrayList<>())
                .add(ModTools.COPPER_PICKAXE.get());
        ours.computeIfAbsent(WeaponProfile.SHOVEL, p -> new ArrayList<>())
                .add(ModTools.COPPER_SHOVEL.get());
        ours.computeIfAbsent(WeaponProfile.HOE, p -> new ArrayList<>())
                .add(ModTools.COPPER_HOE.get());
        // And the thirty-first, which carries the component like the rest of the catalogue and
        // lands in the file for the same two reasons.
        ours.computeIfAbsent(WeaponProfile.ARCABUZ, p -> new ArrayList<>())
                .add(ModItems.ARCABUZ.get());

        for (WeaponProfile profile : WeaponProfile.values()) {
            var appender = tag(WeaponProfileTags.profileTag(profile));
            for (Item item : ours.getOrDefault(profile, List.of())) {
                appender.add(item);
            }
        }
    }

    /**
     * Los nueve tags de categoría -- la llave por la que un tercero entra sin escribir YAML.
     *
     * <p><b>La lista sale de {@code ArchetypeSpec.builtins()}, no de un literal acá</b>: un
     * arquetipo nuevo sin su tag (o al revés) sería dos listas que divergen en silencio, y esta
     * es exactamente la clase de divergencia que sólo se nota cuando un packmate mete su item
     * en un tag que nadie consulta.
     *
     * <p><b>Los nueve nacen vacíos, y es la especificación</b> (decisión #9): el archivo es el
     * camino donde un datapack escribe después -- el patrón de los cuatro vacíos de
     * {@code profile/}, donde {@code tag(...)} sin miembros es lo correcto y en
     * {@code materialTags()} era el bug. Vacío aquí significa "nadie lo pobló todavía", no
     * "este tipo no existe".
     */
    private void categoryTags() {
        for (String type : com.cortes.combat.weaponpack.ArchetypeSpec.builtins().keySet()) {
            tag(TagKey.create(Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID,
                            "category/" + type)));
        }
    }

    /**
     * Las clases de encantamiento: qué arma nuestra hereda cuáles.
     *
     * <h2>Lo que este método reemplaza, y por qué era un problema de diseño</h2>
     *
     * <p>Antes estaba {@code everyWeaponIsABlade()}, que metía <b>las treinta</b> en
     * {@code #minecraft:swords}. Como {@code #minecraft:enchantable/sword} <em>es</em> ese tag, y de
     * ahí cuelgan Knockback, Looting y Sweeping Edge (más Sharpness por {@code sharp_weapon} y
     * Smite/Bane por {@code weapon}), cada arma nuestra heredaba el set de espada entero: un martillo
     * con Sweeping Edge, una alabarda que saquea mejor. <b>El tag es la decisión</b>, así que
     * discriminar es dejar de usar el de vanilla.
     *
     * <h2>Cómo se discrimina</h2>
     *
     * <p>Estos tags son la mitad; la otra son <b>seis overrides</b> en
     * {@code src/main/resources/data/minecraft/enchantment/} — copias completas del JSON de vanilla
     * con <b>sólo</b> {@code supported_items} (y {@code primary_items} en Fire Aspect) apuntando a la
     * <b>unión</b> con estos tags. Unión y no reemplazo: lo vanilla queda como estaba y lo nuestro se
     * agrega. {@code sharpness} y {@code smite} lo hacen por eso; {@code fire_aspect} es el único que
     * <b>quita</b> algo, y es una decisión del dueño (el quemado va a lo contundente, que no tiene
     * Sharpness con el que competir).
     *
     * <p><b>La durabilidad sí es para todas.</b> Unbreaking, Mending y Vanishing cuelgan de
     * {@code #minecraft:enchantable/durability}, que incluye {@code #swords}: sacar las armas de ahí
     * sin reponerlas les habría quitado los tres en silencio, que es la forma de bug que este archivo
     * ya pagó una vez ({@code materialTags}).
     */
    private void enchantClasses() {
        TagKey<Item> blade = own("enchantable/blade");
        TagKey<Item> blunt = own("enchantable/blunt");
        TagKey<Item> all = own("enchantable/all");
        TagKey<Item> loot = own("enchantable/loot");
        TagKey<Item> shove = own("enchantable/shove");
        TagKey<Item> sword = own("enchantable/sword");
        // La clase del hacha, por la misma razón que la de la espada: el hacha del juego es la de
        // vanilla reworkeada, y `profile/axe` es la lista canonica de las NUESTRAS que usan ese
        // molde -- meterle las de vanilla la ensuciaria para su otro lector (WeaponStats).
        TagKey<Item> axe = own("enchantable/axe");
        // La clase de la azada y la de las pesadas, por la misma razon que las dos de arriba: las
        // dos armas del juego son de vanilla. `heavy` NO es `shove` -- shove incluye la pala, y la
        // pala no castiga armadura: su identidad es empujar.
        TagKey<Item> hoe = own("enchantable/hoe");
        TagKey<Item> heavy = own("enchantable/heavy");
        TagKey<Item> durability = TagKey.create(Registries.ITEM,
                ResourceLocation.withDefaultNamespace("enchantable/durability"));

        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                Item item = ModItems.weapon(family.itemName(material)).get();
                tag(all).add(item);
                tag(durability).add(item);
                if (family == WeaponFamily.MARTILLO) {
                    tag(blunt).add(item);
                } else {
                    tag(blade).add(item);
                }
                if (family == WeaponFamily.DAGA) {
                    tag(loot).add(item);
                }
            }
        }
        // El arcabuz es la treinta y uno y pelea con la bayoneta: entra a la clase que pelea y a la
        // de durabilidad, y a ninguna otra -- no es filo, no es contundente y no saquea.
        tag(all).add(ModItems.ARCABUZ.get());
        tag(durability).add(ModItems.ARCABUZ.get());
        // **Y ya no hay clase `gun`.** Existía para darle a Quick Charge de vanilla el alcance del
        // arcabuz, y esa reutilización se cayó en playtest: el efecto de Quick Charge es un número
        // absoluto (−0,25 s por nivel) y sobre nuestra base de 2,5 s da la mitad de lo que el diseño
        // pedía. La recarga es ahora **Pólvora Seca**, un encantamiento nuestro que se pone por
        // MOLDE (`#cortescombat:profile/arcabuz`) como toda identidad -- así que la clase de arma de
        // fuego no la lee nadie, y un tag que nada lee es una línea que hay que mantener sin motivo.

        // El empuje es de lo pesado, y lo pesado incluye lo vanilla reworkeado: hacha, pala y maza.
        // Con `blunt` ya completo (martillos + maza + pala), el empuje es hacha + lo contundente, y
        // la maza y la pala entran por ahi sin nombrarlas dos veces.
        tag(shove).addTag(ItemTags.AXES);
        tag(shove).addTag(blunt);

        // **Y cada clase se COMPLETA con los tags de vanilla que su encantamiento ya tenia.**
        //
        // <p><b>Esto no es cosmético: es la unica forma en que el override del JSON carga.</b> El
        // campo {@code supported_items} de un encantamiento acepta UN tag ({@code "#ns:tag"}) o una
        // lista de ids <em>directos</em> -- pero <b>no una lista de tags</b>: el {@code #} de una
        // lista se parsea como id de item y el registro entero falla al cargar el mundo, con un
        // error que nombra el string y no el archivo. Los tags, en cambio, si pueden contener otros
        // tags ({@code shove} lo hace tres lineas mas arriba). Asi que la union vive donde viven las
        // uniones, y el JSON referencia un solo nombre.</p>
        //
        // <p>Verificado contra las fuentes de 1.21.1: {@code Enchantment.EnchantmentDefinition}
        // declara {@code supported_items} como {@code RegistryCodecs.homogeneousList(ITEM)}, cuyo
        // camino de lista usa ids directos y no acepta {@code #}.</p>
        tag(blade).addTag(vanilla("enchantable/sharp_weapon"));
        tag(all).addTag(vanilla("enchantable/weapon"));
        // Lo "contundente" del diseno es martillo + maza + pala, y no solo los cinco martillos: es
        // la clase a la que van Fire Aspect y el empuje, y las tres armas que castigan armadura.
        tag(blunt).add(Items.MACE).addTag(ItemTags.SHOVELS);
        tag(loot).addTag(vanilla("enchantable/sword"));
        // La clase de la espada: las de vanilla y las nuestras del molde espada. El molde es un tag
        // aparte a proposito -- `profile/sword` es la lista canonica de que armas nuestras usan ese
        // molde, y meterle las de vanilla la ensuciaria para su otro lector (WeaponStats).
        tag(sword).addTag(vanilla("enchantable/sword"));
        tag(sword).addTag(own("profile/sword"));
        // Y el hacha: la del juego es la de vanilla, asi que su clase es `#minecraft:axes` mas las
        // nuestras del molde hacha.
        tag(axe).addTag(ItemTags.AXES);
        tag(axe).addTag(own("profile/axe"));
        // La azada: la del juego es de vanilla, asi que su clase es `#minecraft:hoes` mas las
        // nuestras del molde.
        tag(hoe).addTag(ItemTags.HOES);
        tag(hoe).addTag(own("profile/hoe"));
        // Y las pesadas: los martillos nuestros, la maza de vanilla y las hachas. Sin la pala, que
        // esta en `shove` y no castiga armadura -- por eso no se puede reusar `blunt`, que la
        // incluye.
        tag(heavy).addTag(own("profile/warhammer"));
        tag(heavy).add(Items.MACE);
        tag(heavy).addTag(ItemTags.AXES);
    }

    /** Un tag de vanilla por id, para completar una clase de encantamiento con su alcance original. */
    private static TagKey<Item> vanilla(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(path));
    }

    /** Un tag de encantamiento nuestro, por id. */
    private static TagKey<Item> own(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, path));
    }

    /**
     * The six tool-material tags 1.21.1 does not ship, written here so the recipes have one to read.
     *
     * <h2>The bug, and why it is the same shape as the mace and the trident</h2>
     *
     * <p>{@link WeaponMaterial#repairTag()} names a tag per tier -- {@code iron_tool_materials},
     * {@code diamond_tool_materials} and so on -- and <b>three</b> separate consumers resolve it as
     * {@code minecraft:<name>}: the recipe's body ingredient, the advancement's criterion, and the
     * anvil repair ingredient in {@link CortesTier#getRepairIngredient()}.</p>
     *
     * <p><b>1.21.1 ships exactly one of the seven.</b> The vanilla client jar carries
     * {@code data/minecraft/tags/item/stone_tool_materials.json} and no other
     * {@code *_tool_materials} file at all, and {@code Tiers} agrees from the other side: wood
     * repairs with {@code ItemTags.PLANKS}, stone with {@code STONE_TOOL_MATERIALS}, and iron,
     * gold, diamond and netherite with <b>plain items</b> -- {@code IRON_INGOT},
     * {@code GOLD_INGOT}, {@code DIAMOND}, {@code NETHERITE_INGOT}. There is no
     * {@code iron_tool_materials} for a recipe to read, and there never was.</p>
     *
     * <p>A {@code TagKey} for a path nothing publishes does not throw and does not warn: it
     * resolves to an empty set. The recipe parses, the advancement parses,
     * {@code Loaded 1290 recipes} is printed, and the weapon cannot be made. <b>Only the five stone
     * weapons were craftable</b>, and only because stone is the one tag vanilla actually writes.
     * This is the same failure as the {@code #maces} / {@code #tridents} round that cost the mace
     * and the trident their profiles, found in a third place.</p>
     *
     * <h2>Why the {@code minecraft} namespace and not ours</h2>
     *
     * <p>Because DESIGN.md 5bis names it, and the reason survives the version change.
     * {@link WeaponMaterial} says "when the version that brings copper age arrives this enum loses
     * one line and the recipes keep working, because they read the material tag rather than a named
     * item". A tag we own would be a tag we keep forever; writing into {@code minecraft:} means the
     * day Mojang ships {@code copper_tool_materials} the two files merge and this line becomes
     * redundant, which is the outcome the design was aiming at. Tags from several packs merge
     * additively, so a new tag in vanilla's namespace is legal and is how a mod extends one.</p>
     *
     * <p><b>{@code stone_tool_materials} is deliberately not written.</b> Vanilla ships it with
     * cobblestone, blackstone and cobbled deepslate, and re-declaring a tag that already works
     * would only add a second place for its contents to be wrong.</p>
     */
    private void materialTags() {
        for (WeaponMaterial material : WeaponMaterial.values()) {
            // **`tag(...)` is called inside each arm and not once before the switch.** Calling it
            // unconditionally registers the tag even when nothing is added, so the STONE arm below
            // wrote an empty `minecraft:stone_tool_materials.json` -- a second, empty definition of
            // a tag vanilla already fills. It merges harmlessly, and it is exactly the kind of file
            // that later reads as "we own this tag" when we do not.
            switch (material) {
                case WOODEN -> tag(CortesTier.repairTag(material)).addTag(ItemTags.PLANKS);
                case COPPER -> tag(CortesTier.repairTag(material)).add(Items.COPPER_INGOT);
                case IRON -> tag(CortesTier.repairTag(material)).add(Items.IRON_INGOT);
                case GOLDEN -> tag(CortesTier.repairTag(material)).add(Items.GOLD_INGOT);
                case DIAMOND -> tag(CortesTier.repairTag(material)).add(Items.DIAMOND);
                case NETHERITE -> tag(CortesTier.repairTag(material)).add(Items.NETHERITE_INGOT);
                // Vanilla's own, and the one tag the recipes could already reach. Not touched.
                case STONE -> { }
            }
        }
    }
}
