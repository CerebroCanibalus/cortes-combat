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
 * The wires the reactor hangs on, held by reading the files that carry them.
 *
 * <h2>Why these are textual guards</h2>
 *
 * <p><b>Every one of them is a connection that fails silently when it is cut.</b> A mixin that is
 * not listed in {@code cortescombat.mixins.json} is never applied -- the class compiles, the build
 * is green, and the reactor's wireless power simply does not exist, with nothing in the log. A light
 * override without {@code hasDynamicLightEmission} compiles and the light ignores the charge. A
 * sweep without the all-air skip compiles and is slow. None of these can be asserted by running the
 * game cheaply, and all of them can be asserted by reading the source the game will load.</p>
 *
 * <p><b>The mixin's placement is the one that matters most.</b> {@code SignalGetter} is common --
 * both sides implement it -- so the mixin belongs in the {@code mixins} list and not in
 * {@code client}. In the client list it would load only on the client, and a dedicated server would
 * have a reactor that does nothing. The order check pins the list, not just the name.</p>
 */
class ReactorWiringTest {

    private static final Path MIXINS =
            Path.of("src", "main", "resources", "cortescombat.mixins.json");
    private static final Path MIXIN_SRC = Path.of("src", "main", "java", "com", "cortes", "combat",
            "mixin", "SignalGetterMixin.java");
    private static final Path BLOCK = Path.of("src", "main", "java", "com", "cortes", "combat",
            "block", "NetherReactorBlock.java");
    private static final Path BLOCK_ENTITY = Path.of("src", "main", "java", "com", "cortes", "combat",
            "block", "NetherReactorBlockEntity.java");
    private static final Path SCAN = Path.of("src", "main", "java", "com", "cortes", "combat",
            "block", "ReactorScan.java");
    private static final Path POWERABLE_TAG = Path.of("src", "generated", "resources", "data",
            "cortescombat", "tags", "block", "reactor_powerable.json");
    private static final Path MODEL_PROVIDER = Path.of("src", "main", "java", "com", "cortes",
            "combat", "data", "CortesModelProvider.java");

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path + ": " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("the wire mixin is registered as a common mixin, not a client one")
    void theSignalMixinIsCommon() {
        String json = read(MIXINS);
        int common = json.indexOf("\"mixins\"");
        int client = json.indexOf("\"client\"");
        int name = json.indexOf("RedStoneWireBlockMixin");

        assertTrue(common >= 0 && client >= 0, "cortescombat.mixins.json lost a list: " + json);
        assertTrue(name >= 0, "**RedStoneWireBlockMixin is not in cortescombat.mixins.json at "
                + "all.** A mixin that is not listed is never applied: the class compiles, the build "
                + "is green, and every line in the world keeps its decay with nothing in the log");
        assertTrue(common < name && name < client,
                "RedStoneWireBlockMixin sits outside the common list. The wire is computed on the "
                        + "server and read by the renderer on the client, so the mixin belongs in "
                        + "`mixins`, before `client`");
    }

    @Test
    @DisplayName("the wired region is built, read, registered and annotated with a verified ordinal")
    void theWiredRegionIsWired() {
        String main = read(Path.of("src", "main", "java", "com", "cortes", "combat",
                "CortesCombat.java"));
        assertTrue(main.contains("ReactorScan::onBlockBreak"),
                "**breaking a wire has to rebuild the region**, or a line that was severed keeps "
                        + "drawing free strength until the next state change");
        assertTrue(main.contains("ReactorNetwork.clear()"),
                "**and the region has to die with the world.** It is static and keyed by dimension, "
                        + "so a world that closed would hand its wires' immunity to decay to the next "
                        + "one loaded in this process");

        String be = read(BLOCK_ENTITY);
        assertTrue(be.contains("ReactorNetwork.rebuild(server)"),
                "**refreshState is the moment the network changes**: it is where the reactor learns "
                        + "it is lit, and membership is a pure function of which reactors are lit");

        String scan = read(SCAN);
        assertTrue(scan.contains("ReactorNetwork.rebuild(server)"),
                "a wire placed inside the region has to join it at once, and only a rebuild can know "
                        + "that -- connectivity is a question about the whole line");

        String mixins = read(MIXINS);
        assertTrue(mixins.contains("\"RedStoneWireBlockMixin\""),
                "**the wire mixin is not listed, so it never applies**: the class compiles, the build "
                        + "is green, and every line in the world keeps its decay with nothing in the "
                        + "log");

        String wire = read(Path.of("src", "main", "java", "com", "cortes", "combat", "mixin",
                "RedStoneWireBlockMixin.java"));
        assertTrue(wire.contains("ordinal = 3"),
                "**the ordinal is the guard against a guess.** javap -c counts four Math.max in "
                        + "calculateTargetStrength: three in the neighbour loop and the return one "
                        + "last. Redirecting a loop call would add a phantom unit of strength per "
                        + "neighbour and quietly break signal maths");
        assertTrue(wire.contains("Ljava/lang/Math;max(II)I"),
                "and the redirect has to name the exact descriptor -- OWNER;name(desc), one "
                        + "semicolon after the owner and none after the name. A stray one parses the "
                        + "method name as `max;` and the game refuses to start with "
                        + "`InvalidMemberDescriptorException: Invalid name`, which is a crash no test "
                        + "would have caught: the string below has to be the one that actually "
                        + "works, not the one that is merely present");
        assertTrue(wire.contains("ReactorNetwork.begin(") && wire.contains("ReactorNetwork.end()"),
                "**head and return have to bracket the call**: the position lives in a static only "
                        + "between them, because the compiled wire class has no LocalVariableTable "
                        + "and locals capture would have thrown at startup");
    }

