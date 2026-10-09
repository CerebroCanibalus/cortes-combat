package com.cortes.combat.weaponpack;

import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.weaponpack.WeaponPackSpec.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las diez cosas que la capa 2 puede prometer, afirmadas en JVM pelada.
 *
 * <p>Lo que no se testea acá -- y no debe intentarse -- es el parseo de SnakeYAML (fase 2) y el
 * puente a componentes (fase 3): esto es la función pura que decide la precedencia, y la
 * precedencia es exactamente lo que un packmate no puede adivinar.
 */
class PackResolverTest {

    private static WeaponPackSpec pack(String origin, Entry... entries) {
        return new WeaponPackSpec(origin, List.of(entries), Map.of());
    }

    private static Entry byTag(String tag, String archetype) {
        return new Entry(null, null, tag, archetype, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static Entry byId(String id, String archetype) {
        return new Entry(null, id, null, archetype, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** Los nueve built-in cargan la identidad del tipo, no la cultura. */
    @Test
    void theNineBuiltinsCarryTheirIdentity() {
        Map<String, ArchetypeSpec> arch = ArchetypeSpec.builtins();
        assertEquals(9, arch.size(), "nueve tipos, ni uno más");

        assertEquals(SpecialType.LUNGE, arch.get("rapier").special(),
                "la ropera es su arquetipo: lunge es la identidad");
        assertEquals(SpecialType.NONE, arch.get("spear").special(),
                "**la lanza NO hereda el lunge de la ropera** -- placeholder de versiones futuras");
        assertEquals(SpecialType.PARRY, arch.get("sabre").special(), "el sable parryea");
        assertEquals(SpecialType.SWEEP, arch.get("polearm").special(), "el asta barre");

        ArchetypeSpec hammer = arch.get("hammer");
        assertEquals(WeaponProfile.WARHAMMER, hammer.profile(), "molde warhammer");
        assertEquals(Boolean.FALSE, hammer.canCharge(),
                "**la carga de la nuestra no es gratis**: el martillo ajeno no la hereda");

        // Los limpios: mecánica especial no gratis, y números heredados del molde (null).
        for (String type : List.of("dagger", "sword", "mace", "axe")) {
            assertEquals(SpecialType.NONE, arch.get(type).special(), type + " sin especial");
            assertNull(arch.get(type).canCharge(), type + " hereda la carga del molde");
        }
        assertNull(hammer.slash(), "los assets del arquetipo son null: el genérico es del sistema");
    }

    /** Un match por id no lo tapa un match por tag que esté más abajo: especificidad manda. */
    @Test
    void specificityBeatsOrder() {
        Entry tagEntry = new Entry(null, null, "mymod:knives", "dagger",
                null, new WeaponPackSpec.Stats(3.0F, null, null, null, null, null, null, null),
                null, null, null, null, null, null, null, null, null);
        Entry idEntry = new Entry(null, "mymod:ruby", null, "dagger",
                null, new WeaponPackSpec.Stats(7.0F, null, null, null, null, null, null, null),
                null, null, null, null, null, null, null, null, null);

        ResolvedWeapon resolved = PackResolver.resolve("mymod:ruby", List.of("mymod:knives"),
                List.of(pack("a.yaml", idEntry, tagEntry)));
        assertNotNull(resolved, "matcheó");
        assertEquals(7.0F, resolved.damage(),
                "el id está ARRIBA del tag en el archivo y aun así gana: specificity > orden");
        assertEquals("id=mymod:ruby", resolved.source());
    }

    /** A igualdad de especificidad, gana el que está más abajo -- y el pack posterior manda. */
    @Test
    void onATieTheLowerOneWins() {
        Entry first = new Entry(null, null, "t", "dagger", null,
                new WeaponPackSpec.Stats(1.0F, null, null, null, null, null, null, null),
                null, null, null, null, null, null, null, null, null);
        Entry second = new Entry(null, null, "t", "sword", null,
                new WeaponPackSpec.Stats(2.0F, null, null, null, null, null, null, null),
                null, null, null, null, null, null, null, null, null);

        ResolvedWeapon sameFile = PackResolver.resolve("x", List.of("t"),
                List.of(pack("a.yaml", first, second)));
        assertEquals("sword", sameFile.profile().id(), "más abajo gana dentro del archivo");

        WeaponPackSpec early = pack("a.yaml", first);
        WeaponPackSpec late = pack("b.yaml", second);
        ResolvedWeapon acrossPacks = PackResolver.resolve("x", List.of("t"), List.of(early, late));
        assertEquals("sword", acrossPacks.profile().id(),
                "el archivo alfabéticamente posterior pisa al anterior");
    }

    /** Lo omitido cae al arquetipo, y lo explícito pisa al arquetipo. */
    @Test
    void omittedFallsToTheArchetypeAndExplicitWins() {
        ResolvedWeapon inherited = PackResolver.resolve("x", List.of("t"),
                List.of(pack("a.yaml", byTag("t", "sabre"))));
        assertEquals(WeaponProfile.SABRE, inherited.profile(), "perfil del arquetipo");
        assertEquals(SpecialType.PARRY, inherited.special(), "especial del arquetipo");
        assertNull(inherited.damage(), "números heredados del molde: nada escrito, nada copiado");

        Entry explicit = new Entry(null, "x", null, "sabre", "sword",
                null, "lunge", null, null, null, null, null, null, null, null);
        ResolvedWeapon overrode = PackResolver.resolve("x", List.of(),
                List.of(pack("a.yaml", explicit)));
        assertEquals(WeaponProfile.SWORD, overrode.profile(), "lo explícito pisa al arquetipo");
        assertEquals(SpecialType.LUNGE, overrode.special(), "y también el especial");
    }

    /** {@code profile: none} es una respuesta, no una omisión: el ítem queda fuera del combate. */
    @Test
    void profileNoneIsAnAnswer() {
        Entry entry = new Entry(null, "x", null, "sword", "none",
                null, null, null, null, null, null, null, null, null, null);
        ResolvedWeapon resolved = PackResolver.resolve("x", List.of(),
                List.of(pack("a.yaml", entry)));
        assertEquals(WeaponProfile.NONE, resolved.profile(),
                "una exclusión explícita no cae al tag molde -- es la capa 2 contestando no");
    }

    /** Arquetipo desconocido: warning con archivo, y el perfil cae a la capa de abajo. */
    @Test
    void unknownArchetypeWarnsAndFallsThrough() {
        ResolvedWeapon resolved = PackResolver.resolve("x", List.of("t"),
                List.of(pack("malo.yaml", byTag("t", "katana"))));
        assertNull(resolved.profile(), "sin arquetipo resuelto no hay perfil de la capa 2");
        assertEquals(1, resolved.warnings().size());
        assertTrue(resolved.warnings().get(0).contains("malo.yaml"),
                "el warning nombra el archivo: sin eso nadie lo arregla");
        assertTrue(resolved.warnings().get(0).contains("katana"));
    }

    /** Creates es identidad de nivel id: matchea por el id que va a crear. */
    @Test
    void createsMatchesByItsId() {
        Entry entry = new Entry("mymod:ruby_knife", null, null, "dagger", null,
                null, null, null, null, null, null, null, null, null, null);
        ResolvedWeapon resolved = PackResolver.resolve("mymod:ruby_knife", List.of(),
                List.of(pack("a.yaml", entry)));
        assertNotNull(resolved, "el ítem que el pack crea es su propio match");
        assertEquals(2, entry.specificity());
        assertNull(PackResolver.resolve("otro:id", List.of(), List.of(pack("a.yaml", entry))),
                "y no matchea a cualquier cosa");
    }

    /** El tag se acepta con y sin '#', y lo que no está en ningún pack no devuelve nada. */
    @Test
    void tagWithOrWithoutHashAndNoMatchIsNull() {
        Entry withHash = new Entry(null, null, "#cortescombat:category/hammers", null, null,
                null, null, null, null, null, null, null, null, null, null);
        ResolvedWeapon resolved = PackResolver.resolve("x",
                List.of("cortescombat:category/hammers"), List.of(pack("a.yaml", withHash)));
        assertNotNull(resolved, "con almohadilla también matchea");
        assertNull(resolved.profile(), "sin arquetipo ni profile: la capa 2 sólo dijo 'es mía'");

        assertNull(PackResolver.resolve("nadie", List.of(), List.of(pack("a.yaml", withHash))),
                "sin match no hay opinión: siguen tag molde y vanilla");
    }

    /** Los arquetipos locales del pack pisan a los built-in por nombre (orden de merge). */
    @Test
    void packLocalArchetypesOverrideBuiltins() {
        ArchetypeSpec custom = new ArchetypeSpec(WeaponProfile.MACE, SpecialType.SLAM, null,
                "mymod:slash/mace.png", null);
        WeaponPackSpec pack = new WeaponPackSpec("local.yaml", List.of(byTag("t", "hammer")),
                Map.of("hammer", custom));
        ResolvedWeapon resolved = PackResolver.resolve("x", List.of("t"), List.of(pack));
        assertEquals(WeaponProfile.MACE, resolved.profile(),
                "un pack puede redefinir un arquetipo built-in");
        assertEquals(SpecialType.SLAM, resolved.special());
        assertEquals("mymod:slash/mace.png", resolved.slash());
    }

    /** Un id desconocido en profile/special es warning y caída, no un crash de parseo. */
    @Test
    void unknownIdsWarnInsteadOfThrowing() {
        Entry entry = new Entry(null, "x", null, null, "greatsword",
                null, "katana", null, null, null, null, null, null, null, null);
        ResolvedWeapon resolved = PackResolver.resolve("x", List.of(),
                List.of(pack("a.yaml", entry)));
        assertNull(resolved.profile());
        assertNull(resolved.special());
        assertEquals(2, resolved.warnings().size(), "una advertencia por cada id que no existe");
        assertFalse(resolved.warnings().isEmpty());
    }
}
