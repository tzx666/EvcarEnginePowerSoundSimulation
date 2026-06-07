package com.byd.superpulse.audio

enum class EngineSoundStyle(
    val idleRpm: Double,
    val maxRpm: Double,
    val harmonicMix: Double,
    val roughness: Double,
    val turboMix: Double
) {
    SPORT(idleRpm = 900.0, maxRpm = 7800.0, harmonicMix = 0.80, roughness = 0.045, turboMix = 0.22),
    COMFORT(idleRpm = 650.0, maxRpm = 5500.0, harmonicMix = 0.45, roughness = 0.015, turboMix = 0.10),
    SCI_FI(idleRpm = 500.0, maxRpm = 6800.0, harmonicMix = 0.60, roughness = 0.020, turboMix = 0.35)
}
