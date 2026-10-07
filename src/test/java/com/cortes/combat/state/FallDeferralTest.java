package com.cortes.combat.state;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a held fall is a debt and not a flag, and that applying it is not pending it.
 *
 * <p>The {@code applying} field is the whole reason this is a record and not a boolean, and the
 * assertion that matters is the middle one: if the deferred damage counted as pending, the handler
 * would defer its own second pass and the player would never take fall damage at all with a maza in
 * hand. That is a much larger gift than a ten tick window and it is invisible until somebody falls
 * off a cliff twice.</p>
 */
class FallDeferralTest {

    @Test
    @DisplayName("pending means damage is owed and not already being applied")
    void pendingIsDebtNotFlag() {
        assertFalse(FallDeferral.NONE.pending(), "nothing owed");

        assertTrue(new FallDeferral(5.0F, 100L, false).pending(),
                "damage owed and not being applied is pending");

        assertFalse(new FallDeferral(5.0F, 100L, true).pending(),
                "**the second pass is not pending.** The deferred damage is applied by calling "
                        + "hurt, which fires the event that deferred it, and a handler that counted "
                        + "this as pending would defer its own application forever -- a maza in "
                        + "hand would be permanent immunity to falling");

        assertFalse(new FallDeferral(0.0F, 100L, false).pending(),
                "and a zero is not a debt");
    }
}
