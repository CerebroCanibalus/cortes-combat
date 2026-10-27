package com.cortes.combat.client;

import com.cortes.combat.platform.ResourceIds;

import com.cortes.combat.state.ArcabuzState;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/**
 * Which of the arcabuz's two models the game draws, and the two predicates that choose between them.
 *
 * <h2>Two models, not seven</h2>
 *
 * <p>This class used to hand out a <b>frame number</b> and the project carried five hand-written
 * copies of the weapon, one per frame of the fuse, each of them thirty seven kilobytes of
 * identical geometry that differed only in which strip it pointed at. This file and those five
 * models are now <b>one model of six lines</b>:</p>
 *
 * <pre>
 *   arcabuz.json              the weapon, flintlock up       -- nothing loaded, nothing lit
 *   arcabuz_flintlockdown.json  the weapon, flintlock down   -- a firestone is in the chamber
 *   arcabuz_fuse.json         parent flintlockdown + the animated strip -- the match is lit
 * </pre>
 *
 * <p>The fuse model inherits its geometry and only names a different texture, because
 * <b>the flame was never a separate piece of the weapon to begin with</b>: all five old models had
 * the same eight elements in the same places with the same rotations, and differed only in UVs.
 * So the animation is the game's: a {@code .mcmeta} on a sixty four by three hundred and twenty
 * strip, five frames of sixty four, {@code frametime} 4, which is exactly twenty ticks -- the whole
 * fuse.</p>
 *
 * <h2>Why a texture animation is safe here when it was not the first time</h2>
 *
 * <p>The strip was originally cut into five files because a {@code .mcmeta} runs for ever and
 * nothing in the game could stop it: the playtest reported the weapon as "bugged, it has no
 * textures", because the fuse burned down on its own in the inventory whether or not a shot had
 * been fired. <b>That reason no longer applies, and it is worth being exact about why.</b> The
 * flame is not a texture that plays by itself any more -- it is a texture that only
 * <em>exists</em> while {@link #FUSE} says a fuse is lit, because the whole model that draws it is
 * the one the override picks. The animation runs free because the day the fuse goes out the model
 * stops being drawn at all, and the strip freezes on frame zero.</p>
 *
 * <h2>Why the frame is not a data component</h2>
 *
 * <p>It used to be one, written by the server every tick of the burn. <b>Writing a component on
 * the held stack syncs the stack</b>, the client ends up holding a different {@code ItemStack}, and
 * vanilla plays its item-swap animation -- {@code ItemInHandRenderer#applyItemArmTransform} reads
 * {@code equippedProgress} and moves the weapon <b>sixty centimetres</b> vertically while it
 * settles. Four frame changes in a one second burn is four item swaps in a second, which is what
 * the playtest reported as the weapon bobbing in the hand.</p>
 *
 * <p>It looked exactly like a channel problem and it was not one. No amount of tuning the recoil,
 * the reload or the loaded pose touched it, because the thing moving the weapon was vanilla's
 * equip animation and the trigger was a network write.</p>
 */
public final class ArcabuzModelProperties {

    /**
     * How far through the fuse the client is, 0 to 1. <b>A float, not a flag.</b>
     *
     * <p>It was {@code isBurning() ? 1 : 0}, because the flame was a {@code .mcmeta} and the game
     * chose the frame. That is the bug this is the fix for: <b>a sprite's frame index is a clock
     * belonging to the atlas, not a countdown belonging to the item.</b> Every model that names the
     * same animated texture shares one clock, it starts when the atlas is stitched and nothing ever
     * rewinds it. So the fuse lit for the first time in a session showed frame 0 and the tenth one
     * showed whatever the clock happened to be on, which the playtest reported as "the animation
     * appears sometimes and does not always start at the beginning".</p>
     *
     * <p>A predicate that reads {@link com.cortes.combat.client.ArcabuzFuseState#progress()} can:
     * it is elapsed rather than remaining because every ramp in this project's viewmodel is written
     * as progress, and elapsed is what a set of ascending thresholds can select a frame from.</p>
     */
    public static final ResourceLocation FUSE = ResourceIds.rl("fuse");

    /**
     * A firestone is in the chamber, which is what puts the flintlock down.
     *
     * <p><b>This is the whole of "el flintlock baja".</b> It could not be an offset on the
     * viewmodel: an offset moves the entire weapon, and the playtest rejected that four times over
     * as "too low" and then "it does not go down". Lowering one part of a weapon means the part is
     * a separate model, and the only thing this predicate has to say is whether that model is the
     * one to draw.</p>
     */
    public static final ResourceLocation LOADED = ResourceIds.rl("loaded");

