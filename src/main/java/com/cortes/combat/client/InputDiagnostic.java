package com.cortes.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Why the attack button is or is not doing anything, all of it on one line.
 *
 * <p>This exists because of a playtest report that the log could not answer. The player said the
 * click broke: no attacking, no block breaking, no interacting, from one moment on, with nothing
 * to explain it. Fifteen minutes of server-side trace showed every press arriving normally and
 * then stopping dead, and the log had <b>not one client line</b> -- the only line that says
 * <em>which gate refused</em> is behind {@code /cc trace}, which that session did not have.</p>
 *
 * <p>So the diagnosis had to be a guess, and the guess would have been wrong: the code has seven
 * separate places that can refuse a press, six of them silent by design, and which one was stuck
 * is not something a screenshot or a server log can tell you.</p>
 *
 * <p>Every gate is printed with the value it is reading and the verdict it produces. This is the
 * third time this project has had to add a command that reads the same values the running code
 * reads rather than recomputing them -- after {@code /cc cone} and {@code /cc hud} -- and the
 * reason is always the same: <b>a diagnostic that recomputes the arithmetic is a second copy of
 * it, and one that lies is worse than none.</b> Every number below is the field the gate actually
 * consults, in the order {@code SwingInputHandler.press} consults them.</p>
 *
 * <p>Read it as: the first gate whose verdict is the wrong one is the bug. One line is not a gate and says so.</p>
 */
public final class InputDiagnostic {

    private InputDiagnostic() {
    }

    public static String report() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        StringBuilder out = new StringBuilder();

        if (player == null) {
            return "sin jugador";
        }

        ItemStack hand = player.getMainHandItem();

        out.append("=== PUERTAS DEL CLICK IZQUIERDO (en orden) ===\n");

        // Gate 1: no player, spectator, or already using an item.
        gate(out, "1 jugador/spectator/usandoItem",
                "isUsingItem=" + player.isUsingItem() + " spectator=" + player.isSpectator(),
                player.isUsingItem());

        // Gate 2: a screen is open, so the click belongs to the screen.
        // The one gate that is not really ours at all, and the one a player cannot see.
        String screen = mc.screen == null ? "ninguna" : mc.screen.getClass().getSimpleName();
        gate(out, "2 pantalla abierta", "screen=" + screen, mc.screen != null);

        // Gate 3: ranged weapons are left to vanilla entirely.
        boolean ranged = CombatAim.isRanged(hand);
        gate(out, "3 ranged (lo maneja vanilla)", "item=" + hand.getItem(), !ranged);

        // Gate 4: the guard. Silent refusal, and the one that eats the button hardest.
        boolean guard = GuardInputHandler.predictedHolding();
        gate(out, "4 cubierto", "guardPredicted=" + guard, !guard);

        // Gate 5: the hard cooldown, and it is two numbers.
        boolean latchOk = SwingInputHandler.readyAtTickPassed();
        gate(out, "5a latch local (readyAtTick)", "readyAtTick=" + SwingInputHandler.readyAtTickValue(),
                latchOk);
        boolean syncOk = SwingInputHandler.syncedCooldownReady();
        gate(out, "5b cooldown del servidor", "known=" + SwingInputHandler.cooldownKnown()
                        + " ratio=" + SwingInputHandler.syncedCooldownRatio(),
                syncOk);

        // Gate 6: nothing in the cone and the crosshair on a block means "mining", not a swing.
        boolean target = ranged
                || !ConeTargeting.findTargetForHeldItem(player).isEmpty()
                || !SwingInputHandler.onBlock();
        gate(out, "6 con objetivo o minando", "onBlock=" + SwingInputHandler.onBlock(), target);

        // Not a gate, and printed as one it would be the worst line on the sheet: whether the
        // held item can charge is a fact about the weapon, and nothing refuses a press over it any
        // more. It was gate 6 here until `press` stopped handing the click to vanilla for a
        // weapon that cannot charge, and it stayed on this list afterwards, printing a red arrow
        // beside a rule that no longer existed. The rule that saved it is the whole reason this
        // class exists: read the fields the gates read, and a gate that is gone leaves the sheet.
        var stats = com.cortes.combat.attack.WeaponStats.of(hand);
        out.append("  --   el item carga            perfil=").append(stats.profile())
                .append(" carga=").append(
                        com.cortes.combat.attack.WeaponStats.canCharge(hand))
                .append("   (dato, no puerta)\n");

        out.append("\n=== ESTADO DE LOS DOS BOTONES ===\n");
        out.append("swing abierto=").append(SwingInputHandler.holding())
                .append("  ticks=").append(SwingInputHandler.predictedTicks())
                .append("  claim=").append(SwingInputHandler.claim())
                .append("\n");
        out.append("boton fisico izq=").append(SwingInputHandler.buttonDownForDebug())
                .append("  der=").append(GuardInputHandler.buttonDownForDebug())
                .append("\n");
        out.append("guardia predicha=").append(GuardInputHandler.predictedHolding())
                .append("  aguante=").append(ClientCombatState.guardStamina())
                .append("/").append(ClientCombatState.guardMax())
                .append("\n");
        out.append("cooldown servidor known=").append(SwingInputHandler.cooldownKnown())
                .append(" ratio=").append(SwingInputHandler.syncedCooldownRatio())
                .append("\n");

        // The one-line verdict, because reading seven verdicts under pressure is how you miss it.
        String verdict = firstBlockedGate(mc, player);
        out.append("\nVEREDICTO: ").append(verdict).append("\n");

        return out.toString();
    }

    /**
     * The first gate that is blocking, named.
     *
     * <p>Recomputed from the same fields the gates read, so it cannot disagree with them -- the
     * same rule the report itself follows. It is a convenience, not the source: the seven verdicts
     * above are, and if the two ever differ the seven are right.</p>
     */
    private static String firstBlockedGate(Minecraft mc, LocalPlayer player) {
        if (player.isUsingItem()) {
            return "BLOQUEADO por isUsingItem (esta usando un item; el release no llego?)";
        }
        if (mc.screen != null) {
            return "BLOQUEADO por pantalla abierta: " + mc.screen.getClass().getSimpleName();
        }
        if (GuardInputHandler.predictedHolding()) {
            return "BLOQUEADO por la guardia predicha (boton derecho pegado?)";
        }
        if (!SwingInputHandler.readyAtTickPassed()) {
            return "BLOQUEADO por el latch local del cooldown";
        }
        if (!SwingInputHandler.syncedCooldownReady()) {
            return "BLOQUEADO por el cooldown del servidor (ratio < 1 y no vuelve?)";
        }
        return "libre: ningun gate bloquea el click";
    }

    private static void gate(StringBuilder out, String name, String value, boolean passed) {
        out.append(passed ? "  OK   " : "  >>>  ").append(name)
                .append("   ").append(value).append("\n");
    }
}
