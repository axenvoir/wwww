package com.example.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.game.model.SoundEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    fun play(event: SoundEvent) {
        if (!isEnabled) return
        scope.launch {
            runCatching {
                val samples = pcmCache[event] ?: return@runCatching
                val bufferBytes = samples.size * 2
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

                track.write(samples, 0, samples.size)
                track.play()
                val durationMs = ((samples.size * 1000L) / sampleRate) + 80L
                Thread.sleep(durationMs)
                track.stop()
                track.release()
            }
        }
    }

    fun release() {
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
}
