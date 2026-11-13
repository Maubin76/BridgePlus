package fr.maubin.bridgeplus.ui.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

private const val kMaxAngle = 0.35f													// inclinaison donnant l'amplitude maximale (radians, ~20°)
private const val kSmooth   = 0.12f													// lissage (part de la nouvelle mesure)

/*
	Inclinaison du téléphone par le capteur de rotation « jeu » (sans boussole,
	donc sans dérive magnétique), relative à la position de départ : tenir le
	téléphone comme on veut donne un emblème centré, seul le mouvement compte.
*/
@Composable
actual fun rememberTilt(): State<Offset>?
{
	val vContext = LocalContext.current													// accès aux capteurs
	val vSensors = remember(vContext) { vContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager }	// capteurs
	val vSensor  = remember(vSensors) { vSensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) } ?: return null	// capteur de rotation
	val vTilt    = remember { mutableStateOf(Offset.Zero) }								// inclinaison lissée
	DisposableEffect(vSensor) {
		val vMatrix  = FloatArray(9)															// matrice de rotation
		val vAngles  = FloatArray(3)															// azimut, tangage, roulis
		var vOrigin: Offset? = null																// position de départ
		val vListener = object : SensorEventListener
		{
			/*
				Nouvelle mesure : écart à la position de départ, borné et lissé.
				inEvent : mesure du capteur
			*/
			override fun onSensorChanged(inEvent: SensorEvent)
			{
				SensorManager.getRotationMatrixFromVector(vMatrix, inEvent.values)
				SensorManager.getOrientation(vMatrix, vAngles)
				val vNow   = Offset(vAngles[2], vAngles[1])									// roulis, tangage
				val vStart = vOrigin ?: vNow.also { vOrigin = it }								// position de départ
				val vRaw   = Offset(
					((vNow.x - vStart.x) / kMaxAngle).coerceIn(-1f, 1f),
					((vNow.y - vStart.y) / kMaxAngle).coerceIn(-1f, 1f)
				)																			// écart borné
				vTilt.value = vTilt.value + (vRaw - vTilt.value) * kSmooth
			}

			/*
				Précision du capteur : ignorée.
				inSensor   : capteur
				inAccuracy : précision
			*/
			override fun onAccuracyChanged(
				inSensor   : Sensor?,
				inAccuracy : Int
			) {}
		}
		vSensors.registerListener(vListener, vSensor, SensorManager.SENSOR_DELAY_GAME)
		onDispose { vSensors.unregisterListener(vListener) }
	}
	return vTilt
}
