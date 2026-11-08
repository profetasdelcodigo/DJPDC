package com.profetasdelcodigo.djpdc

import org.junit.Assert.assertEquals
import org.junit.Test

class MixerMathTest {
    @Test
    fun crossfadeEndpointsRouteToOneDeck() {
        val a = MixerMath.equalPowerCrossfade(0f)
        val b = MixerMath.equalPowerCrossfade(1f)

        assertEquals(1f, a.deckA, 0.0001f)
        assertEquals(0f, a.deckB, 0.0001f)
        assertEquals(0f, b.deckA, 0.0001f)
        assertEquals(1f, b.deckB, 0.0001f)
    }

    @Test
    fun centerUsesEqualPowerGains() {
        val center = MixerMath.equalPowerCrossfade(0.5f)
        val expected = 0.7071067f

        assertEquals(expected, center.deckA, 0.0001f)
        assertEquals(expected, center.deckB, 0.0001f)
        assertEquals(1f, center.deckA * center.deckA + center.deckB * center.deckB, 0.0001f)
    }

    @Test
    fun crossfadeClampsOutOfRangePositions() {
        assertEquals(MixerMath.equalPowerCrossfade(0f), MixerMath.equalPowerCrossfade(-2f))
        assertEquals(MixerMath.equalPowerCrossfade(1f), MixerMath.equalPowerCrossfade(3f))
    }

    @Test
    fun channelVolumeIsClampedAndApplied() {
        assertEquals(0.4f, MixerMath.applyChannelVolume(0.8f, 0.5f), 0.0001f)
        assertEquals(0f, MixerMath.applyChannelVolume(-1f, 0.5f), 0.0001f)
        assertEquals(1f, MixerMath.applyChannelVolume(2f, 2f), 0.0001f)
    }
}
