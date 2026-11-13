package fr.maubin.bridgeplus.desktop

import androidx.compose.ui.graphics.asSkiaBitmap
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import fr.maubin.bridgeplus.battery.BatteryAlert
import fr.maubin.bridgeplus.clip.ClipOutcome
import fr.maubin.bridgeplus.clip.ClipRelay
import fr.maubin.bridgeplus.history.ClipHistory
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.link.fromBase64
import fr.maubin.bridgeplus.link.isWebLink
import fr.maubin.bridgeplus.link.toBase64
import fr.maubin.bridgeplus.peers.PcLink
import fr.maubin.bridgeplus.peers.PcLinkState
import fr.maubin.bridgeplus.settings.BridgeSettings
import fr.maubin.bridgeplus.settings.SettingsStore
import fr.maubin.bridgeplus.store.SealedFile
import fr.maubin.bridgeplus.store.SecretBox
import fr.maubin.bridgeplus.transfer.DiskSource
import fr.maubin.bridgeplus.transfer.FileTransfers
import fr.maubin.bridgeplus.transfer.FolderSink
import fr.maubin.bridgeplus.transfer.Transfer
import fr.maubin.bridgeplus.transfer.TransferState
import fr.maubin.bridgeplus.transfer.sizeText
import fr.maubin.bridgeplus.ui.components.LinkLook
import fr.maubin.bridgeplus.ui.theme.logoImage
import fr.maubin.bridgeplus.ui.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

private const val kQuickCheckMs = 5_000L												// lecture rapide des adresses du PC
private const val kFullCheckMs  = 60_000L												// relecture complète des réseaux, même sans changement d'adresse

/*
	Dossier des données de BridgePlus sur le PC : %APPDATA%\BridgePlus (ou
	~/.bridgeplus hors de Windows).
*/
fun dataDir(): File =
	System.getenv("APPDATA")?.let { File(it, "BridgePlus") } ?: File(System.getProperty("user.home"), ".bridgeplus")

/*
	Dossier Téléchargements de l'utilisateur (dossier connu de Windows, même
	déplacé) ; ~/Downloads ailleurs ou en cas d'échec.
*/
fun downloadsDir(): File =
	runCatching { File(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Downloads)) }.getOrNull()?.takeIf { WinBridge.available && it.isDirectory }
		?: File(System.getProperty("user.home"), "Downloads")

/*
	Nom de ce PC : nom de l'ordinateur sous Windows, sinon nom d'hôte.
*/
fun pcName(): String =
	System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
		?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
		?: "PC"

/*
	Version de l'appli, lue dans la ressource générée à la construction ;
	null en développement sans cette ressource.
*/
fun appVersion(): String? =
	runCatching { DesktopApp::class.java.getResourceAsStream("/version.properties")?.use { Properties().apply { load(it) }.getProperty("version") } }.getOrNull()

/*
	Taille en mégaoctets, à une décimale (« 1,4 Mo »).
	inBytes : taille en octets
*/
fun megabytes(inBytes: Int): String = String.format(java.util.Locale.FRENCH, "%.1f Mo", inBytes / (1024.0 * 1024.0))

/*
	Allure de l'état de la liaison du PC : icône et titre en deux tons
	(accueil de la fenêtre), texte court (info-bulle de la zone de
	notification).
	look   : état
	muted  : début du titre, en gris
	strong : fin du titre, en blanc
	detail : précision ; null sans
*/
data class PcLook(
	val look   : LinkLook,
	val muted  : String,
	val strong : String,
	val detail : String?
)
{
	// Titre d'une traite, sans point final (info-bulle).
	val title: String get() = (muted + strong).trimEnd('.')
}

