package com.cortes.combat.data;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.item.WeaponFamily;
import com.cortes.combat.registry.ModArmor;
import com.cortes.combat.registry.ModCreativeTabs;
import com.cortes.combat.registry.ModItems;
import com.cortes.combat.registry.ModTools;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/**
 * Two language files: {@code es_es}, which is the language the design is written in, and
 * {@code en_us}, which is the language Minecraft runs in by default.
 *
 * <h2>Why there are two, and why it was one for months</h2>
 *
 * <p>This class used to generate only Spanish, and the reason written in its comment was that a
 * missing {@code en_us} entry falls back to the key -- so an English client would show
 * {@code item.cortescombat.iron_ropera} in the hotbar, "which is worse than a rough
 * translation".</p>
 *
 * <p>That reasoning stops one step short of where it needed to go. A missing {@code en_us} does
 * not make a rough translation look worse; it makes the mod show its own internals to <b>every
 * player who did not open the options menu and find the language</b>, and {@code en_us} is what
 * Minecraft is in until somebody changes it. Shipping a Spanish mod without Spanish being the
 * default is not a translation decision, it is a broken build.</p>
 *
 * <p>There was also a second rule in here saying English should come from a translator rather than
 * from this class. <b>Nobody asked for that rule</b> -- it was written here as if it were a
 * decision of the project's, and then the next reader treated it as one. A rule nobody chose is
 * worse than no rule, because it survives: it looks like a wall and there is no one behind it.</p>
 *
 * <h2>Two providers, not one provider with a switch inside</h2>
 *
 * <p>Each instance writes one file, so the two languages are two independent methods rather than
 * one with thirty {@code if (locale.equals("es_es"))}. They cannot half-generate each other, and
 * a string added to the Spanish side and forgotten on the English side is a missing line rather
 * than a line in the wrong language.</p>
 *
 * <p>{@code getName()} is not overridden because {@code LanguageProvider} already puts the locale
 * in it: {@code "Languages: es_es for mod: cortescombat"}. Two instances do not collide, which
 * was worth reading rather than assuming -- a collision would be the "Duplicate provider" error
 * this project has already paid once.</p>
 */
public class CortesLanguageProvider extends LanguageProvider {

    private final boolean spanish;

    public CortesLanguageProvider(PackOutput output, String locale) {
        super(output, CortesCombat.MODID, locale);
        // The one place the two files are told apart. Every string below branches on this, so a
        // third language is a new constructor argument and two more methods, not a refactor of
        // thirty conditionals.
        //
        // **Y mira el PREFIJO y no el codigo exacto.** El juego trae siete variantes de espanol y
        // ninguna es un alias de otra: la pila de idiomas es [seleccionado, en_us], asi que un
        // jugador en es_mx no cae a es_es -- cae a INGLES. Con `"es_es".equals(locale)` las otras
        // seis variantes escribian el archivo en ingles con el nombre en espanol, que es la peor
        // forma de fallar: el archivo existe y dice lo contrario.
        this.spanish = locale.startsWith("es_");
    }

    @Override
    public void addTranslations() {
        // Names come from the catalogue rather than from here, so a name a player reads in the
        // hotbar and the string a test asserts are the same two calls. The language difference is
        // the family and the material's own accessor, because English puts the material in front
        // ("Iron Ropera") and Spanish puts it behind with a "de".
        for (WeaponFamily family : WeaponFamily.values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                addItem(ModItems.weapon(family.itemName(material)),
                        spanish ? family.displayName(material) : family.displayNameEn(material));
            }
        }
        addItem(ModItems.COPPER_NUGGET, spanish ? "Pepita de Cobre" : "Copper Nugget");

        // **Three names are identical in both files on purpose.** Ropera, Arcabuz and Shotel are
        // the project's own, and WeaponFamily's comment says why. "Firestone" is a fourth: it is
        // an English word already, and DESIGN.md calls the item that way, so there is no Spanish
        // version of it to choose.
        addItem(ModItems.ARCABUZ, "Arcabuz");
        addItem(ModItems.FIRESTONE, "Firestone");

