package com.drumsheesh.app

import android.content.Context
import com.drumsheesh.core.port.ClockPort
import com.drumsheesh.platform.AndroidUptimeClockAdapter

class AppContainer(
    val appContext: Context,
    val clockPort: ClockPort = AndroidUptimeClockAdapter()
)
