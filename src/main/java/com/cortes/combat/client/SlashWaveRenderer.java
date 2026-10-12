package com.cortes.combat.client;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.entity.SlashWave;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Dibuja la onda de filo con su modelo propio, y lo agranda con el nivel.
 *
 * <h2>Lo que reemplaza</h2>
 *
 * <p>Hasta acá la onda se dibujaba con el {@code ThrownItemRenderer} de vanilla sobre una
 * {@code ItemStack} de flecha, y era un placeholder declarado (ver el javadoc de {@link SlashWave}).
 * Ahora hay modelo y textura del dueño, así que el placeholder se va <b>entero</b>: el renderer, el
 * {@code ItemSupplier} de la entidad y el {@code getItem()} que devolvía la flecha. Dejar el
 * {@code ItemSupplier} puesto sería un componente con su consumidor muerto -- la forma exacta del
 * bug que ya costó una ronda.</p>
 *
 * <h2>Por qué un modelo de bloque en un renderer de entidad</h2>
 *
 * <p>El archivo es un modelo de Blockbench, o sea un {@code BakedModel} de la misma familia que un
 * bloque. La vía corta para dibujarlo es la que usa vanilla para un bloque en la mano:
 * {@code ModelBlockRenderer#renderModel}, con un {@link ModelResourceLocation#standalone} pedido en
 * {@code ModelEvent.RegisterAdditional}.</p>
 *
 * <p><b>Y el registro no es opcional, es la mitad que se olvida.</b> Un modelo que nada referencia
 * es un archivo en disco, no un modelo: el juego no lo hornea, {@code getModel} devuelve el modelo
 * "missing" y la onda sale como el cubo violeta de error. Es la misma lección que dejó el icono
 * plano del arcabuz -- {@code icono=false} en el log, con el archivo correcto al lado.</p>
 *
 * <h2>El orden de la pose</h2>
 *
 * <p>Rotar, escalar, centrar -- en ese orden, y cada uno tiene su razón:</p>
 *
 * <ul>
 *   <li><b>Rotar primero</b> para que las otras dos pasen en el marco del tajo y no en el del mundo.</li>
 *   <li><b>Centrar al final</b> porque el modelo nace en la esquina de un bloque (su elemento va de
 *       Y=0 a Y=26), y la entidad está en el centro de su caja. Sin esto el tajo sale colgado del
 *       hombro del jugador en vez de nacer en el filo.</li>
 * </ul>
 *
 * <p>La traslación es en unidades del modelo <em>ya escalado</em> porque en {@code PoseStack} cada
 * llamada multiplica por la derecha: {@code scale} seguido de {@code translate} da
 * {@code v' = s * (v - c)}, que es centrar y después escalar. Al revés el centro se multiplicaría
 * por la escala y el tajo se iría de cuadro justo en el nivel 3.</p>
 */
public class SlashWaveRenderer extends EntityRenderer<SlashWave> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CortesCombat.MODID, "projectile/slash");

    /**
     * El modelo, con la variante {@code standalone} que NeoForge exige para lo que se pide a mano.
     *
     * <p>{@code RegisterAdditional} rechaza cualquier otra: {@code "Side-loaded models must use the
     * 'standalone' variant"}. El de nombre completo queda "unused" en el bake y no llega nunca.</p>
     */
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(CortesCombat.MODID, "projectile/sweeping_edge"));

    public SlashWaveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** Lo que el juego hornea porque alguien lo pidió. Sin esto, cubo violeta. */
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(MODEL);
    }

    /**
     * Una linea al arranque que dice <b>si el modelo llego a hornearse</b>, y existe porque el
     * sintoma no distingue.
     *
     * <p>Un modelo que no se hornea y un sprite que no llega al atlas se ven <b>igual</b>: magenta y
     * negro. Son dos causas distintas con el mismo dibujo, y averiguar cual de las dos es sin una
     * linea al arranque cuesta una ronda de playtest cada vez. Es exactamente el mismo razonamiento
     * de {@code reportVanillaProfiles}, {@code reportOurRecipes} y {@code reportSurvivalSeam}: una
     * linea que contesta en el arranque lo que si no se contesta mirando la pantalla.</p>
     *
     * <p>{@code horneado=false} significa que {@code RegisterAdditional} no llego -- el modelo es un
     * archivo en disco y {@code getModel} devuelve el faltante. {@code horneado=true} con la pantalla
     * en magenta significa que el modelo esta y <b>lo que falta es el sprite en el atlas</b>: un
     * modelo side-loaded llega a hornearse y <b>no aporta su material</b>, asi que la textura hay que
     * declararla a mano en {@code assets/minecraft/atlases/blocks.json} -- que se CONCATENA con los
     * del resto de los packs ({@code SpriteSourceList#load} usa {@code getResourceStack}), no los
     * pisa. Este caso se dio de verdad: el log dijo {@code horneado=true} y la onda salia magenta.</p>
     */
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        BakedModel baked = event.getModelManager().getModel(MODEL);
        boolean missing = baked == event.getModelManager().getMissingModel();
        com.cortes.combat.CortesCombat.LOGGER.info(
                "[onda] modelo={} horneado={}",
                MODEL, !missing);
    }

    @Override
    public ResourceLocation getTextureLocation(SlashWave entity) {
        return TEXTURE;
    }

    @Override
    public void render(SlashWave entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        Vec3 motion = entity.getDeltaMovement();
        double horizontal = SlashWaveModel.horizontal(motion.x, motion.z);
        float scale = (float) SlashWaveModel.scale(entity.enchantLevel());

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(SlashWaveModel.yawDegrees(motion.x, motion.z)));
        pose.mulPose(Axis.XP.rotationDegrees(SlashWaveModel.pitchDegrees(motion.y, horizontal)));
        pose.scale(scale, scale, scale);
        pose.translate(-SlashWaveModel.CENTER_X, -SlashWaveModel.CENTER_Y,
                -SlashWaveModel.CENTER_Z);

        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        // Translucido y no cutout: el tajo del arte tiene borde suave, y un alpha binario lo
        // convertiria en una silueta dentada a doce bloques de distancia, que es donde mas se ve.
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), buffers.getBuffer(Sheets.translucentItemSheet()), null, model,
                1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();

        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }
}