        // **The block, and `block.<ns>.<path>` rather than `item.`.** That is what a BlockItem's
        // description id is built from, so a line under `item.cortescombat.firestone_block` would
        // sit in the file and never be read. The name keeps "Firestone" in both languages for the
        // reason the item does: it is an English word already.
        add("block." + CortesCombat.MODID + ".firestone_block",
                spanish ? "Bloque de Firestone" : "Firestone Block");

        // **The nether reactor and the dead star.** The reactor is a block, so its key is
        // `block.<ns>.<path>`; the dead star is an item and goes through `addItem`. The dead star
        // is registered ahead of its use in phase two, and it is named now so it does not show its
        // key if it is ever held.
        add("block." + CortesCombat.MODID + ".nether_reactor",
                spanish ? "Reactor del Nether" : "Nether Reactor");
        addItem(ModItems.DEAD_STAR, spanish ? "Estrella Muerta" : "Dead Star");

        // **The copper set, which is neither a weapon nor an ingredient.** Five tools and four
        // armour pieces -- vanilla's gear set that 1.21.1 does not ship. Spanish puts the material
        // behind with "de" and English in front, exactly like the catalogue's thirty, and for the
        // same grammatical reason.
        addItem(ModTools.COPPER_SWORD, spanish ? "Espada de Cobre" : "Copper Sword");
        addItem(ModTools.COPPER_PICKAXE, spanish ? "Pico de Cobre" : "Copper Pickaxe");
        addItem(ModTools.COPPER_AXE, spanish ? "Hacha de Cobre" : "Copper Axe");
        addItem(ModTools.COPPER_SHOVEL, spanish ? "Pala de Cobre" : "Copper Shovel");
        addItem(ModTools.COPPER_HOE, spanish ? "Azada de Cobre" : "Copper Hoe");
        addItem(ModArmor.COPPER_HELMET, spanish ? "Casco de Cobre" : "Copper Helmet");
        addItem(ModArmor.COPPER_CHESTPLATE, spanish ? "Pechera de Cobre" : "Copper Chestplate");
        addItem(ModArmor.COPPER_LEGGINGS, spanish ? "Grebas de Cobre" : "Copper Leggings");
        addItem(ModArmor.COPPER_BOOTS, spanish ? "Botas de Cobre" : "Copper Boots");

        // **The two copper effects.** The key is `effect.<namespace>.<path>`, which is what
        // `MobEffect#getDescriptionId` builds and what the HUD shows beside the icon.
        add("effect.cortescombat.antiseptic", spanish ? "Antiséptico" : "Antiseptic");
        add("effect.cortescombat.purification", spanish ? "Purificación" : "Purification");
        add("effect.cortescombat.revelacion", spanish ? "Revelación" : "Revelation");
    // El de la embestida tambien se dibuja en el HUD durante un segundo, y sin esta linea se
    // lee `cortescombat.lunge` -- el id crudo, que es como se pierde una textura que ya existe.
    add("effect.cortescombat.lunge", spanish ? "Embate" : "Lunge");
    // El de la embestida tambien se dibuja en el HUD durante un segundo, y sin esta linea se
    // lee `cortescombat.lunge` -- el id crudo, que es como se pierde una textura que ya existe.
    // **Los siete de combate, y todos hacen falta.** Sin su `effect.cortescombat.<id>` el HUD
    // muestra el id crudo -- `cortescombat.anti_healing` -- que es la forma mas fea de perder una
    // textura que ya esta dibujada. Y van juntos en vez de repartidos: cada encantamiento nuevo
    // que deje un efecto sin idioma va a fallar en el mismo sitio.
    add("effect.cortescombat.anti_healing", spanish ? "Anti-curación" : "Anti-Healing");
    add("effect.cortescombat.bleeding", spanish ? "Sangrado" : "Bleeding");
    add("effect.cortescombat.quebranto", spanish ? "Quebranto" : "Sunder");
    add("effect.cortescombat.vampiric", spanish ? "Vampírico" : "Vampiric");
    add("effect.cortescombat.drained", spanish ? "Drenado" : "Drained");
    add("effect.cortescombat.moco", spanish ? "Moco" : "Sniffles");
    add("effect.cortescombat.trabazon", spanish ? "Trabazón" : "Lockdown");

