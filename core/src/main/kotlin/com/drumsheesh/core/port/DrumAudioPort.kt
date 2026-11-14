package com.drumsheesh.core.port

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.DrumSoundProfile
import com.drumsheesh.core.model.SnareArticulation

interface DrumAudioPort {
    fun preloadKit(kitId: DrumKitId)
    fun updateSoundProfile(kitId: DrumKitId, profile: DrumSoundProfile) {}
    fun triggerPad(kitId: DrumKitId, padId: DrumPadId, velocity: Float)
    fun triggerSnare(
        kitId: DrumKitId,
        articulation: SnareArticulation,
        velocity: Float,
        roundRobinIndex: Int = 0,
        playbackRateMultiplier: Float = 1.0f
    ) {
        triggerPad(kitId, DrumPadId.SNARE, velocity)
    }
    fun stopAllStreams()
    fun release()
}
