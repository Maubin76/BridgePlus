package fr.maubin.bridgeplus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.core.net.toUri
import fr.maubin.bridgeplus.transfer.Transfer
import fr.maubin.bridgeplus.transfer.TransferState
import fr.maubin.bridgeplus.transfer.sizeText

const val kChannelTransfers   = "transferts"										// canal des transferts (progression, fin)
private const val kProgressId = 3													// notification de progression
private const val kExtraId    = "transfert"											// identifiant du transfert (annulation)
private const val kWakeTag    = "BridgePlus:transfert"								// étiquette des verrous d'éveil

/*
	Notification de progression des transferts en cours : le premier, avec
	sa barre et « Annuler » (et le nombre des suivants) ; retirée quand il
	n'y en a plus.
	inContext : contexte
	inActive  : transferts en cours
*/
fun showProgress(
	inContext : Context,
	inActive  : List<Transfer>
)
{
	val vManager = inContext.getSystemService(NotificationManager::class.java) ?: return			// notifications
	val vFirst   = inActive.firstOrNull() ?: return vManager.cancel(kProgressId)					// transfert montré
	val vCancel  = PendingIntent.getBroadcast(
		inContext, vFirst.id.hashCode(),
		Intent(inContext, TransferCancelReceiver::class.java).putExtra(kExtraId, vFirst.id),
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
	)																								// action « Annuler »
	val vTitle   = "${if (vFirst.outgoing) "Envoi à" else "Réception de"} ${vFirst.peer}"			// titre
	val vMore    = if (inActive.size > 1) " · ${inActive.size - 1} autre${if (inActive.size > 2) "s" else ""} ensuite" else ""	// suivants
	vManager.notify(kProgressId, Notification.Builder(inContext, kChannelTransfers)
		.setSmallIcon(R.drawable.ic_launcher_monochrome)
		.setContentTitle(vTitle)
		.setContentText("${vFirst.name} · ${sizeText(vFirst.done)} sur ${sizeText(vFirst.size)}$vMore")
		.setProgress(1000, (vFirst.progress * 1000).toInt(), vFirst.state == TransferState.Waiting)
		.setOngoing(true)
		.setOnlyAlertOnce(true)
		.addAction(Notification.Action.Builder(null, "Annuler", vCancel).build())
		.build())
}

/*
	Notification de fin : fichier reçu (« Ouvrir », « Ouvrir le dossier »),
	ou échec. Un envoi réussi ne dit rien (il est dans l'historique).
	inContext  : contexte
	inTransfer : transfert terminé
*/
fun showFinished(
	inContext  : Context,
	inTransfer : Transfer
)
{
	val vManager = inContext.getSystemService(NotificationManager::class.java) ?: return			// notifications
	val vBuilder = Notification.Builder(inContext, kChannelTransfers).setSmallIcon(R.drawable.ic_launcher_monochrome).setAutoCancel(true)	// notification
	when (inTransfer.state)
	{
		TransferState.Done   ->
		{
			if (inTransfer.outgoing) return
			val vOpen   = PendingIntent.getActivity(
				inContext, inTransfer.id.hashCode(),
				Intent(Intent.ACTION_VIEW).setDataAndType(inTransfer.location.toUri(), mimeOf(inTransfer.name)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
				PendingIntent.FLAG_IMMUTABLE
			)																						// « Ouvrir »
			val vFolder = PendingIntent.getActivity(
				inContext, 0,
				Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),
				PendingIntent.FLAG_IMMUTABLE
			)																						// « Ouvrir le dossier »
			vBuilder.setContentTitle("Fichier reçu de ${inTransfer.peer}")
				.setContentText("${inTransfer.name} · ${sizeText(inTransfer.size)}")
				.setContentIntent(vOpen)
				.addAction(Notification.Action.Builder(null, "Ouvrir", vOpen).build())
				.addAction(Notification.Action.Builder(null, "Ouvrir le dossier", vFolder).build())
		}
		TransferState.Failed -> vBuilder.setContentTitle(if (inTransfer.outgoing) "Envoi à ${inTransfer.peer} échoué" else "Réception de ${inTransfer.peer} échouée")
			.setContentText("${inTransfer.name} : ${inTransfer.reason.orEmpty()}")
		else                 -> return
	}
	vManager.notify(inTransfer.id.hashCode(), vBuilder.build())
}

/*
	« Annuler » de la notification de progression.
*/
class TransferCancelReceiver : BroadcastReceiver()
{
	override fun onReceive(
		inContext : Context,
		inIntent  : Intent
	)
	{
		inIntent.getStringExtra(kExtraId)?.let { inContext.bridgeApp.files.cancel(it) }
	}
}

/*
	Verrous d'éveil pendant les transferts : le processeur et le Wi-Fi
	restent actifs écran éteint (sinon un gros fichier n'avancerait plus),
	et sont rendus dès qu'il n'y a plus de transfert.
	fContext : contexte
*/
class TransferWakeLocks(fContext: Context)
{
	private val fCpu  = fContext.getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, kWakeTag)?.apply { setReferenceCounted(false) }	// processeur
	private val fWifi = fContext.getSystemService(WifiManager::class.java)?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, kWakeTag)?.apply { setReferenceCounted(false) }	// Wi-Fi

	/*
		Prend ou rend les verrous.
		inBusy : vrai pendant un transfert
	*/
	fun hold(inBusy: Boolean)
	{
		if (inBusy)
		{
			fCpu?.takeIf { !it.isHeld }?.acquire(6 * 60 * 60 * 1000L)
			fWifi?.takeIf { !it.isHeld }?.acquire()
		}
		else
		{
			fCpu?.takeIf { it.isHeld }?.release()
			fWifi?.takeIf { it.isHeld }?.release()
		}
	}
}
