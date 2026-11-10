package com.drumsheesh.core.port

fun interface ClockPort {
    fun currentTimeMillis(): Long

    companion object {
        val System: ClockPort = ClockPort { java.lang.System.currentTimeMillis() }
    }
}