        if (spanish) {
            addSpanish();
        } else {
            addEnglish();
        }
    // **Gold's nine, and the mark they all feed.** `Toque de Midas`, `Eco de Oro`, `Peso Pluma`
    // and `Robo de Vida` are the names the design fixed, so they stay as they are written; the
    // descriptive ones are translated, because they are words and not brands.
    add("enchantment.cortescombat.midas_touch", spanish ? "Toque de Midas" : "Midas Touch");
    add("enchantment.cortescombat.midas_tool", spanish ? "Toque de Midas" : "Midas Touch");
    add("enchantment.cortescombat.gold_echo", spanish ? "Eco de Oro" : "Echo of Gold");
    add("enchantment.cortescombat.life_steal", spanish ? "Robo de Vida" : "Life Steal");
    add("enchantment.cortescombat.wind_walker", spanish ? "Velocidad del Viento" : "Wind Speed");
    add("enchantment.cortescombat.miner_frenzy", spanish ? "Fiebre del Minero" : "Miner's Fever");
    add("enchantment.cortescombat.golden_hands", spanish ? "Manos Doradas" : "Golden Hands");
    add("enchantment.cortescombat.golden_harvest", spanish ? "Cosecha Dorada" : "Golden Harvest");
    add("enchantment.cortescombat.feather_weight", spanish ? "Peso Pluma" : "Feather Weight");
    add("enchantment.cortescombat.phantom_strike", spanish ? "Golpe Fantasma" : "Phantom Strike");
    add("enchantment.cortescombat.phantom_block", spanish ? "Golpe Fantasma" : "Phantom Strike");
    add("effect.cortescombat.death_marked", spanish ? "Marcado de muerte" : "Marked for Death");

    // **La identidad de las armas nuevas, y son descriptivas: se traducen las dos.** Al revés que
    // los nombres de las armas icónicas (Ropera, Alabarda, Arcabuz quedan en español porque son
    // marcas), un encantamiento que dice lo que hace tiene que decirlo en el idioma del que lo lee.
    add("enchantment.cortescombat.steel_reflex", spanish ? "Reflejo de Acero" : "Steel Reflex");
    add("enchantment.cortescombat.audacity", spanish ? "Audacia" : "Audacity");
    add("enchantment.cortescombat.adrenalina", spanish ? "Adrenalina" : "Adrenaline");
    add("enchantment.cortescombat.rhythm_of_iron", spanish ? "Ritmo de Hierro" : "Iron Rhythm");
    add("enchantment.cortescombat.riposte", spanish ? "Contragolpe" : "Riposte");
    add("enchantment.cortescombat.dry_powder", spanish ? "Pólvora Seca" : "Dry Powder");
    add("enchantment.cortescombat.punta_fina", spanish ? "Punta Fina" : "Fine Point");
    add("enchantment.cortescombat.filo_astillado", spanish ? "Filo Astillado" : "Chipped Edge");
    add("enchantment.cortescombat.apunalar", spanish ? "Apuñalar" : "Backstab");
    add("enchantment.cortescombat.sanguijuela", spanish ? "Sanguijuela" : "Leech");
    add("enchantment.cortescombat.aventurero", spanish ? "Aventurero" : "Adventurer");
    add("enchantment.cortescombat.culata", spanish ? "Culata" : "Buttstroke");
    add("enchantment.cortescombat.estocada", spanish ? "Estocada Perforante" : "Piercing Thrust");
    add("enchantment.cortescombat.segundo_aliento", spanish ? "Segundo Aliento" : "Second Wind");
    add("enchantment.cortescombat.guardia_firme", spanish ? "Guardia Firme" : "Steady Guard");
    add("enchantment.cortescombat.cicatrizador", spanish ? "Cicatrizador" : "Bruiser");
    add("enchantment.cortescombat.destripamiento", spanish ? "Destripamiento" : "Gutting");
    add("enchantment.cortescombat.quebranto", spanish ? "Quebranto" : "Sunder");
    add("enchantment.cortescombat.desvio_vampirico", spanish ? "Desvío Vampírico" : "Vampiric Deflection");
    add("enchantment.cortescombat.mecha_corta", spanish ? "Mecha Corta" : "Short Fuse");
    add("enchantment.cortescombat.barredor", spanish ? "Barredor" : "Sweeper");
    add("enchantment.cortescombat.astilladora", spanish ? "Astilladora" : "Splinterer");
    add("enchantment.cortescombat.mella", spanish ? "Mella" : "Notch");
    add("enchantment.cortescombat.segunda_pasada", spanish ? "Segunda Pasada" : "Second Pass");
    add("enchantment.cortescombat.santa_muerte", spanish ? "Santa Muerte" : "Holy Death");
    add("enchantment.cortescombat.inercia", spanish ? "Inercia" : "Inertia");

