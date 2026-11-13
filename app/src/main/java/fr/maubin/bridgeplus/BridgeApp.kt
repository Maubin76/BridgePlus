package fr.maubin.bridgeplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import fr.maubin.bridgeplus.clip.ClipRelay
import fr.maubin.bridgeplus.clip.clipMessage
import fr.maubin.bridgeplus.history.ClipHistory
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.discoverPcs
import fr.maubin.bridgeplus.notice.NoticeOutbox
import fr.maubin.bridgeplus.peers.PairedPc
import fr.maubin.bridgeplus.peers.PhoneLink
import fr.maubin.bridgeplus.settings.BridgeSettings
import fr.maubin.bridgeplus.settings.SettingsStore
import fr.maubin.bridgeplus.store.SealedFile
import fr.maubin.bridgeplus.transfer.FileTransfers
import fr.maubin.bridgeplus.transfer.Transfer
import fr.maubin.bridgeplus.transfer.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

const val kChannelLink = "liaison"													// canal de la notification du service (discrète)

/*
	Appli Android : une seule liaison, un seul relais du presse-papiers et un
	seul moteur des transferts pour toute l'appli (écrans, service, tuile,
	partage), créés à la première demande. Pendant un transfert, le
	téléphone et le Wi-Fi restent éveillés et une notification montre la
	progression. Crée les canaux de notification et inscrit l'écoute du retour
	sur un Wi-Fi.
*/
class BridgeApp : Application()
{
	private val fStore    by lazy { SettingsStore(File(filesDir, "reglages.json")) }		// fichier des réglages
	private val fSettings by lazy { MutableStateFlow(fStore.load()) }						// réglages
	@Volatile private var fWaiting : String? = null											// texte reçu qu'Android n'a pas laissé copier
	private val fSender   = Executors.newSingleThreadExecutor { inTask -> Thread(inTask, "BridgePlus-envoi").apply { isDaemon = true } }	// envois (hors du fil de l'écran)

	private val fScope    = CoroutineScope(SupervisorJob() + Dispatchers.Default)			// suivi des transferts
	private val fSources  = ConcurrentHashMap<String, String>()								// adresses des fichiers envoyés, par transfert

	/*
		Liaison du téléphone (fichier des appareils scellé par le Keystore) ;
		les textes reçus vont au relais du presse-papiers, les fichiers aux
		transferts.
	*/
	val link: PhoneLink by lazy {
		PhoneLink(
			fFile      = SealedFile(File(filesDir, "appareils.bin"), KeystoreBox),
			fName      = phoneName(this),
			fVersion   = BuildConfig.VERSION_NAME,
			fDiscover  = { discoverPcs() },
			fOnMessage = { vPc, vMessage ->
				when (vMessage)
				{
					is LinkMessage.Clip          -> received(vPc.label, vMessage)
					is LinkMessage.NoticeDismiss -> NoticeListener.dismiss(vMessage.key)
					else                         -> files.onMessage(vPc.id, vPc.label, vMessage)
				}
			},
			fOnChunk   = { vPc, vChunk -> files.onChunk(vPc.id, vChunk) },
			fOnClosed  = { vPc -> files.onDisconnected(vPc.id) }
		)
	}

	/*
		Transferts de fichiers avec les PC ; réception dans Download/BridgePlus.
	*/
	val files: FileTransfers by lazy { FileTransfers({ vId, vMessage -> link.send(vId, vMessage) }, { vId, vChunk -> link.sendChunk(vId, vChunk) }, MediaStoreSink(this), ::transferFinished) }

	/*
		Relais du presse-papiers : envoi aux PC reliés, textes reçus placés
		dans le presse-papiers (ou proposés par une notification).
	*/
	val clips: ClipRelay by lazy { ClipRelay({ link.sendAll(it) }, { writeClipboard(this, it) }, { fSettings.value }, { vText, vOut, vPeers -> history.add(vText, vOut, vPeers) }) }

	/*
		Envoi des notifications du téléphone aux PC reliés (filtre : liste
		blanche, exclusions d'office, codes à usage unique).
	*/
	val notices: NoticeOutbox by lazy { NoticeOutbox({ link.sendAll(it) }, { fSettings.value }, { appIconPng(this, it) }, packageName) }

	/*
		Historique chiffré des échanges (clé du Keystore).
	*/
	val history: ClipHistory by lazy { ClipHistory(File(filesDir, "historique"), KeystoreBox) }

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
				NotificationChannel(kChannelTransfers, "Fichiers", NotificationManager.IMPORTANCE_LOW).apply {
					description = "Progression des fichiers envoyés ou reçus, et fichiers arrivés."
				}
			)
			vManager.createNotificationChannel(
				NotificationChannel(kChannelClips, "Textes reçus", NotificationManager.IMPORTANCE_DEFAULT).apply {
					description = "Texte reçu du PC qu'Android n'a pas laissé copier : touchez « Copier »."
				}
			)
		}
		NetworkWatch.register(this)
		fScope.launch { link.state.map { it.connected.map { vPc -> vPc.id }.toSet() }.distinctUntilChanged().collect { notices.newSession() } }
		val vLocks = TransferWakeLocks(this)															// verrous d'éveil
		fScope.launch {
			files.transfers.collect { vAll ->
				val vActive = vAll.filter { it.active }.sortedBy { it.at }								// transferts en cours
				vLocks.hold(vActive.isNotEmpty())
				showProgress(this@BridgeApp, vActive)
			}
		}
	}

	/*
		PC reliés en ce moment (destinataires possibles d'un fichier).
	*/
	fun connectedPcs(): List<PairedPc> = link.state.value.connected

	/*
		Envoie des fichiers à un PC relié ; ils partent l'un après l'autre,
		portés par le service (l'écran peut se fermer).
		inPc      : PC
		inSources : fichiers ouverts
	*/
	fun sendFiles(
		inPc      : PairedPc,
		inSources : List<OpenedSource>
	)
	{
		files.send(inPc.id, inPc.label, inSources).forEachIndexed { vIndex, vTransfer -> fSources[vTransfer.id] = inSources[vIndex].uri }
	}

	/*
		Transfert terminé : un fichier reçu ou envoyé va dans l'historique ;
		un fichier reçu ou un échec se signale par une notification.
		inTransfer : transfert
	*/
	private fun transferFinished(inTransfer: Transfer)
	{
		val vSource = fSources.remove(inTransfer.id)													// adresse du fichier envoyé
		if (inTransfer.state == TransferState.Done) history.addFile(inTransfer.name, inTransfer.size, inTransfer.outgoing, listOf(inTransfer.peer), if (inTransfer.outgoing) vSource.orEmpty() else inTransfer.location)
		showFinished(this, inTransfer)
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
		Remet un élément de l'historique dans le presse-papiers (appli au
		premier plan) ; il ne repart pas vers le PC. Rend le message à montrer
		sur le fil de l'écran.
		inId     : élément
		inOnDone : message à montrer
	*/
	fun copyFromHistory(
		inId     : String,
		inOnDone : (String) -> Unit
	)
	{
		fSender.execute {
			val vText    = history.text(inId)															// texte complet
			val vMessage = when
			{
				vText == null              -> "Cet élément n'existe plus."
				vText.length > kMaxAndroidClip -> "Trop long pour le presse-papiers d'Android."
				else                       -> { clips.markSeen(vText); if (writeClipboard(this, vText)) "Copié" else "Copie impossible." }
			}																							// issue en clair
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
