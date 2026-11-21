package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.model.SoundEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure native Android procedural PCM audio synthesizer for Stage 5 match atmosphere:
 * Referee whistles, leather ball kicks/shots, metallic goalpost clanks, slide tackles,
 * goalkeeper deflections, and stadium crowd goal roars. Zero external assets or internet required.
 */
class MatchAudioEngine {

    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val soundEvents = Channel<SoundEvent>(
        capacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Volatile
    var isEnabled: Boolean = true

    // Pre-synthesized PCM buffers cached in memory for zero-GC low-latency playback
    private val pcmCache: Map<SoundEvent, ShortArray> by lazy {
        mapOf(
            SoundEvent.WHISTLE_SHORT to synthWhistle(durationSec = 0.28f, doubleBlast = false),
            SoundEvent.WHISTLE_FULL to synthWhistle(durationSec = 0.72f, doubleBlast = true),
            SoundEvent.KICK_PASS to synthKick(durationSec = 0.14f, pitchHz = 115f, punch = 0.65f),
            SoundEvent.KICK_SHOT to synthKick(durationSec = 0.22f, pitchHz = 90f, punch = 1.0f),
            SoundEvent.POST_HIT to synthPostClank(),
            SoundEvent.TACKLE to synthTurfSlide(),
            SoundEvent.GK_SAVE to synthKick(durationSec = 0.18f, pitchHz = 155f, punch = 0.85f),
            SoundEvent.GOAL_ROAR to synthCrowdRoar()
        )
    }

    init {
        scope.launch {
            for (event in soundEvents) {
                if (isEnabled) playNow(event)
            }
        }
    }

    fun play(event: SoundEvent) {
        if (!isEnabled) return
        soundEvents.trySend(event)
    }

    private suspend fun playNow(event: SoundEvent) {
        val samples = pcmCache[event] ?: return
        var track: AudioTrack? = null
        try {
            val createdTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .build()
            track = createdTrack
            if (createdTrack.state != AudioTrack.STATE_INITIALIZED) return

            val writtenSamples = createdTrack.write(samples, 0, samples.size)
            if (writtenSamples <= 0) return

            createdTrack.play()
            delay((writtenSamples * 1000L / sampleRate) + 80L)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Audio hardware may be unavailable while the app is backgrounded or interrupted.
        } finally {
            track?.let { audioTrack ->
                runCatching {
                    if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) audioTrack.stop()
                }
                runCatching { audioTrack.release() }
            }
        }
    }

    fun release() {
        soundEvents.close()
        scope.cancel()
    }

