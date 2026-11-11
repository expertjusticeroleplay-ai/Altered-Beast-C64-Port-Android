package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class SidAudioSynthesizer(initialSampleRate: Int = 48000) {

    private val stateLock = Any()

    @Volatile
    var activeSampleRate: Int = initialSampleRate.coerceIn(22050, 96000)
        private set

    @Volatile
    private var nativeFramesPerBurst: Int = 240

    private val registers = IntArray(32)
    private val phase = DoubleArray(3)
    private val noisePhase = DoubleArray(3)
    private val envLevel = DoubleArray(3)
    private val smoothEnv = DoubleArray(3)
    private val envState = IntArray(3)
    private val prevGate = BooleanArray(3)
    private val gateTriggerPending = BooleanArray(3)
    private val smoothDuty = DoubleArray(3) { 0.5 }
    private val lfsr = IntArray(3) { 0x7FFFF8 }
    private val noiseSampleHold = DoubleArray(3)
    private val noiseFilterState = DoubleArray(3)
    private var randomReadLfsr: Int = 0x5A3C9B

    private var svfLow = 0.0
    private var svfBand = 0.0
    private var smoothMasterVol = 0.0
    private var dcBlockX = 0.0
    private var dcBlockY = 0.0
    private var lpStage1 = 0.0
    private var lpStage2 = 0.0

    private var envSmoothCoeff = 0.03
    private var dutySmoothCoeff = 0.015
    private var volSmoothCoeff = 0.015
    private var noiseLpCoeff = 0.35
    private var analogLpCoeff = 0.65
    private var dcPole = 0.997

    @Volatile
    private var osc3Readback: Int = 0x55
    @Volatile
    private var env3Readback: Int = 0x00

    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    @Volatile
    private var isStreamingActive: Boolean = false

    @Volatile
    private var isAppInForeground: Boolean = true

    @Volatile
    var isPaused: Boolean = false
        private set

    @Volatile
    var soundEnabled: Boolean = true

    @Volatile
    private var lastFramePreview: ShortArray = ShortArray(activeSampleRate / 50)

    private val attackRates = doubleArrayOf(
        0.002, 0.008, 0.016, 0.024, 0.038, 0.056, 0.068, 0.080,
        0.100, 0.250, 0.500, 0.800, 1.000, 3.000, 5.000, 8.000
    )

    private val decayReleaseRates = doubleArrayOf(
        0.006, 0.024, 0.048, 0.072, 0.114, 0.168, 0.204, 0.240,
        0.300, 0.750, 1.500, 2.400, 3.000, 9.000, 15.000, 24.000
    )

    init {
        recomputeFilterCoefficients(activeSampleRate)
    }

    fun configureForDevice(context: Context?) {
        var detectedRate = 0
        var detectedBurst = 0
        if (context != null) {
            runCatching {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    detectedRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
                    detectedBurst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 0
                }
            }
        }
        if (detectedRate <= 0) {
            runCatching {
                detectedRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
            }
        }
        val finalRate = if (detectedRate in 22050..96000) detectedRate else activeSampleRate
        val defaultBurst = (finalRate / 200).coerceIn(128, 512)
        val finalBurst = if (detectedBurst in 64..1024) detectedBurst else defaultBurst
        synchronized(stateLock) {
            activeSampleRate = finalRate
            nativeFramesPerBurst = finalBurst
            recomputeFilterCoefficients(finalRate)
            val previewLen = (finalRate / 50).coerceAtLeast(1)
            if (lastFramePreview.size != previewLen) {
                lastFramePreview = ShortArray(previewLen)
            }
        }
    }

    private fun recomputeFilterCoefficients(rate: Int) {
        val sr = rate.toDouble().coerceAtLeast(22050.0)
        envSmoothCoeff = (1.0 - exp(-1.0 / (sr * 0.0007))).coerceIn(0.005, 0.25)
        dutySmoothCoeff = (1.0 - exp(-1.0 / (sr * 0.0020))).coerceIn(0.002, 0.15)
        volSmoothCoeff = (1.0 - exp(-1.0 / (sr * 0.0025))).coerceIn(0.002, 0.15)
        noiseLpCoeff = (1.0 - exp(-2.0 * PI * 3600.0 / sr)).coerceIn(0.10, 0.85)
        analogLpCoeff = (1.0 - exp(-2.0 * PI * 9200.0 / sr)).coerceIn(0.25, 0.90)
        dcPole = (1.0 - (2.0 * PI * 22.0 / sr)).coerceIn(0.990, 0.9995)
    }

    fun setAppForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    fun start(context: Context? = null) {
        if (isStreamingActive && audioTrack != null) return
        if (context != null) {
            configureForDevice(context)
        } else {
            runCatching {
                val nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
                if (nativeRate in 22050..96000 && nativeRate != activeSampleRate) {
                    synchronized(stateLock) {
                        activeSampleRate = nativeRate
                        nativeFramesPerBurst = (nativeRate / 200).coerceIn(128, 512)
                        recomputeFilterCoefficients(nativeRate)
                        lastFramePreview = ShortArray(nativeRate / 50)
                    }
                }
            }
        }

        val sr = activeSampleRate
        val burstFrames = nativeFramesPerBurst.coerceIn(128, 512)
        val minBufBytes = runCatching {
            AudioTrack.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
        }.getOrDefault(sr / 5)

        val safeMinBytes = if (minBufBytes > 0) minBufBytes else (sr / 10) * 2
        val targetBytes = max(safeMinBytes * 4, burstFrames * 2 * 16)
        val chunkBytes = burstFrames * 2
        val alignedBufferBytes = ((targetBytes + chunkBytes - 1) / chunkBytes) * chunkBytes

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(alignedBufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        audioTrack = track
        isStreamingActive = true

        val pumpThread = Thread({
            runCatching {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            }
            val chunkSamples = burstFrames
            val workChunk = ShortArray(chunkSamples)
            val silenceChunk = ShortArray(chunkSamples)
            val previewSize = (sr / 50).coerceAtLeast(1)
            val rollingPreview = ShortArray(previewSize)
            var previewWritePos = 0
            var lastUnderrunCount = 0

            runCatching {
                track.write(silenceChunk, 0, chunkSamples, AudioTrack.WRITE_BLOCKING)
                track.write(silenceChunk, 0, chunkSamples, AudioTrack.WRITE_BLOCKING)
                track.play()
            }

            while (isStreamingActive) {
                if (isPaused) {
                    runCatching {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.write(silenceChunk, 0, chunkSamples, AudioTrack.WRITE_BLOCKING)
                        } else {
                            Thread.sleep(5L)
                        }
                    }
                    continue
                }
                if (!soundEnabled || !isAppInForeground) {
                    synchronized(stateLock) {
                        synthesizeSamplesLocked(workChunk, chunkSamples, muted = true)
                    }
                    runCatching {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.write(silenceChunk, 0, chunkSamples, AudioTrack.WRITE_BLOCKING)
                        } else {
                            Thread.sleep(5L)
                        }
                    }
                    continue
                }

                synchronized(stateLock) {
                    synthesizeSamplesLocked(workChunk, chunkSamples, muted = false)
                }

                var copied = 0
                while (copied < chunkSamples) {
                    val space = previewSize - previewWritePos
                    val toCopy = min(chunkSamples - copied, space)
                    System.arraycopy(workChunk, copied, rollingPreview, previewWritePos, toCopy)
                    previewWritePos = (previewWritePos + toCopy) % previewSize
                    copied += toCopy
                }
                lastFramePreview = rollingPreview

                runCatching {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val currentUnderruns = track.underrunCount
                        if (currentUnderruns > lastUnderrunCount) {
                            lastUnderrunCount = currentUnderruns
                            track.write(workChunk, 0, chunkSamples, AudioTrack.WRITE_NON_BLOCKING)
                        }
                    }
                    track.write(workChunk, 0, chunkSamples, AudioTrack.WRITE_BLOCKING)
                }.onFailure {
                    runCatching { Thread.sleep(4L) }
                }
            }
        }, "GZBeast-SID-AudioThread")
        pumpThread.isDaemon = true
        pumpThread.start()
        audioThread = pumpThread
    }

    fun stop() {
        isStreamingActive = false
        val worker = audioThread
        audioThread = null
        if (worker != null) {
            runCatching {
                worker.interrupt()
                worker.join(150L)
            }
        }
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            runCatching {
                track.pause()
                track.flush()
                track.release()
            }
        }
    }

    fun reset() {
        synchronized(stateLock) {
            for (i in 0 until 24) {
                registers[i] = 0
            }
            for (v in 0..2) {
                envState[v] = 3
                prevGate[v] = false
                gateTriggerPending[v] = false
                smoothDuty[v] = 0.5
                lfsr[v] = 0x7FFFF8
            }
            svfLow = 0.0
            svfBand = 0.0
        }
    }

    fun writeRegister(offset: Int, value: Int) {
        val reg = offset and 0x1F
        val byteVal = value and 0xFF
        synchronized(stateLock) {
            registers[reg] = byteVal
            if (reg == 4 || reg == 11 || reg == 18) {
                val voice = reg / 7
                if ((byteVal and 0x08) != 0) {
                    phase[voice] = 0.0
                    noisePhase[voice] = 0.0
                    lfsr[voice] = 0x7FFFF8
                }
                val gate = (byteVal and 0x01) != 0
                if (gate && !prevGate[voice]) {
                    envState[voice] = 1
                    gateTriggerPending[voice] = true
                } else if (!gate && prevGate[voice]) {
                    if (!gateTriggerPending[voice]) {
                        envState[voice] = 3
                    }
                }
                prevGate[voice] = gate
            }
        }
    }

    fun readRegister(offset: Int): Int {
        val reg = offset and 0x1F
        synchronized(stateLock) {
            if (reg == 0x1B) {
                val bit = ((randomReadLfsr ushr 22) xor (randomReadLfsr ushr 17)) and 0x01
                randomReadLfsr = ((randomReadLfsr shl 1) or bit) and 0x7FFFFF
                return (osc3Readback xor (randomReadLfsr and 0xFF)) and 0xFF
            }
            if (reg == 0x1C) {
                return env3Readback and 0xFF
            }
            return registers[reg] and 0xFF
        }
    }

    fun playSynthesizedNote(
        voice: Int,
        freqHz: Double,
        waveform: Int = 0x21,
        attackDecay: Int = 0x19,
        sustainRelease: Int = 0xF5,
        pulseWidth: Int = 2048
    ) {
        val v = voice.coerceIn(0, 2)
        val base = v * 7
        synchronized(stateLock) {
            if ((registers[0x18] and 0x0F) == 0) {
                registers[0x18] = (registers[0x18] and 0xF0) or 0x0F
            }
            if (freqHz <= 0.0) {
                writeRegister(base + 4, waveform and 0xFE)
                return
            }
            val clockHz = 985248.0
            val freqWord = ((freqHz * 16777216.0) / clockHz).toInt().coerceIn(0, 0xFFFF)
            val pw = pulseWidth.coerceIn(128, 3968)
            writeRegister(base, freqWord and 0xFF)
            writeRegister(base + 1, (freqWord ushr 8) and 0xFF)
            writeRegister(base + 2, pw and 0xFF)
            writeRegister(base + 3, (pw ushr 8) and 0x0F)
            writeRegister(base + 5, attackDecay and 0xFF)
            writeRegister(base + 6, sustainRelease and 0xFF)
            writeRegister(base + 4, waveform and 0xFE)
            writeRegister(base + 4, waveform or 0x01)
        }
    }

    fun renderFrameSamples(samplesCount: Int = activeSampleRate / 50): ShortArray {
        val targetCount = samplesCount.coerceAtLeast(1)
        if (isPaused) {
            return ShortArray(targetCount)
        }
        if (isStreamingActive) {
            val preview = lastFramePreview
            if (preview.size == targetCount) {
                return preview.copyOf()
            }
            val resized = ShortArray(targetCount)
            val copyLen = min(preview.size, targetCount)
            System.arraycopy(preview, 0, resized, 0, copyLen)
            return resized
        }

        val out = ShortArray(targetCount)
        if (!soundEnabled) {
            return out
        }
        synchronized(stateLock) {
            synthesizeSamplesLocked(out, targetCount, muted = false)
        }
        return out
    }

    private fun polyBlep(t: Double, dt: Double): Double {
        if (dt <= 0.0 || dt >= 0.5) return 0.0
        return if (t < dt) {
            val x = t / dt
            x + x - x * x - 1.0
        } else if (t > 1.0 - dt) {
            val x = (t - 1.0) / dt
            x * x + x + x + 1.0
        } else {
            0.0
        }
    }

    private fun synthesizeSamplesLocked(out: ShortArray, samplesCount: Int, muted: Boolean) {
        val sr = activeSampleRate.toDouble().coerceAtLeast(22050.0)
        val invSampleRate = 1.0 / sr
        val clockHz = 985248.0

        val reg15 = registers[0x15] and 0x07
        val reg16 = registers[0x16] and 0xFF
        val reg17 = registers[0x17] and 0xFF
        val reg18 = registers[0x18] and 0xFF

        val targetMasterVolume = if (muted) 0.0 else (reg18 and 0x0F) / 15.0
        val voice3Off = (reg18 and 0x80) != 0
        val filterMask = reg17 and 0x07
        val filterModeMask = reg18 and 0x70

        val fcWord = (reg16 shl 3) or reg15
        val cutoffHz = (30.0 + (fcWord / 2047.0) * 9500.0).coerceIn(30.0, sr * 0.42)
        val svfF = (2.0 * sin(PI * cutoffHz * invSampleRate)).coerceIn(0.005, 0.72)
        val resNib = (reg17 ushr 4) and 0x0F
        val svfQ = (1.4 - (resNib / 15.0) * 0.95).coerceIn(0.45, 1.4)

        val wrappedVoice = BooleanArray(3)

        for (s in 0 until samplesCount) {
            smoothMasterVol += (targetMasterVolume - smoothMasterVol) * volSmoothCoeff

            for (v in 0..2) {
                if (gateTriggerPending[v]) {
                    envState[v] = 1
                    gateTriggerPending[v] = false
                }
                val base = v * 7
                val freqWord = registers[base] or (registers[base + 1] shl 8)
                val control = registers[base + 4]
                val ad = registers[base + 5]
                val srReg = registers[base + 6]

                val attackIdx = (ad ushr 4) and 0x0F
                val decayIdx = ad and 0x0F
                val sustainLevel = ((srReg ushr 4) and 0x0F) / 15.0
                val releaseIdx = srReg and 0x0F

                when (envState[v]) {
                    1 -> {
                        val rate = attackRates[attackIdx]
                        envLevel[v] += invSampleRate / rate
                        if (envLevel[v] >= 1.0) {
                            envLevel[v] = 1.0
                            envState[v] = if (prevGate[v]) 2 else 3
                        }
                    }
                    2 -> {
                        val rate = decayReleaseRates[decayIdx]
                        if (envLevel[v] > sustainLevel) {
                            envLevel[v] -= invSampleRate / rate
                            if (envLevel[v] < sustainLevel) {
                                envLevel[v] = sustainLevel
                            }
                        } else {
                            envLevel[v] = sustainLevel
                        }
                        if (!prevGate[v]) {
                            envState[v] = 3
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

                smoothEnv[v] += (envLevel[v] - smoothEnv[v]) * envSmoothCoeff

                if ((control and 0x08) != 0) {
                    phase[v] = 0.0
                    wrappedVoice[v] = false
                } else {
                    val freqHz = (freqWord.toDouble() * clockHz) / 16777216.0
                    val step = (freqHz * invSampleRate).coerceIn(0.0, 0.48)
                    var newPhase = phase[v] + step
                    if (newPhase >= 1.0) {
                        newPhase -= newPhase.toInt().toDouble()
                        wrappedVoice[v] = true
                    } else {
                        wrappedVoice[v] = false
                    }
                    phase[v] = newPhase

                    var nPhase = noisePhase[v] + step * 16.0
                    if (nPhase >= 1.0) {
                        val ticks = nPhase.toInt().coerceIn(1, 16)
                        nPhase -= ticks.toDouble()
                        var regVal = lfsr[v]
                        for (t in 0 until ticks) {
                            val bit = ((regVal ushr 22) xor (regVal ushr 17)) and 0x01
                            regVal = ((regVal shl 1) or bit) and 0x7FFFFF
                        }
                        lfsr[v] = regVal
                        val tapByte =
                            (((regVal ushr 20) and 1) shl 7) or
                            (((regVal ushr 18) and 1) shl 6) or
                            (((regVal ushr 14) and 1) shl 5) or
                            (((regVal ushr 11) and 1) shl 4) or
                            (((regVal ushr 9) and 1) shl 3) or
                            (((regVal ushr 5) and 1) shl 2) or
                            (((regVal ushr 2) and 1) shl 1) or
                            (regVal and 1)
                        noiseSampleHold[v] = (tapByte / 127.5) - 1.0
                    }
                    noisePhase[v] = nPhase
                }
            }

            for (v in 0..2) {
                val control = registers[v * 7 + 4]
                if ((control and 0x02) != 0) {
                    val modVoice = (v + 2) % 3
                    if (wrappedVoice[modVoice]) {
                        phase[v] = 0.0
                    }
                }
            }

            var directSum = 0.0
            var filterInput = 0.0

            for (v in 0..2) {
                val base = v * 7
                val freqWord = registers[base] or (registers[base + 1] shl 8)
                val pwWord = registers[base + 2] or ((registers[base + 3] and 0x0F) shl 8)
                val control = registers[base + 4]
                val freqHz = (freqWord.toDouble() * clockHz) / 16777216.0
                val step = (freqHz * invSampleRate).coerceIn(0.0, 0.48)
                val curPhase = phase[v]

                var waveSample = 0.0
                var waveCount = 0

                if ((control and 0x10) != 0) {
                    var tri = if (curPhase < 0.5) {
                        curPhase * 4.0 - 1.0
                    } else {
                        3.0 - curPhase * 4.0
                    }
                    if ((control and 0x04) != 0) {
                        val modVoice = (v + 2) % 3
                        if (phase[modVoice] >= 0.5) {
                            tri = -tri
                        }
                    }
                    waveSample += tri
                    waveCount++
                }

                if ((control and 0x20) != 0) {
                    var saw = curPhase * 2.0 - 1.0
                    saw -= polyBlep(curPhase, step)
                    waveSample += saw
                    waveCount++
                }

                if ((control and 0x40) != 0) {
                    val targetDuty = (pwWord / 4095.0).coerceIn(0.04, 0.96)
                    smoothDuty[v] += (targetDuty - smoothDuty[v]) * dutySmoothCoeff
                    val duty = smoothDuty[v]
                    var pulse = if (curPhase < duty) 1.0 else -1.0
                    pulse += polyBlep(curPhase, step)
                    val fallingPhase = if (curPhase >= duty) curPhase - duty else curPhase + 1.0 - duty
                    pulse -= polyBlep(fallingPhase, step)
                    pulse -= (2.0 * duty - 1.0) * 0.45
                    waveSample += pulse
                    waveCount++
                }

                if ((control and 0x80) != 0) {
                    noiseFilterState[v] += (noiseSampleHold[v] - noiseFilterState[v]) * noiseLpCoeff
                    waveSample += noiseFilterState[v] * 0.72
                    waveCount++
                }

                if (v == 2) {
                    val oscNorm = if (waveCount > 0) (waveSample / waveCount.toDouble()) else (curPhase * 2.0 - 1.0)
                    osc3Readback = (((oscNorm.coerceIn(-1.0, 1.0) + 1.0) * 127.5).toInt()) and 0xFF
                    env3Readback = ((smoothEnv[2].coerceIn(0.0, 1.0) * 255.0).toInt()) and 0xFF
                }

                if (v == 2 && voice3Off && (filterMask and 0x04) == 0) {
                    continue
                }

                if (waveCount > 0) {
                    val voiceOut = (waveSample / waveCount.toDouble()) * smoothEnv[v]
                    if ((filterMask and (1 shl v)) != 0 && filterModeMask != 0) {
                        filterInput += voiceOut
                    } else {
                        directSum += voiceOut
                    }
                }
            }

            val mixed = if (filterMask != 0 && filterModeMask != 0) {
                svfLow += svfF * svfBand
                val svfHigh = filterInput - svfLow - svfQ * svfBand
                svfBand += svfF * svfHigh
                svfLow = svfLow.coerceIn(-4.0, 4.0)
                svfBand = svfBand.coerceIn(-4.0, 4.0)

                var filteredSum = 0.0
                var modeCount = 0
                if ((filterModeMask and 0x10) != 0) {
                    filteredSum += svfLow
                    modeCount++
                }
                if ((filterModeMask and 0x20) != 0) {
                    filteredSum += svfBand
                    modeCount++
                }
                if ((filterModeMask and 0x40) != 0) {
                    filteredSum += svfHigh
                    modeCount++
                }
                if (modeCount > 1) {
                    filteredSum /= modeCount.toDouble()
                }
                directSum + filteredSum
            } else {
                directSum
            }

            if (muted) {
                out[s] = 0
            } else {
                val rawSignal = mixed * 0.30 * smoothMasterVol
                val dcOut = rawSignal - dcBlockX + dcPole * dcBlockY
                dcBlockX = rawSignal
                dcBlockY = dcOut

                lpStage1 += (dcOut - lpStage1) * analogLpCoeff
                lpStage2 += (lpStage1 - lpStage2) * analogLpCoeff

                val driven = lpStage2 * 1.15
                val softLimited = driven / (1.0 + abs(driven) * 0.38)
                val finalFloat = softLimited.coerceIn(-0.98, 0.98)
                out[s] = (finalFloat * 25500.0).toInt().toShort()
            }
        }
    }
}
