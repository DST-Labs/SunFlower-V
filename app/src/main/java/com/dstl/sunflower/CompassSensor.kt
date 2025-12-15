package com.dstl.sunflower
import android.content.Context
import android.hardware.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberCompassHeading(): State<Float> {
    val context = LocalContext.current
    val heading = remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> System.arraycopy(event.values, 0, gravity, 0, 3)
                    Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                }

                val R = FloatArray(9)
                val ok = SensorManager.getRotationMatrix(R, null, gravity, geomagnetic)
                if (ok) {
                    val ori = FloatArray(3)
                    SensorManager.getOrientation(R, ori)
                    var deg = Math.toDegrees(ori[0].toDouble()).toFloat()
                    deg = (deg + 360f) % 360f
                    heading.floatValue = deg
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        accel?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        mag?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose { sensorManager.unregisterListener(listener) }
    }
    return heading
}
