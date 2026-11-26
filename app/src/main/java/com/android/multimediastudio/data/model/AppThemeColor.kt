package com.android.multimediastudio.data.model

enum class AppThemeColor(val displayName: String) {
    DEFAULT("Purple"),
    OCEAN("Ocean"),
    EMERALD("Emerald"),
    AMBER("Amber"),
    CRIMSON("Crimson"),
    MONOCHROME("Monochrome");

    companion object {
        fun fromName(name: String?): AppThemeColor {
            if (name == null) return MONOCHROME
            if (name == "FOREST") return EMERALD
            return entries.firstOrNull { it.name == name } ?: MONOCHROME
        }
    }
}
