package com.cortes.combat.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two rules that keep this mod cheap to move to another Minecraft version, held by reading the
 * source.
 *
 * <h2>Why these are textual guards and not unit tests</h2>
 *
 * <p><b>Both rules are about where a thing is written, and nothing at runtime can observe where a
 * thing was written.</b> A {@code ResourceLocation} built inline and one built through
 * {@link com.cortes.combat.CortesCombat#rl} are the same object; a rule about the pure packages
 * returning a correct number is not evidence that the package stayed pure. So the question has to be
 * asked of the source the game is compiled from, the same way {@code ReactorWiringTest} asks about
 * its wires.</p>
 *
 * <h2>Why it matters enough to guard</h2>
 *
 * <p><b>Neither rule is enforceable by discipline, and this project has the receipts.</b> The track
 * {@code BREAK} on the sabre, {@code RemoteMotion.cooldownRatio} and the orphan icon listener were
 * all things written once, correctly, in a place nobody called. An abstraction that nothing holds in
 * place degrades at the speed of the next feature. The version-coupled spellings are the same shape
 * and there were sixty-seven of them: cheap to write inline, and each one an edit on the day the
 * version moves.</p>
 *
 * <p><b>And the failure mode of a guard that silently checks nothing is real here.</b> If the
 * package list went stale, every assertion below would pass over an empty set and read as "the core
 * is pure". So the list is asserted to be non-empty before it is used. (^o^)</p>
 */
class PortabilityGuardsTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * The file allowed to name the version-coupled factory, and the only one.
     *
     * <p>It is the file that holds {@code rl()}: {@code platform/ResourceIds}, in one body per
     * leg (shared tree = 1.21.1 spelling, {@code src/v120} twin = 1.20.1 spelling, which this
     * guard never scans). Anything else naming the factory is a call site that will cost an
     * edit on the next version bump, which is the whole thing this guard exists to stop.
     * (μ_μ)</p>
     */
    private static final String FACADE_FILE = "ResourceIds.java";

    /**
     * Packages with zero Minecraft imports today, asserted to stay that way.
     *
     * <p><b>This is a ratchet and not a target.</b> It lists what is already clean, because a guard
     * that fails on day one gets deleted instead of fixed. The list grows as packages get cleaned;
     * it never shrinks. {@code registry}, {@code mixin}, {@code client} and {@code data} can never
     * be on it and are not supposed to be: they extend classes of the game on purpose.
     * (⌒‿⌒)</p>
     */
    private static final List<String> PURE_PACKAGES = List.of("state", "viewmodel", "weapon", "pose");

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * The file with comments removed, which is not optional here.
     *
     * <p>Because this project's comments <b>name the thing they are explaining</b>: the javadoc on
     * {@code rl()} spells {@code ResourceLocation.fromNamespaceAndPath} to say why it exists, and a
     * guard reading prose would find that phrase and fail with the code intact. It is the lesson
     * this project paid for three times -- the same one behind the {@code ReactorShake} textual
     * guard -- and the cheap way to write it right is this: <b>ask the code</b>. (￣ε￣＠)</p>
     */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static List<Path> sources() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(MAIN)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        } catch (IOException e) {
            throw new AssertionError("could not walk " + MAIN + ": " + e.getMessage(), e);
        }
        return out;
    }

    private static String relative(Path path) {
        return MAIN.relativize(path).toString().replace('\\', '/');
    }

    private static List<Path> sourcesIn(String pkg) {
        // Anchored to the mod's own root, because a substring match was the first version and it
        // found `core/weapon/` while looking for `weapon/` -- a guard that answers "does this path
        // contain the word" is answering a different question than "is this that package". (￣▽￣)
        String root = "com/cortes/combat/" + pkg + "/";
        List<Path> out = new ArrayList<>();
        for (Path path : sources()) {
            if (relative(path).startsWith(root)) {
                out.add(path);
            }
        }
        return out;
    }

    /**
     * <b>One spelling of "make me a resource location", in one file.</b>
     *
     * <p>The factory that builds one is the cheapest example of a version-coupled API: 1.21 introduced
     * {@code ResourceLocation.fromNamespaceAndPath} and deprecated the constructor, so every call site
     * written inline is an edit on the day the target moves -- sixty-seven of them across forty-seven
     * files, all the same edit, which is the exact kind of work a version port should not contain.</p>
     *
     * <p><b>Sabotage to verify:</b> put a {@code ResourceLocation.fromNamespaceAndPath(...)} back in
     * any file and this fails naming the file and the line. σ(￣、￣)</p>
     */
    @Test
    @DisplayName("a resource location is built through rl() and nowhere else")
    void resourceLocationsGoThroughTheFactory() {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            if (path.getFileName().toString().equals(FACADE_FILE)) {
                continue;
            }
            String code = stripComments(read(path));
            if (code.contains("fromNamespaceAndPath") || code.contains("new ResourceLocation(")) {
                offenders.add(relative(path));
            }
        }
        assertTrue(offenders.isEmpty(),
                "**estos archivos construyen un ResourceLocation a mano**: " + offenders
                        + " -- usa CortesCombat.rl(path). La fábrica acoplada a la versión tiene que "
                        + "estar en un solo archivo o cada bump de Minecraft la vuelve a cobrar");
    }

    /**
     * <b>The packages that are pure today stay pure, and a package cannot be listed and be empty.</b>
     *
     * <p>This is the half of the rule that survives versions for free: a package that imports nothing
     * from {@code net.minecraft} or {@code net.neoforged} compiles against any of them unchanged, and
     * that is where {@code state}, {@code viewmodel} and the rule tables live. It is also why the
     * mod's tests can run in a bare JVM -- the test classpath cannot see Minecraft at all, so a pure
     * package is one whose rules are provable without booting the game.</p>
     *
     * <p><b>The second assertion is the one that matters more.</b> A package list that still names a
     * directory somebody deleted would make every check below pass over zero files, and a guard that
     * scans nothing is green forever. That is the {@code SoundPoolTest} failure mode: the assertion
     * threw the folder away and asked nothing. (╯°□°)╯</p>
     */
    @Test
    @DisplayName("the pure packages stay free of Minecraft, and the list is not stale")
    void thePurePackagesStayPure() {
        List<String> problems = new ArrayList<>();
        for (String pkg : PURE_PACKAGES) {
            List<Path> files = sourcesIn(pkg);
            if (files.isEmpty()) {
                problems.add("paquete '" + pkg + "' sin archivos: la lista quedó vieja y el guard "
                        + "está mirando el vacío");
                continue;
            }
            for (Path path : files) {
                String code = stripComments(read(path));
                if (code.contains("import net.minecraft.") || code.contains("import net.neoforged.")) {
                    problems.add(relative(path));
                }
            }
        }
        assertTrue(problems.isEmpty(),
                "**la capa pura dejó de ser pura**: " + problems + " -- un tipo de Minecraft no cruza "
                        + "la frontera de la lógica; si el dato es necesario, la façade lo entrega ya "
                        + "convertido (ver TooltipLine/Component en item/tooltip)");
    }

    /**
     * <b>The storage mechanism is named in three files, and in three files only.</b>
     *
     * <p>There were one hundred and twelve call sites naming {@code ModAttachments} across eighteen
     * files, and every one of them was the same edit on the day the version moved: NeoForge replaced
     * {@code AttachmentType} with the capability system it had before, and a port would have rewritten
     * all one hundred and twelve. Now the mechanism lives in {@code CombatState} alone, which is the
     * one file a port rewrites -- and this guard is what keeps it that way.</p>
     *
     * <p><b>Three files are allowed because three jobs exist.</b> {@code ModAttachments} defines the
     * types, {@code CortesCombat} registers them against the mod bus, and {@code CombatState} is the
     * only door anyone walks through. A fourth file naming either the registry or the
     * {@code getData}/{@code setData} verbs is a call site that went around the facade, and the next
     * version bump collects it with interest.</p>
     *
     * <p><b>Sabotage to verify:</b> write {@code player.getData(ModAttachments.CHARGE.get())} back
     * into any gameplay file and this names it. (¬‿¬)</p>
     */
    @Test
    @DisplayName("player state is touched through CombatState and nowhere else")
    void playerStateGoesThroughTheFacade() {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String name = path.getFileName().toString();
            if (name.equals("CombatState.java") || name.equals("ModAttachments.java")
                    || name.equals("CortesCombat.java")) {
                continue;
            }
            String code = stripComments(read(path));
            if (code.contains("ModAttachments.") || code.contains(".getData(")
                    || code.contains(".setData(")) {
                offenders.add(relative(path));
            }
        }
        assertTrue(offenders.isEmpty(),
                "**estos archivos tocan el estado del jugador por fuera de la facade**: " + offenders
                        + " -- usa CombatState.charge(player) y sus hermanos. El mecanismo de "
                        + "almacenamiento cambia por version y el call site no deberia nombrarlo");
    }

    /**
     * <b>Vanilla's enchantment statics are spelled in one file, and in one file only.</b>
     *
     * <p>{@code EnchantmentHelper.modifyDamage} and {@code modifyKnockback} are the 1.20.5
     * data-driven pipeline; one version down the same questions are a hand-rolled loop over
     * {@code getEnchantments}. Three call sites spelled the helper inline, so a port would have
     * rewritten the damage formula to reach the storage. Now the engine asks {@code EnchantView}
     * and the version difference lands in that file. A second file naming the helper is a call
     * site that went around the facade.</p>
     *
     * <p><b>Sabotage to verify:</b> write {@code EnchantmentHelper.modifyDamage(...)} back into
     * the engine and this names it. (¬‿¬)</p>
     */
    @Test
    @DisplayName("vanilla enchantment math is asked through EnchantView and nowhere else")
    void enchantmentMathGoesThroughTheFacade() {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            if (path.getFileName().toString().equals("EnchantView.java")) {
                continue;
            }
            String code = stripComments(read(path));
            if (code.contains("EnchantmentHelper")) {
                offenders.add(relative(path));
            }
        }
        assertTrue(offenders.isEmpty(),
                "**estos archivos llaman a EnchantmentHelper por fuera de la facade**: " + offenders
                        + " -- usa EnchantView. El helper cambia por version y el call site no "
                        + "deberia nombrarlo");
    }

    /**
     * <b>No version conditions inline, and this is a deliberate limitation of the setup.</b>
     *
     * <p>Stonecutter's {@code //?} markers only take effect where a leg processes them. The 1.21.1
     * leg chisels (processes) its tree; the 1.20.1 leg compiles a Sync-renamed copy that no
     * condition processing ever touches. An inline branch would therefore compile <em>both</em>
     * sides on 1.20.1 -- the 1.21.1 spelling first, which does not exist there -- and the failure
     * would arrive as a confusing compile error instead of a clear rule.</p>
     *
     * <p>So version differences live in whole files: the shared twin under {@code src/main} and
     * the 1.20.1 body at the same path under {@code src/v120}, picked by the leg's source set
     * wiring. A file that needs a branch and cannot be a facade is a design smell, not a reason
     * for an inline condition. (￣ω￣)</p>
     *
     * <p><b>Sabotage to verify:</b> write a {@code //? if} line into any gameplay file and this
     * names it. (¬‿¬)</p>
     */
    @Test
    @DisplayName("no inline version conditions; branches live in whole files")
    void noInlineVersionConditions() {
        List<String> offenders = new ArrayList<>();
        for (Path path : sources()) {
            String code = read(path);
            if (code.contains("//?") || code.contains("//$") || code.contains("//~")) {
                offenders.add(relative(path));
            }
        }
        assertTrue(offenders.isEmpty(),
                "**estos archivos traen condiciones de version inline**: " + offenders + " -- la "
                        + "pata 1.20.1 no las procesa y compilaria las dos ramas. La diferencia va "
                        + "en un archivo entero bajo src/v120, nunca en un comentario");
    }
}
