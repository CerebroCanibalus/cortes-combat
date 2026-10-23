package com.cortes.combat.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The warhammer's damage type is one id in Java and three JSON files, and they have to agree.
 *
 * <p><b>Nothing about this can be caught by a compile.</b> The key compiles whether or not the
 * datapack file exists, and the tag files parse whether or not they name the type. The failure
 * shows up as a {@code MissingRegistryKeyException} thrown out of
 * {@code DamageSources#getHolderOrThrow} in the middle of a player's swing, which means it is only
 * ever found by playing.</p>
 *
 * <p>There is a second way these files can be wrong that is worse than missing: a
 * {@code "replace": true} in a tag extension <em>replaces</em> the tag it extends. Vanilla's own
 * {@code bypasses_armor} holds eighteen entries, and a file that replaces it would quietly delete
 * all of them for every player in the game. This mod has shipped exactly that mistake once
 * already, in {@code sounds.json}.</p>
 */
class DamageTypeDeclarationTest {

    private static final String ID = "cortescombat:warhammer_ignored_armour";

    /**
     * The second declared type, which exists to be recognised rather than to behave differently.
     *
     * <p>It is named here because the test that counts the declared types is what made adding a
     * second one a decision instead of an accident. The axe's type carries no tags at all: it does
     * not bypass armour and it does not bypass a shield, because a charged axe still gets reduced
     * by a diamond plate and breaks a guard by hand rather than by asking a tag to do it.</p>
     */
    private static final String AXE_ID = "cortescombat:axe_charged";

    private static final Path DATA = Path.of("src", "main", "resources", "data");
    private static final Path TYPE_FILE =
            DATA.resolve(Path.of("cortescombat", "damage_type", "warhammer_ignored_armour.json"));

    private static final Path AXE_FILE =
            DATA.resolve(Path.of("cortescombat", "damage_type", "axe_charged.json"));

    /** Read a file under the mod's data folder, or fail with the path in the message. */
    private static String read(Path path) {
        assertTrue(Files.isRegularFile(path), "missing: " + path
                + " -- a damage type is one id in Java and one file here, and only the id compiles");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static String tag(String name) {
        return read(DATA.resolve(Path.of("minecraft", "tags", "damage_type", name + ".json")));
    }

    /**
     * The name the Java side builds the key from, and the name the file is called, are one string
     * written twice, so they are compared.
     *
     * <p><b>By reading the source, and that is forced rather than chosen.</b> Nothing in
     * {@code src/test} is allowed to name a Minecraft type: {@code ResourceKey} is not on the test
     * compile classpath at all, and asking reflectively fails too, because loading
     * {@code ModDamageTypes} resolves {@code Player} and there is no Minecraft on the test
     * <em>runtime</em> classpath either. That is the same constraint that made {@code WeaponStats}
     * untestable and got its tables extracted into {@code WeaponTables}.</p>
     *
     * <p>So the one gap a JVM test can close here is closed by looking at the text: the literal
     * the key is built from has to be the literal the file is named. Every other check below reads
     * the shipped data.</p>
     */
    @Test
    @DisplayName("the name the key is built from is the name the file has")
    void theKeyMatchesTheFile() throws IOException {
        Path source = Path.of("src", "main", "java", "com", "cortes", "combat", "registry",
                "ModDamageTypes.java");
        assertTrue(Files.isRegularFile(source), "missing: " + source);

        String java = Files.readString(source, StandardCharsets.UTF_8);
        String literal = "\"warhammer_ignored_armour\"";
        // The factory moved to `platform.ResourceIds` so the version-coupled spelling lives in one
        // file per leg; the namespace is implied there and is still the mod id, so the comparison
        // this test exists for -- the literal against the file name -- is unchanged. (￣ω￣)
        assertTrue(java.contains("ResourceIds.rl(" + literal + ")"),
                "ModDamageTypes.java no longer builds the key from the mod id and the literal "
                        + literal + ". The datapack file is still called that, so the key and the "
                        + "file have drifted apart and the game throws on the first charged hit");

        // Two now, and both are named above. A third has to widen this list rather than
        // quietly arrive.
        try (var files = Files.list(DATA.resolve("cortescombat").resolve("damage_type"))) {
            var declared = files.map(p -> p.getFileName().toString().replace(".json", "")).sorted()
                    .toList();
            assertEquals(List.of("axe_charged", "warhammer_ignored_armour"), declared,
                    "the declared damage types. This test checks them by name, so a new one has to "
                            + "be added here to be checked at all");
        }
        assertTrue(Files.isRegularFile(AXE_FILE), "missing: " + AXE_FILE);
    }

    /**
     * The axe's type is in no tag at all, and that is the design.
     *
     * <p>Not {@code bypasses_armor}: a charged axe hit still gets reduced by a diamond plate. The
     * weapon takes the armour apart twice as fast and breaks a guard in one hit, and reaching past
     * armour is the warhammer's job. Tagging both the same way would have made the axe strictly
     * better than the warhammer at the one thing the warhammer is for.</p>
     *
     * <p>Not {@code bypasses_shield} either, for the same reason: the guard is emptied by hand so
     * the defender's stamina is spent, and a tag would have let the hit through for free.</p>
     */
    @Test
    @DisplayName("the axe's type bypasses nothing")
    void theAxeBypassesNothing() {
        String bypassesArmor = tag("bypasses_armor");
        assertTrue(bypassesArmor.contains(ID),
                "the warhammer's is in there, for the comparison");
        assertTrue(!bypassesArmor.contains(AXE_ID),
                "and the axe's is NOT. A charged axe keeps its armour: it wears the plate down "
                        + "twice as fast and breaks the guard by hand, which is a different "
                        + "weapon from one that ignores the plate entirely");

        String isPlayerAttack = tag("is_player_attack");
        assertTrue(isPlayerAttack.contains(AXE_ID),
                "but it is a player attack, so enchantments that fire on one and the kill credit "
                        + "and the death message all still work");
    }

    /** And it is a player attack with the same death message, exactly like the other one. */
    @Test
    @DisplayName("the axe's type reads as a player attack too")
    void theAxeReadsAsAPlayerAttack() {
        String json = read(AXE_FILE);
        assertTrue(json.contains("\"message_id\": \"player\""), json
                + "\n\nsame death message as the warhammer's and as vanilla's player_attack. A new "
                + "message id would put the raw key on the death screen for anyone whose language "
                + "pack does not have it yet");
        assertTrue(json.contains("\"scaling\": \"when_caused_by_living_non_player\""), json
                + "\n\nand the same scaling: it is a melee hit and it scales like one");
    }

    /**
     * It reads as a player attack, because that is all it is.
     *
     * <p>{@code message_id: "player"} resolves to {@code death.attack.player.player} -- "%1$s was
     * slain by %2$s" -- the same line a normal hit produces, so a warhammer kill needs no lang
     * entry and reads identically. A new message id would put the raw key on the death screen for
     * anyone whose language pack does not have it yet, which is the way a damage type announces
     * itself as new.</p>
     */
    @Test
    @DisplayName("it is a player attack with the same death message")
    void itSoundsLikeAPlayerAttack() {
        String json = read(TYPE_FILE);
        assertTrue(json.contains("\"message_id\": \"player\""), json
                + "\n\nmessage_id has to stay 'player'. It is what resolves the death message to "
                + "death.attack.player.player, the same line vanilla's own player_attack uses");
        assertTrue(json.contains("\"scaling\": \"when_caused_by_living_non_player\""), json
                + "\n\nsame scaling as minecraft:player_attack. This is a melee hit and it must "
                + "scale the way a melee hit scales");
    }

    /**
     * The tag it belongs to, and the other one it belongs to.
     *
     * <p>{@code bypasses_armor} is the whole point: it is what makes {@code LivingEntity} skip
     * {@code CombatRules.getDamageAfterAbsorb}, and without it the flag has nowhere to land.
     * {@code is_player_attack} costs nothing and says what is true -- vanilla reads it in exactly
     * one place, the Overkill advancement, which also demands a mace in the mainhand, so this can
     * never complete by accident -- but a third-party datapack or mod asking "was that a player
     * attack?" gets the true answer.</p>
     */
    @Test
    @DisplayName("it is in bypasses_armor and in is_player_attack")
    void itIsInTheTwoTags() {
        assertTrue(tag("bypasses_armor").contains(ID),
                "bypasses_armor is what actually ignores the armour. Without it the flag is "
                        + "computed and thrown away, which is where it lived for two rounds");
        assertTrue(tag("is_player_attack").contains(ID),
                "it is a player attack. Vanilla reads this tag in one place and cannot fire it, "
                        + "but it is the answer other code asks for");
    }

    /**
     * Neither tag replaces the one it extends.
     *
     * <p>This is the assertion that is really about damage. Vanilla's {@code bypasses_armor}
     * carries eighteen damage types -- fire, drowning, the void, starvation -- and {@code replace:
     * true} would delete all eighteen for every world. The failure is invisible in singleplayer
     * testing because nothing else in this mod uses those types.</p>
     */
    @Test
    @DisplayName("neither tag replaces the vanilla one")
    void neitherTagReplaces() {
        for (String name : new String[]{"bypasses_armor", "is_player_attack"}) {
            String json = tag(name);
            assertTrue(json.contains("\"replace\": false"), name + ".json is:\n" + json
                    + "\n\nA tag extension with replace:true deletes the tag it extends. Vanilla's "
                    + "bypasses_armor holds eighteen entries and every one of them would stop "
                    + "ignoring armour for the whole game");
            assertTrue(!json.contains("\"replace\": true"), name + ".json replaces its tag");
        }
    }
}