    add("enchantment.cortescombat.ensarte", spanish ? "Ensarte" : "Skewer");
    add("enchantment.cortescombat.paliza", spanish ? "Paliza" : "Pummeling");
    add("enchantment.cortescombat.retirada", spanish ? "Retirada" : "Withdrawal");
    add("enchantment.cortescombat.sentencia", spanish ? "Sentencia" : "Sentence");
    add("enchantment.cortescombat.trabazon", spanish ? "Trabazón" : "Lockdown");
    add("enchantment.cortescombat.gripa", spanish ? "Gripa" : "Sniffles");
    add("enchantment.cortescombat.corte_gemelo", spanish ? "Corte Gemelo" : "Twin Cut");
    add("enchantment.cortescombat.capacitor", spanish ? "Capacitor" : "Capacitor");
    add("enchantment.cortescombat.rey_de_las_moscas", spanish ? "Rey de las Moscas" : "Fly Lord");
    add("enchantment.cortescombat.pala_antimaterial", spanish ? "Pala Antimaterial" : "Antimatter Shovel");
    add("enchantment.cortescombat.mungus", spanish ? "Mungus" : "Mungus");

    // **Y las descripciones, que es la compatibilidad con Enchantment Descriptions.** El mod de
    // Darkhax (295M de descargas, hay version NeoForge 1.21.1) lee `enchantment.<modid>.<enchid>.desc`
    // del mapa de localizacion y la muestra en el tooltip del item. **Vanilla 1.21.1 no tiene ese
    // mecanismo**: verificado contra las fuentes, el campo `"description"` del JSON **es** el nombre
    // (lo usa `Enchantment.getFullname`), y las 28 claves `.desc` que trae vanilla son de **items**
    // (banner patterns y discos), no de encantamientos. O sea que esto es dato puro: sin la linea,
    // el mod no tiene nada que mostrar, y el jugador lee un encantamiento sin saber que hace.
    //
    // **Y la clave lleva el id, no el nombre.** El juego resuelve la clave contra el registro, y un
    // nombre cambiado (o un id renombrado) romperia la descripcion en silencio: el mod no encuentra
    // la clave y no dice por que. Por eso el test camina `CombatEnchantRules.IDS` y `GOLD_IDS`.
    //
    // **Y el texto adivina, no anuncia.** Un numero leido se olvida; una pista que falta el numero
    // es un jugador que va a medirlo, que es justamente lo que el sistema de este mod pide. (￣ε￣＠)
    add("enchantment.cortescombat.midas_touch.desc",
            spanish ? "Los que caen dejan cobre; a veces mas."
                    : "The fallen leave copper; sometimes more.");
    add("enchantment.cortescombat.gold_echo.desc",
            spanish ? "Tu marca es una invitacion que todos atienden."
                    : "Your mark is an invitation everyone answers.");
    add("enchantment.cortescombat.life_steal.desc",
            spanish ? "Lo que le quitas a otros, te quedas un poco."
                    : "What you take from others, you keep a little.");
    add("enchantment.cortescombat.wind_walker.desc",
            spanish ? "El viento te lleva; cuanto mas alto, mas lejos."
                    : "The wind carries you; the higher, the further.");
    add("enchantment.cortescombat.feather_weight.desc",
            spanish ? "La mano pesa menos; el golpe, mas lento."
                    : "The hand weighs less; the swing, slower.");
    add("enchantment.cortescombat.phantom_strike.desc",
            spanish ? "La armadura rival cuenta menos de lo que dice."
                    : "Enemy armour counts for less than it says.");
    add("enchantment.cortescombat.midas_tool.desc",
            spanish ? "La mina entrega su tesoro banado en oro."
                    : "The mine delivers its treasure bathed in gold.");
    add("enchantment.cortescombat.miner_frenzy.desc",
            spanish ? "La piedra cede mas rapido de lo que deberia."
                    : "The stone yields faster than it should.");
    add("enchantment.cortescombat.golden_hands.desc",
            spanish ? "Lo que la pala toca ya es tuyo."
                    : "What the shovel touches is already yours.");
    add("enchantment.cortescombat.golden_harvest.desc",
            spanish ? "Lo cortado vuelve a la tierra; a veces doble."
                    : "What is cut returns to the earth; sometimes twice.");
    add("enchantment.cortescombat.phantom_block.desc",
            spanish ? "A veces el bloque decide dar el doble."
                    : "Sometimes the block decides to give twice.");

