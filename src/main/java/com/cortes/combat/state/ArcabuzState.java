package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The arcabuz: whether a firestone is in it, whether a fuse is burning, and how much of a reload
 * is left.
 *
 * <p>Pure, and a record with a Codec, for the reason this project has one rule about mutable
 * gameplay state. The three failures this exact shape exists to prevent, all of which this
 * codebase has already paid for:</p>
 *
 * <ul>
 *   <li><b>A static</b> would be one gun for the whole server. With two players in a world the
 *       second one to press would overwrite the first's countdown.</li>
 *   <li><b>A field on a listener</b> does not survive a respawn and does not reset on login. Every
 *       one of these flags is a button the player holds, and a button that survives something the
 *       player did not do is the bug the guard's held flag already was: coming back from the death
 *       screen with a loaded musket nobody loaded.</li>
 *   <li><b>A state that outlives the button that made it.</b> {@code reloadHeld} is the fourth
 *       field here and it exists for exactly that: it is the server's memory of a mouse button,
 *       and it is reconciled against the window every tick rather than trusted.</li>
 * </ul>
 *
 * <h2>Three conditions, and two flags cannot tell them apart</h2>
 *
 * <p>An earlier version had two flags and the reload was a single press. The problem that created
 * is worth writing down, because it is not a bug anybody would have predicted: <b>one press and
 * one release both reached the server</b>, so a single click of the right button loaded the gun
 * and then -- on the release, a few hundredths of a second later -- found it loaded and lit the
 * fuse. The gun fired by itself a second after being reloaded, which read as a weapon deciding to
 * shoot rather than as an input handler sending two packets for one click.</p>
 *
 * <p>The fix was one line in the input handler, and the state was already correct. It is written
 * down here because the lesson is not "check the action" -- it is <b>"a state machine with a press
 * and a release on the same button needs the release filtered before the state ever sees it",
 * and the symptom lands twenty ticks downstream of the cause.</b></p>
 *
 * <h2>The reload is a hold, not a press</h2>
 *
 * <p>{@link #RELOAD_TICKS} and {@link #reloadTicks}: the gun fills while the button is down and
 * empties the moment it comes up. A reload that completes on a press and then sits there has no
 * reason to be a reload -- it is a shot with extra steps. Holding it is what makes loading a
 * decision the player makes over time.</p>
 *
 * @param fuseTicks   ticks of fuse left, zero when nothing is burning
 * @param reloadTicks ticks of loading left, zero when the gun is not being loaded
 * @param loaded      whether a firestone is in the chamber
 * @param reloadHeld  whether the server believes the right button is down
 */
public record ArcabuzState(int fuseTicks, int reloadTicks, boolean loaded, boolean reloadHeld) {

    /**
     * Twenty ticks: one whole second, per the design.
     *
     * <p><b>The number is also the texture's.</b> {@code arcabuz.png} is a 64x320 strip of five
     * 64x64 frames -- one unlit and four burning -- and its {@code .mcmeta} spends five ticks a
     * frame, so the four lit frames arrive across exactly twenty. The shot waits this long and
     * the fuse the player watches is this long, which is the only reason the two numbers can be
     * trusted to stay in step. {@code ModelDeclarationTest} reads the mcmeta and compares.</p>
     */
    public static final int FUSE_TICKS = 20;

    /**
     * Fifty ticks: two and a half seconds of holding the right button.
     *
     * <p><b>Half a second slower than the halberd's whole sweep cost.</b> A sweep takes four
     * halberd swings and about two seconds; loading a gun takes two and a half and no swing. That
     * comparison is the whole reason the number is not twenty ticks: the halberd's button is
     * <em>paid for</em> in tempo and this one is <em>paid for</em> in standing still, and if the
     * two were the same length the gun would read as the cheap button.</p>
     *
     * <p>Two and a half rather than three because the reload ends on a tick the player can feel:
     * at fifty ticks the whole thing is 1.25 seconds of half-second granularity, so the last
     * tenth of a second is the only part of it that is not obviously a decision.</p>
     */
    public static final int RELOAD_TICKS = 50;

    /** Nothing burning, nothing loading, nothing loaded. A gun picked up off the ground. */
    public static final ArcabuzState EMPTY = new ArcabuzState(0, 0, false, false);

    /**
     * The save format, and <b>every field is optional with a default</b>.
 *
     * <p>This is not defensive coding, it is the consequence of a rule this class did not have
     * written down before it cost something: <b>the codec of a record <em>is</em> its save
     * format</b>. Growing this record from two fields to four -- to add the reload -- silently
     * invalidated every world that had ever saved a player holding a gun, and the symptom was an
     * {@code AttachmentHolder} error on login:</p>
 *
 * <pre>
 *   Failed to deserialize data attachment cortescombat:arcabuz. Skipping.
 *   No key reload_held in MapLike[{fuse_ticks:0,loaded:0b}]
 * </pre>
 *
 * <p>Non-fatal, which is what makes it worth writing down: the attachment is skipped, the gun comes
 * back empty, and nothing tells the player why their loaded musket is gone. A mod that adds a field
 * to something persisted has to read the old shape, or it decides that other people's saves are
 * disposable.</p>
 *
 * <p>All four optional rather than only the two new ones, so the rule is not "the two new fields
 * have defaults" -- which the next person adds a fifth field against -- but "this format can always
 * read what it can find and answer EMPTY for the rest".</p>
     */
    public static final Codec<ArcabuzState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("fuse_ticks", 0).forGetter(ArcabuzState::fuseTicks),
            Codec.INT.optionalFieldOf("reload_ticks", 0).forGetter(ArcabuzState::reloadTicks),
            Codec.BOOL.optionalFieldOf("loaded", false).forGetter(ArcabuzState::loaded),
            Codec.BOOL.optionalFieldOf("reload_held", false).forGetter(ArcabuzState::reloadHeld)
    ).apply(instance, ArcabuzState::new));

    /** Whether a fuse is actually burning right now. */
    /**
     * Whether a lit fuse or a running reload has to be snuffed because the gun left the hand.
     *
     * <p><b>The rule, on the record, because a decision nothing can test is a decision that drifts.</b>
     * This was written inline in {@code Arcabuz#tick}, which needs a {@code ServerPlayer} to
     * reach and therefore had no test at all -- and it is the rule that decides whether a player
     * who swaps weapons mid-burn gets to steal the shot.</p>
     *
     * <p><b>Why the press could not have caught it.</b> The press is refused when the held stack is
     * not the one that was pressed, but {@code ItemStack#isSameItemSameComponents} compares the
     * item and its components, not the identity of the stack -- so two arcabuz compare equal and
     * that guard has never been able to tell them apart. And a press cannot see twenty ticks into
     * the future anyway: the fuse is lit on the press and goes off long after it.</p>
     *
     * <p><b>A snuff, not a transfer.</b> The firestone is spent at the light, so a gun snuffed this
     * way is empty and its ball is gone. Same price as missing, and it stays legible: the gun goes
     * down and is empty until reloaded.</p>
     *
     * @param arcabuzInHand whether the main hand is holding an arcabuz at all, this tick
     * @return true when the state must be thrown away rather than stepped
     */
    public boolean shouldSnuff(boolean arcabuzInHand) {
        return !arcabuzInHand && (isBurning() || isReloading());
    }

    public boolean isBurning() {
        return fuseTicks > 0;
    }

    /** Whether the gun is being loaded right now, which is a third thing between the other two. */
    public boolean isReloading() {
        return reloadTicks > 0;
    }

    /**
     * Whether a press right now would light the fuse.
     *
     * <p><b>All three gates, and the third is new.</b> A gun with a firestone in it and a fuse
     * going is a gun that is already going to fire; a second press must not extend it, and must
     * certainly not reset it to twenty, or holding the button would be a machine that delays
     * itself forever. And a gun that is <em>loading</em> has nothing to light: without that gate a
     * press during a reload lit a fuse on a chamber that was still being filled.</p>
     */
    public boolean canLight() {
        return loaded && !isBurning() && !isReloading();
    }

    /**
     * Whether a press right now would start a reload.
     *
     * <p>Refused while the fuse burns for the reason {@link #canLight()} is: an arcabuz with a
     * live fuse and a fresh charge would be a gun that fires twice off one firestone. Refused
     * while reloading for the ordinary reason: a second press must not restart a reload the player
     * is already four tenths into.</p>
     *
     * <p><b>Whether the player can pay is not one of these gates.</b> It cannot be: the state is
     * pure and knows nothing about inventories. The firestone is checked when the reload
     * <em>completes</em>, which is also why a reload the player abandons costs nothing.</p>
     */
    public boolean canReload() {
        return !loaded && !isBurning() && !isReloading();
    }

    /**
     * The fuse lit, and the one firestone gone.
     *
     * <p><b>Spent at the light and not at the shot.</b> That is the rule the state exists to
     * express: the press is what costs the firestone, so a gun that is already going has nothing
     * left to go and cannot be topped up behind the player's back while the fuse burns. It is
     * also why {@link #loaded} is false in here even though the shot has not happened yet --
     * "loaded" answers "can this press start a fuse", and after this press it cannot.</p>
     */
    /**
     * Enciende la mecha, con el largo que le toque.
     *
     * <p><b>El total entra como argumento y no se toma de {@link #FUSE_TICKS}</b>, que es la base:
     * Mecha Corta acorta la mecha, y el estado tiene que contar la mecha que el arma tiene puesta y no
     * la de fabrica. Un `light()` sin argumentos contaba veinte ticks siempre, asi que con el
     * encantamiento el disparo salia diez ticks despues de que la animacion hubiera terminado.</p>
     */
    public ArcabuzState light(int fuse) {
        return new ArcabuzState(fuse, 0, false, false);
    }

    /** La mecha de fabrica. Ver {@link #light(int)} para el caso con encantamiento. */
    public ArcabuzState light() {
        return light(FUSE_TICKS);
    }

    /**
     * Loading starts: the full timer, and <b>the held flag is carried over</b>.
     *
     * <p><b>That last half was a bug, and it was the whole of "no deja recargar".</b> This method
     * used to return {@code reloadHeld = false}, on the reasoning that "a press must not assume the
     * button is down". The reasoning was right and the implementation fought the packet order: the
     * client sends {@code held = true} and <em>then</em> the press, so by the time this runs the flag
     * is already true -- and clearing it here put the reload into the one state that can never
     * advance, because the only thing that advances it is a flag the press had just wiped. The gun
     * would accept the reload and then sit at fifty ticks for ever.</p>
     *
     * <p>So the flag is preserved, and the caller says what it knows about the button explicitly
     * rather than depending on two packets arriving in an order.</p>
     */
    public ArcabuzState startReload() {
        return startReload(RELOAD_TICKS);
    }

    /**
     * Lo mismo, con la recarga que este arcabuz va a tardar <b>de verdad</b>.
     *
     * <p>{@code startReload()} queda como el valor de fábrica; el servidor le pasa el número real,
     * que sale de Pólvora Seca (ver {@code Arcabuz#reloadTicks}). El piso del número vive en
     * {@code CombatEnchantRules#reloadTicks}, no acá: una recarga siempre cuesta algo aunque el
     * encantamiento sume más de lo que hay, y el piso se fuerza en código y no se confía al dato.</p>
     */
    public ArcabuzState startReload(int totalTicks) {
        return new ArcabuzState(0, totalTicks, false, reloadHeld);
    }

    /** Lo mínimo que una recarga puede costar, en ticks: un segundo. */
    public static final int MIN_RELOAD_TICKS = 20;

    /**
     * What the state becomes when the stack in hand says the chamber is loaded.
     *
     * <p><b>The same reconciliation {@code Arcabuz#resolve} does on the press, moved into the pure
     * class so it can be tested and so the two places cannot be written differently.</b> The record
     * carries {@code loaded} because its gates are pure and cannot see an inventory, and the stack
     * carries it because the inventory is the truth about which particular gun is charged. They are
     * two answers to one question and they drift apart the moment the gun leaves the hand.
     *
     * <p>Concretely: swap to a sword and the client's viewmodel wipes its own copy, so picking the
     * musket back up draws an empty gun and asks for a firestone that was already spent. The state
     * here was right the whole time; nobody had asked it.
     *
     * @param chamberOnStack whether the stack in hand carries the chamber component
     * @return this when the two already agree, which is every tick of normal play
     */
    public ArcabuzState withChamber(boolean chamberOnStack) {
        // The same instance when nothing changed, for the reason step() and withHeld give: this goes
        // into an attachment, and a record that compares equal still costs a write and a sync. The
        // caller also compares by identity to decide whether to send a packet, so this is the whole
        // of "cheap": one compare against a value already on the stack, and a packet only on the
        // tick where the two answers disagree.
        if (chamberOnStack == loaded) {
            return this;
        }
        // **The stack can only ever say "yes".** This is the asymmetry, and it is the whole reason
        // the empty direction is not simply the negation.
        //
        // A chamber is spent at the light, in the same statement that lights the fuse, and the
        // stack loses the component there. So `loaded == false` on the server is normal while a fuse
        // burns -- the ball is in the barrel -- and a record that re-read `false` from the stack on
        // every tick would empty a gun that is about to fire, and then empty it again every tick
        // after. The tick's own reconciliation calls this with the stack's answer; writing
        // `chamberOnStack` blindly would be correct for the "picked the gun back up" case and wrong
        // for the twenty ticks around every shot.
        //
        // The empty direction is therefore the shot's business and nobody else's: `resolve` removes
        // the component and lights the fuse in one breath, and there is no other event in which a
        // charged gun becomes empty. So this method only ever fills, and a stack that says "no" to a
        // record that says "yes" means the player is holding a gun the server has never heard of.
        if (!chamberOnStack) {
            return this;
        }
        return new ArcabuzState(fuseTicks, reloadTicks, true, reloadHeld);
    }

    /** What the server now believes about the mouse button. */
    public ArcabuzState withHeld(boolean held) {
        // The same instance when nothing changed, for the reason step() gives: this goes into an
        // attachment and a record that compares equal still costs a write and a sync.
        return held == reloadHeld ? this
                : new ArcabuzState(fuseTicks, reloadTicks, loaded, held);
    }

    /**
     * The button came up mid-reload.
     *
     * <p><b>The timer is thrown away rather than paused.</b> Pausing would let a player tap the
     * button in rhythm and fill the gun in half the time, and a reload that can be paused is a
     * reload with no duration.</p>
     */
    public ArcabuzState cancelReload() {
        return isReloading() ? new ArcabuzState(0, 0, false, false) : this;
    }

    /** Loading finished. The firestone is spent by the caller on this same tick. */
    public ArcabuzState finishReload() {
        return new ArcabuzState(0, 0, true, false);
    }

    /**
     * One tick: what the state becomes, whether the shot goes off, and whether the gun finished
     * loading.
     *
     * <p><b>The reload advances only while the flag is up, which is the whole mechanic.</b> Every
     * other field moves on its own clock and this one moves on the player's, and that asymmetry is
     * why the flag has to be reconciled rather than trusted: a flag left true reloads a gun nobody
     * is holding.</p>
     *
     * <p>Copied from {@link ParryState#step()} shape for shape, and for the same reason it is
     * there. The caller used to be the place that decided, and a caller that decides can decide
     * "the window is closed, nothing to report" -- which is true of the <em>packet</em> and
     * silently also taken as true of the countdown.</p>
     */
    public Step step() {
        if (isReloading()) {
            if (!reloadHeld) {
                return Step.of(cancelReload(), false, false, false);
            }
            int left = reloadTicks - 1;
            if (left <= 0) {
                return Step.of(finishReload(), false, true, true);
            }
            return Step.of(new ArcabuzState(0, left, false, true), false, false, false);
        }
        if (!isBurning()) {
            // The same instance rather than an equal new one: the caller writes this to an
            // attachment, and a record that compares equal is the difference between a quiet tick
            // and a write.
            return Step.of(this, false, false, false);
        }
        int left = fuseTicks - 1;
        boolean fires = left <= 0;
        // `loaded` goes through untouched, and that is correct rather than an oversight: `light()`
        // is what empties the chamber, so by the time a fuse is burning `loaded` has been false for
        // the whole burn. Writing `fires ? false : loaded` here -- which is exactly what a reading of
        // "the flintlock never comes back up" suggests -- is a no-op that reads like a fix, and a
        // comment above it can then describe a bug that does not exist.
        //
        // Where the client's `loaded` actually comes from is the literal `true` in the lit packet,
        // not this record. The client keeps the loaded pose for the length of the burn on purpose,
        // so the gun does not drop to its empty pose for a full second and then spring back.
        return Step.of(new ArcabuzState(Math.max(0, left), 0, loaded, false), fires, fires, false);
    }

    /**
     * The tick's outcome: the state to store, whether to shoot, whether to tell the client, and
     * whether the gun just finished loading.
     *
     * <p>{@code reloadDone} exists so the caller spends the firestone <em>at the moment the gun is
     * actually loaded</em> and not at the press that started it. That is a design decision and
     * the opposite of what the fuse does, on purpose: a player who lets go of the button has not
     * loaded anything, and charging them for it would make the release read as a bug.</p>
     *
     * @param state what goes into the attachment
     * @param fireNow whether the shot resolves on this tick
     * @param tellClient whether the visible state changed this tick
     * @param reloadDone whether the gun became loaded this tick
     */
    public record Step(ArcabuzState state, boolean fireNow, boolean tellClient, boolean reloadDone) {

        /** What a caller writes to the attachment, and what it sends to the player. */
        public static Step of(ArcabuzState state, boolean fireNow, boolean tellClient,
                              boolean reloadDone) {
            return new Step(state, fireNow, tellClient, reloadDone);
        }
    }

    /** Whether this is exactly the state a gun picked up off the ground is in. */
    public boolean isEmpty() {
        return this.equals(EMPTY);
    }
}