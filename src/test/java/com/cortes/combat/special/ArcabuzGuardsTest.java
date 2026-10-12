package com.cortes.combat.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Los arreglos de la ronda del arcabuz: <b>la recarga pertenece a un arma, el sprite a un contexto,
 * el culatazo sale al disparar y el creativo no paga municion</b>.
 *
 * <h2>Por que lee archivos</h2>
 *
 * <p>{@code Arcabuz} nombra tipos de Minecraft en su firma, asi que no se puede cargar en una JVM
 * pelada. Misma linea que el resto de los guards de fuente del proyecto -- y la misma regla de
 * siempre al escribirlos: <b>pedir la llamada al ras y no el nombre de un metodo</b>.</p>
 */
class ArcabuzGuardsTest {

    private static final String ARCABUZ = "src/main/java/com/cortes/combat/special/Arcabuz.java";
    private static final String MUZZLE = "src/main/java/com/cortes/combat/special/ArcabuzMuzzle.java";
    private static final String MIXIN =
            "src/main/java/com/cortes/combat/mixin/ArcabuzGuiIconMixin.java";
    private static final String SHOT =
            "src/main/java/com/cortes/combat/client/ClientArcabuzShot.java";

    @Test
    @DisplayName("la recarga y la mecha quedan atadas al arma que las empezo")
    void theActionBelongsToAGun() {
        String code = strip(read(ARCABUZ));
        // **Dos anotaciones: una al encender la mecha y otra al empezar la recarga.** Son las dos
        // acciones que cobran ticks despues -- veinte la mecha, cincuenta la recarga -- y las dos
        // cobraban contra `getMainHandItem()` en el momento del cobro, no en el del apriete.
        assertEquals(2, count(code, "player.setData(ModAttachments.ARCABUZ_GUN.get(), player.getMainHandItem());"),
                "**las dos acciones en vuelo tienen que anotar contra que arma van.** Recargar un "
                        + "arcabuz, cambiar a otro y esperar terminaba con la Firestone puesta en el "
                        + "SEGUNDO y gastada del inventario del jugador -- porque cambiar de un "
                        + "arcabuz a otro no dispara el `shouldSnuff`, que solo pregunta si dejo de "
                        + "haber un arcabuz en la mano. Es el reporte del playtest: \"si recargas un "
                        + "arcabuz y pasas a uno sin recargar, este podra disparar como si fuera "
                        + "recargado\"");
        assertTrue(code.contains("if (described != heldNow) {"),
                "**la comparacion tiene que ser EXACTAMENTE esa, sin condiciones extra.** Anotar sin "
                        + "comparar no arregla nada, y cualquier condicion de mas -- como exigir que el "
                        + "anotado no sea vacio -- reabre el bug: tras la primera cancelacion el "
                        + "anotado queda vacio, el segundo cambio de arma ya no resetea, el estado "
                        + "hereda `loaded=true` del arma anterior y `resolve` encuentra "
                        + "`canLight()` verdadero sobre un arma sin recamara. **Dos armas cargadas de "
                        + "una sola recarga**");
        assertFalse(code.contains("ownerGun != net.minecraft.world.item.ItemStack.EMPTY"),
                "y la version condicionada del bug tiene que seguir fuera");
    }

    @Test
    @DisplayName("la asimetria de withChamber es una decision, y el llamador es quien la compensa")
    void theStickyLoadedIsCompensatedAtTheCallSite() {
        // **Las dos mitades del bug, ancladas por separado, porque cada una sola es correcta.**
        //
        // `withChamber` NO baja `loaded` a false, y esta bien que no lo haga: durante la combustion
        // la recamara ya se gasto del stack, y un record que releyera `false` cada tick vaciaria un
        // arma a punto de disparar. Lo que no puede pasar es que ese `loaded` pegajoso sobreviva a un
        // cambio de arma -- y eso se arregla en el llamador, no aca.
        String state = strip(read(
                "src/main/java/com/cortes/combat/state/ArcabuzState.java"));
        assertTrue(state.contains("if (!chamberOnStack) {")
                        && state.contains("return this;"),
                "**`withChamber` solo puede LLENAR.** Si alguien lo hace simetrico para \"arreglar\" "
                        + "esto, vacia el arma en cada uno de los veinte ticks que dura la mecha. El "
                        + "comentario que lo explica tiene que seguir ahi");
        String arcabuz = strip(read(ARCABUZ));
        assertTrue(arcabuz.contains("player.setData(ModAttachments.ARCABUZ.get(), ArcabuzState.EMPTY)"),
                "**y el llamador compensa vaciando el estado entero cuando el arma cambia.** Reinicia "
                        + "la recamara Y la accion en vuelo de una vez, que es lo que hace que el "
                        + "`loaded` pegajoso no pueda cruzar de un arcabuz a otro");
    }

