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
import com.drumsheesh.core.model.DrumSoundProfile
import com.drumsheesh.core.port.DrumAudioPort
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Low-latency Android [SoundPool] implementation of [DrumAudioPort] (`USAGE_GAME` /
 * `CONTENT_TYPE_SONIFICATION`, 16 polyphonic streams) backed by [ProceduralDrumSynthesizer]
 * WAV buffers cached in `context.cacheDir/drum_kits/`.
 *
 * Supports live per-pad volume & pitch tuning plus background-threaded room/punch/warmth
 * buffer regeneration without interrupting active pad triggers.
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
        private const val SYNTH_CACHE_VERSION: String = "v5"
    }

    private data class PendingSwap(
        val kitId: DrumKitId,
        val padId: DrumPadId,
        val synthKey: String
    )

    private val appContext: Context = context.applicationContext
    private val chokeTracker = HiHatChokeTracker()
    private val loadedSoundIds = ConcurrentHashMap<Pair<DrumKitId, DrumPadId>, Int>()
    private val readySoundIds = ConcurrentHashMap<Int, Boolean>()
    private val activeStreamIds = ConcurrentHashMap.newKeySet<Int>()
    private val profilesByKit = ConcurrentHashMap<DrumKitId, DrumSoundProfile>()
    private val requestedSynthKeys = ConcurrentHashMap<DrumKitId, String>()
    private val loadedSynthKeys = ConcurrentHashMap<DrumKitId, String>()
    private val pendingSwaps = ConcurrentHashMap<Int, PendingSwap>()
    private val synthExecutor: ExecutorService = Executors.newSingleThreadExecutor()

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
                setOnLoadCompleteListener { pool, sampleId, status ->
                    if (status == 0) {
                        readySoundIds[sampleId] = true
                        val swap = pendingSwaps.remove(sampleId)
                        if (swap != null) {
                            val key = swap.kitId to swap.padId
                            val previousId = loadedSoundIds.put(key, sampleId)
                            if (previousId != null && previousId != sampleId) {
                                readySoundIds.remove(previousId)
                                runCatching { pool.unload(previousId) }
                            }
                        }
                    } else {
                        pendingSwaps.remove(sampleId)
                    }
                }
            }
    }

    @Synchronized
    override fun preloadKit(kitId: DrumKitId) {
        val profile = profilesByKit.getOrPut(kitId) { DrumSoundProfile.defaultForKit(kitId) }
        val synthKey = profile.synthCacheKey()
        requestedSynthKeys[kitId] = synthKey
        if (loadedSynthKeys[kitId] == synthKey && loadedSoundIds.containsKey(kitId to DrumPadId.SNARE)) {
            return
        }
        loadKitBuffers(kitId, profile, synthKey, replaceExisting = false)
    }

    override fun updateSoundProfile(kitId: DrumKitId, profile: DrumSoundProfile) {
        profilesByKit[kitId] = profile
        val targetKey = profile.synthCacheKey()
        val prevRequested = requestedSynthKeys.put(kitId, targetKey)
        if (loadedSynthKeys[kitId] == targetKey && prevRequested == targetKey) {
            return
        }

        synthExecutor.execute {
            val latestProfile = profilesByKit[kitId] ?: profile
            val latestKey = latestProfile.synthCacheKey()
            // Coalesce rapid slider moves: skip stale intermediate jobs
            if (latestKey != requestedSynthKeys[kitId]) return@execute
            if (loadedSynthKeys[kitId] == latestKey) return@execute
            loadKitBuffers(kitId, latestProfile, latestKey, replaceExisting = true)
        }
    }

    @Synchronized
    private fun loadKitBuffers(
        kitId: DrumKitId,
        profile: DrumSoundProfile,
        synthKey: String,
        replaceExisting: Boolean
    ) {
        val pool = soundPool ?: createSoundPool().also { soundPool = it }
        val kitRootDir = File(appContext.cacheDir, "drum_kits/${kitId.id}")
        val versionedDir = File(kitRootDir, "${SYNTH_CACHE_VERSION}_$synthKey")
        if (!versionedDir.exists()) {
            versionedDir.mkdirs()
        }

        for (padId in DrumPadId.entries) {
            val key = kitId to padId
            if (!replaceExisting && loadedSoundIds.containsKey(key)) continue

            val wavFile = File(versionedDir, "${padId.name.lowercase()}.wav")
            if (!wavFile.exists() || wavFile.length() <= ProceduralDrumSynthesizer.WAV_HEADER_BYTES) {
                val wavBytes = renderPadWavBytes(kitId, padId, profile)
                wavFile.writeBytes(wavBytes)
            }

            val soundId = pool.load(wavFile.absolutePath, 1)
            if (soundId > 0) {
                if (replaceExisting && loadedSoundIds.containsKey(key)) {
                    // Wait until SoundPool finishes decoding the replacement buffer before swapping
                    pendingSwaps[soundId] = PendingSwap(kitId, padId, synthKey)
                } else {
                    loadedSoundIds[key] = soundId
                }
            }
        }
        loadedSynthKeys[kitId] = synthKey

        // Clean stale cached synth versions for this kit to keep cacheDir compact
        kitRootDir.listFiles()?.forEach { child ->
            if (child.name != versionedDir.name) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun renderPadWavBytes(
        kitId: DrumKitId,
        padId: DrumPadId,
        profile: DrumSoundProfile
    ): ByteArray {
        if (kitId == DrumKitId.ACOUSTIC_STUDIO) {
            val assetBytes = runCatching {
                appContext.assets.open("kits/acoustic-studio/${padId.name.lowercase()}.wav").use { input ->
                    input.readBytes()
                }
            }.getOrNull()
            if (assetBytes != null && assetBytes.size > ProceduralDrumSynthesizer.WAV_HEADER_BYTES) {
                return ProceduralDrumSynthesizer.processWavWithProfile(assetBytes, padId, profile)
            }
        }
        return ProceduralDrumSynthesizer.synthesizeWavBytes(kitId, padId, profile)
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

        val profile = profilesByKit[kitId] ?: DrumSoundProfile.defaultForKit(kitId)
        val padMix = profile.settingFor(padId)
        if (padMix.clampedVolume <= 0.01f) {
            return
        }

        val soundId = loadedSoundIds[key] ?: return
        val priority = if (padId == DrumPadId.KICK_RIGHT || padId == DrumPadId.SNARE) 2 else 1
        val finalVolume = (clampedVel * padMix.clampedVolume).coerceIn(0.05f, 1.0f)
        val streamId = pool.play(
            soundId,
            finalVolume,
            finalVolume,
            priority,
            0,
            padMix.playbackRate
        )
        if (streamId > 0) {
            activeStreamIds.add(streamId)
            if (padId == DrumPadId.HI_HAT_OPEN) {
                chokeTracker.recordOpenHiHatStream(streamId)
            }
        }

        hapticAdapter.pulseForHit(finalVolume)
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
        synthExecutor.shutdownNow()
        pendingSwaps.clear()
        loadedSoundIds.clear()
        readySoundIds.clear()
        profilesByKit.clear()
        requestedSynthKeys.clear()
        loadedSynthKeys.clear()
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
