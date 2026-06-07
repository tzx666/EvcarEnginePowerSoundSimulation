package com.byd.superpulse.car

import android.content.Context
import android.hardware.bydauto.bodywork.AbsBYDAutoBodyworkListener
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
import android.hardware.bydauto.gearbox.AbsBYDAutoGearboxListener
import android.hardware.bydauto.gearbox.BYDAutoGearboxDevice
import android.hardware.bydauto.setting.AbsBYDAutoSettingListener
import android.hardware.bydauto.setting.BYDAutoSettingDevice
import android.hardware.bydauto.speed.AbsBYDAutoSpeedListener
import android.hardware.bydauto.speed.BYDAutoSpeedDevice
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BydCarDataSource(context: Context) {

    private val appContext = context.applicationContext

    private val speedDevice = runCatching { BYDAutoSpeedDevice.getInstance(appContext) }.getOrNull()
    private val gearboxDevice = runCatching { BYDAutoGearboxDevice.getInstance(appContext) }.getOrNull()
    private val bodyworkDevice = runCatching { BYDAutoBodyworkDevice.getInstance(appContext) }.getOrNull()
    private val settingDevice = runCatching { BYDAutoSettingDevice.getInstance(appContext) }.getOrNull()

    private val _telemetry = MutableStateFlow(CarTelemetry())
    val telemetry: StateFlow<CarTelemetry> = _telemetry.asStateFlow()

    private var started = false
    private var lastSpeedKmh: Double? = null
    private var lastSpeedUpdateMs: Long = 0L

    private val speedListener = object : AbsBYDAutoSpeedListener() {
        override fun onSpeedChanged(speed: Double) = updateSpeed(speed.coerceAtLeast(0.0))
        override fun onAccelerateDeepnessChanged(deepness: Int) = update { copy(throttleDepth = deepness.coerceIn(0, 100)) }
        override fun onBrakeDeepnessChanged(deepness: Int) = update { copy(brakeDepth = deepness.coerceIn(0, 100)) }
    }

    private val gearboxListener = object : AbsBYDAutoGearboxListener() {
        override fun onGearboxAutoModeTypeChanged(mode: Int) = update { copy(gearboxMode = mode) }
    }

    private val bodyworkListener = object : AbsBYDAutoBodyworkListener() {
        override fun onSteeringWheelValueChanged(command: Int, value: Double) {
            if (command == BYDAutoBodyworkDevice.BODYWORK_CMD_STEERING_WHEEL_ANGEL) {
                update { copy(steeringAngle = value) }
            }
        }

        override fun onPowerLevelChanged(level: Int) = update { copy(powerLevel = level) }
    }

    private val settingListener = object : AbsBYDAutoSettingListener() {
        override fun onDriveConfigTypeChanged(type: Int) = update { copy(driveConfigType = type) }
    }

    fun start() {
        if (started) return
        started = true

        runCatching { speedDevice?.registerListener(speedListener) }
        runCatching { gearboxDevice?.registerListener(gearboxListener) }
        runCatching { bodyworkDevice?.registerListener(bodyworkListener) }
        runCatching { settingDevice?.registerListener(settingListener) }

        snapshotCurrentValues()
    }

    fun stop() {
        if (!started) return
        started = false

        runCatching { speedDevice?.unregisterListener(speedListener) }
        runCatching { gearboxDevice?.unregisterListener(gearboxListener) }
        runCatching { bodyworkDevice?.unregisterListener(bodyworkListener) }
        runCatching { settingDevice?.unregisterListener(settingListener) }
    }

    private fun snapshotCurrentValues() {
        val speed = runCatching { speedDevice?.getCurrentSpeed() ?: 0.0 }.getOrDefault(0.0)
        val throttle = runCatching { speedDevice?.getAccelerateDeepness() ?: 0 }.getOrDefault(0)
        val brake = runCatching { speedDevice?.getBrakeDeepness() ?: 0 }.getOrDefault(0)
        val gear = runCatching { gearboxDevice?.getGearboxAutoModeType() ?: 0 }.getOrDefault(0)
        val steering = runCatching {
            bodyworkDevice?.getSteeringWheelValue(BYDAutoBodyworkDevice.BODYWORK_CMD_STEERING_WHEEL_ANGEL) ?: 0.0
        }.getOrDefault(0.0)
        val power = runCatching { bodyworkDevice?.getPowerLevel() ?: 0 }.getOrDefault(0)

        lastSpeedKmh = speed.coerceAtLeast(0.0)
        lastSpeedUpdateMs = SystemClock.elapsedRealtime()

        update {
            copy(
                speedKmh = speed,
                throttleDepth = throttle.coerceIn(0, 100),
                brakeDepth = brake.coerceIn(0, 100),
                gearboxMode = gear,
                steeringAngle = steering,
                powerLevel = power
            )
        }
    }

    private fun updateSpeed(speedKmh: Double) {
        val nowMs = SystemClock.elapsedRealtime()
        val previousSpeedKmh = lastSpeedKmh
        val previousTimeMs = lastSpeedUpdateMs

        val acceleration = if (previousSpeedKmh != null && previousTimeMs > 0L) {
            val dtSeconds = ((nowMs - previousTimeMs) / 1000.0).coerceAtLeast(0.05)
            val speedDeltaMps = (speedKmh - previousSpeedKmh) / 3.6
            (speedDeltaMps / dtSeconds).coerceIn(-8.0, 8.0)
        } else {
            0.0
        }

        lastSpeedKmh = speedKmh
        lastSpeedUpdateMs = nowMs

        update {
            val smoothedAcceleration = longitudinalAccelerationMps2 * 0.72 + acceleration * 0.28
            copy(
                speedKmh = speedKmh,
                longitudinalAccelerationMps2 = smoothedAcceleration
            )
        }
    }

    private fun update(transform: CarTelemetry.() -> CarTelemetry) {
        _telemetry.value = _telemetry.value.transform()
    }
}
