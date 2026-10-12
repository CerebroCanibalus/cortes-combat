package com.cortes.combat.mixin;

import com.cortes.combat.enchant.CombatEnchantRules;
import com.cortes.combat.registry.ModEffects;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trabazon: el nivel dos saca la carrera ademas del salto.
 *
 * <h2>Por que hay un mixin y no un evento</h2>
 *
 * <p>Porque <b>no existe</b>. {@code setSprinting(false)} desde el servidor no alcanza y la razon
 * esta en quien decide: la carrera la elige el <b>cliente</b>, cada tick, en
 * {@code LocalPlayer#aiStep}, a partir de la tecla y del impulso. El servidor solo se entera por
 * paquete. Asi que el servidor puede decir "no estas corriendo" todo lo que quiera y el cliente
 * vuelve a correr en el tick siguiente -- que es exactamente el sintoma que reporto el playtest:
 * <i>"no esta dejando sin correr, solo sin saltar"</i>.</p>
 *
 * <p>Y no hay evento de NeoForge para esto: se busco en las fuentes de 21.1.250 y no hay ninguna
 * clase con {@code Sprint} en el nombre. La jerarquia del proyecto dice mixin cuando no hay
 * evento, y esta es la puerta real: {@code canStartSprinting()} es <b>privada</b> y es la unica
 * pregunta que el cliente se hace antes de arrancar a correr.</p>
 *
 * <h2>Las dos mitades, y por que hacen falta las dos</h2>
 *
 * <p>Este mixin <b>impide arrancar</b>. {@code TrabazonHandler#onTick} <b>corta la que ya estaba
 * andando</b>, porque un cuerpo que ya venia corriendo cuando lo golpearon no pasa por aca -- su
 * carrera no se reinicia, sigue. Con las dos, el golpe frena al que corria y no lo deja volver a
 * arrancar; con una sola, queda la mitad del encantamiento.</p>
 *
 * <p>El nivel se pregunta igual que en el handler, por el <b>amplificador mas uno</b>, para que las
 * dos mitades no puedan discrepar sobre que nivel es cual.</p>
 */
@Mixin(LocalPlayer.class)
public abstract class TrabazonSprintMixin {

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void cortescombat$trabazonBlocksSprint(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        MobEffectInstance effect = self.getEffect(ModEffects.TRABAZON);
        if (effect != null
                && CombatEnchantRules.trabazonBlocksSprint(effect.getAmplifier() + 1)) {
            cir.setReturnValue(false);
        }
    }
}
