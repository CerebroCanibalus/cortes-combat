package com.cortes.combat.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The phase two wires, held by reading the files that carry them.
 *
 * <h2>Why these are textual guards</h2>
 *
 * <p><b>Every one of them is a connection that fails silently when cut.</b> A blast scheduler that
 * is written but never registered carves nothing, with a clean build and a clean log. A client-only
 * reference that is not hidden behind a never-invoked method takes a dedicated server down at
 * verification with a {@code NoClassDefFoundError}. A {@code saveAdditional} that forgets the
 * overload gives the client a camera that never shakes. None of these can be asserted by running the
 * game cheaply; all of them can be asserted by reading the source the game will load.</p>
 *
 * <p>{@link ReactorWiringTest} holds phase one's wires; this is the same idea for the second half,
 * kept separate so a phase one failure and a phase two failure do not read as one.</p>
 */
class ReactorPhaseTwoWiringTest {

    private static final Path BE = src("block", "NetherReactorBlockEntity.java");
    private static final Path BLAST = src("block", "ReactorBlast.java");
    private static final Path BLOCK = src("block", "NetherReactorBlock.java");
    private static final Path COMMAND = src("command", "CortesCommand.java");
    private static final Path CLIENT_SHAKE = src("client", "ReactorShake.java");
    private static final Path CLIENT_ENTRY = src("client", "CortesCombatClient.java");
    private static final Path MAIN = Path.of("src", "main", "java", "com", "cortes", "combat",
            "CortesCombat.java");

    private static Path src(String pkg, String file) {
        return Path.of("src", "main", "java", "com", "cortes", "combat", pkg, file);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path + ": " + e.getMessage(), e);
        }
    }

    private static int count(String haystack, String needle) {
        int hits = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            hits++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return hits;
    }

    @Test
    @DisplayName("the star crater is a staged sphere, budgeted, and never a single explode")
    void theStarCraterIsStaged() {
        String be = read(BE);
        String blast = read(BLAST);

        assertTrue(be.contains("ReactorBlast.request(server, pos, ReactorOverload.STAR_DESTRUCTION_RADIUS)"),
                "**the nether star has to hand its crater to the staged scheduler.** A raw "
                        + "level.explode of the nominal power is hundreds of thousands of ray steps in "
                        + "one tick and the watchdog kills it; the design and the measurement both live "
                        + "in ReactorOverload");
        assertFalse(blast.contains("explode("),
                "**the scheduler must not explode anything.** It removes blocks, and the reactor is "
                        + "already gone before it starts, so there is no blast to re-catch it and no "
                        + "chain: a second explosion here is the recursion the firestone block paid for");
        assertTrue(blast.contains("ChunkStatus.FULL"),
                "an unloaded chunk has to be skipped, or a crater at the simulation edge generates "
                        + "chunks in order to destroy them");
        assertTrue(blast.contains("hasOnlyAir"),
                "an all-air section has to be skipped whole, or an air-filled crater is still walked "
                        + "cell by cell");
        assertTrue(blast.contains("defaultDestroyTime() < 0.0F"),
                "unbreakable blocks -- bedrock, barriers, portal frames -- have to be left standing, "
                        + "which is what the game itself does for an explosion");
        assertTrue(blast.contains("ReactorOverload.BLAST_POSITIONS_PER_TICK")
                        && blast.contains("ReactorOverload.BLAST_REMOVALS_PER_TICK"),
                "the two budgets have to be the ones the pure class can be tested against, not "
                        + "numbers typed into the scheduler");
    }

    @Test
    @DisplayName("the blast scheduler is registered on the server tick")
    void theBlastIsRegistered() {
        String main = read(MAIN);
        assertTrue(main.contains("ReactorBlast::onServerTick"),
                "**a scheduler that is never ticked carves nothing.** The registration is the wire: "
                        + "the class compiles, the build is green, and a nether star explosion simply "
                        + "never happens");
    }

    @Test
    @DisplayName("the shake is the camera event, and the event is subscribed")
    void theShakeIsTheCameraEvent() {
        String shake = read(CLIENT_SHAKE);
        String entry = read(CLIENT_ENTRY);

        assertTrue(shake.contains("ViewportEvent.ComputeCameraAngles"),
                "**the shake has to use the supported camera event.** Firing from Camera.setup, it "
                        + "needs no mixin and no access transformer; a mixin here would be surface for "
                        + "nothing");
        assertTrue(shake.contains("event.setRoll(") && shake.contains("event.setPitch("),
                "and it has to write the angles back, or the handler runs every frame and moves nothing");
        assertTrue(entry.contains("ReactorShake.class"),
                "**a listener that is not registered is a class with a method nobody calls.** The "
                        + "camera event is on the game bus, and this is the line that subscribes it");
    }

    @Test
    @DisplayName("no client class is referenced from a method a server invokes")
    void theClientReferenceIsBehindAClientOnlyMethod() {
        String be = read(BE);
        int publish = be.indexOf("private void publishShake()");
        int firstUse = be.indexOf("ReactorShake");
        assertTrue(publish >= 0 && firstUse > publish,
                "**the client reference has to live inside a method a dedicated server never calls.** "
                        + "A method that runs is verified, and verifying a reference to a client class "
                        + "without Minecraft on the classpath is a NoClassDefFoundError at startup");
        assertTrue(be.contains("private void forgotOnTheClient()"),
                "the removal side needs the same treatment, or unloading a chunk takes the server down");
        assertTrue(count(be, "com.cortes.combat.client.ReactorShake") == 2,
                "and the reference belongs in exactly those two methods, and nowhere else");
    }

    @Test
    @DisplayName("the overload is saved and synced, and water is the star's way out")
    void theOverloadIsPersistedAndCancelledWithWater() {
        String be = read(BE);
        String block = read(BLOCK);

        assertTrue(be.contains("tag.putString(\"overload\""),
                "**the overload has to ride in the update tag.** The client has no refresh path; if "
                        + "it is not saved, the camera never learns there is a star counting down");
        assertTrue(be.contains("readKind("),
                "and reading it back has to tolerate an unknown name from an older save rather than "
                        + "throwing in loadAdditional");
        assertTrue(block.contains("Items.WATER_BUCKET") && block.contains("cancelStar"),
                "the water bucket is the star's only deliberate exit, and it must be handled in the "
                        + "block's own use before vanilla's water placement gets the click");
        assertTrue(be.contains("ModItems.DEAD_STAR.get()"),
                "and the cancel has to drop the spent star, or the only thing the player gets back is "
                        + "an empty bucket");
        assertTrue(be.contains("placeItemBackInInventory(new ItemStack(Items.BUCKET))"),
                "and return the empty bucket for the one poured");
        assertTrue(block.contains("Blocks.WATER"),
                "water that arrives on its own cancels too, not only a bucket poured on purpose");
    }

    @Test
    @DisplayName("the debug command can reach every part of the overload")
    void theDebugCommandExists() {
        String command = read(COMMAND);
        assertTrue(command.contains("Commands.literal(\"reactor\")"),
                "the reactor subcommand is what makes a one-hour clock testable at all");
        for (String hook : new String[]{"forceOverload", "setCharge", "setDebugActive", "addClock"}) {
            assertTrue(command.contains(hook),
                    "**/cc reactor has to reach " + hook + ".** A debug command that cannot force the "
                            + "state it exists to test is a debug command that lies");
        }
    }
}
