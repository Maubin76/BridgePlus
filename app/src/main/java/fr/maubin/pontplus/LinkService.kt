package fr.maubin.pontplus

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import fr.maubin.pontplus.peers.PcStatus
import fr.maubin.pontplus.peers.PhoneLinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val kNotificationId = 1												// notification du service

/*
	Service de premier plan qui tient la liaison avec les PC, de type
	« connectedDevice » (échanges avec un appareil externe par le réseau,
	sans limite de durée). Il ne tourne que sur le Wi-Fi de la maison : hors
	de chez soi il s'arrête, et c'est NetworkWatch qui le relance au retour.
	Sa notification, la plus discrète possible, dit seulement « Relié à
	<PC> ». Un changement de Wi-Fi relance aussitôt les PC absents ; la perte
	du Wi-Fi de la maison arrête le service.
*/
class LinkService : Service()
{
	private val fScope    = CoroutineScope(SupervisorJob() + Dispatchers.Default)	// suivi de l'état
	private var fCallback : ConnectivityManager.NetworkCallback? = null				// écoute du Wi-Fi

	override fun onBind(inIntent: Intent?): IBinder? = null

	override fun onCreate()
	{
		super.onCreate()
		startForeground(kNotificationId, notification(pontLink.state.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
		fScope.launch {
			pontLink.state.map { text(it) }.distinctUntilChanged().collect {
				getSystemService(NotificationManager::class.java)?.notify(kNotificationId, notification(pontLink.state.value))
			}
		}
		watchWifi()
	}

	override fun onStartCommand(
		inIntent  : Intent?,
		inFlags   : Int,
		inStartId : Int
	): Int
	{
		val vLink = pontLink																	// liaison
		if (vLink.peers().pcs.isEmpty() || !isOnHome(this, vLink.peers().home))
		{
			stopSelf()
			return START_NOT_STICKY
		}
		vLink.start()
		vLink.nudge()
		return START_STICKY
	}

	override fun onDestroy()
	{
		fCallback?.let { runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it) } }
		fScope.cancel()
		pontLink.stop()
		super.onDestroy()
	}

	/*
		Écoute du Wi-Fi tant que le service tourne : un nouveau Wi-Fi relance
		les PC absents ; hors du Wi-Fi de la maison, le service s'arrête.
	*/
	private fun watchWifi()
	{
		val vCallback = object : ConnectivityManager.NetworkCallback()
		{
			override fun onAvailable(inNetwork: Network) = check()
			override fun onLost(inNetwork: Network) = check()
		}																						// rappels du système
		val vRequest  = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()	// Wi-Fi seulement
		runCatching { getSystemService(ConnectivityManager::class.java)?.registerNetworkCallback(vRequest, vCallback) }.onSuccess { fCallback = vCallback }
	}

	/*
		Réseau changé : relance des PC absents à la maison, arrêt ailleurs.
	*/
	private fun check()
	{
		if (isOnHome(this, pontLink.peers().home)) pontLink.nudge() else stopSelf()
	}

	/*
		Notification du service : « Relié à <PC> », ou ce qui se passe.
		inState : état de la liaison
	*/
	private fun notification(inState: PhoneLinkState): Notification
	{
		val vOpen = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)	// ouverture de l'appli
		return Notification.Builder(this, kChannelLink)
			.setSmallIcon(R.drawable.ic_launcher_monochrome)
			.setContentTitle(text(inState))
			.setContentIntent(vOpen)
			.setOngoing(true)
			.setShowWhen(false)
			.setCategory(Notification.CATEGORY_SERVICE)
			.build()
	}

	/*
		Texte de la notification.
		inState : état de la liaison
	*/
	private fun text(inState: PhoneLinkState): String
	{
		val vConnected = inState.connected.map { it.label }										// PC reliés
		return when
		{
			vConnected.isNotEmpty()                                       -> "Relié à ${vConnected.joinToString(" et ")}"
			inState.pcs.any { inState.statusOf(it.id) == PcStatus.Refused } -> "Le PC refuse ce téléphone"
			inState.pcs.any { inState.statusOf(it.id) == PcStatus.Searching } -> "Recherche du PC…"
			else                                                          -> "En attente du PC"
		}
	}

	companion object
	{
		/*
			Démarre le service si le téléphone a un PC appairé et est sur le
			Wi-Fi de la maison. Rend faux si Android refuse le démarrage
			depuis l'arrière-plan (appli soumise à l'optimisation de la
			batterie).
			inContext : contexte
		*/
		fun startIfHome(inContext: Context): Boolean
		{
			val vLink = inContext.pontLink																// liaison
			if (vLink.peers().pcs.isEmpty() || !isOnHome(inContext, vLink.peers().home)) return true
			return try
			{
				inContext.startForegroundService(Intent(inContext, LinkService::class.java))
				true
			}
			catch (inError: ForegroundServiceStartNotAllowedException)
			{
				false
			}
		}
	}
}
