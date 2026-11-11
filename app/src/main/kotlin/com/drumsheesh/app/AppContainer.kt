package com.drumsheesh.app

import android.content.Context
import com.drumsheesh.core.ai.AiGrooveGeneratorPort
import com.drumsheesh.core.ai.GrooveRepositoryPort
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.platform.AndroidUptimeClockAdapter
import com.drumsheesh.platform.ai.GeminiAiGrooveAdapter
import com.drumsheesh.platform.audio.SoundPoolDrumAudioAdapter
import com.drumsheesh.platform.storage.FileGrooveRepositoryAdapter

class AppContainer(
    val appContext: Context,
    val clockPort: ClockPort = AndroidUptimeClockAdapter(),
    val drumAudioAdapter: SoundPoolDrumAudioAdapter = SoundPoolDrumAudioAdapter(appContext),
    val aiGrooveGeneratorPort: AiGrooveGeneratorPort = GeminiAiGrooveAdapter(),
    val grooveRepositoryPort: GrooveRepositoryPort = FileGrooveRepositoryAdapter(appContext)
)
