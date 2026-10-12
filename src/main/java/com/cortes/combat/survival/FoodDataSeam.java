package com.cortes.combat.survival;

/**
 * Proof, at runtime, that {@code FoodDataMixin} actually landed on {@code FoodData}.
 *
 * <h2>Why a marker is needed at all</h2>
 *
 * <p>Because the two ways this seam can be off do not look alike, and only one of them is
 * loud:</p>
 *
 * <ul>
 *   <li><b>A descriptor that does not match</b> throws during class transformation. With
 *       {@code required: true} and {@code injectors.defaultRequire: 1} in
 *       {@code cortescombat.mixins.json}, an injection that finds no target is fatal &mdash; and
 *       that is a real, verified safety net.</li>
 *   <li><b>A config that never got registered</b> loads the class perfectly well, applies nothing,
 *       and reports success. The whole survival rework would then be off while every log line in
 *       the world stayed green.</li>
 * </ul>
 *
 * <p>The first is caught by loading the class. The second is caught only by asking the class
 * what it became &mdash; which is what this interface is. The mixin declares it, so a
 * {@code FoodData} that has been through the mixin implements it and one that has not does
 * not. Reading {@code isAssignableFrom} is then an honest question with an honest answer, in
 * contrast to logging "the seam is in place" after merely loading the class, which would be
 * true in exactly the case where it matters and false in the one that is silent.</p>
 *
 * <h2>Why it needs a boot-time check at all</h2>
 *
 * <p>Because {@code FoodData} is loaded by the first player who joins, not during startup &mdash;
 * verified with {@code jcmd GC.class_histogram} against a running dedicated server with nobody
 * connected: neither {@code net.minecraft.world.food.FoodData} nor
 * {@code net.minecraft.world.entity.player.Player} was loaded yet, while
 * {@code net.minecraft.world.item.ItemStack} already had four and a half thousand instances. So
 * without this, nothing in the boot sequence would notice a broken survival seam until somebody
 * connected, and the first symptom would arrive as a crash on the server of whoever was testing.
 * Loading it on {@code ServerStartedEvent} moves that to the moment the world comes up.</p>
 *
 * <p>And no, checking this in a unit test cannot work: the transformation only happens inside a
 * running game with the mixin service attached. This is the assertion the test cannot make, said
 * out loud &mdash; the same shape as {@code CortesCombat}'s recipe and profile reports.</p>
 */
public interface FoodDataSeam {
}
