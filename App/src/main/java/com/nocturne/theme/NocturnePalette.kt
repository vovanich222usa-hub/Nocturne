package com.nocturne.theme

import android.graphics.Color

data class NocturnePalette(
    val background: Int,
    val surface: Int,
    val surfaceElevated: Int,
    val primary: Int,
    val primaryText: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val outline: Int,
    val isLight: Boolean
) {
    companion object {
        fun forTheme(id: NocturneThemeId): NocturnePalette = when (id) {
            NocturneThemeId.MIDNIGHT_LAVENDER -> NocturnePalette(
                Color.parseColor("#141629"), Color.parseColor("#20243B"),
                Color.parseColor("#292E49"), Color.parseColor("#C7B5F2"),
                Color.parseColor("#171629"), Color.parseColor("#F0ECFF"),
                Color.parseColor("#AAA6C3"), Color.parseColor("#414765"), false
            )
            NocturneThemeId.AURORA -> NocturnePalette(
                Color.parseColor("#071D20"), Color.parseColor("#103238"),
                Color.parseColor("#19434A"), Color.parseColor("#35E0CF"),
                Color.parseColor("#052326"), Color.parseColor("#E5FFFC"),
                Color.parseColor("#A4D2CE"), Color.parseColor("#28656A"), false
            )
            NocturneThemeId.WARM_SAND -> NocturnePalette(
                Color.parseColor("#F8F0E4"), Color.parseColor("#F3E1CD"),
                Color.parseColor("#EBD1B8"), Color.parseColor("#C94F2D"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#48291F"),
                Color.parseColor("#80665B"), Color.parseColor("#D9BDA6"), true
            )
            NocturneThemeId.OLED_NOIR -> NocturnePalette(
                Color.parseColor("#050506"), Color.parseColor("#111114"),
                Color.parseColor("#1A1A20"), Color.parseColor("#A855F7"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#F7F3FF"),
                Color.parseColor("#B8B0C4"), Color.parseColor("#303039"), false
            )
            NocturneThemeId.ARCTIC_BLUE -> NocturnePalette(
                Color.parseColor("#DCEBFA"), Color.parseColor("#C8DDF4"),
                Color.parseColor("#B5D0EF"), Color.parseColor("#0754C7"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#10264A"),
                Color.parseColor("#4C6385"), Color.parseColor("#A4C3E8"), true
            )
        }
    }
}
