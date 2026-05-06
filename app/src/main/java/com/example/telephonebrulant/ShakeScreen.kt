package com.example.telephonebrulant

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable

@Composable
class ShakeScreen(context: Context, private val onShakeUpdate: (Float) -> Unit) : SensorEventListener {
    private val SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = SensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun start(){
        accelerometer?.let { SensorManager.registerListener(this, it, SensorManager.) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        TODO("Not yet implemented")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        TODO("Not yet implemented")
    }

}