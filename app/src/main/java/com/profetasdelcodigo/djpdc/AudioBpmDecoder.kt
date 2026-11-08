package com.profetasdelcodigo.djpdc

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes up to 45 seconds of the first audio stream to mono PCM for BPM analysis.
 * Call from Dispatchers.IO; the extractor/codec are always released.
 */
object AudioBpmDecoder {
    fun decodeMono(context: Context, uri: Uri, maxSeconds: Int = 45): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    inputFormat = format
                    break
                }
            }
            if (audioTrack < 0 || inputFormat == null) return null
            extractor.selectTrack(audioTrack)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT
            val output = ArrayList<Float>(sampleRate * maxSeconds)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0

            while (!outputDone && output.size < sampleRate * maxSeconds && idleLoops < 500) {
                var didWork = false
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        didWork = true
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        else android.media.AudioFormat.ENCODING_PCM_16BIT
                        didWork = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = codec.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            val pcm = buffer.duplicate().order(ByteOrder.nativeOrder())
                            pcm.position(info.offset)
                            pcm.limit(info.offset + info.size)
                            if (pcmEncoding == android.media.AudioFormat.ENCODING_PCM_FLOAT) {
                                while (pcm.remaining() >= 4 && output.size < sampleRate * maxSeconds) {
                                    var mono = 0f
                                    repeat(channels.coerceAtLeast(1)) {
                                        if (pcm.remaining() >= 4) mono += pcm.float
                                    }
                                    output.add(mono / channels.coerceAtLeast(1))
                                }
                            } else {
                                while (pcm.remaining() >= 2 * channels.coerceAtLeast(1) && output.size < sampleRate * maxSeconds) {
                                    var mono = 0f
                                    repeat(channels.coerceAtLeast(1)) { mono += pcm.short / 32768f }
                                    output.add(mono / channels.coerceAtLeast(1))
                                }
                            }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                        didWork = true
                    }
                }
                idleLoops = if (didWork) 0 else idleLoops + 1
            }
            return if (output.size >= sampleRate * 8) Pair(output.toFloatArray(), sampleRate) else null
        } finally {
            try { codec?.stop() } catch (_: Exception) { }
            codec?.release()
            extractor.release()
        }
    }
}
