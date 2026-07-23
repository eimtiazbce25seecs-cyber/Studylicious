package com.example.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundManager {
    private var toneGenerator: ToneGenerator? = null
    private var ambientJob: Job? = null
    private var activeAudioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            toneGenerator = null
        }
    }

    fun playTimerCompletionChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
        } catch (e: Exception) {
            // Fallback
        }
    }

    fun playBlockTransitionChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
        } catch (e: Exception) {
            // Fallback
        }
    }

    fun playDuelActionBeep() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_PIP, 150)
        } catch (e: Exception) {
            // Fallback
        }
    }

    fun startAmbientSoundscape(soundName: String) {
        stopAmbientSoundscape()

        ambientJob = scope.launch {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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

            activeAudioTrack = track
            track.play()

            val buffer = ShortArray(1024)
            var sampleIndex = 0L

            while (isActive) {
                for (i in buffer.indices) {
                    val t = sampleIndex / sampleRate.toDouble()
                    sampleIndex++

                    val sampleValue: Double = when {
                        soundName.contains("Fan Hum", ignoreCase = true) -> {
                            // Low-frequency fan hum (60 Hz + 120 Hz) with filtered noise
                            val hum = sin(2.0 * Math.PI * 60.0 * t) * 0.3 + sin(2.0 * Math.PI * 120.0 * t) * 0.15
                            val noise = (Math.random() * 2.0 - 1.0) * 0.1
                            (hum + noise) * 0.25
                        }
                        soundName.contains("Alpha Waves", ignoreCase = true) -> {
                            // Deep 220Hz tone modulated at 10Hz Alpha rhythm
                            val carrier = sin(2.0 * Math.PI * 220.0 * t)
                            val alphaMod = (sin(2.0 * Math.PI * 10.0 * t) + 1.0) * 0.5
                            carrier * alphaMod * 0.2
                        }
                        soundName.contains("Exam-Hall", ignoreCase = true) -> {
                            // Soft tick every 1.0 second
                            val secFraction = (sampleIndex % sampleRate) / sampleRate.toDouble()
                            val tick = if (secFraction < 0.005) {
                                sin(2.0 * Math.PI * 1200.0 * t) * (1.0 - secFraction / 0.005)
                            } else 0.0
                            val roomNoise = (Math.random() * 2.0 - 1.0) * 0.02
                            (tick * 0.3) + roomNoise
                        }
                        soundName.contains("Nature Rustle", ignoreCase = true) -> {
                            // Gentle wind/leaf rustle amplitude modulation
                            val windMod = (sin(2.0 * Math.PI * 0.3 * t) + 1.0) * 0.5
                            val pinkNoise = (Math.random() * 2.0 - 1.0) * windMod * 0.15
                            pinkNoise
                        }
                        else -> {
                            // Soft warm background noise
                            (Math.random() * 2.0 - 1.0) * 0.05
                        }
                    }

                    // Convert to 16-bit PCM short
                    val pcmVal = (sampleValue.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                    buffer[i] = pcmVal
                }
                track.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stopAmbientSoundscape() {
        ambientJob?.cancel()
        ambientJob = null
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        activeAudioTrack = null
    }
}
