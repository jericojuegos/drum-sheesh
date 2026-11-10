package com.drumsheesh.app

import android.content.Context
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.platform.AndroidUptimeClockAdapter
import com.drumsheesh.platform.audio.SoundPoolDrumAudioAdapter

class AppContainer(
    val appContext: Context,
    val clockPort: ClockPort = AndroidUptimeClockAdapter(),
    val drumAudioAdapter: SoundPoolDrumAudioAdapter = SoundPoolDrumAudioAdapter(appContext)
)
