package com.profetasdelcodigo.djpdc

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Equal-power crossfader math for two independent audio decks.
 * Position 0f is Deck A, 1f is Deck B, and 0.5f is the center.
 *
 * This class only calculates gains; it does not play or mix audio by itself.
 */
data class CrossfadeGains(
    val deckA: Float,
    val deckB: Float
)

object MixerMath {
    fun equalPowerCrossfade(position: Float): CrossfadeGains {
        val normalized = position.coerceIn(0f, 1f)
        val angle = normalized * (PI.toFloat() / 2f)
        return CrossfadeGains(
            deckA = cos(angle).coerceIn(0f, 1f),
            deckB = sin(angle).coerceIn(0f, 1f)
        )
    }

    fun applyChannelVolume(gain: Float, channelVolume: Float): Float {
        return gain.coerceIn(0f, 1f) * channelVolume.coerceIn(0f, 1f)
    }
}
