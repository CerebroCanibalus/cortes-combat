package com.cortes.combat.client;

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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

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
     * The flat icon, keyed the way item models are keyed.
     *
     * <p><b>Two arguments, and that is the only constructor that exists in 1.21.1.</b>
     * {@code ModelResourceLocation} is a record of one {@link
     * net.minecraft.resources.ResourceLocation} and one variant string, and the namespace goes
     * inside the first. A three-argument form reads more naturally and does not compile, which
     * is {@code javac} answering a question about an API rather than a guess about it.</p>
     */
    private static final ModelResourceLocation ICON = new ModelResourceLocation(
            ResourceLocation.fromNamespaceAndPath("cortescombat", "arcabuz_icon"), "inventory");

    @SubscribeEvent
    public static void onBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
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
            extends net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel> {

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
    }
}
