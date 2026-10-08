package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.config.ClientConfig;
import com.cortes.combat.client.ArcabuzModelProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entry point.
 *
 * <p>Separate from the common class because {@code InputEvent} and the GUI events do not
 * exist on a dedicated server: referencing them from common code fails to load there.
 * NeoForge allows several {@code @Mod} classes with the same id as long as their
 * {@code dist} differs.</p>
 */
@Mod(value = CortesCombat.MODID, dist = Dist.CLIENT)
public class CortesCombatClient {

    public CortesCombatClient(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.register(SwingInputHandler.class);
        // ItemViewModel is deliberately absent. It used to be registered here, because the
        // viewmodel was drawn from a RenderHandEvent listener. It is drawn from
        // ItemInHandRendererMixin now, in the item's own frame, so the class has no event methods
        // at all and registering it aborts mod construction with "has no @SubscribeEvent
        // methods, but register was called anyway". The bus is not a place to park a class that
        // nothing subscribes to.
        NeoForge.EVENT_BUS.register(ChargeFovEffect.class);
        NeoForge.EVENT_BUS.register(GuardInputHandler.class);
        NeoForge.EVENT_BUS.register(SpecialInputHandler.class);
        NeoForge.EVENT_BUS.register(ClientTasks.class);
        // The nether star's camera shake. Registered here like every other client listener, on the
        // game bus, because it is a per-frame viewport event fired from Camera.setup.
        NeoForge.EVENT_BUS.register(ReactorShake.class);
        NeoForge.EVENT_BUS.register(ConeDebugRenderer.class);
        // The weapon tooltips. Registered here rather than through @EventBusSubscriber because
        // this class already is the client's subscription list, and a second list is a second
        // place to forget one.
        NeoForge.EVENT_BUS.register(CombatTooltipHandler.class);
        // **The mod bus and not the game bus, and the difference is not a style choice.**
        // `ModelEvent.ModifyBakingResult` implements `IModBusEvent`: registering this on
        // `NeoForge.EVENT_BUS` means the listener never fires, so the arcabuz would keep drawing as
        // a rotating 3D model in every slot and nothing would say so -- the same shape of silent
        // failure as the missing `@SubscribeEvent` that cost the arcabuz its fuse frames.
        modEventBus.addListener(FlatInventoryIcon::onBakingResult);
        modEventBus.addListener(CortesCombatClient::registerParticles);
        modEventBus.addListener(CortesCombatClient::registerLayers);
        modEventBus.addListener(CortesCombatClient::onClientSetup);
    }

