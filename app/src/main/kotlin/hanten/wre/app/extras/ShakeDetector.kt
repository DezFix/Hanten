package hanten.wre.app.extras

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.sqrt

/**
 * Hanten 3.0 easter egg: a firm shake (two jolts within a second) calls [onShake].
 * Listens to the accelerometer only while the owner is resumed.
 */
class ShakeDetector(
	context: Context,
	private val isEnabled: () -> Boolean,
	private val onShake: () -> Unit,
) : SensorEventListener, DefaultLifecycleObserver {

	private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
	private var firstJoltAt = 0L
	private var lastShakeAt = 0L

	override fun onResume(owner: LifecycleOwner) {
		if (!isEnabled()) return
		val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
		sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
	}

	override fun onPause(owner: LifecycleOwner) {
		sensorManager?.unregisterListener(this)
	}

	override fun onSensorChanged(event: SensorEvent) {
		val (x, y, z) = event.values
		val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
		if (g < JOLT_THRESHOLD) return
		val now = System.currentTimeMillis()
		if (now - lastShakeAt < COOLDOWN_MS) return
		if (now - firstJoltAt > WINDOW_MS) {
			firstJoltAt = now
			return
		}
		if (now - firstJoltAt > MIN_GAP_MS) {
			firstJoltAt = 0L
			lastShakeAt = now
			onShake()
		}
	}

	override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

	private companion object {

		const val JOLT_THRESHOLD = 2.6f
		const val WINDOW_MS = 1000L
		const val MIN_GAP_MS = 150L
		const val COOLDOWN_MS = 3000L
	}
}
