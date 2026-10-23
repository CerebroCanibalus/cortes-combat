package com.cortes.combat.net;

import com.cortes.combat.CortesCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: toggle the debug overlays. Development only.
 *
 * <p>Commands run on the server, so it cannot reach a client-side static directly. The flag
 * travels as a packet like everything else.</p>
 *
 * <p><b>That sentence was written here first, and the trace flag was then added as a plain static
 * anyway.</b> The symptom was a playtest log with two hundred and ninety-nine server lines and
 * not one client line, which looked exactly like "the client never produced anything" and was
 * really "the command flipped a boolean in the other classloader". In singleplayer the integrated
 * server loads its own copy of every mod class, so a static is <b>two</b> statics, and the one
 * the command touches is the one nobody was reading.</p>
 *
 * @param trace whether the per-swing trace is on. Travels here rather than living on the server
 *              because the lines that matter most are drawn on the client, so a flag that never
 *              reaches the client is not a flag
 * @param dump  ask the client to print its HUD geometry and nothing else change. Not a flag: it
 *              is a one-shot request, and it is here rather than in a payload of its own because
 *              it is the same trip and the same reason -- the numbers are the client's
 * @param input ask the client to print every gate that can refuse an attack press. Also a
 *              one-shot, and it exists because a playtest reported the click dying completely
 *              and the only line that says <em>which gate</em> refused is a client line this
 *              session did not have. Eight silent gates and no way to tell them apart is the
 *              diagnosis, not the log
 */
public record DebugTogglePayload(boolean cone, boolean hud, boolean trace, boolean dump,
                                 boolean input)
        implements CustomPacketPayload {

    public static final Type<DebugTogglePayload> TYPE = new Type<>(
            CortesCombat.rl("debug_toggle"));

    // Five components: StreamCodec.composite supports arities 1 to 6, and this is inside it.
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugTogglePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DebugTogglePayload::cone,
                    ByteBufCodecs.BOOL, DebugTogglePayload::hud,
                    ByteBufCodecs.BOOL, DebugTogglePayload::trace,
                    ByteBufCodecs.BOOL, DebugTogglePayload::dump,
                    ByteBufCodecs.BOOL, DebugTogglePayload::input,
                    DebugTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
