package fr.maubin.bridgeplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import fr.maubin.bridgeplus.link.discoverPcs
import fr.maubin.bridgeplus.peers.PhoneLink
import fr.maubin.bridgeplus.store.SealedFile
import java.io.File

const val kChannelLink = "liaison"													// canal de la notification du service (discrète)

/*
	Appli Android : une seule liaison pour toute l'appli (écrans, service,
	récepteurs), créée à la première demande. Crée le canal de notification
	du service et inscrit l'écoute du retour sur un Wi-Fi.
*/
class BridgeApp : Application()
{
	/*
		Liaison du téléphone (fichier des appareils scellé par le Keystore).
	*/
	val link: PhoneLink by lazy {
		PhoneLink(
			fFile     = SealedFile(File(filesDir, "appareils.bin"), KeystoreBox),
			fName     = phoneName(this),
			fVersion  = BuildConfig.VERSION_NAME,
			fDiscover = { discoverPcs() }
		)
	}

	override fun onCreate()
	{
		super.onCreate()
		getSystemService(NotificationManager::class.java)?.createNotificationChannel(
			NotificationChannel(kChannelLink, "Liaison avec le PC", NotificationManager.IMPORTANCE_MIN).apply {
				description = "Indique discrètement que BridgePlus est relié au PC."
				setShowBadge(false)
			}
		)
		NetworkWatch.register(this)
	}
}

/*
	Liaison de l'appli, depuis n'importe quel contexte.
*/
val Context.bridgeLink: PhoneLink get() = (applicationContext as BridgeApp).link
