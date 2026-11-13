package fr.maubin.pontplus

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/*
	Retour sur un Wi-Fi, même appli fermée : le système rappelle ce
	récepteur (inscription par PendingIntent, qui survit à la fin du
	processus mais pas au redémarrage : BootReceiver la refait). S'il s'agit
	du Wi-Fi de la maison, la liaison est relancée.
*/
class NetworkWatch : BroadcastReceiver()
{
	override fun onReceive(
		inContext : Context,
		inIntent  : Intent
	)
	{
		LinkService.startIfHome(inContext)
	}

	companion object
	{
		/*
			Inscrit l'écoute des Wi-Fi auprès du système (sans effet si elle
			l'est déjà : le même PendingIntent remplace l'inscription).
			inContext : contexte
		*/
		fun register(inContext: Context)
		{
			val vIntent  = PendingIntent.getBroadcast(
				inContext, 0, Intent(inContext, NetworkWatch::class.java),
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
			)																					// rappel (le système y ajoute le réseau)
			val vRequest = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()	// Wi-Fi seulement
			runCatching { inContext.getSystemService(ConnectivityManager::class.java)?.registerNetworkCallback(vRequest, vIntent) }
		}
	}
}

/*
	Redémarrage du téléphone ou mise à jour de l'appli : l'écoute des Wi-Fi
	est réinscrite et la liaison relancée si le téléphone est à la maison.
	Ces deux occasions font partie des exceptions où Android permet de
	démarrer un service de premier plan depuis l'arrière-plan.
*/
class BootReceiver : BroadcastReceiver()
{
	override fun onReceive(
		inContext : Context,
		inIntent  : Intent
	)
	{
		if (inIntent.action != Intent.ACTION_BOOT_COMPLETED && inIntent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
		NetworkWatch.register(inContext)
		LinkService.startIfHome(inContext)
	}
}