    @Test
    @DisplayName("the radio field is gone: the reactor feeds wire, it does not answer for the air")
    void theRadioFieldIsGone() {
        assertFalse(Files.exists(MIXIN_SRC),
                "**SignalGetterMixin has to be deleted, not merely unlisted.** The redesign powers "
                        + "through the wire -- the reactor is a source, and `RedStoneWireBlockMixin` "
                        + "removes the per-hop decay on the wires it reaches. A leftover file that "
                        + "is not in the json is dead code the next reader will happily re-list, "
                        + "and a radius hook re-listed puts the whole bug back: a field-fed wire "
                        + "beside the reactor reads as a lever, and the reactor toggles itself "
                        + "every tick");
        String json = read(MIXINS);
        assertFalse(json.contains("SignalGetterMixin"),
                "the radius hook is registered again: " + json);
        assertTrue(json.contains("RedStoneWireBlockMixin"),
                "and its replacement has to be there instead: " + json);
    }

    @Test
    @DisplayName("the sweep survives a re-entrant re-queue")
    void theSweepIsIteratedOverASnapshot() {
        String src = read(SCAN);
        assertFalse(src.contains("PENDING.iterator()"),
                "**advancing the sweep from its own iterator is a crash waiting for a re-entry.** "
                        + "advance() hands a position to neighborChanged, which can walk all the way "
                        + "back to a reactor: setPowered -> refreshState -> request -> "
                        + "PENDING.removeIf. That mutates the list under its own iterator and throws "
                        + "ConcurrentModificationException in the tick loop, which is what killed "
                        + "the server at 00:11:20. A snapshot lets the re-entrant request queue its "
                        + "fresh scan for the next tick while this one finishes what it was "
                        + "walking");
        assertTrue(src.contains("new ArrayList<>(PENDING)"),
                "the snapshot has to be the list itself, or the iterator is back: " + src);
        assertTrue(src.contains("pending == scan"),
                "removal has to be by identity, because a re-entrant request may already have "
                        + "replaced this scan with a fresh one for the same centre -- removing by "
                        + "equality would take the new one out with it");
    }

    @Test
    @DisplayName("a component placed inside a live field is re-evaluated at once")
    void placementInsideTheFieldIsReEvaluated() {
        String main = read(Path.of("src", "main", "java", "com", "cortes", "combat",
                "CortesCombat.java"));
        assertTrue(main.contains("ReactorScan::onBlockPlace"),
                "**the placement listener has to be registered on the game bus.** The sweep runs on "
                        + "activate, deactivate and radius change and never on placement, so any "
                        + "component whose own code only listens for a neighbour change stays dark "
                        + "forever: \"placed after activation and it never lit\"");
        String src = read(SCAN);
        assertTrue(src.contains("event.getPlacedBlock().is(ReactorTags.POWERABLE)"),
                "the listener has to filter on the powerable tag, or every block placed anywhere in "
                        + "the world pays a neighbour update for nothing");
        assertTrue(src.contains("ActiveReactors.covering("),
                "and it has to name the covering reactor as the source, the way the sweep does, "
                        + "so the update looks like one the field fired rather than a bare "
                        + "re-check");
    }

