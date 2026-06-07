package com.byd.superpulse

import android.os.Bundle
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.byd.superpulse.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBydDebug.setOnClickListener {
            startActivity(Intent(this, BydDebugActivity::class.java))
        }
        binding.btnAccelSensor.setOnClickListener {
            startActivity(Intent(this, AccelSensorActivity::class.java))
        }
    }
}