    /**
     * **No GUI predicate, on purpose.**
     *
     * <p>There used to be one, and it was the wrong instrument for the question: a predicate answers
     * "what does this stack look like", and the question the sprite-versus-gun choice asks is "where
     * is it being drawn". Both a slot and the gun in the player's hand are drawn with a screen open,
     * so the predicate could not tell them apart and the gun turned into a flat sprite every time the
     * inventory was opened. The choice now lives in {@code ArcabuzGuiIconMixin}, at the point where
     * the display context is actually known.</p>
    

    private ArcabuzModelProperties() {
    }

    /**
     * **La llave del icono en 1.20.1 es un {@code ResourceLocation} PELADO, y esto esta
     * verificado en las fuentes de Forge 47.4.28, no supuesto.**
     *
     * <p>El parche de {@code ModelBakery} hace {@code f_119214_.put(rl, unbakedmodel)} con el
     * {@code ResourceLocation} que devolvio {@code ModelEvent.RegisterAdditional} -- sin
     * variante, sin envolverlo. Y la busqueda con variante <em>falla</em>:
     * {@code ModelResourceLocation} sobreescribe {@code hashCode()} sumando su variante, asi
     * que un {@code get(mrl)} cae en otro balde del {@code HashMap} y devuelve el <b>missing
     * model</b> por el {@code getOrDefault} de {@code ModelManager}.</p>
     *
     * <p>Eso es exactamente lo que se veia: el log decia "icono plano listo" (el otro
     * mecanismo, el wrapper del mapa, si encontraba la llave) y la GUI seguia sin texturas
     * porque el mixin -- que corre DESPUES y es el que decide en que contextos va el sprite --
     * pedia {@code arcabuz_icon#standalone} y recibia el missing model. Dos mecanismos, una
     * llave mal pedida, y el sintoma final lo escribia el que corria ultimo. (μ_μ)</p>
     */
    public static final ResourceLocation ICON = ResourceIds.rl("arcabuz_icon");

    /**
     * El modelo del sprite, pedido por la llave con la que el bakery lo archivo.
     *
     * <p>{@code getModel(ResourceLocation)} es la sobrecarga que Forge agrega a
     * {@code ModelManager} justo para esto; la vanilla ({@code m_119422_}) toma
     * {@code ModelResourceLocation} y es la que fallaba.</p>
     */
    public static net.minecraft.client.resources.model.BakedModel iconModel() {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(ICON);
    }

    /**
     * Registers both predicates. Client side, once, at startup.
     *
     * <p><b>Both read the client's own state and nothing else.</b> No clock, no interpolation: the
     * gun a player sees is the gun every other player sees, because the countdown that decides
     * both of these arrived from the server in one packet per transition.</p>
     */
    public static void register() {
        ItemProperties.register(com.cortes.combat.registry.ModItems.ARCABUZ.get(), FUSE,
                (stack, level, entity, seed) -> ArcabuzFuseState.progress(
                        level == null ? ArcabuzState.FUSE_TICKS
                                : com.cortes.combat.enchant.CombatEnchantRules.fuseTicks(
                                        com.cortes.combat.enchant.CombatEnchantments.level(
                                                level.registryAccess(), stack,
                                                com.cortes.combat.enchant.CombatEnchantments.MECHA_CORTA),
                                        ArcabuzState.FUSE_TICKS)));
        // **Off the stack, and the parameter this used to ignore is the whole fix.**
        //
        // This was `ArcabuzFuseState.isLoaded()`, a static that no item switch ever cleared, so it
        // described whichever gun happened to be in the player's hands whenever the last packet
        // arrived -- and the playtest read that as "the flintlock is always down". It is not a
        // rendering problem at all: the chamber is a data component on the stack, so reading it
        // off the stack the game is literally drawing makes the two identical by construction.
        //
        // **Or'd with a burning fuse, and that half is not a fudge.** The chamber is spent at the
        // light, so a gun with a live fuse is empty -- but the player was told to keep it pointed
        // down for the whole second, and dropping the pose when the ball leaves is what made the
        // weapon spring back mid-burn.
        ItemProperties.register(com.cortes.combat.registry.ModItems.ARCABUZ.get(), LOADED,
                (stack, level, entity, seed) -> ArcabuzFuseState.chamberVisible() ? 1.0F : 0.0F);
        // **The third predicate is gone, and so is the `[[overrides]]` entry that used it.**
        // The flat sprite is chosen by `ArcabuzGuiIconMixin`, which reads the display context: the
        // hand is drawn with a screen open too, and that was the whole bug -- a predicate that asks
        // "is a screen open?" gets the same answer for a slot and for the gun in the player's hand.
        // What is left is what a predicate CAN answer, which is what the stack has been through.
    }
}
