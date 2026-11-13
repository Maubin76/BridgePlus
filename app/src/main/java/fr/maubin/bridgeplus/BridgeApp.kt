package fr.maubin.bridgeplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import fr.maubin.bridgeplus.clip.ClipRelay
import fr.maubin.bridgeplus.clip.clipMessage
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.discoverPcs
import fr.maubin.bridgeplus.peers.PhoneLink
import fr.maubin.bridgeplus.settings.BridgeSettings
import fr.maubin.bridgeplus.settings.SettingsStore
import fr.maubin.bridgeplus.store.SealedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

const val kChannelLink = "liaison"													// canal de la notification du service (discrète)

/*
	Appli Android : une seule liaison et un seul relais du presse-papiers
	pour toute l'appli (écrans, service, tuile, partage), créés à la première
	demande. Crée les canaux de notification et inscrit l'écoute du retour
	sur un Wi-Fi.
*/
class BridgeApp : Application()
{
	private val fStore    by lazy { SettingsStore(File(filesDir, "reglages.json")) }		// fichier des réglages
	private val fSettings by lazy { MutableStateFlow(fStore.load()) }						// réglages
	@Volatile private var fWaiting : String? = null											// texte reçu qu'Android n'a pas laissé copier
	private val fSender   = Executors.newSingleThreadExecutor { inTask -> Thread(inTask, "BridgePlus-envoi").apply { isDaemon = true } }	// envois (hors du fil de l'écran)

	/*
		Liaison du téléphone (fichier des appareils scellé par le Keystore) ;
		les textes reçus vont au relais du presse-papiers.
	*/
	val link: PhoneLink by lazy {
		PhoneLink(
			fFile      = SealedFile(File(filesDir, "appareils.bin"), KeystoreBox),
			fName      = phoneName(this),
			fVersion   = BuildConfig.VERSION_NAME,
			fDiscover  = { discoverPcs() },
			fOnMessage = { vPc, vMessage -> if (vMessage is LinkMessage.Clip) received(vPc.label, vMessage) }
		)
	}

	/*
		Relais du presse-papiers : envoi aux PC reliés, textes reçus placés
		dans le presse-papiers (ou proposés par une notification).
	*/
	val clips: ClipRelay by lazy { ClipRelay({ link.sendAll(it) }, { writeClipboard(this, it) }, { fSettings.value }) }

	/*
		Réglages en cours.
	*/
	val settings: StateFlow<BridgeSettings> get() = fSettings

	override fun onCreate()
	{
		super.onCreate()
		getSystemService(NotificationManager::class.java)?.let { vManager ->
			vManager.createNotificationChannel(
				NotificationChannel(kChannelLink, "Liaison avec le PC", NotificationManager.IMPORTANCE_MIN).apply {
					description = "Indique discrètement que BridgePlus est relié au PC."
					setShowBadge(false)
				}
			)
			vManager.createNotificationChannel(
				NotificationChannel(kChannelClips, "Textes reçus", NotificationManager.IMPORTANCE_DEFAULT).apply {
					description = "Texte reçu du PC qu'Android n'a pas laissé copier : touchez « Copier »."
				}
			)
		}
		NetworkWatch.register(this)
	}

	/*
		Modifie les réglages et les enregistre.
		inBlock : modification
	*/
	fun updateSettings(inBlock: (BridgeSettings) -> BridgeSettings)
	{
		fSettings.update(inBlock)
		runCatching { fStore.save(fSettings.value) }
	}

	/*
		Propose un texte à l'envoi sur un fil à part (le réseau est interdit
		sur le fil de l'écran), puis rend le message à montrer sur le fil de
		l'écran.
		inText      : texte ; null sans texte
		inSensitive : contenu marqué sensible
		inExplicit  : geste explicite (envoyé même si l'envoi automatique est désactivé)
		inOnDone    : message à montrer ; null quand il n'y a rien à dire
	*/
	fun sendClip(
		inText      : String?,
		inSensitive : Boolean,
		inExplicit  : Boolean,
		inOnDone    : (String?) -> Unit = {}
	)
	{
		fSender.execute {
			val vMessage = clipMessage(clips.offer(inText, inSensitive, inExplicit))				// issue en clair
			Handler(Looper.getMainLooper()).post { inOnDone(vMessage) }
		}
	}

	/*
		Rend, puis oublie, le texte reçu qui attend d'être copié ; null sans.
	*/
	fun takeWaiting(): String? = fWaiting.also { fWaiting = null }

	/*
		Texte reçu d'un PC : placé dans le presse-papiers ; si Android refuse,
		gardé en mémoire et proposé par une notification.
		inPeer : nom du PC
		inClip : message reçu
	*/
	private fun received(
		inPeer : String,
		inClip : LinkMessage.Clip
	)
	{
		if (clips.receive(inPeer, inClip) || !fSettings.value.clipReceive) return
		fWaiting = inClip.text
		notifyReceived(this, inPeer, inClip.text)
	}
}

/*
	Appli BridgePlus, depuis n'importe quel contexte.
*/
val Context.bridgeApp: BridgeApp get() = applicationContext as BridgeApp

/*
	Liaison de l'appli, depuis n'importe quel contexte.
*/
val Context.bridgeLink: PhoneLink get() = bridgeApp.link
