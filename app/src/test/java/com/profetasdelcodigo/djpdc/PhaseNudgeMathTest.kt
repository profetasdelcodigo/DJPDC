package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Test

class PhaseNudgeMathTest {
    @Test fun advancesByRequestedAmount() {
        assertEquals(1_020L, PhaseNudgeMath.adjust(1_000L, 20L, 10_000L))
    }

    @Test fun delaysByRequestedAmount() {
        assertEquals(980L, PhaseNudgeMath.adjust(1_000L, -20L, 10_000L))
    }

    @Test fun clampsAtBeginningAndEnd() {
        assertEquals(0L, PhaseNudgeMath.adjust(10L, -20L, 10_000L))
        assertEquals(10_000L, PhaseNudgeMath.adjust(9_990L, 20L, 10_000L))
    }

    @Test fun unknownDurationDoesNotClampToZero() {
        assertEquals(1_020L, PhaseNudgeMath.adjust(1_000L, 20L, 0L))
    }

    @Test fun invalidPositionReturnsZero() {
        assertEquals(0L, PhaseNudgeMath.adjust(-1L, 20L, 10_000L))
    }
}
