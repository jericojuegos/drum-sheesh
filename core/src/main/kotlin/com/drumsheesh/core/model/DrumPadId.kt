package com.drumsheesh.core.model

enum class PadCategory {
    CYMBAL,
    SNARE,
    TOM,
    KICK
}

enum class DrumPadId(
    val displayName: String,
    val shortLabel: String,
    val shortcutKey: String,
    val category: PadCategory,
    val chokesOpenHiHat: Boolean = false
) {
    CRASH(
        displayName = "16\" Crash Cymbal",
        shortLabel = "CRASH",
        shortcutKey = "Y",
        category = PadCategory.CYMBAL
    ),
    RIDE(
        displayName = "20\" Ride Cymbal",
        shortLabel = "RIDE",
        shortcutKey = "U",
        category = PadCategory.CYMBAL
    ),
    HI_HAT_OPEN(
        displayName = "Open Hi-Hat",
        shortLabel = "OPEN HH",
        shortcutKey = "E",
        category = PadCategory.CYMBAL
    ),
    HI_HAT_CLOSED(
        displayName = "Closed Hi-Hat",
        shortLabel = "CLOSED HH",
        shortcutKey = "R",
        category = PadCategory.CYMBAL,
        chokesOpenHiHat = true
    ),
    TOM_HIGH(
        displayName = "10\" High Tom",
        shortLabel = "TOM 1",
        shortcutKey = "G",
        category = PadCategory.TOM
    ),
    TOM_MID(
        displayName = "12\" Mid Tom",
        shortLabel = "TOM 2",
        shortcutKey = "H",
        category = PadCategory.TOM
    ),
    TOM_FLOOR(
        displayName = "16\" Floor Tom",
        shortLabel = "FLOOR TOM",
        shortcutKey = "J",
        category = PadCategory.TOM
    ),
    SNARE(
        displayName = "14\" Studio Snare",
        shortLabel = "SNARE",
        shortcutKey = "F",
        category = PadCategory.SNARE
    ),
    KICK_LEFT(
        displayName = "Left Kick Pedal",
        shortLabel = "KICK L",
        shortcutKey = "C",
        category = PadCategory.KICK
    ),
    KICK_RIGHT(
        displayName = "Right Kick Pedal",
        shortLabel = "KICK R",
        shortcutKey = "V",
        category = PadCategory.KICK
    )
}
