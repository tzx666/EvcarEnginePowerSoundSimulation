package com.byd.superpulse

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.byd.superpulse.audio.EngineSoundStyle
import com.byd.superpulse.audio.EngineSoundSynthesizer
import com.byd.superpulse.car.CarTelemetry
import com.byd.superpulse.databinding.ActivityAccelSensorBinding

class AccelSensorActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityAccelSensorBinding
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private val synthesizer = EngineSoundSynthesizer()
    private var soundEnabled: Boolean = false
    private var style: EngineSoundStyle = EngineSoundStyle.SPORT
    private var intensity: Double = 0.65
    private var zeroToHundredSeconds: Double = 7.5

    // low-pass filter gravity estimates
    private var gravityX: Double = 0.0
    private var gravityY: Double = 0.0
    private var gravityZ: Double = 0.0

    // smoothed frontend values
    private var smoothAcceleration: Double = 0.0
    private var smoothThrottle: Double = 0.0
    private var rpm: Double = 900.0
    private var estimatedSpeedKmh: Double = 0.0

    private var lastSynthesizerUpdateMs: Long = 0L

    companion object {
        private const val ALPHA = 0.8
        private const val ACCEL_SMOOTH = 0.12
        private const val THROTTLE_SMOOTH = 0.08
        private const val RPM_SMOOTH = 0.07
        private const val SPEED_SMOOTH = 0.05
        private const val IDLE_RPM = 900.0
        private const val REDLINE_RPM = 7800.0
        private const val DEBUG_GAIN = 2.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccelSensorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            binding.sensorStatus.text = "\u2705 \u8bbe\u5907\u4e0d\u652f\u6301\u52a0\u901f\u5ea6\u4f20\u611f\u5668"
            return
        }
        binding.sensorStatus.text = "\u25cf \u4f20\u611f\u5668\u5c31\u7eea"

        initSoundUi()
        binding.zeroToHundredInput.setText("%.1f".format(zeroToHundredSeconds))
        binding.zeroToHundredInput.setOnEditorActionListener { _, _, _ ->
            zeroToHundredSeconds = binding.zeroToHundredInput.text.toString()
                .toDoubleOrNull()?.coerceIn(2.0, 30.0) ?: zeroToHundredSeconds
            binding.zeroToHundredInput.setText("%.1f".format(zeroToHundredSeconds))
            true
        }
    }

    private fun initSoundUi() {
        binding.styleSport.isChecked = true
        binding.intensitySeekBar.progress = (intensity * 100).toInt()
        binding.intensityValue.text = "${binding.intensitySeekBar.progress}%"

        binding.soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            soundEnabled = isChecked
        }
        binding.styleGroup.setOnCheckedChangeListener { _, checkedId ->
            style = when (checkedId) {
                R.id.styleComfort -> EngineSoundStyle.COMFORT
                R.id.styleScifi -> EngineSoundStyle.SCI_FI
                else -> EngineSoundStyle.SPORT
            }
        }
        binding.intensitySeekBar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fu: Boolean) {
                    intensity = (p.coerceIn(0, 100)) / 100.0
                    binding.intensityValue.text = "$p%"
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) = Unit
            })
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        synthesizer.start()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        synthesizer.stop()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()

        gravityX = ALPHA * gravityX + (1 - ALPHA) * x
        gravityY = ALPHA * gravityY + (1 - ALPHA) * y
        gravityZ = ALPHA * gravityZ + (1 - ALPHA) * z

        val linearX = x - gravityX
        val linearY = y - gravityY
        val linearZ = z - gravityZ

        val horizontalAccel = Math.sqrt(linearX * linearX + linearY * linearY)
        val fullThrottleAccel = (100.0 / 3.6) / zeroToHundredSeconds
        val estimatedThrottle = (horizontalAccel / fullThrottleAccel).coerceIn(0.0, 1.0)

        smoothAcceleration += (horizontalAccel - smoothAcceleration) * ACCEL_SMOOTH
        val rawThrottle = estimateThrottleFromAcceleration(smoothAcceleration, zeroToHundredSeconds)
        smoothThrottle += (rawThrottle - smoothThrottle) * THROTTLE_SMOOTH

        val targetRpm = IDLE_RPM + smoothThrottle * (REDLINE_RPM - IDLE_RPM)
        rpm += (targetRpm - rpm) * RPM_SMOOTH
        estimatedSpeedKmh += (smoothThrottle * 200.0 - estimatedSpeedKmh) * SPEED_SMOOTH

        val gear = estimateGear(smoothThrottle, rpm)
        val debugGainThrottle = (smoothThrottle * DEBUG_GAIN).coerceAtMost(1.0)

        // rate-limit synthesizer updates to ~20Hz to avoid noise-induced crackles
        val now = SystemClock.elapsedRealtime()
        if (now - lastSynthesizerUpdateMs >= 50L) {
            lastSynthesizerUpdateMs = now
            val telemetry = CarTelemetry(
                speedKmh = estimatedSpeedKmh.coerceAtLeast(0.0),
                longitudinalAccelerationMps2 = smoothAcceleration,
                throttleDepth = (smoothThrottle * 100).toInt().coerceIn(0, 100),
                brakeDepth = 0,
                gearboxMode = 4,
                steeringAngle = 0.0,
                powerLevel = 1,
                driveConfigType = 0
            )
            synthesizer.update(telemetry, style, soundEnabled, intensity, zeroToHundredSeconds)
        }

        binding.accelX.text = "%.3f".format(linearX)
        binding.accelY.text = "%.3f".format(linearY)
        binding.accelZ.text = "%.3f".format(linearZ)
        binding.horizontalAccel.text = "%.3f m/s\u00b2".format(horizontalAccel)
        binding.estimatedThrottle.text = "%.1f%%".format(estimatedThrottle * 100)
        binding.zeroToHundredDisplay.text = "%.1f s".format(zeroToHundredSeconds)
        binding.fullThrottleAccelDisplay.text = "%.3f m/s\u00b2".format(fullThrottleAccel)
        binding.smoothAccel.text = "%.3f m/s\u00b2".format(smoothAcceleration)
        binding.smoothThrottle.text = "%.1f%%".format(smoothThrottle * 100)
        binding.debugThrottle.text = "%.1f%%".format(debugGainThrottle * 100)
        binding.rpmValue.text = "%.0f".format(rpm)
        binding.gearValue.text = gear
        binding.speedValue.text = "%.1f km/h".format(estimatedSpeedKmh)
    }

    private fun estimateGear(throttle: Double, rpm: Double): String {
        if (throttle < 0.04) return "N"
        return when {
            rpm < 2400 -> "1"
            rpm < 3900 -> "2"
            rpm < 5600 -> "3"
            else -> "S"
        }
    }

    private fun estimateThrottleFromAcceleration(accelMps2: Double, t100: Double): Double {
        val fullG = (100.0 / 3.6) / t100.coerceIn(2.0, 30.0)
        return (accelMps2 / fullG).coerceIn(0.0, 1.0)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