    /**
     * Layer order is taken from the real {@link VanillaGuiLayers} order, verified against the
     * 1.21.1 sources:
     * {@code CAMERA_OVERLAYS -> CROSSHAIR -> HOTBAR -> ... -> PLAYER_HEALTH}.
     *
     * <p>So: the vignette goes <b>below</b> the health bar (it is a screen effect, and
     * covering the health would hide the thing it is warning about), and everything that lives
     * around the crosshair sits <b>above</b> it. Using {@code registerAboveAll} for any of them
     * would be wrong.</p>
     *
     * <p>The crosshair is <b>replaced</b>, not joined. The slot already exists, and the vanilla
     * layer draws the attack cooldown into it, which would put a second cooldown readout on
     * screen next to the brackets.</p>
     *
     * <p>Relative order above the crosshair: brackets, then orbs. The brackets are the widest
     * thing on screen and the orbs are the smallest; drawing the orbs last keeps them readable
     * in the rare case a 9 orb row and a bracket column overlap, and costs nothing otherwise.</p>
     */
    /**
     * Binds the slash particle to its sprite.
     *
     * <p>{@code registerSpriteSet} and not {@code registerSpecial}: the sprite set is where the
     * particle's textures come from, and the slice out of the vertical strip is done by the
     * particle itself. One type covers every weapon because the sprite travels in the payload.</p>
     */
    private static void registerParticles(RegisterParticleProvidersEvent event) {
        // The particle binds its own sprite, and the two lines after it are why.
        //
        // ParticleEngine calls pickSprite from exactly one place: the deprecated
        // register(ParticleType, ParticleProvider.Sprite) overload, which binds on the provider's
        // behalf. registerSpriteSet just stores the provider, so a particle registered that way
        // arrives with sprite == null and its four UV accessors read a null sprite. The null
        // guards in SlashParticle keep that from being a crash, which is exactly why it went
        // unnoticed: the particle rendered nothing and said nothing.
        event.registerSpriteSet(com.cortes.combat.registry.ModParticleTypes.SLASH.get(),
                sprites -> (options, level, x, y, z, dx, dy, dz) -> {
                    if (!level.isClientSide) {
                        return null;
                    }
                    com.cortes.combat.particle.SlashParticle particle =
                            new com.cortes.combat.particle.SlashParticle(
                                    level, x, y, z, dx, dy, dz, options);
                    particle.bindSprite(sprites);
                    return particle;
                });

        // The purification plume. Unlike the slash this is a normal animated sprite: the 16x64
        // art is an .mcmeta animation (four frames read off the strip's height), so the particle
        // only binds the sprite set and the atlas walks the frames. No UV cutting, no frame count
        // in Java. SimpleParticleType as the option type, because it carries no data.
        event.registerSpriteSet(com.cortes.combat.registry.ModParticleTypes.PURIFIED.get(),
                sprites -> (options, level, x, y, z, dx, dy, dz) ->
                        new com.cortes.combat.particle.PurifiedParticle(
                                level, x, y, z, dx, dy, dz, sprites));
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "damage_overlay"),
                new DamageOverlayLayer());
        event.replaceLayer(VanillaGuiLayers.CROSSHAIR, new CombatCrosshairLayer());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "cooldown_brackets"),
                new CooldownBracketLayer());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "guard_orbs"),
                new GuardOrbsLayer());
    }

    /**
     * Raises the starting FOV, once.
     *
     * <p>Only while the option still reads as vanilla's own 70. That single comparison is the
     * whole mechanism: it means a fresh install gets a sane view, and a player who has already
     * picked their own number is never overruled, with no flag to remember and nothing to go
     * stale. The value is left in the options screen either way, so it stays adjustable.</p>
     */
    /**
     * {@code @SubscribeEvent} on the mod bus, which is what makes any of the above run.
     *
     * <p><b>This annotation was missing, and nothing in the file said so.</b> The method had a
     * javadoc explaining in detail why the fuse properties have to be registered on this event and
     * not in the constructor, it took the right event type, and it enqueued the right work -- and it
     * was never called, because a method that handles a lifecycle event is a convention, not a
     * declaration. With no subscriber, {@link ArcabuzModelProperties#register()} never ran, so
     * {@code ItemProperties} had no entry for either predicate, and
     * {@code ItemOverrides#resolve} fills an unregistered property with {@code NEGATIVE_INFINITY}.
     * Every override then failed, on every frame, for the whole session, and the gun drew its base
     * model forever: the flintlock never moved and the flame never appeared.</p>
     *
     * <p>The two symptoms looked like two bugs in two files, and the one real cause was a line
     * that was not there. Nothing warns about an unsubscribed handler, the client log is clean, and
     * the model bakes without complaint -- a predicate nobody registered is indistinguishable from
     * a predicate that is simply never satisfied.</p>
     */
    /**
     * The flat sprite's model, loaded as a top-level model so the renderer can ask for it by name.
     *
     * <p><b>Without this the mixin would be handed the missing model and the slot would draw a
     * cube of nothing.</b> A model only reaches {@code ModelManager} if something lists it as a
     * load: an item's inventory model, or a model a blockstate points at. The icon was neither
     * while it was only an override target -- override targets are baked into the parent model, not
     * published -- so asking for it by name had nothing to ask.</p>
     */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onRegisterAdditionalModels(
            net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(ArcabuzModelProperties.ICON_MODEL);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // **The arcabuz's fuse frame, first, and on this event rather than in the constructor.**
        //
        // The first version called it from the constructor and the client died before the world
        // loaded: `NullPointerException: Trying to access unbound value: ResourceKey[minecraft:item
        // / cortescombat:arcabuz]`. It reads `ModItems.ARCABUZ.get()` to say which item the
        // property belongs to, and the constructor runs before the item registry is frozen and
        // bound, so the deferred item is not a value yet.
        //
        // `ItemProperties` is a client-side table the models consult when they are baked, and
        // baking happens after client setup -- so this is the window, which is where vanilla's
        // fishing rod puts its own `cast` property too.
        event.enqueueWork(ArcabuzModelProperties::register);

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        if (mc.options.fov().get() != ClientConfig.VANILLA_FOV) {
            return;
        }
        int wanted = ClientConfig.STARTING_FOV.get();
        if (wanted == ClientConfig.VANILLA_FOV) {
            return;
        }
        mc.options.fov().set(wanted);
    }
}
