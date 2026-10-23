package com.cortes.combat.client;

import com.cortes.combat.platform.ResourceIds;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * La barra de carga, <b>una sola para todas las armas que cargan algo</b>.
 *
 * <h2>Mismo sprite, mismo sistema, distinto color</h2>
 *
 * <p>El playtest lo pidio asi y es la decision correcta: una barra nueva dibujada aparte seria un
 * segundo sistema que mantener, con su propio ancho, su propio hueco con la mira y su propia forma
 * de crecer -- y las dos se verian distintas sin que nadie lo haya decidido. Lo unico que cambia
 * entre la carga del golpe y la de un arma magica es <b>el color</b>, y el color es una
 * multiplicacion sobre el sprite.</p>
 *
 * <h2>Los dos sprites y no un rectangulo</h2>
 *
 * <p>El vacio es el zocalo y el lleno lleva la salpicadura del final del trazo, asi que un
 * relleno escalado habria tenido que inventar ese detalle. Escalar el lleno por la carga y dibujar
 * el vacio debajo da la salpicadura gratis, y aparece <b>solo cuando la carga esta llena</b>.</p>
 *
 * <h2>Por que hay {@code slot} y no dos alturas escritas en los que llaman</h2>
 *
 * <p>Las dos barras pueden estar vivas a la vez -- un arma que carga con el izquierdo y con el
 * derecho -- y se dibujarian una encima de la otra. El hueco entre ellas sale de la propia altura
 * de la barra, no de un numero en cada llamador: dos numeros para "cuanto mide la barra" es como se
 * llega a que una tape a la otra cuando alguien rediseña el arte.</p>
 */
public final class ChargeBar {

    private static final ResourceLocation CHARGE = sprite("charge_bar");
    private static final ResourceLocation CHARGE_EMPTY = sprite("charge_bar_emtpy");

    private static final int WIDTH = 30;
    private static final int HEIGHT = 15;

    /** El hueco entre el fondo de la mira y la primera barra. El del {@code CombatCrosshairLayer}. */
    private static final int GAP = 9;

    /** El aire entre dos barras apiladas. */
    private static final int STACK_GAP = 2;

    /** El arte tal cual: la barra del golpe cargado. */
    public static final int TINT_PLAIN = 0xFFFFFFFF;

    /**
     * El de las armas magicas: un violeta que se lee como el orbe de antimateria.
     *
     * <p>Es una <b>multiplicacion</b> sobre el sprite y no un reemplazo, asi que el arte tiene que
     * ser claro para que tiña -- sobre un sprite oscuro un tinte violeta da violeta oscuro, que a
     * treinta pixeles de la mira se lee como "la barra no cargo". El arte del proyecto es claro, y
     * si algun dia deja de serlo esto es lo que hay que mirar primero.</p>
     */
    public static final int TINT_ARCANE = 0xFFC06BFF;

    private ChargeBar() {
    }

    /**
     * Dibuja una barra. {@code slot} 0 es la del golpe y 1 la de abajo.
     *
     * <p>Se sale sin dibujar si la carga es cero: una barra vacia dibujada en el zocalo es una barra
     * que aparece cuando el jugador todavia no apreto nada.</p>
     */
    public static void render(GuiGraphics graphics, int centreX, int centreY, float ratio, int tint,
                              int slot) {
        if (ratio <= 0.0F) {
            return;
        }
        int x = centreX - WIDTH / 2;
        int y = centreY + HudAnchor.crosshairReach() + GAP + slot * (HEIGHT + STACK_GAP);

        float alpha = ((tint >>> 24) & 0xFF) / 255.0F;
        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        graphics.setColor(red, green, blue, alpha);
        graphics.blit(CHARGE_EMPTY, x, y, WIDTH, HEIGHT,
                0.0F, 0.0F, WIDTH, HEIGHT, WIDTH, HEIGHT);

        int filled = Math.max(1, Math.round(WIDTH * Math.min(1.0F, ratio)));
        graphics.blit(CHARGE, x, y, filled, HEIGHT,
                0.0F, 0.0F, filled, HEIGHT, WIDTH, HEIGHT);
        // El color se devuelve SIEMPRE, incluso saliendo por arriba: `GuiGraphics` es un buffer
        // compartido y un tinte que sobrevive tiñe todo lo que se dibuje despues en el frame.
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static ResourceLocation sprite(String name) {
        return ResourceIds.rl("textures/gui/sprites/hud/" + name + ".png");
    }
}
