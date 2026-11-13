package fr.maubin.bridgeplus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

const val kChannelRing       = "sonnerie"												// canal de la sonnerie (priorité haute)
const val kChannelLinks      = "liens"													// canal des liens reçus du PC
private const val kRingId    = 4														// notification de la sonnerie
private const val kRingMs    = 60_000L													// durée maximale de la sonnerie

/*
	Sonnerie pour retrouver le téléphone, demandée par le PC : son de réveil
	en boucle, à plein volume même en silencieux (canal des alarmes, permis
	en « Ne pas déranger »), avec vibration ; une notification prioritaire
	porte « Arrêter ». Elle s'arrête d'elle-même après 60 s, et le volume
	des alarmes d'avant est remis.
*/
object Ringer
{
	private var fPlayer  : MediaPlayer? = null											// lecture en cours ; null sans
	private var fVolume  = -1																// volume des alarmes d'avant ; -1 sans
	private val fRinging = MutableStateFlow(false)										// sonnerie en cours
	private val fMain    = Handler(Looper.getMainLooper())								// fil principal (arrêt différé)

	/*
		Vrai pendant la sonnerie (bouton « Arrêter » de l'accueil).
	*/
	val ringing: StateFlow<Boolean> = fRinging

	/*
		Lance la sonnerie (sans effet si elle sonne déjà).
		inContext : contexte
	*/
	@Synchronized
	fun start(inContext: Context)
	{
		if (fPlayer != null) return
		val vContext = inContext.applicationContext												// contexte de l'appli
		val vAudio   = vContext.getSystemService(AudioManager::class.java)						// volumes
		val vSound   = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)	// son
		vAudio?.let { fVolume = it.getStreamVolume(AudioManager.STREAM_ALARM); it.setStreamVolume(AudioManager.STREAM_ALARM, it.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
		fPlayer = runCatching {
			MediaPlayer().apply {
				setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
				setDataSource(vContext, vSound)
				isLooping = true
				prepare()
				start()
			}
		}.getOrNull()
		vContext.getSystemService(VibratorManager::class.java)?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
		fRinging.value = true
		showRing(vContext)
		fMain.postDelayed({ stop(vContext) }, kRingMs)
	}

	/*
		Arrête la sonnerie, remet le volume des alarmes et retire la
		notification.
		inContext : contexte
	*/
	@Synchronized
	fun stop(inContext: Context)
	{
		val vContext = inContext.applicationContext												// contexte de l'appli
		fMain.removeCallbacksAndMessages(null)
		fPlayer?.let { runCatching { it.stop() }; it.release() }
		fPlayer = null
		vContext.getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
		if (fVolume >= 0) vContext.getSystemService(AudioManager::class.java)?.setStreamVolume(AudioManager.STREAM_ALARM, fVolume, 0)
		fVolume = -1
		fRinging.value = false
		vContext.getSystemService(NotificationManager::class.java)?.cancel(kRingId)
	}

	/*
		Notification prioritaire de la sonnerie, avec « Arrêter ».
		inContext : contexte
	*/
	private fun showRing(inContext: Context)
	{
		val vStop = PendingIntent.getBroadcast(inContext, 0, Intent(inContext, RingStopReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)	// « Arrêter »
		val vOpen = PendingIntent.getActivity(inContext, 0, Intent(inContext, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)		// ouvrir l'appli
		inContext.getSystemService(NotificationManager::class.java)?.notify(kRingId, Notification.Builder(inContext, kChannelRing)
			.setSmallIcon(R.drawable.ic_launcher_monochrome)
			.setContentTitle("Le PC fait sonner ce téléphone")
			.setContentText("Touchez « Arrêter » pour couper la sonnerie.")
			.setCategory(Notification.CATEGORY_ALARM)
			.setOngoing(true)
			.setContentIntent(vOpen)
			.setDeleteIntent(vStop)
			.addAction(Notification.Action.Builder(null, "Arrêter", vStop).build())
			.build())
	}
}

/*
	« Arrêter » de la notification de la sonnerie.
*/
class RingStopReceiver : BroadcastReceiver()
{
	override fun onReceive(
		inContext : Context,
		inIntent  : Intent
	) = Ringer.stop(inContext)
}

/*
	Batterie du téléphone, lue dans l'annonce du système : niveau (%) et
	charge ; null si l'annonce est illisible.
	inIntent : annonce ACTION_BATTERY_CHANGED
*/
fun batteryOf(inIntent: Intent): Pair<Int, Boolean>?
{
	val vLevel  = inIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)							// niveau brut
	val vScale  = inIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)							// échelle
	if (vLevel < 0 || vScale <= 0) return null
	val vStatus = inIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)							// état
	return (vLevel * 100 / vScale) to (vStatus == BatteryManager.BATTERY_STATUS_CHARGING || vStatus == BatteryManager.BATTERY_STATUS_FULL)
}

/*
	Lien reçu du PC : ouvert tout de suite si BridgePlus est à l'écran (Android
	interdit sinon d'ouvrir le navigateur depuis l'arrière-plan), sinon
	proposé par une notification à toucher. Seuls les liens web arrivent ici.
	inContext : contexte
	inPeer    : PC qui l'envoie
	inUrl     : lien
*/
fun openLinkFromPc(
	inContext : Context,
	inPeer    : String,
	inUrl     : String
)
{
	val vView = Intent(Intent.ACTION_VIEW, inUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)	// ouverture du lien
	if (MainActivity.visible && runCatching { inContext.startActivity(vView) }.isSuccess) return
	val vOpen = PendingIntent.getActivity(inContext, inUrl.hashCode(), vView, PendingIntent.FLAG_IMMUTABLE)	// toucher la notification
	inContext.getSystemService(NotificationManager::class.java)?.notify(inUrl.hashCode(), Notification.Builder(inContext, kChannelLinks)
		.setSmallIcon(R.drawable.ic_launcher_monochrome)
		.setContentTitle("Lien de $inPeer")
		.setContentText(inUrl.toUri().host ?: inUrl)
		.setContentIntent(vOpen)
		.setAutoCancel(true)
		.build())
}
