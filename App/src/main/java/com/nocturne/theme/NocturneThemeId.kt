package com.nocturne.theme

enum class NocturneThemeId(val storageKey: String, val title: String) {
    MIDNIGHT_LAVENDER("midnight_lavender", "Midnight Lavender"),
    AURORA("aurora", "Aurora"),
    WARM_SAND("warm_sand", "Warm Sand"),
    OLED_NOIR("oled_noir", "OLED Noir"),
    ARCTIC_BLUE("arctic_blue", "Arctic Blue");

    companion object {
        fun fromStorage(value: String?): NocturneThemeId =
            entries.firstOrNull { it.storageKey == value } ?: AURORA
    }
}