    add("enchantment.cortescombat.steel_reflex.desc",
            spanish ? "Un acero que aprende de cada golpe que recibe."
                    : "A steel that learns from every blow it turns aside.");
    add("enchantment.cortescombat.audacity.desc",
            spanish ? "Cada cuerpo que atraviesas te devuelve el aire."
                    : "Every body you pass through gives your breath back.");
    add("enchantment.cortescombat.adrenalina.desc",
            spanish ? "El dolor te hace mas rapido; la segunda vez, mas fuerte."
                    : "Pain makes you quicker; the second time, stronger.");
    add("enchantment.cortescombat.rhythm_of_iron.desc",
            spanish ? "La cadena se alarga mientras el ritmo no se rompa."
                    : "The chain stretches while the rhythm holds.");
    add("enchantment.cortescombat.riposte.desc",
            spanish ? "Lo que te devuelven tras cubrirte pesa menos."
                    : "What comes back after you cover weighs less.");
    add("enchantment.cortescombat.dry_powder.desc",
            spanish ? "La segunda carga espera menos que la primera."
                    : "The second loading waits less than the first.");
    add("enchantment.cortescombat.punta_fina.desc",
            spanish ? "Cuanto mas cerca esta, mas hondo muerde."
                    : "The closer they are, the deeper it bites.");
    add("enchantment.cortescombat.filo_astillado.desc",
            spanish ? "La armadura ajena pesa en contra de su dueno."
                    : "Foreign armour weighs against its owner.");
    add("enchantment.cortescombat.apunalar.desc",
            spanish ? "Lo que no te ve venir le duele el doble."
                    : "What doesn't see it coming hurts twice as much.");
    add("enchantment.cortescombat.sanguijuela.desc",
            spanish ? "Su sangre termina en tus venas."
                    : "Their blood ends up in your veins.");
    add("enchantment.cortescombat.aventurero.desc",
            spanish ? "Los que caen dejan experiencia de sobra."
                    : "The fallen leave experience to spare.");
    add("enchantment.cortescombat.culata.desc",
            spanish ? "La bayoneta no empuja: arrolla."
                    : "The bayonet doesn't shove: it sweeps.");
    add("enchantment.cortescombat.estocada.desc",
            spanish ? "Lo que el lunge atraviesa no se esconde tras armadura."
                    : "What the lunge pierces does not hide behind armour.");
    add("enchantment.cortescombat.segundo_aliento.desc",
            spanish ? "Cuando tu guardia cae, tu brazo se aviva."
                    : "When your guard falls, your arm quickens.");
    add("enchantment.cortescombat.guardia_firme.desc",
            spanish ? "El primer aguante de cada rato sale gratis."
                    : "The first stand of every while is free.");
    add("enchantment.cortescombat.cicatrizador.desc",
            spanish ? "Las curas llegan mas flojas de lo prometido."
                    : "Healing arrives weaker than promised.");
    add("enchantment.cortescombat.destripamiento.desc",
            spanish ? "El cargado no mata: deja abierto."
                    : "The charged hit doesn't kill: it leaves you open.");
    add("enchantment.cortescombat.quebranto.desc",
            spanish ? "Cada golpe le arranca un poco de coraza."
                    : "Each hit peels a little of his shell.");
    add("enchantment.cortescombat.desvio_vampirico.desc",
            spanish ? "Cada parry te hace un poco mas grande."
                    : "Each parry makes you a little greater.");
    add("enchantment.cortescombat.mecha_corta.desc",
            spanish ? "La mecha se consume mas aprisa."
                    : "The fuse burns down faster.");
    add("enchantment.cortescombat.barredor.desc",
            spanish ? "El brazo barre mas de lo que parece."
                    : "The arm sweeps more than it seems.");
    add("enchantment.cortescombat.astilladora.desc",
            spanish ? "Bloquear contra este filo cuesta el doble."
                    : "Blocking against this edge costs double.");
    add("enchantment.cortescombat.mella.desc",
            spanish ? "Su armadura se gasta mas rapido que la tuya."
                    : "Their armour wears faster than yours.");
    add("enchantment.cortescombat.segunda_pasada.desc",
            spanish ? "Un barrido que no mato te devuelve el brazo."
                    : "A sweep that killed nobody gives your arm back.");
    add("enchantment.cortescombat.santa_muerte.desc",
            spanish ? "Lo que mata se queda un rato mas."
                    : "What kills lingers a while longer.");
    add("enchantment.cortescombat.inercia.desc",
            spanish ? "La cadena no empuja: levanta."
                    : "The streak doesn't shove: it lifts.");
    add("enchantment.cortescombat.ensarte.desc",
            spanish ? "La estocada no se detiene en el primero."
                    : "The thrust does not stop at the first.");
    add("enchantment.cortescombat.paliza.desc",
            spanish ? "Cerrar una serie tiene su recompensa."
                    : "Closing a streak has its reward.");
    add("enchantment.cortescombat.sentencia.desc",
            spanish ? "Cinco golpes de cadena y el cuerpo se rinde."
                    : "Five streak hits and the body gives out.");
    add("enchantment.cortescombat.trabazon.desc",
            spanish ? "Un cargado no te deja las piernas."
                    : "A charged hit takes your legs from you.");
    add("enchantment.cortescombat.gripa.desc",
            spanish ? "Cada golpe deja al rival mas mocoso."
                    : "Every hit leaves the target snifflier.");
    add("enchantment.cortescombat.corte_gemelo.desc",
            spanish ? "El corte no viene solo."
                    : "The cut never comes alone.");
    add("enchantment.cortescombat.capacitor.desc",
            spanish ? "Lo que te disparan vuelve con intereses."
                    : "What they fire at you returns with interest.");
    add("enchantment.cortescombat.rey_de_las_moscas.desc",
            spanish ? "El disparo no termina donde cae."
                    : "The shot doesn't end where it lands.");
    add("enchantment.cortescombat.retirada.desc",
            spanish ? "Agachate y el lunge hara lo inesperado."
                    : "Sneak, and the lunge does the unexpected.");
    add("enchantment.cortescombat.pala_antimaterial.desc",
            spanish ? "Un orbe que llama a los suyos y no perdona."
                    : "An orb that calls its own and spares none.");
    add("enchantment.cortescombat.mungus.desc",
            spanish ? "Diez Firestones por un tiro que abre la montana."
                    : "Ten Firestones for one shot that opens the mountain.");
    }

    /**
     * The Spanish strings, and the ones a player reads in this project's own language.
     *
     * <p>The recipe book description is one line rather than thirty: thirty entries that differ
     * only in the item name are not thirty translations, they are one translation typed thirty
     * times, and a translator asked to do that eventually leaves one of them in the other
     * language.</p>
     */

    private void addSpanish() {
        add("advancements.cortescombat.weapon", "Hecho a mano.");
        add("advancements.cortescombat.arcabuz",
                "Una caña, un mechero, un dispensador y madera. La Firestone es lo caro.");

        // %s and nothing else. TranslatableContents rejects every other format at render time with
        // "Unsupported format", so "%.1f" here would be a line that does not appear rather than a
        // line that is formatted. The numbers arrive pre-rounded as strings.
        add("tooltip.cortescombat.damage", "%s de daño");
        add("tooltip.cortescombat.charged_damage", "%s de daño cargado");
        add("tooltip.cortescombat.attack_speed", "%s golpes por segundo");
        add("tooltip.cortescombat.range", "%s de alcance");
        add("tooltip.cortescombat.durability", "Durabilidad: %s");
        // El empuje, base y cargado, igual que el daño tiene dos lineas. Ver WeaponTooltip.
        add("tooltip.cortescombat.knockback", "Empuje: %s");
        add("tooltip.cortescombat.charged_knockback", "Empuje cargado: %s");
        add("tooltip.cortescombat.targets", "Objetivos: %s");

        // **La mecanica de cada arma, para las siete que tienen una.** La prosa es prosa; los
        // numeros dentro de ella salen de MaceFall, ParryState, StreakState, ArcabuzState y
        // ArcabuzStats. Ver WeaponTooltip.mechanicLines.
        add("tooltip.cortescombat.mechanic.mace",
                "El daño es la caída: hasta %s, cayendo %s bloques. Pegar cancela el daño de caída");
        add("tooltip.cortescombat.mechanic.trident",
                "Click derecho lo lanza; cargado, trae al objetivo hacia ti");
        add("tooltip.cortescombat.mechanic.sabre",
                "Click derecho: parry. Ventana de %s s, cooldown de %s s");
        add("tooltip.cortescombat.mechanic.halberd",
                "Click derecho: barre todo el arco, y gasta el mismo cooldown que un golpe");
        add("tooltip.cortescombat.mechanic.ropera",
                "Click derecho: embestida hacia adelante, y atraviesa a varios");
        add("tooltip.cortescombat.mechanic.warhammer",
                "Escala con cada golpe seguido; la racha dura %s s");
        add("tooltip.cortescombat.mechanic.arcabuz",
                "Gasta una Firestone del inventario: click derecho recarga, click izquierdo dispara");
        add("tooltip.cortescombat.arcabuz.reload", "Recarga: %s s");
        add("tooltip.cortescombat.arcabuz.shot", "Disparo: %s de daño");

        // **La categoría, en azul, como la de vanilla.** Va primera en el bloque porque es la que
        // enmarca todas las de abajo: quien lee "Arma corta" sabe cómo leer el alcance que viene
        // después. Ver WeaponCategory para la taxonomía y por qué se clasifica por capacidad.
        add("tooltip.cortescombat.category.ranged", "Arma a distancia");
        add("tooltip.cortescombat.category.long", "Arma larga");
        add("tooltip.cortescombat.category.heavy", "Arma pesada");
        add("tooltip.cortescombat.category.short", "Arma corta");
        add("tooltip.cortescombat.category.medium", "Arma media");
        add("tooltip.cortescombat.category.natural", "Arma natural");
        add("tooltip.cortescombat.category.tool", "Herramienta");

        add("tooltip.cortescombat.size.very_small", "Radio muy pequeño");
        add("tooltip.cortescombat.size.small", "Radio pequeño");
        add("tooltip.cortescombat.size.medium", "Radio medio");
        add("tooltip.cortescombat.size.wide", "Radio ancho");
        add("tooltip.cortescombat.size.very_wide", "Radio muy ancho");
        add("tooltip.cortescombat.size.maximum", "Radio máximo");

        add("tooltip.cortescombat.special.multi_hit",
                "Click derecho: golpea a todo lo que hay en el cono");
        add("tooltip.cortescombat.special.parry",
                "Click derecho: parry, devuelve el golpe que le pegues");
        add("tooltip.cortescombat.special.slam",
                "Click derecho: golpe en el suelo");
        add("tooltip.cortescombat.special.shove",
                "Click derecho: empujón corto");
        add("tooltip.cortescombat.special.lunge",
                "Click derecho: embestida hacia adelante");
        add("tooltip.cortescombat.special.sweep",
                "Click derecho: barrido por el suelo");
        add("tooltip.cortescombat.special.throw",
                "Click derecho: lanzamiento");
        add("tooltip.cortescombat.special.fusee",
                "Click derecho: gasta una Firestone, y un segundo después quemo todo el cono");

        // By key and not by holder: there is no add(TagKey) overload for a creative tab and the
        // holder is not a TagKey, so the key is spelled out. It is the translation key, which is
        // also what the tab's title() looks up at runtime -- so these two cannot disagree.
        add(ModCreativeTabs.COMBAT_KEY, "Cortés Combat");
    }

    /**
     * The English strings, and the three weapon names are deliberately <b>not</b> in here.
     *
     * <p>"Iron Ropera", "Iron Alabarda" and "Iron Arcabuz" read in English and still name the
     * same weapons, because those three are the project's iconic names and translating them would
     * give the two languages two different sets of weapons. "Iron Dagger" and "Iron War Hammer"
     * <em>are</em> translated, because those two are descriptive rather than named. "Iron
     * Shotel" is the shotel.</p>
     */
    private void addEnglish() {
        add("advancements.cortescombat.weapon", "Made by hand.");
        add("advancements.cortescombat.arcabuz",
                "A bamboo, a firer, a dispenser and some wood. The Firestone is the expensive part.");

        add("tooltip.cortescombat.damage", "%s Damage");
        add("tooltip.cortescombat.charged_damage", "%s Charged Damage");
        add("tooltip.cortescombat.attack_speed", "%s Attacks per Second");
        add("tooltip.cortescombat.range", "%s Reach");
        add("tooltip.cortescombat.durability", "Durability: %s");
        add("tooltip.cortescombat.knockback", "Knockback: %s");
        add("tooltip.cortescombat.charged_knockback", "Charged Knockback: %s");
        add("tooltip.cortescombat.targets", "Targets: %s");

        // The mechanic of each weapon, for the seven that have one. See the Spanish block.
        add("tooltip.cortescombat.mechanic.mace",
                "The damage is the fall: up to %s, falling %s blocks. Landing a hit cancels the fall");
        add("tooltip.cortescombat.mechanic.trident",
                "Right click throws it; charged, it pulls the target in");
        add("tooltip.cortescombat.mechanic.sabre",
                "Right click: parry. A %s s window, a %s s cooldown");
        add("tooltip.cortescombat.mechanic.halberd",
                "Right click: sweeps the whole arc, and costs the same cooldown as a hit");
        add("tooltip.cortescombat.mechanic.ropera",
                "Right click: forward lunge, and it passes through several");
        add("tooltip.cortescombat.mechanic.warhammer",
                "Escalates with each consecutive hit; the streak lasts %s s");
        add("tooltip.cortescombat.mechanic.arcabuz",
                "Spends a Firestone from the inventory: right click reloads, left click fires");
        add("tooltip.cortescombat.arcabuz.reload", "Reload: %s s");
        add("tooltip.cortescombat.arcabuz.shot", "Shot: %s damage");

        // The category, in vanilla's own blue. See the Spanish block for why it goes first.
        add("tooltip.cortescombat.category.ranged", "Ranged Weapon");
        add("tooltip.cortescombat.category.long", "Long Weapon");
        add("tooltip.cortescombat.category.heavy", "Heavy Weapon");
        add("tooltip.cortescombat.category.short", "Short Weapon");
        add("tooltip.cortescombat.category.medium", "Medium Weapon");
        add("tooltip.cortescombat.category.natural", "Natural Weapon");
        add("tooltip.cortescombat.category.tool", "Tool");

        add("tooltip.cortescombat.size.very_small", "Very Small Radius");
        add("tooltip.cortescombat.size.small", "Small Radius");
        add("tooltip.cortescombat.size.medium", "Medium Radius");
        add("tooltip.cortescombat.size.wide", "Wide Radius");
        add("tooltip.cortescombat.size.very_wide", "Very Wide Radius");
        add("tooltip.cortescombat.size.maximum", "Maximum Radius");

        add("tooltip.cortescombat.special.multi_hit",
                "Right Click: hits everything in the cone");
        add("tooltip.cortescombat.special.parry",
                "Right Click: parry, returns the hit you take");
        add("tooltip.cortescombat.special.slam", "Right Click: ground slam");
        add("tooltip.cortescombat.special.shove", "Right Click: short shove");
        add("tooltip.cortescombat.special.lunge", "Right Click: forward lunge");
        add("tooltip.cortescombat.special.sweep", "Right Click: ground sweep");
        add("tooltip.cortescombat.special.throw", "Right Click: throw");
        add("tooltip.cortescombat.special.fusee",
                "Right Click: spends a Firestone, and a second later burns the whole cone");

        add(ModCreativeTabs.COMBAT_KEY, "Cortés Combat");
    }
}
