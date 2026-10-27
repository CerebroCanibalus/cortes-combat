package com.cortes.combat.client;

import com.cortes.combat.platform.ResourceIds;

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
        // **Y los temblores puntuales, que son otra lista.** El del reactor vive mientras el bloque
        // este; el de un reventon tiene que sobrevivir catorce ticks a la cosa que lo causo. Los
        // dos suman al mismo pitch y al mismo roll, asi que se componen solos.
        NeoForge.EVENT_BUS.register(CameraShake.class);
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
        // **Both listeners, and the second one was the half nobody wired.** `onRegisterAdditional`
        // is what makes the game BAKE the flat icon (a model nobody references is a file on disk,
        // not a model), and without it `onBakingResult` ran every launch, found nothing, and said
        // `icono=false` -- a correct handler asking a map that nothing had filled.
        modEventBus.addListener(FlatInventoryIcon::onRegisterAdditional);
        modEventBus.addListener(FlatInventoryIcon::onBakingResult);
        // **El modelo de la onda de filo, y por la misma razón que el icono del arcabuz.** Un
        // modelo que nada referencia es un archivo en disco: el juego no lo hornea y el renderer
        // recibe el modelo "missing" -- el cubo violeta, sin una línea de error en el log.
        modEventBus.addListener(SlashWaveRenderer::onRegisterAdditional);
        // **Y la linea que dice si llego a hornearse.** Sin ella, "no se ve" y "falta el sprite" son
        // el mismo magenta en pantalla.
        modEventBus.addListener(SlashWaveRenderer::onBakingCompleted);
        modEventBus.addListener(CortesCombatClient::registerParticles);
        modEventBus.addListener(CortesCombatClient::registerLayers);
        modEventBus.addListener(CortesCombatClient::onClientSetup);
        modEventBus.addListener(CortesCombatClient::registerEntityRenderers);
        // **Las poses de los weapon packs, que son assets y no config.** Se releen con cada
        // resource reload (F3+T las trae sin reiniciar), igual que los modelos y los sonidos.
        // El evento existe con el mismo nombre y método en las dos patas; el rename del Sync
        // lo traduce solo.
        modEventBus.addListener(CortesCombatClient::registerPackPoses);
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
        //
        // **Y decia SANGRE, que era el tipo equivocado.** El efecto de purificacion del cobre
        // spawnea PURIFIED desde PurificationEffect:75, y con el proveedor registrado sobre SANGRE
        // ese tipo se quedaba SIN proveedor: ParticleEngine devuelve null para un tipo sin
        // proveedor y el cliente descarta la particula en silencio, sin error y sin log. O sea que
        // la purificacion cobraba el dano y no se dibujaba nunca. El sintoma es el mismo que ya
        // costo el slash: una particula que se registra, se spawnea, y no se ve.
        //
        // Y el segundo registerSpriteSet de abajo -- el de SangreParticle -- pisaba a este, asi que
        // PurifiedParticle era codigo muerto por partida doble.
        event.registerSpriteSet(com.cortes.combat.registry.ModParticleTypes.PURIFIED.get(),
                sprites -> (options, level, x, y, z, dx, dy, dz) ->
                        new com.cortes.combat.particle.PurifiedParticle(
                                level, x, y, z, dx, dy, dz, sprites));

        // La sangre. A diferencia de la pluma, el dx/dy/dz que llega del paquete SIRVE: es el
        // salpic&oacute;n que la gota trae al nacer, y el que despues la gravedad tira al piso.
        // Por eso el proveedor no los ignora -- ver SangreParticles sobre lo que el paquete puede
        // y no puede decirle a una gota.
        event.registerSpriteSet(com.cortes.combat.registry.ModParticleTypes.SANGRE.get(),
                sprites -> (options, level, x, y, z, dx, dy, dz) ->
                        new com.cortes.combat.particle.SangreParticle(
                                level, x, y, z, dx, dy, dz, sprites));

        // Una mosca. Su velocidad de salida se descarta dentro de la propia part&iacute;cula:
        // una mosca se propulsa sola.
        event.registerSpriteSet(com.cortes.combat.registry.ModParticleTypes.MOSCA.get(),
                sprites -> (options, level, x, y, z, dx, dy, dz) ->
                        new com.cortes.combat.particle.MoscaParticle(
                                level, x, y, z, dx, dy, dz, sprites));

        // **La lectura de un golpe.** El proveedor recibe las opciones, y de ahi salen las dos
        // cosas que el punto necesita: el color del split y cual de las DOS texturas usar. El
        // sprite se elige por indice dentro de la particula, no por edad -- ver HurtParticle.
        event.registerSpriteSet(com.cortes.combat.registry.ModParticleTypes.HURT.get(),
                sprites -> (options, level, x, y, z, dx, dy, dz) ->
                        new com.cortes.combat.particle.HurtParticle(
                                level, x, y, z, dx, dy, dz, sprites, options));

        // El corazoncito verde. Sin opciones: el color esta en el arte y el ascenso en la clase.
        event.registerSpriteSet(com.cortes.combat.registry.ModParticleTypes.CURAR.get(),
                sprites -> (options, level, x, y, z, dx, dy, dz) ->
                        new com.cortes.combat.particle.CurarParticle(
                                level, x, y, z, dx, dy, dz, sprites));
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(ResourceIds.rl("damage_overlay"),
                new DamageOverlayLayer());
        // **Envuelta y no reemplazada, y ese cambio es una llave de config.**
        // `replaceLayer` tiraba la capa de vanilla para siempre: con `hud.combatCrosshair`
        // apagada quedaba la pantalla sin mira, que es lo contrario de lo que el comentario de la
        // config promete ("con esto apagado se dibuja la vanilla"). `wrapLayer` entrega la capa
        // vieja y devuelve la nueva, asi que la decision se toma por frame y la vanilla sigue
        // viva debajo. (⌒‿⌒)
        CombatCrosshairLayer crosshair = new CombatCrosshairLayer();
        event.wrapLayer(VanillaGuiLayers.CROSSHAIR, vanilla -> (graphics, delta) -> {
            if (ClientConfig.HUD_CROSSHAIR.get()) {
                crosshair.render(graphics, delta);
            } else {
                vanilla.render(graphics, delta);
            }
        });
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceIds.rl("cooldown_brackets"),
                new CooldownBracketLayer());
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceIds.rl("guard_orbs"),
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
    // (onRegisterAdditionalModels se borró de esta clase: con el id del icono corregido
    // registraría dos veces el mismo MRL que FlatInventoryIcon.onRegisterAdditional, y nadie
    // lo llamaba. Un solo sitio para el registro del icono: FlatInventoryIcon.)
    @net.neoforged.bus.api.SubscribeEvent
    /**
     * El renderer de la onda de filo: el suyo, con el modelo del dueño.
     *
     * <p>Antes era el {@code ThrownItemRenderer} de vanilla sobre una flecha, y era un placeholder
     * declarado. El modelo existe, así que el placeholder se fue con él -- y el {@code ItemSupplier}
     * de la entidad también, porque un componente que ya nadie lee es la forma exacta de un bug que
     * este proyecto ya pagó.</p>
     *
     * <p>Ver {@link SlashWaveRenderer} para el orden de la pose y para el registro del modelo, que
     * es la mitad que se olvida: un modelo que nada pide no se hornea.</p>
     */
    private static void registerEntityRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.cortes.combat.registry.ModEntities.SLASH_WAVE.get(),
                SlashWaveRenderer::new);
        // **Y la nube de moscas, aunque su renderer no dibuje nada.**
        //
        // No es decoracion: `EntityRenderDispatcher` dereferencia el renderer en `shouldRender` y
        // en `render` sin guarda de nulo, asi que un EntityType sin renderer registrado no es una
        // entidad invisible sino un NPE en el render thread la primera vez que alguien mira hacia
        // ahi. Lo que dibuja la nube son las particulas que el servidor le suelta adentro.
        event.registerEntityRenderer(com.cortes.combat.registry.ModEntities.MOB_CLOUD.get(),
                MobCloudRenderer::new);
        // Y el orbe de antimateria, por lo mismo: su renderer dibuja nada y tiene que existir.
        event.registerEntityRenderer(com.cortes.combat.registry.ModEntities.ANTIMATTER_ORB.get(),
                AntimatterOrbRenderer::new);
        // **Y el Firestone de Mungus, con el renderer de items tirados.** Una Firestone entera
        // dibujada por el mismo renderer que dibuja una papa volando: el proyectil es lo que la
        // recamara escupe, y un renderer propio seria dibujar a mano un item que el juego ya sabe
        // dibujar. La escala es tres porque la bola es de un bloque y el item solo es chico.
        event.registerEntityRenderer(com.cortes.combat.registry.ModEntities.MUNGUS_SHELL.get(),
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(
                        context, 3.0F, true));
    }

    /**
     * Las poses de los weapon packs entran al reload de recursos del cliente.
     *
     * <p>Público y no privado aunque nadie lo llame desde afuera: el EventBus de Forge 1.20.1
     * rechaza un {@code @SubscribeEvent} privado en carga de clase, y esta clase se carga en
     * las dos patas. (¬‿¬)</p>
     */
    public static void registerPackPoses(
            net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(com.cortes.combat.weaponpack.WeaponPackPoses.INSTANCE);
    }

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
