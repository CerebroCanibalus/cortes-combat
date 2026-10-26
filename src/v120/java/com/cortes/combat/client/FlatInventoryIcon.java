package com.cortes.combat.client;

import com.cortes.combat.platform.ResourceIds;


import java.util.Map;

import com.cortes.combat.registry.ModItems;

import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ModelEvent;

/**
 * The arcabuz draws as a flat sprite in the inventory, because a musket is a terrible icon in 3D.
 *
 * <h2>What "draws as a cube" actually is</h2>
 *
 * <p>The report was "in the inventory you can see the model and it looks bad", and it is one boolean
 * read in one line of vanilla. {@code GuiGraphics#renderItem} does:</p>
 *
 * <pre>
 *   BakedModel bakedmodel = itemRenderer.getModel(stack, level, entity, seed);
 *   pose.translate(x + 8, y + 8, 150 + (bakedmodel.isGui3d() ? guiOffset : 0));
 *   Lighting.setupForFlatItems()   // chosen by the same flag, one line below
 *   itemRenderer.render(stack, ItemDisplayContext.GUI, ..., bakedmodel);
 * </pre>
 *
 * <p>So {@code isGui3d()} decides whether the slot draws the item as a rotating lit block or as a
 * flat sprite, and there is no separate "flat icon" path in vanilla. A model either has 3D geometry
 * and is drawn as 3D in a slot, or it does not.</p>
 *
 * <h2>Why the flag is false for every vanilla weapon and true for ours</h2>
 *
 * <p>Read out of {@code ItemModelGenerator}: every generated item model -- every vanilla sword,
 * every tool -- is written with {@code setGui3d(false)}. The flag is on only for block items whose
 * whole point is to look like the block they are. Every other weapon here is a {@code SwordItem}
 * with a generated model and inherits that.</p>
 *
 * <p><b>The arcabuz does not.</b> Its model is hand drawn in Blockbench with eight real elements,
 * because a musket cannot be read as a flat sprite. That same geometry is what the inventory picks
 * up, and a two block gun seen end-on in a sixteen pixel slot is a grey rectangle. The art that
 * fixes it already exists at {@code textures/item/arcabuz_icon.png} and nothing was drawing it.</p>
 *
 * <h2>Why the icon is a separate model and not this one with a flag off</h2>
 *
 * <p><b>Checked, because the obvious answer does not work.</b> {@code ItemModelShaper#getItemModel}
 * hands the <em>same</em> baked model to the inventory and to the hand -- there is one model per
 * item, not one per context. So turning {@code isGui3d} off on the musket's own model would leave
 * the slot drawing its eight elements flat, lit like a sprite: still a musket's worth of geometry,
 * still unrecognisable, and the hand would keep the flat version too.</p>
 *
 * <p>What the slot actually asks for is {@code ItemRenderer#getModel(stack, level, entity, seed)},
 * which resolves item overrides and <em>is</em> reached with {@code ItemDisplayContext.GUI} as the
 * render context. So the icon is its own model, {@code models/item/arcabuz_icon.json}, a plain
 * {@code item/generated} over {@code layer0} -- the same shape every vanilla sword has -- and the
 * arcabuz's model points at it for the GUI.</p>
 *
 * <p><b>And the model cannot say so itself.</b> The model's {@code display} block holds transforms,
 * not a second model: a JSON field that swapped models per context would be the answer if it
 * existed, and it does not. This handler is that field.</p>
 *
 * <h2>What is deliberately NOT changed</h2>
 *
 * <p>The hand, the ground, the creative tab and the four fuse-frame overrides all keep the musket's
 * own model. The gun's animation is the reason the item exists and a flat sprite in the inventory is
 * a readability fix, not a downgrade.</p>
 */
public final class FlatInventoryIcon {

    private FlatInventoryIcon() {
    }

    /**
     * The flat icon as a PLAIN {@code ResourceLocation}, no variant.
     *
     * <p><b>Verified in the Forge 1.20.1 sources, not inferred:</b> the patch to {@code
     * ModelBakery} types the additional-models set as {@code Set<ResourceLocation>} and loads
     * each entry with {@code loadTopLevel(rl)} "without the ModelResourceLocation limitation".
     * Registering a {@code ModelResourceLocation} with the NeoForge-1.21.1 {@code "standalone"}
     * variant here landed on vanilla's BLOCKSTATE lookup instead &mdash; the client log carried
     * the evidence line,</p>
     *
     * <pre>Exception loading blockstate definition: 'cortescombat:blockstates/arcabuz_icon.json'
     * missing model for variant: 'cortescombat:arcabuz_icon#'</pre>
     *
     * <p>&mdash; and what got baked under the MRL key was not the icon but the MISSING model:
     * {@code models.get(ICON)} returned non-null as always, the "listo" line printed as always,
     * and the slot drew the magenta checkerboard. The map lookup below reads the same plain
     * {@code ResourceLocation} beside it. (⌒‿⌒)</p>
     */
    private static final ResourceLocation ICON = ResourceIds.rl("arcabuz_icon");