    private fun synthWhistle(durationSec: Float, doubleBlast: Boolean): ShortArray {
        val n = (sampleRate * durationSec).toInt()
        val out = ShortArray(n)
        val rng = Random(42)
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = if (doubleBlast) {
                when {
                    t < 0.18f -> sin(PI * t / 0.18).toFloat()
                    t < 0.26f -> 0f
                    t < 0.44f -> sin(PI * (t - 0.26) / 0.18).toFloat()
                    t < 0.50f -> 0f
                    else -> sin(PI * (t - 0.50) / (durationSec - 0.50)).toFloat().coerceAtLeast(0f)
                }
            } else {
                val attack = (t / 0.02f).coerceAtMost(1f)
                val release = ((durationSec - t) / 0.04f).coerceIn(0f, 1f)
                attack * release
            }
            // Authentic pea-whistle trill (dual sine modulated at ~34 Hz)
            val trill = 1f + 0.18f * sin(2.0 * PI * 34.0 * t).toFloat()
            val f1 = 2780f * (1f + 0.015f * sin(2.0 * PI * 34.0 * t).toFloat())
            val f2 = 3010f
            val wave = (0.65f * sin(2.0 * PI * f1 * t).toFloat() +
                0.28f * sin(2.0 * PI * f2 * t).toFloat() +
                0.07f * (rng.nextFloat() * 2f - 1f)) * trill * env * 0.45f
            out[i] = (wave.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthKick(durationSec: Float, pitchHz: Float, punch: Float): ShortArray {
        val n = (sampleRate * durationSec).toInt()
        val out = ShortArray(n)
        val rng = Random(11)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val freq = pitchHz * exp(-t * 14f) + 38f
            phase += 2.0 * PI * freq / sampleRate
            val thump = sin(phase).toFloat() * exp(-t * 22f)
            val snap = (rng.nextFloat() * 2f - 1f) * exp(-t * 75f) * 0.45f
            val sample = (thump + snap) * punch * 0.72f
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthPostClank(): ShortArray {
        val durationSec = 0.42f
        val n = (sampleRate * durationSec).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 8.5f)
            val wave = (0.5f * sin(2.0 * PI * 420.0 * t).toFloat() +
                0.35f * sin(2.0 * PI * 890.0 * t).toFloat() +
                0.22f * sin(2.0 * PI * 1410.0 * t).toFloat()) * env * 0.65f
            out[i] = (wave.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthTurfSlide(): ShortArray {
        val durationSec = 0.24f
        val n = (sampleRate * durationSec).toInt()
        val out = ShortArray(n)
        val rng = Random(99)
        var lp = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val env = sin(PI * t / durationSec).toFloat().coerceAtLeast(0f) * exp(-t * 3.5f)
            val white = rng.nextFloat() * 2f - 1f
            lp += (white - lp) * 0.22f
            val sample = lp * env * 0.55f
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthCrowdRoar(): ShortArray {
        val durationSec = 1.65f
        val n = (sampleRate * durationSec).toInt()
        val out = ShortArray(n)
        val rng = Random(777)
        var lp1 = 0f
        var lp2 = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / sampleRate
            val swell = (t / 0.22f).coerceAtMost(1f) * ((durationSec - t) / 0.55f).coerceIn(0f, 1f)
            val white = rng.nextFloat() * 2f - 1f
            lp1 += (white - lp1) * 0.14f
            lp2 += (lp1 - lp2) * 0.18f
            val cheerMod = 0.85f + 0.15f * sin(2.0 * PI * 4.5 * t).toFloat()
            val sample = (lp2 * 2.4f + lp1 * 0.5f) * swell * cheerMod * 0.58f
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    @Volatile private var ambienceTrack: AudioTrack? = null
    @Volatile private var ambienceVolume: Float = 0.24f

    fun startStadiumAmbience() {
        if (!isEnabled) return
        if (ambienceTrack != null) return
        runCatching {
            val pcm = synthAmbientCrowdLoop(sampleRate)
            val bufferBytes = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bufferBytes)
                .build()

            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size, -1)
            track.setVolume(if (isEnabled) ambienceVolume else 0f)
            track.play()
            ambienceTrack = track
        }.onFailure {
            ambienceTrack = null
        }
    }

    fun setAmbienceExcitement(excitement: Float) {
        val clamped = excitement.coerceIn(0f, 1f)
        val volume = 0.16f + (0.55f - 0.16f) * clamped
        ambienceVolume = volume
        if (!isEnabled) return
        runCatching {
            ambienceTrack?.setVolume(volume)
        }
    }

    fun stopStadiumAmbience() {
        val track = ambienceTrack ?: return
        ambienceTrack = null
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.release()
        }
    }

    internal fun synthAmbientCrowdLoop(sampleRate: Int = 22050): ShortArray {
        val durationSec = 3.5f
        val n = (sampleRate * durationSec).toInt()
        if (n <= 0) return ShortArray(0)
        val fadeSamples = (sampleRate * 0.20f).toInt().coerceAtMost(n / 4)
        val raw = FloatArray(n + fadeSamples)
        val rng = Random(1337)
        var b0 = 0f
        var b1 = 0f
        var b2 = 0f
        var brown = 0f
        for (i in 0 until raw.size) {
            val white = rng.nextFloat() * 2f - 1f
            b0 = 0.99765f * b0 + white * 0.0990460f
            b1 = 0.96300f * b1 + white * 0.2965164f
            b2 = 0.57000f * b2 + white * 1.0526913f
            val pink = (b0 + b1 + b2 + white * 0.1848f) * 0.18f
            brown = (brown + 0.02f * white) / 1.02f
            val t = i.toFloat() / sampleRate
            val phase1 = 2.0 * PI * 1.0 * ((i % n).toDouble() / n)
            val phase2 = 2.0 * PI * 3.0 * ((i % n).toDouble() / n)
            val murmur = 0.78f + 0.14f * sin(phase1).toFloat() + 0.08f * sin(phase2).toFloat()
            val lowChant = 0.06f * sin(2.0 * PI * 110.0 * t).toFloat() * (0.5f + 0.5f * sin(phase2).toFloat())
            raw[i] = (pink * 0.65f + brown * 1.35f) * murmur + lowChant
        }
        val out = ShortArray(n)
        for (i in 0 until n) {
            val sample = if (i < fadeSamples && fadeSamples > 0) {
                val alpha = i.toFloat() / fadeSamples
                raw[i] * alpha + raw[n + i] * (1f - alpha)
            } else {
                raw[i]
            }
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE * 0.45f).toInt().toShort()
        }
        return out
    }
}
}