    @Test
    @DisplayName("the light reads the charge, and says it is dynamic")
    void theLightIsDynamicAndReadsTheCharge() {
        String src = read(BLOCK);
        assertTrue(src.contains("int getLightEmission(BlockState state, BlockGetter level, BlockPos pos)"),
                "the block has to override the per-position light hook, or the light cannot follow "
                        + "the firestone count at all");
        assertTrue(src.contains("boolean hasDynamicLightEmission(BlockState state)"),
                "**and it has to say the emission is dynamic.** Without this the engine may call the "
                        + "hook with an empty level and the origin position, and the answer would be "
                        + "computed from a block entity that is not there");
        assertTrue(src.contains("ReactorCharge.lightFor"),
                "the light has to come from the one curve, not a second copy of it");
    }

    @Test
    @DisplayName("the wired region stops at the reactor's radius, and a break rebuilds after the removal")
    void theWiredRegionIsBoundedAndBreaksAfterwards() {
        String net = read(Path.of("src", "main", "java", "com", "cortes", "combat", "block",
                "ReactorNetwork.java"));
        assertTrue(net.contains("ActiveReactors.entries("),
                "**the walk has to read the radius, not only the position.** Connectivity alone is "
                        + "charge-blind: one firestone would claim every wire it can reach, so a "
                        + "fresh one-charge reactor inherited the old ten-charge one's reach -- the "
                        + "report this bound exists to fix");
        assertTrue(net.contains("radiusSquared"),
                "and the bound has to be squared distance, the same integer test the sweep uses, so "
                        + "membership never pokes outside the radius the sweep will actually notify "
                        + "on an off-switch");

        String be = read(BLOCK_ENTITY);
        int removed = be.indexOf("void onBlockRemoved");
        assertTrue(removed >= 0, "the reactor lost its removal hook: " + BLOCK_ENTITY);
        assertTrue(be.indexOf("ReactorNetwork.rebuild(server)", removed) > 0,
                "**breaking the reactor has to rebuild from onBlockRemoved, not from BreakEvent.** "
                        + "NeoForge fires the break event (fireBlockBreak) before removeBlock, so a "
                        + "rebuild there sees the reactor still standing, keeps every wire a member, "
                        + "and the severed line holds strength forever: the reported redstone that "
                        + "stays on until the world reloads");

        String wire = read(Path.of("src", "main", "java", "com", "cortes", "combat", "mixin",
                "RedStoneWireBlockMixin.java"));
        assertTrue(wire.contains("method = \"onRemove\""),
                "**a severed wire needs a rebuild of its own, on onRemove.** It runs inside "
                        + "setBlock, after the wire is gone and before the neighbour cascade "
                        + "starts, so the downstream half recomputes with correct membership and "
                        + "goes dark -- a BreakEvent rebuild alone fires too early for that");
    }

    @Test
    @DisplayName("the automation cap is thirty and the slot cap is forty, from the constants")
    void theTwoCapsAreInTheirTwoPlaces() {
        String src = read(BLOCK_ENTITY);
        assertTrue(src.contains("ReactorCharge.MAX_NORMAL - charge()"),
                "**the hopper cap has to be computed from the current charge, in the wrapper's "
                        + "insertItem.** isItemValid is not given the count, so a cap placed there "
                        + "cannot know how full the reactor is");
        assertTrue(src.contains("slot == SLOT_FIRESTONE ? ReactorCharge.MAX_OVERLOAD : 1"),
                "and the slot's own limit is the manual ceiling, so the direct insert can reach "
                        + "forty where the wrapper stopped at thirty");
    }

    @Test
    @DisplayName("the sweep skips loaded-empty work and forces a real re-evaluation")
    void theSweepIsBudgetedAndSkips() {
        String src = read(SCAN);
        assertTrue(src.contains("hasOnlyAir"),
                "**an all-air section has to be skipped whole.** A thirty-charge reactor is about "
                        + "twelve million positions; walking the empty ones is the difference "
                        + "between a background settle and a watchdog");
        assertTrue(src.contains("ChunkStatus.FULL"),
                "an unloaded chunk has to be skipped, or the sweep pays for chunks that are not "
                        + "ticking and cannot react anyway");
        assertTrue(src.contains("level.neighborChanged("),
                "**the re-evaluation has to be neighborChanged and not updateNeighborsAt.** The "
                        + "first makes the block at a position re-check itself; the second tells the "
                        + "blocks around it, which leaves the component dark");
        int budgetCharges = src.split("budget\\.positions--", -1).length - 1;
        assertTrue(budgetCharges >= 4,
                "**every path in the sweep has to charge the budget, including the three skips.** "
                        + "A skip that advances the cursor without spending the budget walks every "
                        + "unloaded chunk column in a single tick without yielding -- and a large "
                        + "reactor's radius is mostly unloaded chunks, so that is the watchdog "
                        + "freeze. Found " + budgetCharges + " budget charges, expected at least 4: "
                        + "the inner loop plus the unloaded chunk, past-the-last-section and all-air "
                        + "skips");
    }

