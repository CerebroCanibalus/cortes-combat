package com.cortes.combat.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offsets add up, and the add is the whole composition model.
 *
 * <p>Every reason the tracks are additive lives in this one operator: two tracks that both push
 * the item back add, which is what lets "wind up", "tremble" and "strike" be three independent
 * rows of data instead of one function with three branches. A scale that replaced rather than
 * added would make the last track written the only one that matters.</p>
 */
class OffsetTest {

    private static final Offset A = new Offset(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
    private static final Offset B = new Offset(0.5, -0.5, 1.5, -2.0, 0.25, 10.0);

    @Test
    @DisplayName("adding is component-wise on all six")
    void plusIsComponentWise() {
        Offset sum = A.plus(B);
        assertEquals(1.5, sum.x(), 0.0);
        assertEquals(1.5, sum.y(), 0.0);
        assertEquals(4.5, sum.z(), 0.0);
        assertEquals(2.0, sum.pitch(), 0.0);
        assertEquals(5.25, sum.yaw(), 0.0);
        assertEquals(16.0, sum.roll(), 0.0);
    }

    @Test
    @DisplayName("scaling is component-wise on all six")
    void timesIsComponentWise() {
        Offset half = A.times(0.5F);
        assertEquals(0.5, half.x(), 0.0);
        assertEquals(1.0, half.y(), 0.0);
        assertEquals(1.5, half.z(), 0.0);
        assertEquals(2.0, half.pitch(), 0.0);
        assertEquals(2.5, half.yaw(), 0.0);
        assertEquals(3.0, half.roll(), 0.0);
    }

    @Test
    @DisplayName("adding zero changes nothing, exactly")
    void zeroIsNeutral() {
        Offset sum = A.plus(Offset.ZERO);
        assertEquals(A.x(), sum.x(), 0.0);
        assertEquals(A.roll(), sum.roll(), 0.0);
    }

    @Test
    @DisplayName("scaling by zero gives something that reports itself as zero")
    void zeroScaleIsZero() {
        assertTrue(A.times(0.0F).isZero());
    }

    @Test
    @DisplayName("a genuine offset does not claim to be zero")
    void aRealOffsetIsNotZero() {
        assertTrue(!A.isZero());
        assertTrue(!new Offset(0, 0, 0, 0, 0, 0.0001).isZero(),
                "a fraction of a degree is still a movement");
    }

    @Test
    @DisplayName("adding is commutative and associative, so track order does not matter")
    void additionIsOrdered() {
        Offset left = A.plus(B).plus(A);
        Offset right = A.plus(A).plus(B);
        assertEquals(left.x(), right.x(), 0.0);
        assertEquals(left.roll(), right.roll(), 0.0);
    }
}