/*
	État de la liaison du PC, en clair.
	inState : état de la liaison
*/
fun pcLook(inState: PcLinkState): PcLook
{
	val vConnected = inState.phones.filter { it.id in inState.connected }.map { it.label }	// téléphones reliés
	return when
	{
		inState.error != null      -> PcLook(LinkLook.Refused, "Écoute ", "impossible.", inState.error)
		inState.phones.isEmpty()   -> PcLook(LinkLook.Off, "Aucun téléphone ", "appairé.", "Appairez un téléphone pour commencer.")
		!inState.onHome            -> PcLook(LinkLook.AwayFromHome, "Hors du réseau ", "de la maison.", "BridgePlus attend le retour sur le réseau de la maison.")
		vConnected.isNotEmpty()    -> PcLook(LinkLook.Connected, "Relié à ", "${vConnected.joinToString(" et ")}.", null)
		inState.phones.size == 1   -> PcLook(LinkLook.Absent, "${inState.phones.single().label} est ", "absent.", "La liaison reprendra dès que le téléphone sera sur le Wi-Fi de la maison.")
		else                       -> PcLook(LinkLook.Absent, "Téléphones ", "absents.", "La liaison reprendra dès qu'un téléphone sera sur le Wi-Fi de la maison.")
	}
}

/*
	Appli PC : liaison avec les téléphones, presse-papiers partagé et son
	historique chiffré, raccourci de la carte, réglages,
	et surveillance du réseau (lecture rapide des adresses toutes les 5 s ;
	relecture complète des réseaux à chaque changement, et au moins toutes
	les minutes).
	fDir      : dossier des données
	fBox      : coffret du fichier des appareils
	fNetworks : lecture des réseaux du PC
*/
class DesktopApp(
	private val fDir      : File                      = dataDir(),
	private val fBox      : SecretBox                 = DpapiBox,
	private val fNetworks : () -> List<LocalNetwork>  = ::currentNetworks
)
{
	private val fScope    = CoroutineScope(SupervisorJob() + Dispatchers.IO)				// tâches de fond
	private val fStore    = SettingsStore(File(fDir, "reglages.json"))						// fichier des réglages
	private val fSettings = MutableStateFlow(fStore.load())									// réglages

	private val fNotices  = MutableSharedFlow<String>(extraBufferCapacity = 8)				// messages pour l'utilisateur (notifications)
	private var fBoard    : WinBridge? = null											// pont avec Windows (presse-papiers, raccourci) ; null ailleurs
	private val fHotkeyOk = MutableStateFlow(true)											// raccourci accepté par Windows
	private val fPresses  = MutableSharedFlow<ActiveWindow>(extraBufferCapacity = 4)		// appuis sur le raccourci (fenêtre active à ce moment)

	/*
		Historique chiffré des échanges (DPAPI).
	*/
	val history = ClipHistory(File(fDir, "historique"), fBox)

	private val fChoice   = MutableStateFlow<List<File>?>(null)							// fichiers en attente du choix du téléphone ; null sans
	private val fSources  = ConcurrentHashMap<String, String>()							// chemins des fichiers envoyés, par transfert
	private val fNoticeTags = ConcurrentHashMap<String, Pair<String, String>>()			// notifications du téléphone affichées : étiquette → (téléphone, clé)
	private val fBattery  = MutableStateFlow<Map<String, LinkMessage.Battery>>(emptyMap())	// batterie de chaque téléphone
	private val fAlerts   = ConcurrentHashMap<String, BatteryAlert>()						// alerte de batterie faible, par téléphone

	/*
		Mises à jour de l'appli (GitHub), créées par Main ; null avant.
	*/
	var updates: UpdateManager? = null

	/*
		Batterie de chaque téléphone relié (identifiant → niveau et charge).
	*/
	val battery: StateFlow<Map<String, LinkMessage.Battery>> = fBattery

	/*
		Liaison avec les téléphones ; les textes reçus vont au relais du
		presse-papiers, les fichiers aux transferts.
	*/
	val link: PcLink = PcLink(
		fFile      = SealedFile(File(fDir, "appareils.bin"), fBox),
		fName      = pcName(),
		fVersion   = appVersion() ?: "dev",
		fNetworks  = fNetworks,
		fOnMessage = { vPhone, vMessage ->
			when (vMessage)
			{
				is LinkMessage.Clip          -> clips.receive(vPhone.label, vMessage)
				is LinkMessage.PhoneNotice   -> showNotice(vPhone.id, vPhone.label, vMessage)
				is LinkMessage.NoticeRemoved -> noticeTag(vPhone.id, vMessage.key).let { vTag -> fNoticeTags.remove(vTag); WinToast.remove(vTag) }
				is LinkMessage.AppIcon       -> saveIcon(vMessage)
				is LinkMessage.Battery       -> batteryReceived(vPhone.id, vPhone.label, vMessage)
				is LinkMessage.OpenLink      -> if (isWebLink(vMessage.url)) fScope.launch { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(vMessage.url.trim())) } }
				else                         -> files.onMessage(vPhone.id, vPhone.label, vMessage)
			}
		},
		fOnChunk   = { vPhone, vChunk -> files.onChunk(vPhone.id, vChunk) },
		fOnClosed  = { vPhone -> files.onDisconnected(vPhone.id); fBattery.update { it - vPhone.id } }
	)

	/*
		Transferts de fichiers avec les téléphones ; réception dans le dossier
		réglé (Téléchargements\BridgePlus par défaut).
	*/
	val files: FileTransfers = FileTransfers(link::send, link::sendChunk, FolderSink(::receiveDir), ::transferFinished)

	/*
		Fichiers en attente du choix du téléphone (plusieurs sont reliés) ;
		null sans.
	*/
	val pendingChoice: StateFlow<List<File>?> = fChoice

	/*
		Relais du presse-papiers : copies du PC vers les téléphones reliés,
		textes reçus placés dans le presse-papiers de Windows.
	*/
	val clips: ClipRelay = ClipRelay(link::sendAll, { fBoard?.write(it) ?: false }, { fSettings.value }, { vText, vOut, vPeers -> history.add(vText, vOut, vPeers) })

	/*
		Appuis sur le raccourci de la carte, avec la fenêtre active à ce
		moment (la carte descend sur son écran, puis lui rend la main).
	*/
	val hotkeyPresses: SharedFlow<ActiveWindow> = fPresses

	/*
		Vrai si Windows a accepté le raccourci réglé.
	*/
	val hotkeyOk: StateFlow<Boolean> = fHotkeyOk

	/*
		Vrai si le raccourci peut être réglé (Windows).
	*/
	val hasHotkey: Boolean get() = WinBridge.available

	/*
		Messages à montrer à l'utilisateur dans une notification (texte trop
		long, par exemple).
	*/
	val notices: SharedFlow<String> = fNotices

	/*
		Réglages en cours.
	*/
	val settings: StateFlow<BridgeSettings> = fSettings

	/*
		Démarre la surveillance du réseau.
	*/
	fun start()
	{
		if (WinBridge.available)
		{
			fScope.launch { runCatching { WinToast.setup(writeIcon()) } }
			WinToast.onDismissed = ::dismissNotice
			fBoard = WinBridge(::copied) { ActiveWindow.current().takeIf { !it.isOwn }?.let { fPresses.tryEmit(it) } }.also { it.start() }
			fScope.launch { registerHotkey() }
		}
		fScope.launch {
			var vPrint = ""																	// dernière empreinte des adresses
			var vLast  = 0L																	// dernière relecture complète
			while (true)
			{
				val vNow  = System.currentTimeMillis()											// maintenant
				val vNext = addressFingerprint()												// adresses actuelles
				if (vNext != vPrint || vNow - vLast >= kFullCheckMs || link.state.value.offer != null)
				{
					vPrint = vNext
					vLast  = vNow
					runCatching { link.refresh() }
				}
				delay(kQuickCheckMs)
			}
		}
	}

	/*
		Enregistre auprès de Windows le raccourci réglé ; note s'il a été
		accepté.
	*/
	fun registerHotkey()
	{
		val vHotkey = Hotkey.parse(fSettings.value.historyHotkey)										// raccourci réglé
		fHotkeyOk.value = fBoard?.register(vHotkey.modifiers, vHotkey.key) ?: true
	}

	/*
		Dossier de réception des fichiers : celui des réglages, sinon
		Téléchargements\BridgePlus.
	*/
	fun receiveDir(): File = fSettings.value.receiveFolder.takeIf { it.isNotBlank() }?.let(::File) ?: File(downloadsDir(), "BridgePlus")

	/*
		Envoie des fichiers à un téléphone : celui donné, le seul relié, ou,
		s'il y en a plusieurs, celui que l'utilisateur choisira (pendingChoice).
		Les dossiers sont ignorés.
		inFiles   : fichiers
		inPhoneId : téléphone ; null pour le choisir
	*/
	fun sendFiles(
		inFiles   : List<File>,
		inPhoneId : String? = null
	)
	{
		val vFiles     = inFiles.filter { it.isFile }													// fichiers seulement
		if (vFiles.isEmpty()) return
		val vConnected = link.state.value.let { vState -> vState.phones.filter { it.id in vState.connected } }	// téléphones reliés
		val vTarget    = when
		{
			inPhoneId != null    -> vConnected.firstOrNull { it.id == inPhoneId }
			vConnected.size == 1 -> vConnected.single()
			else                 -> null
		}																									// destinataire
		if (vTarget == null)
		{
			if (vConnected.isEmpty()) notify("Aucun téléphone relié", "Les fichiers ne sont pas partis : le téléphone doit être sur le Wi-Fi de la maison.")
			else fChoice.value = vFiles
			return
		}
		fChoice.value = null
		files.send(vTarget.id, vTarget.label, vFiles.map(::DiskSource)).forEachIndexed { vIndex, vTransfer -> fSources[vTransfer.id] = vFiles[vIndex].absolutePath }
	}

	/*
		Abandonne le choix du téléphone (fichiers non envoyés).
	*/
	fun cancelChoice()
	{
		fChoice.value = null
	}

	/*
		Commande transmise par une seconde instance de BridgePlus : lien d'un
		bouton de notification (« bridgeplus:ouvrir?id=… »), ou fichier du
		menu de l'Explorateur. Un lien n'agit que sur un élément de
		l'historique : son identifiant, tiré au hasard, est vérifié.
		inCommand : commande (« lien <adresse> », « envoyer <chemin> »)
	*/
	fun command(inCommand: String)
	{
		val vVerb = inCommand.substringBefore(' ')													// nature
		val vArg  = inCommand.substringAfter(' ', "")												// argument
		when (vVerb)
		{
			"envoyer" -> sendFiles(listOf(File(vArg)))
			"lien"    ->
			{
				val vAction = vArg.removePrefix("bridgeplus:").substringBefore('?')					// action
				val vId     = vArg.substringAfter("id=", "").let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }	// élément
				if (vAction == "masquer") { WinToast.remove(vId); dismissNotice(vId); return }
				val vFile   = history.items.value.firstOrNull { it.id == vId && it.isFile }?.location?.let(::File) ?: return	// fichier
				if (vAction == "ouvrir") openFile(vFile) else showInFolder(vFile)
			}
		}
	}

	/*
		Ouvre un fichier avec l'appli associée de Windows.
		inFile : fichier
	*/
	fun openFile(inFile: File)
	{
		fScope.launch { runCatching { java.awt.Desktop.getDesktop().open(inFile) } }
	}

	/*
		Montre un fichier dans l'Explorateur, sélectionné.
		inFile : fichier
	*/
	fun showInFolder(inFile: File)
	{
		fScope.launch {
			runCatching { ProcessBuilder("explorer.exe", "/select,", inFile.absolutePath).start() }
				.onFailure { runCatching { java.awt.Desktop.getDesktop().open(inFile.parentFile) } }
		}
	}

	/*
		Fait sonner les téléphones reliés (même en silencieux), ou arrête la
		sonnerie ; rend leurs noms (vide : aucun téléphone relié).
		inOn : vrai pour sonner
	*/
	fun ring(inOn: Boolean): List<String> = link.sendAll(LinkMessage.Ring(inOn))

	/*
		Ouvre un lien sur les téléphones reliés (une notification à toucher si
		BridgePlus n'y est pas affiché). Seuls les liens web partent ; rend
		le message à montrer.
		inUrl : lien
	*/
	fun sendLink(inUrl: String): String
	{
		if (!isWebLink(inUrl)) return "Ce n'est pas un lien web (http ou https)."
		val vPeers = link.sendAll(LinkMessage.OpenLink(inUrl.trim()))								// téléphones joints
		return if (vPeers.isEmpty()) "Aucun téléphone relié." else "Lien envoyé à ${vPeers.joinToString(" et ")}."
	}

	/*
		Batterie reçue d'un téléphone : retenue, et alerte une fois par
		décharge sous 20 % si le réglage le demande.
		inPhoneId : téléphone
		inPhone   : son nom
		inBattery : niveau et charge
	*/
	private fun batteryReceived(
		inPhoneId : String,
		inPhone   : String,
		inBattery : LinkMessage.Battery
	)
	{
		fBattery.update { it + (inPhoneId to inBattery) }
		val vAlert = fAlerts.computeIfAbsent(inPhoneId) { BatteryAlert() }.update(inBattery.level, inBattery.charging)	// alerte à donner
		if (vAlert && fSettings.value.batteryAlert) notify("Batterie faible", "$inPhone : ${inBattery.level} %. Pensez à le recharger.")
	}

	/*
		Notification du téléphone : affichée comme une notification de
		Windows (icône de l'appli, titre, texte, « WhatsApp · Pixel »), suivie
		pour que sa fermeture la masque sur le téléphone ; rien n'est
		enregistré. Sans notifications de Windows, la zone de notification
		prend le relais.
		inPhoneId : téléphone
		inPhone   : son nom
		inNotice  : notification
	*/
	private fun showNotice(
		inPhoneId : String,
		inPhone   : String,
		inNotice  : LinkMessage.PhoneNotice
	)
	{
		if (!fSettings.value.noticesShow) return
		val vTag   = noticeTag(inPhoneId, inNotice.key)												// étiquette
		val vTitle = inNotice.title.ifBlank { inNotice.app }											// titre
		fNoticeTags[vTag] = inPhoneId to inNotice.key
		while (fNoticeTags.size > 500) fNoticeTags.keys.firstOrNull()?.let(fNoticeTags::remove)
		fScope.launch {
			val vShown = WinToast.show(
				inTitle       = vTitle,
				inBody        = inNotice.text,
				inActions     = listOf(ToastAction("Masquer sur le téléphone", WinToast.link("masquer", vTag))),
				inTag         = vTag,
				inImage       = iconFile(inNotice.pkg),
				inAttribution = "${inNotice.app} · $inPhone"
			)																						// notification partie
			if (!vShown) fNotices.tryEmit("$vTitle : ${inNotice.text}")
		}
	}

	/*
		Notification du téléphone fermée sur le PC (croix, clic, bouton
		« Masquer sur le téléphone ») : masquée aussi sur le téléphone.
		inTag : étiquette
	*/
	private fun dismissNotice(inTag: String)
	{
		val (vPhoneId, vKey) = fNoticeTags.remove(inTag) ?: return									// téléphone et clé
		link.send(vPhoneId, LinkMessage.NoticeDismiss(vKey))
	}

	/*
		Icône d'une appli du téléphone, gardée dans le dossier des données
		(seule chose gardée des notifications).
		inIcon : icône reçue
	*/
	private fun saveIcon(inIcon: LinkMessage.AppIcon)
	{
		val vPng = fromBase64(inIcon.png)?.takeIf { it.size in 1..256 * 1024 } ?: return				// image
		runCatching { iconFile(inIcon.pkg).apply { parentFile.mkdirs() }.writeBytes(vPng) }
	}

	/*
		Fichier de l'icône d'une appli du téléphone (nom tiré du paquet).
		inPkg : paquet
	*/
	private fun iconFile(inPkg: String): File = File(fDir, "icones/${noticeTag("icone", inPkg)}.png")

	/*
		Étiquette d'une notification : empreinte courte du téléphone et de la
		clé (sûre comme nom de fichier et dans les notifications de Windows,
		64 caractères au plus).
		inPhoneId : téléphone
		inKey     : clé de la notification
	*/
	private fun noticeTag(
		inPhoneId : String,
		inKey     : String
	): String = toBase64(MessageDigest.getInstance("SHA-256").digest("$inPhoneId|$inKey".toByteArray(Charsets.UTF_8))).take(22)

	/*
		Logo (PNG) des notifications de Windows, écrit dans le dossier des
		données.
	*/
	private fun writeIcon(): File =
		File(fDir, "bridgeplus.png").apply {
			parentFile.mkdirs()
			writeBytes(Image.makeFromBitmap(logoImage(256).asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		}

	/*
		Notification native de Windows (sinon celle de la zone de
		notification).
		inTitle   : titre
		inBody    : texte
		inActions : boutons
	*/
	fun notify(
		inTitle   : String,
		inBody    : String,
		inActions : List<ToastAction> = emptyList()
	)
	{
		fScope.launch { if (!WinToast.show(inTitle, inBody, inActions.firstOrNull()?.link, inActions)) fNotices.tryEmit("$inTitle : $inBody") }
	}

	/*
		Transfert terminé : un fichier reçu va dans l'historique et se signale
		par une notification avec « Ouvrir » et « Ouvrir le dossier » ; un
		envoi réussi va dans l'historique sans bruit ; un échec se signale.
		inTransfer : transfert
	*/
	private fun transferFinished(inTransfer: Transfer)
	{
		val vSource = fSources.remove(inTransfer.id)													// chemin du fichier envoyé
		when (inTransfer.state)
		{
			TransferState.Done      ->
			{
				val vItem = history.addFile(inTransfer.name, inTransfer.size, inTransfer.outgoing, listOf(inTransfer.peer), if (inTransfer.outgoing) vSource.orEmpty() else inTransfer.location)	// élément de l'historique
				if (!inTransfer.outgoing) notify(
					"Fichier reçu de ${inTransfer.peer}",
					"${inTransfer.name} · ${sizeText(inTransfer.size)}",
					listOf(ToastAction("Ouvrir", WinToast.link("ouvrir", vItem.id)), ToastAction("Ouvrir le dossier", WinToast.link("dossier", vItem.id)))
				)
			}
			TransferState.Failed    -> notify(if (inTransfer.outgoing) "Envoi à ${inTransfer.peer} échoué" else "Réception de ${inTransfer.peer} échouée", "${inTransfer.name} : ${inTransfer.reason.orEmpty()}")
			else                    -> Unit
		}
	}

	/*
		Change le raccourci de la carte et l'enregistre auprès de Windows.
		inSpec : raccourci (texte des réglages, « Ctrl+Alt+V »)
	*/
	fun setHotkey(inSpec: String)
	{
		updateSettings { it.copy(historyHotkey = inSpec) }
		fScope.launch { registerHotkey() }
	}

	/*
		Remet un élément de l'historique dans le presse-papiers ; il ne repart
		pas vers le téléphone (déjà échangé). Rend faux s'il n'existe plus.
		inId : élément
	*/
	fun copyFromHistory(inId: String): Boolean
	{
		val vText = history.text(inId) ?: return false													// texte complet
		clips.markSeen(vText)
		return fBoard?.write(vText) ?: false
	}

	/*
		Copie d'une autre appli sur le PC : proposée au relais. Un texte trop
		long le dit dans une notification ; le reste est silencieux.
		inText      : texte copié ; null sans texte
		inSensitive : contenu marqué sensible
	*/
	private fun copied(
		inText      : String?,
		inSensitive : Boolean
	)
	{
		val vResult = clips.offer(inText, inSensitive)												// issue
		if (vResult.outcome == ClipOutcome.TooLarge) fNotices.tryEmit("Texte trop long pour le presse-papiers (${megabytes(vResult.bytes)}) : il n'a pas été envoyé au téléphone.")
	}

	/*
		Lance une action de la liaison en tâche de fond (commandes réseau,
		fichier scellé).
		inBlock : action
	*/
	fun background(inBlock: PcLink.() -> Unit)
	{
		fScope.launch { runCatching { link.inBlock() } }
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
		Arrête la liaison et les tâches (fermeture de l'appli).
	*/
	fun stop()
	{
		WinToast.stop()
		fScope.cancel()
		link.stop()
	}
}