    @Test
    @DisplayName("the powerable tag is written and carries the components")
    void thePowerableTagExists() {
        assertTrue(Files.isRegularFile(POWERABLE_TAG),
                "missing " + POWERABLE_TAG + " -- run gradlew runData. The sweep only spends a "
                        + "neighbour update on positions this tag matches, so an empty or absent tag "
                        + "is a reactor that turns nothing on");
        String json = read(POWERABLE_TAG);
        assertTrue(json.contains("minecraft:redstone_lamp"),
                "the lamp is the reactor's headline component and is not in the tag: " + json);
        assertTrue(json.contains("minecraft:redstone_wire"),
                "**the wire has to be swept: under design A it is the field's only outlet**, so when "
                        + "the reactor switches, every wire in the radius has to recompute its level "
                        + "before anything downstream can know. The tag decides who is re-evaluated "
                        + "and the mixin decides who is fed, and on the wire those are the same "
                        + "block");
        assertFalse(json.contains("cortescombat:nether_reactor"),
                "the reactor must not power itself: a reactor in its own filter would be swept and "
                        + "re-evaluated by every scan, forever");
    }

    @Test
    @DisplayName("a reactor never covers its own cell")
    void theReactorDoesNotPowerItself() {
        String src = read(Path.of("src", "main", "java", "com", "cortes", "combat", "block",
                "ActiveReactors.java"));
        assertTrue(src.contains("pos.equals(reactor.pos())"),
                "**a reactor must not count its own cell as covered.** Its own position is at "
                        + "distance zero from itself, so without this skip it answers its own "
                        + "hasNeighborSignal with true: it latches itself on at the first neighbour "
                        + "update and no lever can ever switch it back off");

        String block = read(BLOCK);
        assertFalse(block.contains("server.hasNeighborSignal(pos)"),
                "**the reactor's switch cannot read a signal at all.** The field feeds the wire, so "
                        + "hasNeighborSignal on the reactor's own position is its own power coming "
                        + "back as a command: off, sweep, zero, on -- a toggle every tick. There is "
                        + "no way to tell a stored fifteen from the field apart from one from a "
                        + "lever, so the question itself is wrong");
        assertTrue(block.contains("ActiveReactors.switchSignal(server, pos)"),
                "**and the read it has instead is switchSignal**: only a control in "
                        + "ReactorTags.SWITCH -- a lever, a button, a plate -- may switch the "
                        + "reactor off, because a wire can be in the field's grip and never in that "
                        + "tag. Read the call sites: " + block);
    }

    @Test
    @DisplayName("the block draws its own model, and the item draws the EMPTY one")
    void theBlockIsNotInvisibleAndTheItemIsNotCharged() {
        String block = read(BLOCK);
        assertTrue(block.contains("protected RenderShape getRenderShape(BlockState state)"),
                "**BaseEntityBlock answers RenderShape.INVISIBLE by default.** It is the class for "
                        + "blocks whose look comes from a BlockEntityRenderer, so it suppresses the "
                        + "baked model -- and this block has none. Without the override the reactor "
                        + "places as an invisible box that still has collision and light, which reads "
                        + "as 'broken' rather than as 'missing'");
        assertTrue(block.contains("return RenderShape.MODEL;"),
                "the override has to return MODEL, which is 'draw the blockstate model'");

        String provider = read(MODEL_PROVIDER);
        assertTrue(provider.contains(":block/nether_reactor_empty\""),
                "**the item model has to inherit the EMPTY base model.** An item has no charge, so "
                        + "inheriting the charged model draws a lit core in the hotbar and tells the "
                        + "player the reactor in their hand is running when it is not");
    }

