package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max
import kotlin.math.min

class SidAudioSynthesizer(private val sampleRate: Int = 44100) {

    private val registers = IntArray(32)
    private val phase = DoubleArray(3)
    private val envLevel = DoubleArray(3)
    private val envState = IntArray(3)
    private val prevGate = BooleanArray(3)
    private val lfsr = IntArray(3) { 0x7FFFF8 }
    private var filterLow = 0.0
    private var filterBand = 0.0

    private var audioTrack: AudioTrack? = null
    @Volatile
    var soundEnabled: Boolean = true

    private val attackRates = doubleArrayOf(
        0.002, 0.008, 0.016, 0.024, 0.038, 0.056, 0.068, 0.080,
        0.100, 0.250, 0.500, 0.800, 1.000, 3.000, 5.000, 8.000
    )

    private val decayReleaseRates = doubleArrayOf(
        0.006, 0.024, 0.048, 0.072, 0.114, 0.168, 0.204, 0.240,
        0.300, 0.750, 1.500, 2.400, 3.000, 9.000, 15.000, 24.000
    )

    fun start() {
        if (audioTrack != null) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuf, sampleRate / 10)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        audioTrack = track
    }

    fun stop() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null
    }

    fun reset() {
        registers.fill(0)
        phase.fill(0.0)
        envLevel.fill(0.0)
        envState.fill(0)
        prevGate.fill(false)
        for (i in 0..2) {
            lfsr[i] = 0x7FFFF8
        }
        filterLow = 0.0
        filterBand = 0.0
    }

    fun writeRegister(offset: Int, value: Int) {
        val reg = offset and 0x1F
        val byteVal = value and 0xFF
        registers[reg] = byteVal
        if (reg == 4 || reg == 11 || reg == 18) {
            val voice = reg / 7
            val gate = (byteVal and 0x01) != 0
            if (gate && !prevGate[voice]) {
                envState[voice] = 1
            } else if (!gate && prevGate[voice]) {
                envState[voice] = 3
            }
            prevGate[voice] = gate
        }
    }

    fun renderFrameSamples(samplesCount: Int = sampleRate / 50): ShortArray {
        val out = ShortArray(samplesCount)
        if (!soundEnabled) {
            audioTrack?.write(out, 0, out.size, AudioTrack.WRITE_NON_BLOCKING)
            return out
        }

        val masterVolume = (registers[0x18] and 0x0F) / 15.0
        val voice3Off = (registers[0x18] and 0x80) != 0
        val invSampleRate = 1.0 / sampleRate.toDouble()
        val clockHz = 985248.0

        for (s in 0 until samplesCount) {
            var mixed = 0.0

            for (v in 0..2) {
                val base = v * 7
                val freqWord = registers[base] or (registers[base + 1] shl 8)
                val pwWord = (registers[base + 2] or ((registers[base + 3] and 0x0F) shl 8)).coerceIn(64, 4032)
                val control = registers[base + 4]
                val ad = registers[base + 5]
                val sr = registers[base + 6]

                val attackIdx = (ad ushr 4) and 0x0F
                val decayIdx = ad and 0x0F
                val sustainLevel = ((sr ushr 4) and 0x0F) / 15.0
                val releaseIdx = sr and 0x0F

                when (envState[v]) {
                    1 -> {
                        val rate = attackRates[attackIdx]
                        envLevel[v] += invSampleRate / rate
                        if (envLevel[v] >= 1.0) {
                            envLevel[v] = 1.0
                            envState[v] = 2
                        }
                    }
                    2 -> {
                        val rate = decayReleaseRates[decayIdx]
                        envLevel[v] -= invSampleRate / rate
                        if (envLevel[v] <= sustainLevel) {
                            envLevel[v] = sustainLevel
                        }
                    }
                    3 -> {
                        val rate = decayReleaseRates[releaseIdx]
                        envLevel[v] -= invSampleRate / rate
                        if (envLevel[v] <= 0.0) {
                            envLevel[v] = 0.0
                            envState[v] = 0
                        }
                    }
                }

                val freqHz = (freqWord.toDouble() * clockHz) / 16777216.0
                val step = freqHz * invSampleRate
                val oldPhase = phase[v]
                var newPhase = oldPhase + step
                if (newPhase >= 1.0) {
                    newPhase -= newPhase.toInt().toDouble()
                    if ((control and 0x80) != 0) {
                        val regVal = lfsr[v]
                        val bit = ((regVal ushr 22) xor (regVal ushr 17)) and 0x01
                        lfsr[v] = ((regVal shl 1) or bit) and 0x7FFFFF
                    }
                }
                phase[v] = newPhase

                if (v == 2 && voice3Off) {
                    continue
                }

                var waveSample = 0.0
                var waveCount = 0

                if ((control and 0x10) != 0) {
                    val tri = if (newPhase < 0.5) {
                        newPhase * 4.0 - 1.0
                    } else {
                        3.0 - newPhase * 4.0
                    }
                    waveSample += tri
                    waveCount++
                }
                if ((control and 0x20) != 0) {
                    val saw = newPhase * 2.0 - 1.0
                    waveSample += saw
                    waveCount++
                }
                if ((control and 0x40) != 0) {
                    val duty = pwWord / 4095.0
                    val pulse = if (newPhase < duty) 1.0 else -1.0
                    waveSample += pulse
                    waveCount++
                }
                if ((control and 0x80) != 0) {
                    val noise = ((lfsr[v] and 0xFFF) / 2048.0) - 1.0
                    waveSample += noise
                    waveCount++
                }

                if (waveCount > 0) {
                    waveSample /= waveCount.toDouble()
                    mixed += waveSample * envLevel[v]
                }
            }

            val sampleFloat = (mixed * 0.33 * masterVolume).coerceIn(-1.0, 1.0)
            out[s] = (sampleFloat * 28000.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        try {
            audioTrack?.write(out, 0, out.size, AudioTrack.WRITE_NON_BLOCKING)
        } catch (_: Throwable) {
        }
        return out
    }
}
