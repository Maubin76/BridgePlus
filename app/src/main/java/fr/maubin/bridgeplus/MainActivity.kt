package fr.maubin.bridgeplus

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import fr.maubin.bridgeplus.peers.PcStatus
import fr.maubin.bridgeplus.peers.PhoneLinkState
import fr.maubin.bridgeplus.ui.components.LinkLook
import fr.maubin.bridgeplus.ui.components.LocalButtonScale
import fr.maubin.bridgeplus.ui.components.LocalAnimationsEnabled
import fr.maubin.bridgeplus.ui.theme.BridgePlusTheme

/*
	Écran de l'appli, comme dans KeePlus : l'accueil, et deux écrans de
	travail ouverts par les icônes de son en-tête.
*/
enum class PhoneScreen
{
	Home,																					// état de la liaison, derniers échanges
	Devices,																				// PC appairés, appairage
	Settings																				// réseau de la maison, autorisations
}

/*
	État de la liaison du téléphone, en clair : toujours une icône et un
	texte.
	look   : état
	muted  : début du titre, en gris
	strong : fin du titre, en blanc
	detail : précision ; null sans
*/
data class PhoneLook(
	val look   : LinkLook,
	val muted  : String,
	val strong : String,
	val detail : String?
)

/*
	État de la liaison du téléphone, en clair.
	inState   : état de la liaison
	inNetwork : réseau du téléphone
	inOnHome  : téléphone sur le Wi-Fi de la maison
*/
fun phoneLook(
	inState   : PhoneLinkState,
	inNetwork : PhoneNetwork,
	inOnHome  : Boolean
): PhoneLook
{
	val vConnected = inState.connected.map { it.label }										// PC reliés
	val vRefused   = inState.pcs.firstOrNull { inState.statusOf(it.id) == PcStatus.Refused }	// PC qui refuse
	return when
	{
		inState.pcs.isEmpty()   -> PhoneLook(LinkLook.Off, "Votre téléphone et votre PC, ", "reliés.", "Presse-papiers, fichiers et notifications passent par le Wi-Fi de la maison. Appairez un PC pour commencer.")
		!inNetwork.permission   -> PhoneLook(LinkLook.AwayFromHome, "Wi-Fi ", "non reconnu.", "Autorisez la localisation : elle sert seulement à lire le nom du Wi-Fi.")
		!inOnHome               -> PhoneLook(LinkLook.AwayFromHome, "Hors du réseau ", "de la maison.", "BridgePlus se met en veille et reprendra à votre retour.")
		vConnected.isNotEmpty() -> PhoneLook(LinkLook.Connected, "Relié à ", "${vConnected.joinToString(" et ")}.", null)
		vRefused != null        -> PhoneLook(LinkLook.Refused, "${vRefused.label} ", "refuse ce téléphone.", inState.refusals[vRefused.id])
		!inState.running        -> PhoneLook(LinkLook.Off, "Liaison ", "arrêtée.", "Elle reprend à l'ouverture de BridgePlus. Autorisez BridgePlus en arrière-plan pour qu'elle reprenne seule.")
		inState.pcs.any { inState.statusOf(it.id) == PcStatus.Searching } -> PhoneLook(LinkLook.Searching, "Recherche ", "du PC…", null)
		inState.pcs.size == 1   -> PhoneLook(LinkLook.Absent, "${inState.pcs.single().label} est ", "absent.", "La liaison reprendra dès que le PC sera allumé, sur le même Wi-Fi.")
		else                    -> PhoneLook(LinkLook.Absent, "PC ", "absents.", "La liaison reprendra dès qu'un PC sera allumé, sur le même Wi-Fi.")
	}
}

/*
	Activité principale : accueil, appareils, réglages. À
	chaque retour à l'écran, l'écoute des Wi-Fi est réinscrite et la liaison
	relancée si le téléphone est à la maison (démarrage permis : l'appli est
	au premier plan).
*/
class MainActivity : ComponentActivity()
{
	@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")								// convention « in » des paramètres
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		enableEdgeToEdge()
		setContent {
			val vAnimations = remember { Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }	// animations du système
			BridgePlusTheme {
				CompositionLocalProvider(LocalAnimationsEnabled provides vAnimations) {
					PhoneApp()
				}
			}
		}
	}

	override fun onResume()
	{
		super.onResume()
		NetworkWatch.register(this)
		LinkService.startIfHome(this)
	}
}

/*
	Appli du téléphone : écran affiché, retour du système vers l'accueil. Le
	réseau et les autorisations sont relus à chaque retour à l'écran
	(l'utilisateur a pu les changer dans les réglages d'Android). Les écrans
	de travail ont des boutons réduits de 20 %, comme dans KeePlus.
*/
@Composable
fun PhoneApp()
{
	val vContext = LocalContext.current															// contexte
	val vState   by vContext.bridgeLink.state.collectAsState()										// état de la liaison
	var vScreen  by rememberSaveable { mutableStateOf(PhoneScreen.Home) }							// écran affiché
	var vResumes by remember { mutableIntStateOf(0) }												// retours à l'écran
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vResumes++ }
	BackHandler(enabled = vScreen != PhoneScreen.Home) { vScreen = PhoneScreen.Home }
	val vNetwork = remember(vResumes, vState) { currentPhoneNetwork(vContext) }						// réseau du téléphone
	val vOnHome  = remember(vNetwork, vState.home) { vNetwork.wifi?.let { it in vState.home } == true }	// à la maison
	val vHome    = { vScreen = PhoneScreen.Home }													// retour à l'accueil
	when (vScreen)
	{
		PhoneScreen.Home     -> HomeScreen(vState, phoneLook(vState, vNetwork, vOnHome), vResumes) { vScreen = it }
		PhoneScreen.Devices  -> CompositionLocalProvider(LocalButtonScale provides 0.8f) { DevicesScreen(vState, vHome) }
		PhoneScreen.Settings -> CompositionLocalProvider(LocalButtonScale provides 0.8f) { SettingsScreen(vState, vNetwork, vResumes, vHome) }
	}
}
