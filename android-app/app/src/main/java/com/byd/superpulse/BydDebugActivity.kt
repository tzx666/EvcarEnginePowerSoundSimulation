package com.byd.superpulse

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.byd.superpulse.audio.EngineSoundStyle
import com.byd.superpulse.audio.EngineSoundSynthesizer
import com.byd.superpulse.car.BydCarDataSource
import com.byd.superpulse.databinding.ActivityBydDebugBinding
import kotlinx.coroutines.launch

class BydDebugActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBydDebugBinding

    private lateinit var carDataSource: BydCarDataSource
    private val synthesizer = EngineSoundSynthesizer()

    private var style: EngineSoundStyle = EngineSoundStyle.SPORT
    private var enabled: Boolean = false
    private var intensity: Double = 0.65
    private var zeroToHundredSeconds: Double = 7.5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBydDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        carDataSource = BydCarDataSource(this)
        initUi()
        collectTelemetry()
    }

    override fun onStart() {
        super.onStart()
        carDataSource.start()
        synthesizer.start()
    }

    override fun onStop() {
        super.onStop()
        carDataSource.stop()
        synthesizer.stop()
    }

    private fun initUi() {
        binding.styleSport.isChecked = true
        binding.intensitySeekBar.progress = (intensity * 100).toInt()
        binding.intensityValue.text = "${binding.intensitySeekBar.progress}%"
        binding.zeroToHundredInput.setText("%.1f".format(zeroToHundredSeconds))
        binding.soundSwitch.setOnCheckedChangeListener { _, isChecked -> enabled = isChecked }
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
        binding.zeroToHundredInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                zeroToHundredSeconds = s?.toString()?.toDoubleOrNull()?.coerceIn(2.0, 30.0)
                    ?: zeroToHundredSeconds
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun collectTelemetry() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                carDataSource.telemetry.collect { telemetry ->
                    synthesizer.update(
                        telemetry = telemetry, style = style,
                        enabled = enabled, intensity = intensity,
                        zeroToHundredSeconds = zeroToHundredSeconds
                    )
                    val t = EngineSoundSynthesizer.estimateThrottleFromAcceleration(
                        telemetry.longitudinalAccelerationMps2, zeroToHundredSeconds
                    )
                    binding.telemetryText.text = buildString {
                        appendLine("\u901f\u5ea6: ${"%.1f".format(telemetry.speedKmh)} km/h")
                        appendLine("\u7eb5\u5411\u52a0\u901f\u5ea6: ${"%.2f".format(telemetry.longitudinalAccelerationMps2)} m/s\u00b2")
                        appendLine("\u96f6\u767e\u65f6\u95f4: ${"%.1f".format(zeroToHundredSeconds)} s")
                        appendLine("\u62df\u5408\u6cb9\u95e8: ${"%.0f".format(t * 100)}%")
                        appendLine("\u539f\u59cb\u7535\u95e8: ${telemetry.throttleDepth}%\uff08\u4ec5\u663e\u793a\uff09")
                        appendLine("\u5239\u8f66: ${telemetry.brakeDepth}%")
                        appendLine("\u6863\u4f4d: ${telemetry.gearboxMode}")
                        appendLine("\u65b9\u5411\u76d8: ${"%.1f".format(telemetry.steeringAngle)}")
                        appendLine("\u7535\u6e90: ${telemetry.powerLevel}")
                        append("\u9a7e\u9a76\u914d\u7f6e: ${telemetry.driveConfigType}")
                    }
                }
            }
        }
    }
}
