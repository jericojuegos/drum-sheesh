package com.drumsheesh.core.port

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId
import com.drumsheesh.core.model.DrumSoundProfile

interface DrumAudioPort {
    fun preloadKit(kitId: DrumKitId)
    fun updateSoundProfile(kitId: DrumKitId, profile: DrumSoundProfile) {}
    fun triggerPad(kitId: DrumKitId, padId: DrumPadId, velocity: Float)
    fun stopAllStreams()
    fun release()
}
