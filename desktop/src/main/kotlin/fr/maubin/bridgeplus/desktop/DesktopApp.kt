package fr.maubin.bridgeplus.desktop

import fr.maubin.bridgeplus.clip.ClipOutcome
import fr.maubin.bridgeplus.clip.ClipRelay
import fr.maubin.bridgeplus.history.ClipHistory
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.peers.PcLink
import fr.maubin.bridgeplus.peers.PcLinkState
import fr.maubin.bridgeplus.settings.BridgeSettings
import fr.maubin.bridgeplus.settings.SettingsStore
import fr.maubin.bridgeplus.store.SealedFile
import fr.maubin.bridgeplus.store.SecretBox
import fr.maubin.bridgeplus.ui.components.LinkLook
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
import java.io.File
import java.util.Properties

private const val kQuickCheckMs = 5_000L												// lecture rapide des adresses du PC
private const val kFullCheckMs  = 60_000L												// relecture complète des réseaux, même sans changement d'adresse

/*
	Dossier des données de BridgePlus sur le PC : %APPDATA%\BridgePlus (ou
	~/.bridgeplus hors de Windows).
*/
fun dataDir(): File =
	System.getenv("APPDATA")?.let { File(it, "BridgePlus") } ?: File(System.getProperty("user.home"), ".bridgeplus")

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

	/*
		Liaison avec les téléphones ; les textes reçus vont au relais du
		presse-papiers.
	*/
	val link: PcLink = PcLink(SealedFile(File(fDir, "appareils.bin"), fBox), pcName(), appVersion() ?: "dev", fNetworks, { vPhone, vMessage ->
		if (vMessage is LinkMessage.Clip) clips.receive(vPhone.label, vMessage)
	})

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
		fScope.cancel()
		link.stop()
	}
}
