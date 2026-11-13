package fr.maubin.pontplus.desktop

import fr.maubin.pontplus.link.LocalNetwork
import fr.maubin.pontplus.peers.PcLink
import fr.maubin.pontplus.peers.PcLinkState
import fr.maubin.pontplus.settings.PontSettings
import fr.maubin.pontplus.settings.SettingsStore
import fr.maubin.pontplus.store.SealedFile
import fr.maubin.pontplus.store.SecretBox
import fr.maubin.pontplus.ui.components.LinkLook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Properties

private const val kQuickCheckMs = 5_000L												// lecture rapide des adresses du PC
private const val kFullCheckMs  = 60_000L												// relecture complète des réseaux, même sans changement d'adresse

/*
	Dossier des données de PontPlus sur le PC : %APPDATA%\PontPlus (ou
	~/.pontplus hors de Windows).
*/
fun dataDir(): File =
	System.getenv("APPDATA")?.let { File(it, "PontPlus") } ?: File(System.getProperty("user.home"), ".pontplus")

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
	Allure de l'état de la liaison du PC : icône et texte (zone de
	notification, accueil de la fenêtre).
	look   : état
	title  : texte court (info-bulle, titre)
	detail : précision ; null sans
*/
data class PcLook(
	val look   : LinkLook,
	val title  : String,
	val detail : String?
)

/*
	État de la liaison du PC, en clair.
	inState : état de la liaison
*/
fun pcLook(inState: PcLinkState): PcLook
{
	val vConnected = inState.phones.filter { it.id in inState.connected }.map { it.label }	// téléphones reliés
	return when
	{
		inState.error != null      -> PcLook(LinkLook.Refused, "Écoute impossible", inState.error)
		inState.phones.isEmpty()   -> PcLook(LinkLook.Off, "Aucun téléphone appairé", "Appairez un téléphone pour commencer.")
		!inState.onHome            -> PcLook(LinkLook.AwayFromHome, "Hors du réseau de la maison", "PontPlus attend le retour sur le réseau de la maison.")
		vConnected.isNotEmpty()    -> PcLook(LinkLook.Connected, "Relié à ${vConnected.joinToString(" et ")}", null)
		else                       -> PcLook(LinkLook.Absent, if (inState.phones.size == 1) "${inState.phones.single().label} est absent" else "Téléphones absents", "La liaison reprendra dès que le téléphone sera sur le Wi-Fi de la maison.")
	}
}

/*
	Appli PC : liaison avec les téléphones, réglages, et surveillance du
	réseau (lecture rapide des adresses toutes les 5 s ; relecture complète
	des réseaux à chaque changement, et au moins toutes les minutes).
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

	/*
		Liaison avec les téléphones.
	*/
	val link = PcLink(SealedFile(File(fDir, "appareils.bin"), fBox), pcName(), appVersion() ?: "dev", fNetworks)

	/*
		Réglages en cours.
	*/
	val settings: StateFlow<PontSettings> = fSettings

	/*
		Démarre la surveillance du réseau.
	*/
	fun start()
	{
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
	fun updateSettings(inBlock: (PontSettings) -> PontSettings)
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
