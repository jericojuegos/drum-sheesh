package com.drumsheesh.core.port

import com.drumsheesh.core.model.DrumKitId
import com.drumsheesh.core.model.DrumPadId

interface DrumAudioPort {
    fun preloadKit(kitId: DrumKitId)
    fun triggerPad(kitId: DrumKitId, padId: DrumPadId, velocity: Float)
    fun stopAllStreams()
    fun release()
}
