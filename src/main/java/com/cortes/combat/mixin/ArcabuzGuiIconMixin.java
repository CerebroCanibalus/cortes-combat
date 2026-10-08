package com.cortes.combat.mixin;

import com.cortes.combat.client.ArcabuzModelProperties;
import com.cortes.combat.registry.ModItems;
import net.minecraft.client.Minecraft;
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
 * The musket: a sprite in a screen, the gun everywhere else.
 *
 * <h2>What was wrong</h2>
 *
 * <p>The arcabuz ships two shapes -- the built model in three dimensions, and a flat sprite for
 * slots -- and the choice used to live in a model override keyed on a predicate that asked
 * {@code Minecraft.screen != null}. <b>That predicate is true for the hand too.</b> Open the
 * inventory while holding the gun and the item in hand stopped being a gun: the viewmodel was
 * drawn from the sprite the inventory uses, and it changed back the moment the screen closed. A
 * predicate answers "what does this stack look like", and the question is actually "where is it
 * being drawn", which only the renderer knows.</p>
 *
 * <h2>Where the question can be answered</h2>
 *
 * <p><b>Here, and not in the predicate.</b> {@code render} receives the {@link ItemDisplayContext}
 * for the frame it is about to draw, and vanilla solves this exact problem in this exact method for
 * the trident and the spyglass: when the context is {@code GUI}, {@code GROUND} or {@code FIXED} it
 * swaps the three-dimensional model for the flat one, and in any other context it draws what the
 * item's own model resolved to. This is the same move with the same shape.</p>
 *
 * <p>The hotbar keeps the gun, and the reason is the same one the old predicate had: a screen being
 * open is what a GUI means, and the hotbar is not drawn from a screen. So {@code screen != null}
 * survives -- it just moved to the one place where it is the whole answer instead of half of it.</p>
 *
 * <h2>What it does not do</h2>
 *
 * <p>It does not choose between the fuse frames, or between the flintlock up and down. Those are
 * the model's own overrides and they still resolve as they always did, in the hand and in the
 * world; only the flat sprite in a screen is decided here, which is also the only one of the three
 * that a predicate cannot answer.</p>
 */
@Mixin(ItemRenderer.class)
public class ArcabuzGuiIconMixin {

    /**
     * The flat sprite's model, at the path the item model has always had. It lives in
     * {@link ArcabuzModelProperties#ICON_MODEL}, which is also the name the load is registered
     * under.
     */
    private static final net.minecraft.client.resources.model.ModelResourceLocation ICON =
            ArcabuzModelProperties.ICON_MODEL;

    /**
     * In a screen, the slot gets the sprite; everywhere else, the gun stays a gun.
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
        if (displayContext != ItemDisplayContext.GUI || !itemStack.is(ModItems.ARCABUZ.get())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // **No screen is the hotbar**, and the gun drawn beside the hand should be the gun.
        if (mc.screen == null) {
            return;
        }
        BakedModel icon = mc.getModelManager().getModel(ICON);
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
