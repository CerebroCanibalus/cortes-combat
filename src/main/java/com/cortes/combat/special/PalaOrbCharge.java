package com.cortes.combat.special;

import com.cortes.combat.enchant.CombatEnchantments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * La carga de la pala antimaterial: sostener, y soltar cargado suelta el orbe.
 *
 * <h2>Por que una carga y no un boton</h2>
 *
 * <p>El orbe es lo mas fuerte del catalogo -- catorce de dano en un radio de cuatro, mas un tiron de
 * casi dos bloques -- y un boton que lo suelta en el press no tiene contrajuego: se aprieta y ya.
 * Los cuarenta ticks son <b>el aviso</b>: el enemigo ve el arma levantarse y temblar, y tiene
 * cuarenta ticks para salir del radio. El precio no cambio (sigue el cooldown de seis segundos); lo
 * que se agrego es el derecho a correrse.</p>
 *
 * <h2>El sonido es de vanilla, y es el de la mesa de encantamientos</h2>
 *
 * <p>El playtest pidio "un sonido de carga magico del juego vanilla". Se prefiere uno del juego a
 * inventar un archivo: suena como lo que el jugador ya asocia con magia, no hay que grabar nada, y
 * el que corta la carga <b>ya lo reconoce</b>. Se reproduce en bucle mientras carga y se <b>para</b>
 * cuando la carga se corta -- que es la mitad del aviso, porque un sonido que sigue sonando despues
 * de soltar dice lo contrario de lo que paso.</p>
 */
public final class PalaOrbCharge implements SpecialCharge {

    @Override
    public int ticks() {
        return com.cortes.combat.enchant.CombatEnchantRules.PALA_ANTIMATERIAL_CHARGE_TICKS;
    }

    @Override
    public boolean canCharge(ItemStack held, Level level) {
        return CombatEnchantments.level(level.registryAccess(), held,
                CombatEnchantments.PALA_ANTIMATERIAL) > 0;
    }

    @Override
    public SoundEvent chargingSound() {
        return SoundEvents.ENCHANTMENT_TABLE_USE;
    }

    @Override
    public void release(ServerPlayer player, ServerLevel level) {
        SpecialHandler.launchAntimatter(player, level);
    }
}
