package com.drumsheesh.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.drumsheesh.core.audio.ProceduralDrumSynthesizer
import com.drumsheesh.core.engine.HiHatChokeTracker
import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.port.DrumAudioPort
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Low-latency Android [SoundPool] implementation of [DrumAudioPort] (`USAGE_GAME` /
 * `CONTENT_TYPE_SONIFICATION`, 16 polyphonic streams) backed by [ProceduralDrumSynthesizer]
 * WAV buffers cached in `context.cacheDir/drum_kits/`.
 *
 * Implements [DefaultLifecycleObserver] so active cymbal/drum streams are immediately stopped
 * on `ON_STOP` when the app goes to background.
 */
class SoundPoolDrumAudioAdapter(
    context: Context,
    private val hapticAdapter: AndroidHapticFeedbackAdapter = AndroidHapticFeedbackAdapter(context)
) : DrumAudioPort, DefaultLifecycleObserver {

    companion object {
        const val MAX_POLYPHONIC_STREAMS: Int = 16
    }

    private val appContext: Context = context.applicationContext
    private val chokeTracker = HiHatChokeTracker()
    private val loadedSoundIds = ConcurrentHashMap<Pair<DrumKitId, DrumPadId>, Int>()
    private val readySoundIds = ConcurrentHashMap<Int, Boolean>()
    private val activeStreamIds = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var soundPool: SoundPool? = createSoundPool()

    init {
        preloadKit(DrumKitId.ACOUSTIC_STUDIO)
    }

    private fun createSoundPool(): SoundPool {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        return SoundPool.Builder()
            .setMaxStreams(MAX_POLYPHONIC_STREAMS)
            .setAudioAttributes(attributes)
            .build()
            .apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        readySoundIds[sampleId] = true
                    }
                }
            }
    }

    @Synchronized
    override fun preloadKit(kitId: DrumKitId) {
        val pool = soundPool ?: createSoundPool().also { soundPool = it }
        val kitDir = File(appContext.cacheDir, "drum_kits/${kitId.id}")
        if (!kitDir.exists()) {
            kitDir.mkdirs()
        }

        for (padId in DrumPadId.entries) {
            val key = kitId to padId
            if (loadedSoundIds.containsKey(key)) continue

            val wavFile = File(kitDir, "${padId.name.lowercase()}.wav")
            if (!wavFile.exists() || wavFile.length() <= ProceduralDrumSynthesizer.WAV_HEADER_BYTES) {
                val wavBytes = ProceduralDrumSynthesizer.synthesizeWavBytes(kitId, padId)
                wavFile.writeBytes(wavBytes)
            }

            val soundId = pool.load(wavFile.absolutePath, 1)
            if (soundId > 0) {
                loadedSoundIds[key] = soundId
            }
        }
    }

    override fun triggerPad(kitId: DrumKitId, padId: DrumPadId, velocity: Float) {
        val clampedVel = velocity.coerceIn(0.1f, 1.0f)
        val pool = soundPool ?: return

        // Ensure kit is preloaded
        val key = kitId to padId
        if (!loadedSoundIds.containsKey(key)) {
            preloadKit(kitId)
        }

        // Enforce immediate Hi-Hat Choke when HI_HAT_CLOSED is triggered
        val streamsToChoke = chokeTracker.onPadTriggered(padId)
        for (streamId in streamsToChoke) {
            pool.stop(streamId)
            activeStreamIds.remove(streamId)
        }

        val soundId = loadedSoundIds[key] ?: return
        val priority = if (padId == DrumPadId.KICK_RIGHT || padId == DrumPadId.SNARE) 2 else 1
        val streamId = pool.play(soundId, clampedVel, clampedVel, priority, 0, 1.0f)
        if (streamId > 0) {
            activeStreamIds.add(streamId)
            if (padId == DrumPadId.HI_HAT_OPEN) {
                chokeTracker.recordOpenHiHatStream(streamId)
            }
        }

        hapticAdapter.pulseForHit(clampedVel)
    }

    override fun stopAllStreams() {
        val pool = soundPool ?: return
        for (streamId in activeStreamIds) {
            pool.stop(streamId)
        }
        activeStreamIds.clear()
        chokeTracker.clear()
    }

    override fun onStop(owner: LifecycleOwner) {
        stopAllStreams()
    }

    @Synchronized
    override fun release() {
        stopAllStreams()
        loadedSoundIds.clear()
        readySoundIds.clear()
        soundPool?.release()
        soundPool = null
    }
}

/**
 * Provides crisp tactile vibration pulse on drum pad pointer-down using `android.permission.VIBRATE`.
 */
class AndroidHapticFeedbackAdapter(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun pulseForHit(velocity: Float) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val amplitude = (velocity.coerceIn(0.4f, 1.0f) * 200f).toInt().coerceIn(1, 255)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(8L, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(8L)
            }
        } catch (_: SecurityException) {
            // Gracefully ignore if vibration is restricted by OS battery saver
        }
    }
}
