package com.cortes.combat.mixin;

import com.cortes.combat.client.ArcabuzModelProperties;
import com.cortes.combat.registry.ModItems;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The musket: the flat sprite where things are listed, the gun where it is held.
 *
 * <h2>The question, and where it can be answered</h2>
 *
 * <p>The arcabuz ships two shapes -- the built model in three dimensions, and a flat sprite for
 * slots -- and the choice used to live in a model override keyed on a predicate that asked
 * {@code Minecraft.screen != null}. <b>That predicate is true for the hand too.</b> Open the
 * inventory while holding the gun and the item in hand stopped being a gun. A predicate answers
 * "what does this stack look like", and the real question is "where is it being drawn", which only
 * the renderer knows.</p>
 *
 * <p><b>Here, and nowhere else.</b> {@code render} receives the {@link ItemDisplayContext} for the
 * frame it is about to draw, and vanilla solves this exact problem in this exact method for the
 * trident and the spyglass:</p>
 *
 * <pre>{@code
 * boolean flat = displayContext == GUI || displayContext == GROUND || displayContext == FIXED;
 * if (flat && stack.is(Items.TRIDENT)) { model = getModel(TRIDENT_MODEL); }
 * }</pre>
 *
 * <p>So the three contexts are copied rather than invented, and this is the same move with the same
 * shape.</p>
 *
 * <h2>Why {@code screen != null} had to go, and it is the playtest's bug</h2>
 *
 * <p>Because <b>the hotbar is drawn with {@code ItemDisplayContext.GUI} as well</b> -- the same
 * context the inventory slot uses. So the old guard made the hotbar a gun only while no screen was
 * open: open the inventory and the gun in the hotbar turned into the sprite, close it and it turned
 * back. That is the report, word for word: "the 3D model and the 2D sprite keep swapping between the
 * GUI and normal play". The context alone says the same thing in both places, so nothing swaps.</p>
 *
 * <p><b>What is lost is the three-dimensional gun in the hotbar.</b> It is a real loss and it is
 * deliberate: the trident is exactly this, and there is no way to tell the hotbar from an inventory
 * slot that does not go back to guessing. A gun that is a gun in the hotbar half the time is worse
 * than a gun that is a sprite where every other item is a sprite.</p>
 *
 * <h2>What it does not do</h2>
 *
 * <p>It does not choose between the fuse frames, or between the flintlock up and down. Those are
 * the model's own overrides and they still resolve as they always did, in the hand and in the
 * world; only the flat sprite in a listing is decided here, which is also the only one of the three
 * that a predicate cannot answer.</p>
 */
@Mixin(ItemRenderer.class)
public class ArcabuzGuiIconMixin {

    /**
     * The flat sprite's model, asked through the per-leg door.
     *
     * <p><b>Y la puerta es una funcion y no una constante, con la razon puesta:</b> las dos
     * patas archivan el icono con llaves distintas -- 1.21.1 por
     * {@code ModelResourceLocation} con variante {@code standalone}, 1.20.1 por el
     * {@code ResourceLocation} pelado que el parche de {@code ModelBakery} usa al cargarlo.
     * Pedirla con la llave de la otra pata devuelve el missing model sin decir nada, y este
     * mixin es justo el que lo dibuja. {@code ArcabuzModelProperties#iconModel} es la unica
     * linea que sabe de la diferencia.</p>
     */

    /**
     * In a listing, the stack gets the sprite; in the hand, the gun stays a gun.
     *
     * <p>The recursion guard is {@code icon == p_model}: this calls {@code render} back with the
     * icon, that frame hits the same entry point, and this is the line that makes the second visit
     * a no-op instead of an endless loop. The gun's own three-dimensional overrides are untouched
     * by that, because they already resolved before this ran.</p>
     */
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"), cancellable = true)
    private void cortescombat$flatIconInGui(ItemStack itemStack, ItemDisplayContext displayContext,
                                           boolean leftHand, PoseStack poseStack,
                                           MultiBufferSource bufferSource, int combinedLight,
                                           int combinedOverlay, BakedModel p_model,
                                           CallbackInfo ci) {
        if (!itemStack.is(ModItems.ARCABUZ.get())) {
            return;
        }
        // **Los tres contextos de vanilla, y ninguno mas.** Ver la nota de la clase: es el conjunto
        // exacto del tridente -- `GUI` (ranura y hotbar), `GROUND` (tirado) y `FIXED` (marco de
        // item). La mano no esta en la lista, asi que ahi el arma sigue siendo el modelo de tres
        // dimensiones.
        boolean flat = displayContext == ItemDisplayContext.GUI
                || displayContext == ItemDisplayContext.GROUND
                || displayContext == ItemDisplayContext.FIXED;
        if (!flat) {
            return;
        }
        BakedModel icon = ArcabuzModelProperties.iconModel();
        // Guard: sin icono (o ya re-renderizando el icono = segunda visita) no toca nada.
        if (icon == null || icon == p_model) {
            return;
        }
        // **The cast is the whole trick, and it is how a mixin calls the class it is mixed
        // into.** `this` inside a mixin does not expose the target's methods -- the mixin is
        // compiled as its own class -- so the call has to go through the target type. The
        // `(Object)` step is what stops javac rejecting an impossible cast between two unrelated
        // classes, which is exactly what a mixin and its target are before they are woven.
        ((ItemRenderer) (Object) this).render(itemStack, displayContext, leftHand, poseStack,
                bufferSource, combinedLight, combinedOverlay, icon);
        ci.cancel();
    }
}
