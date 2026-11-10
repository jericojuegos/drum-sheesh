package com.drumsheesh.platform

import android.os.SystemClock
import com.drumsheesh.core.port.ClockPort

class AndroidUptimeClockAdapter : ClockPort {
    override fun currentTimeMillis(): Long = SystemClock.elapsedRealtime()
}