    /**
     * Ask for the icon, because nothing else does.
     *
     * <p><b>This is the fix, and it came out of the log rather than out of reasoning.</b> The
     * handler above has been correct the whole time: the model is right, the wrapper is right, and
     * the game's own line said so every launch &mdash;</p>
     *
     * <pre>[arcabuz/inventario] no se aplico el icono plano: icono=false arcabuz=true</pre>
     *
     * <p><b>The musket was in the map and the icon was not.</b> A model is only baked when something
     * asks for it, and the only thing that referenced {@code arcabuz_icon} was this class, which
     * then looked in the map and found nothing &mdash; because it had not put itself there. The file
     * sat on disk, correct, and the game never looked at it.</p>
     *
     * <p><b>That is the third time this shape has cost the project something</b> (see the atlas note
     * in the design docs: an atlas built from {@code block/} and {@code item/} does not read
     * {@code models/}; the particle folder is singular; {@code sounds/} is plural). A file that
     * exists is not a file the game reads, and the question a test has to ask is <em>"does
     * anything reference this?"</em> &mdash; not "is it there?".</p>
     */
    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        // 1.20.1: plain ResourceLocation, no variant. See ICON for the client log that proved it.
        event.register(ICON);
    }

    @SubscribeEvent
    public static void onBakingResult(ModelEvent.ModifyBakingResult event) {
        // 1.20.1 keys the map by ResourceLocation: a ModelResourceLocation IS one,
        // so the same constants read the same map.
        Map<ResourceLocation, BakedModel> models = event.getModels();
        BakedModel icon = models.get(ICON);
        BakedModel arcabuz = models.get(new ModelResourceLocation(ModItems.ARCABUZ.getId(), "inventory"));
        if (icon == null || arcabuz == null) {
            // **One line rather than a crash.** This fires before the item registry is readable in
            // some load orders, and a model missing from the map is a model this does not apply to.
            // The line matters because the failure is otherwise invisible: the gun keeps drawing as a
            // grey rectangle in every slot and nothing anywhere mentions an icon.
            org.slf4j.LoggerFactory.getLogger("cortescombat").info(
                    "[arcabuz/inventario] no se aplico el icono plano: icono={} arcabuz={}",
                    icon != null, arcabuz != null);
            return;
        }
        models.put(new ModelResourceLocation(ModItems.ARCABUZ.getId(), "inventory"),
                new FlatIconFor(arcabuz, icon));
        org.slf4j.LoggerFactory.getLogger("cortescombat")
                .info("[arcabuz/inventario] icono plano listo para el inventario");
    }

    /**
     * The musket, except in a slot.
     *
     * <p><b>Two things, and the second one is the whole mechanism.</b> {@link #isGui3d()} returning
     * false makes the slot use flat lighting and skip the Z nudge. {@link #getQuads} returning the
     * icon's single quad instead of eight elements is what actually puts the sprite on screen --
     * without it the slot would draw the musket's geometry with sprite lighting, which is the wrong
     * picture and the wrong shading at once.</p>
     *
     * <p>The transform list is delegated on purpose: it is the GUI's own default and it is what
     * centres a sixteen pixel quad in a sixteen pixel slot.</p>
     */
    private static final class FlatIconFor
            extends net.minecraftforge.client.model.BakedModelWrapper<BakedModel> {

        private final BakedModel icon;

        FlatIconFor(BakedModel musket, BakedModel icon) {
            super(musket);
            this.icon = icon;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(
                BlockState state, Direction side, RandomSource random) {
            return icon.getQuads(state, side, random);
        }

        // **La textura tambien, y no solo los quads.** `BakedModelWrapper` delega `getTexture()`
        // al musket, y en 1.20.1 el renderer enlaza la textura desde ahi en vez de desde los
        // quads -- asi que sin este override el sprite del icono se dibuja con la textura del
        // musket, que es la que falta en el atlas de items y sale el bloque de missing textures.
        // (¬‿¬)

        @Override
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
            return icon.getParticleIcon();
        }
    }
}
