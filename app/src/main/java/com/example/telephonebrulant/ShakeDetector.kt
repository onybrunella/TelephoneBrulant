package com.example.telephonebrulant

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(context: Context, private val onShakeUpdate: (x : Float, y :Float, z : Float) -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer : Sensor?= sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastShakeTime=0L

    companion object {
        // Threshold for detecting a shake (2.7G ~ strong enough shake)
        private const val SHAKE_THRESHOLD_GRAVITY= 2.7f
        private const val SHAKE_COOLDOWN_MS=300L
    }

    fun start() {
        //sensor?.let{
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
  //  }
    }

    fun stop(){
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH

        val magnitude = sqrt(x * x + y * y + z * z)

        if (magnitude > SHAKE_THRESHOLD_GRAVITY) {
            val now=System.currentTimeMillis()
            if(now-lastShakeTime>SHAKE_COOLDOWN_MS){
                onShakeUpdate(x,y,z)
            }
          //  onShakeUpdate(magnitude) // si c'est assez fort, c'est que c'est problament un vrai shake
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }



}