    @Test
    @DisplayName("el sprite plano depende del CONTEXTO de dibujo, y no de si hay una pantalla abierta")
    void theFlatIconIsChosenByContext() {
        String mixin = strip(read(MIXIN));
        assertTrue(mixin.contains("ItemDisplayContext.GUI")
                        && mixin.contains("ItemDisplayContext.GROUND")
                        && mixin.contains("ItemDisplayContext.FIXED"),
                "**los tres contextos de vanilla**, que es el conjunto exacto que usa el tridente "
                        + "para lo mismo: en una ranura, tirado en el piso, y en un marco de item");
        // **Y `screen` no puede volver.** La hotbar se dibuja con `ItemDisplayContext.GUI` igual que
        // la ranura del inventario, asi que preguntar por la pantalla convertia la hotbar en sprite
        // al abrir el inventario y la devolvia al modelo al cerrarlo: el playtest lo describio como
        // "el modelo 3D y el sprite 2D se la pasan cambiando entre GUI y juego normal".
        assertFalse(mixin.contains("mc.screen"),
                "**la condicion que parpadeaba tiene que estar fuera.** `screen != null` es verdad "
                        + "para la hotbar cuando hay una pantalla abierta, que es literalmente el "
                        + "parpadeo reportado");
    }

    @Test
    @DisplayName("el disparo empuja al que dispara, y Mungus el doble")
    void theShotKicks() {
        String code = strip(read(ARCABUZ));
        // **La llamada al ras, y ANTES de las dos ramas que hacen `return`.** Ponerla al final del
        // metodo la dejaria sin correr en los dos casos que mas la necesitan.
        int call = code.indexOf("recoil(player, mungus);");
        int mungusBranch = code.indexOf("if (mungus > 0) {");
        assertTrue(call > 0, "el disparo no empuja a nadie");
        assertTrue(mungusBranch > call,
                "**el culatazo va antes de la rama de Mungus.** Mungus y Rey de las Moscas salen con "
                        + "`return`, asi que al final del metodo el empuje no correria justo en los "
                        + "dos tiros que mas tendrian que sentirse");
        String muzzle = strip(read(MUZZLE));
        assertTrue(muzzle.contains("RECOIL") && muzzle.contains("MUNGUS_RECOIL_FACTOR"),
                "y el doble de Mungus es una constante con nombre, no un `* 2` escrito en el sitio "
                        + "que empuja");
    }

    @Test
    @DisplayName("el creativo no paga municion, y solo el creativo")
    void creativeDoesNotPay() {
        String code = strip(read(ARCABUZ));
        assertEquals(2, count(code, "player.isCreative()"),
                "**las dos mitades del pago: no exigirla y no cobrarla.** Con una sola, el creativo "
                        + "recarga y no puede, o puede y le vacia el inventario");
        assertTrue(code.contains("found < 0 && !freeAmmo"),
                "y la puerta sigue siendo la misma para todos los demas modos de juego");
    }

    @Test
    @DisplayName("el humo de la mecha dice donde cayo y a que distancia del ojo")
    void theFuseSmokeIsMeasured() {
        String shot = strip(read(SHOT));
        // **La instrumentacion es la decision de esta ronda.** El reporte ("sale de en medio de la
        // pantalla") tiene dos causas que se ven identicas: la matriz de pose degenerada -- y
        // entonces el humo nace sobre la lente -- o el offset equivocado dentro del modelo. La
        // posicion sola no las separa; la distancia al ojo si.
        assertTrue(shot.contains("distOjo="),
                "sin la distancia al ojo el log sigue sin poder distinguir \"esta en la camara\" de "
                        + "\"esta en el arma\", que es lo unico que decide si hay que arreglar el "
                        + "marco o retunear tres numeros");
        assertTrue(shot.contains("matrix.m30()"),
                "y la traslacion cruda de la matriz es la prueba directa: deberia rondar "
                        + "(0,56, -0,52, -0,72) segun `ItemInHandRendererMixin`");
    }

    // ------------------------------------------------------------------ lectura

    private static int count(String haystack, String needle) {
        int found = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            found++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return found;
    }

    private static String strip(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static String read(String relative) {
        Path file = Path.of(relative.replace('/', java.io.File.separatorChar));
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("no se pudo leer " + file + ": " + e.getMessage(), e);
        }
    }
}
