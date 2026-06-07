package com.byd.superpulse.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
import android.hardware.bydauto.gearbox.BYDAutoGearboxDevice
import com.byd.superpulse.car.CarTelemetry
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

class EngineSoundSynthesizer {

    private data class EngineTarget(
        val enabled: Boolean = false,
        val style: EngineSoundStyle = EngineSoundStyle.SPORT,
        val intensity: Double = 0.65,
        val rpm: Double = 700.0,
        val throttle: Double = 0.0,
        val brake: Double = 0.0,
        val speed: Double = 0.0,
        val steering: Double = 0.0,
        val reverse: Boolean = false,
        val startupPulse: Double = 0.0,
        val shiftPop: Double = 0.0
    )

    private val sampleRate = 48_000
    private val running = AtomicBoolean(false)

    @Volatile
    private var target = EngineTarget()
    private var renderThread: Thread? = null

    private var previousTelemetry = CarTelemetry()
    private var startupEnvelope = 0.0
    private var shiftEnvelope = 0.0
    private var smoothEstimatedThrottle = 0.0

    fun start() {
        if (!running.compareAndSet(false, true)) return
        renderThread = thread(name = "super-pulse-audio", isDaemon = true) { renderLoop() }
    }

    fun stop() {
        running.set(false)
        renderThread?.join(300)
        renderThread = null
    }

    fun update(
        telemetry: CarTelemetry,
        style: EngineSoundStyle,
        enabled: Boolean,
        intensity: Double,
        zeroToHundredSeconds: Double
    ) {
        val estimatedThrottle = estimateThrottleFromAcceleration(
            accelerationMps2 = telemetry.longitudinalAccelerationMps2,
            zeroToHundredSeconds = zeroToHundredSeconds
        )
        smoothEstimatedThrottle += (estimatedThrottle - smoothEstimatedThrottle) * 0.18
        val throttle = smoothEstimatedThrottle.coerceIn(0.0, 1.0)
        val brake = telemetry.brakeDepth.coerceIn(0, 100) / 100.0
        val speed = telemetry.speedKmh.coerceAtLeast(0.0)

        val isPowered = telemetry.powerLevel != BYDAutoBodyworkDevice.BODYWORK_POWER_LEVEL_OFF
        val reverse = telemetry.gearboxMode == BYDAutoGearboxDevice.GEARBOX_AUTO_MODE_R
        val parkOrNeutral = telemetry.gearboxMode == BYDAutoGearboxDevice.GEARBOX_AUTO_MODE_P ||
            telemetry.gearboxMode == BYDAutoGearboxDevice.GEARBOX_AUTO_MODE_N

        if (previousTelemetry.powerLevel != telemetry.powerLevel && isPowered) {
            startupEnvelope = 1.0
        }

        val previousEstimatedThrottle = estimateThrottleFromAcceleration(
            accelerationMps2 = previousTelemetry.longitudinalAccelerationMps2,
            zeroToHundredSeconds = zeroToHundredSeconds
        )
        val throttleDrop = (previousEstimatedThrottle - estimatedThrottle).coerceAtLeast(0.0)
        val speedGain = telemetry.speedKmh - previousTelemetry.speedKmh
        if (throttleDrop > 0.22 && speedGain > 1.5) {
            shiftEnvelope = 1.0
        }

        val rawRpm = when {
            !enabled || !isPowered -> 0.0
            parkOrNeutral -> style.idleRpm + throttle * (style.maxRpm - style.idleRpm) * 0.35
            else -> {
                val fromThrottle = throttle * (style.maxRpm - style.idleRpm)
                val fromSpeed = speed * 42.0
                val fromBrake = brake * 1400.0
                (style.idleRpm + fromThrottle + fromSpeed - fromBrake)
            }
        }

        val sportBoost = if (telemetry.gearboxMode == BYDAutoGearboxDevice.GEARBOX_AUTO_MODE_S) 1.12 else 1.0
        val finalRpm = rawRpm * sportBoost

        startupEnvelope *= 0.93
        shiftEnvelope *= 0.88

        target = EngineTarget(
            enabled = enabled && isPowered,
            style = style,
            intensity = intensity.coerceIn(0.0, 1.0),
            rpm = finalRpm.coerceIn(style.idleRpm * 0.85, style.maxRpm),
            throttle = throttle,
            brake = brake,
            speed = speed,
            steering = telemetry.steeringAngle,
            reverse = reverse,
            startupPulse = startupEnvelope,
            shiftPop = shiftEnvelope
        )

        previousTelemetry = telemetry
    }

    companion object {
        fun estimateThrottleFromAcceleration(
            accelerationMps2: Double,
            zeroToHundredSeconds: Double
        ): Double {
            val fullThrottleAcceleration = (100.0 / 3.6) / zeroToHundredSeconds.coerceIn(2.0, 30.0)
            return (accelerationMps2 / fullThrottleAcceleration).coerceIn(0.0, 1.0)
        }
    }

    private fun renderLoop() {
        val minSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minSize, sampleRate / 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val frameCount = 512
        val buffer = ShortArray(frameCount)

        var smoothRpm = 750.0
        var smoothVolume = 0.0
        var phase = 0.0
        var secondPhase = 0.0
        var lcg = 0x12345678

        track.play()

        while (running.get()) {
            val t = target
            smoothRpm += (t.rpm - smoothRpm) * 0.05

            val reverseAtten = if (t.reverse) 0.85 else 1.0
            val targetVolume = if (t.enabled) (0.08 + t.intensity * 0.50) * reverseAtten else 0.0
            smoothVolume += (targetVolume - smoothVolume) * 0.06

            val baseFreq = smoothRpm / 60.0
            val fundamentalStep = 2.0 * PI * baseFreq / sampleRate
            val secondStep = 2.0 * PI * (baseFreq * 2.0) / sampleRate

            for (i in 0 until frameCount) {
                phase += fundamentalStep
                secondPhase += secondStep

                if (phase > PI * 2) phase -= PI * 2
                if (secondPhase > PI * 2) secondPhase -= PI * 2

                lcg = lcg * 1664525 + 1013904223
                val noise = (((lcg ushr 8) and 0xFFFF) / 32768.0 - 1.0)

                val engineCore = when (t.style) {
                    EngineSoundStyle.SPORT -> {
                        sin(phase) * 0.72 + sin(secondPhase + sin(phase) * 0.3) * t.style.harmonicMix
                    }

                    EngineSoundStyle.COMFORT -> {
                        sin(phase) * 0.85 + sin(secondPhase) * 0.20
                    }

                    EngineSoundStyle.SCI_FI -> {
                        val fm = sin(secondPhase * 0.5) * (4.0 + t.throttle * 7.0)
                        sin(phase + fm) * 0.75 + sin(secondPhase * 1.5) * 0.25
                    }
                }

                val steeringBoost = (abs(t.steering) / 540.0).coerceIn(0.0, 0.18)
                val accelSweep = sin(phase * (1.0 + t.throttle * 0.35)) * t.style.turboMix * t.throttle
                val decelCrackle = noise * t.style.roughness * t.brake

                val startup = sin(phase * 0.4) * t.startupPulse * 0.5
                val shiftPop = noise * t.shiftPop * 0.55

                val mixed = (engineCore + accelSweep + decelCrackle + startup + shiftPop + steeringBoost)
                val sample = (mixed * smoothVolume).coerceIn(-1.0, 1.0)
                buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }

            track.write(buffer, 0, buffer.size)
        }

        track.pause()
        track.flush()
        track.release()
    }
}

