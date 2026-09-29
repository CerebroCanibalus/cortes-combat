package com.cortes.combat.client;

/**
 * Client-side debug switches, driven by the {@code /cc} commands.
 *
 * <p>Plain statics on the client are acceptable here: they are display flags for a single
 * local player, they carry no gameplay meaning, and the server never reads them. The
 * gameplay state that *is* authoritative lives in attachments.</p>
 */
public final class ClientDebugFlags {

    private static boolean coneVisible;
    private static boolean hudVisible = true;

    private ClientDebugFlags() {
    }

    public static boolean coneVisible() {
        return coneVisible;
    }

    public static void setConeVisible(boolean visible) {
        coneVisible = visible;
    }

    public static boolean hudVisible() {
        return hudVisible;
    }

    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }
}
