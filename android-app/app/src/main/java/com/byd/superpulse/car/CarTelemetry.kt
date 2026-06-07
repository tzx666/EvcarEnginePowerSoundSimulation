package com.byd.superpulse.car

data class CarTelemetry(
    val speedKmh: Double = 0.0,
    val throttleDepth: Int = 0,
    val longitudinalAccelerationMps2: Double = 0.0,
    val brakeDepth: Int = 0,
    val gearboxMode: Int = 0,
    val steeringAngle: Double = 0.0,
    val powerLevel: Int = 0,
    val driveConfigType: Int = 0
)