    @Test
    @DisplayName("a loaded nether star draws the star textures")
    void theStarDrawsItsOwnTextures() {
        String block = read(BLOCK);
        assertTrue(block.contains("BooleanProperty.create(\"star\")"),
                "**the star has to be a block state property.** A model is chosen from the state and "
                        + "from nothing else -- the block entity's data reaches the client too, but "
                        + "no model reads it -- so without this the reactor draws the normal core "
                        + "with a star loaded, which is exactly the report");
        assertTrue(block.contains("builder.add(CHARGED, ACTIVE, STAR)"),
                "and it has to be in the state definition, or the property is rejected when the "
                        + "state is built and every `when` clause in the blockstate matches nothing");
        assertTrue(read(BLOCK_ENTITY).contains("setValue(NetherReactorBlock.STAR,"),
                "refreshState has to carry the overload into the state, or the property never "
                        + "changes after placement and the block is frozen on whatever it was when "
                        + "it was set down");

        String provider = read(MODEL_PROVIDER);
        assertTrue(provider.contains("addProperty(\"star\", \"true\")"),
                "**the blockstate has to switch on `star`, and with AND** -- `charged` alone would "
                        + "stack a second full cube on the normal base, and a depth fight is not a "
                        + "texture. Selector.Deserializer.getCondition builds an AndCondition from "
                        + "more than one key in `when`, which is what makes the swap legal");
        assertTrue(provider.contains("nether_reactor_star_e"),
                "the star's bloom is its own model, or the normal core's glow is painted over the "
                        + "star's art");

        String state = read(Path.of("src", "generated", "resources", "assets", "cortescombat",
                "blockstates", "nether_reactor.json"));
        assertTrue(state.contains("cortescombat:block/nether_reactor_star"),
                "the generated blockstate does not offer the star model -- run gradlew runData: "
                        + state);
        assertTrue(state.contains("cortescombat:block/nether_reactor_star_e"),
                "nor the star's bloom: " + state);
    }

    /**
     * **The published radius only grows, and it is the whole fix for the dip.**
     *
     * <p>The regression was "the signal dips when the overload ends, and throwing the lever fixes
     * it": the star publishes a flat hundred and the firestone at forty publishes sixty, and the
     * radius then fell back to the charge's own curve. Every wire past the smaller number lost its
     * membership, recomputed with {@code -1}, and {@code max(i, j)} never gives back a point that
     * was lost -- the line stayed dark until a lever swept the area again.</p>
     *
     * <p>None of that can be asserted by running the game cheaply, and all of it can be asserted
     * by reading the four lines that have to be there.</p>
     */
    @Test
    void thePublishedRadiusOnlyGrows() {
        String be = read(BLOCK_ENTITY);

        assertTrue(be.contains("if (newRadius > publishedRadius)"),
                "**el radio publicado tiene que subir, nunca bajar** -- sin este `if`, terminar una "
                        + "sobrecarga encoge el radio al de la carga real y los cables de mas "
                        + "pierden membresia para siempre (max(i, j) no devuelve el punto perdido): "
                        + be);

        assertTrue(be.contains("int published = lit ? publishedRadius : 0"),
                "**apagado publica cero** -- el high-water es de los cables, no de la palanca. Sin "
                        + "esta rama, tirar de la palanca dejaria el reactor registrado con el radio "
                        + "historico y la red seguiria encendida: " + be);

        assertTrue(be.contains("tag.putInt(\"radius\", publishedRadius)"),
                "**y se guarda**, porque un radio que solo crece es un hecho sobre los cables del "
                        + "mundo: sin persistirlo, recargar el mundo lo devuelve al de la carga y el "
                        + "mismo sintoma vuelve despues de cualquier reinicio: " + be);

        assertTrue(be.contains("publishedRadius = tag.getInt(\"radius\")"),
                "y se lee, que es la otra mitad de guardarlo: " + be);
    }

    /**
     * **El nombre viejo decia "el ultimo" y la regla es "el mas alto".** Un campo que se llama
     * {@code lastRadius} y sube es un campo que miente sobre si mismo en cada lectura, y el
     * siguiente lector va a confiar en el nombre. Muerto el nombre, este test es el que lo mata.
     */
    @Test
    void theRadiusFieldWasRenamed() {
        String be = read(BLOCK_ENTITY);
        assertFalse(be.contains("lastRadius"),
                "sigue el nombre viejo: `lastRadius` describe un campo que solo sube, y eso es una "
                        + "mentira en cada lectura. La marca de agua se llama publishedRadius: " + be);
    }
}
