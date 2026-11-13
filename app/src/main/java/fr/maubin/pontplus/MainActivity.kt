package fr.maubin.pontplus

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import fr.maubin.pontplus.peers.PcStatus
import fr.maubin.pontplus.peers.PhoneLinkState
import fr.maubin.pontplus.ui.components.LinkLook
import fr.maubin.pontplus.ui.components.LocalAnimationsEnabled
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont
import fr.maubin.pontplus.ui.theme.PontPlusTheme

/*
	Onglet de l'appli.
	label : intitulé
	icon  : icône
*/
enum class PhoneTab(
	val label : String,
	val icon  : ImageVector
)
{
	Home("Accueil", LineIcons.Home),													// état de la liaison, derniers échanges
	Devices("Appareils", LineIcons.Devices),											// PC appairés, appairage
	Settings("Réglages", LineIcons.Settings)											// réseau de la maison, autorisations
}

/*
	État de la liaison du téléphone, en clair : toujours une icône et un
	texte.
	look   : état
	title  : titre
	detail : précision ; null sans
*/
data class PhoneLook(
	val look   : LinkLook,
	val title  : String,
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
		inState.pcs.isEmpty()   -> PhoneLook(LinkLook.Off, "Aucun PC appairé", "Appairez un PC depuis l'onglet Appareils.")
		!inNetwork.permission   -> PhoneLook(LinkLook.AwayFromHome, "Wi-Fi non reconnu", "Autorisez la localisation : elle sert seulement à lire le nom du Wi-Fi.")
		!inOnHome               -> PhoneLook(LinkLook.AwayFromHome, "Hors du réseau de la maison", "PontPlus se met en veille et reprendra à votre retour.")
		vConnected.isNotEmpty() -> PhoneLook(LinkLook.Connected, "Relié à ${vConnected.joinToString(" et ")}", null)
		vRefused != null        -> PhoneLook(LinkLook.Refused, "${vRefused.label} refuse ce téléphone", inState.refusals[vRefused.id])
		!inState.running        -> PhoneLook(LinkLook.Off, "Liaison arrêtée", "Elle reprend à l'ouverture de PontPlus. Autorisez PontPlus en arrière-plan pour qu'elle reprenne seule.")
		inState.pcs.any { inState.statusOf(it.id) == PcStatus.Searching } -> PhoneLook(LinkLook.Searching, "Recherche du PC…", null)
		else                    -> PhoneLook(LinkLook.Absent, if (inState.pcs.size == 1) "${inState.pcs.single().label} est absent" else "PC absents", "La liaison reprendra dès que le PC sera allumé, sur le même Wi-Fi.")
	}
}

/*
	Activité principale : trois onglets (Accueil, Appareils, Réglages). À
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
			PontPlusTheme {
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
	Appli du téléphone : onglet affiché et barre de navigation. Le réseau et
	les autorisations sont relus à chaque retour à l'écran (l'utilisateur a
	pu les changer dans les réglages d'Android).
*/
@Composable
fun PhoneApp()
{
	val vContext = LocalContext.current															// contexte
	val vState   by vContext.pontLink.state.collectAsState()										// état de la liaison
	var vTab     by rememberSaveable { mutableStateOf(PhoneTab.Home) }								// onglet affiché
	var vResumes by remember { mutableIntStateOf(0) }												// retours à l'écran
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vResumes++ }
	val vNetwork = remember(vResumes, vState) { currentPhoneNetwork(vContext) }						// réseau du téléphone
	val vOnHome  = remember(vNetwork, vState.home) { vNetwork.wifi?.let { it in vState.home } == true }	// à la maison
	val vColors  = Pont.colors																		// palette
	Column(Modifier.fillMaxSize().background(vColors.ground)) {
		Box(Modifier.weight(1f).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
			when (vTab)
			{
				PhoneTab.Home     -> HomeScreen(vState, phoneLook(vState, vNetwork, vOnHome), vResumes)
				PhoneTab.Devices  -> DevicesScreen(vState)
				PhoneTab.Settings -> SettingsScreen(vState, vNetwork, vResumes)
			}
		}
		NavigationBar(containerColor = vColors.surface) {
			for (vEntry in PhoneTab.entries)
			{
				NavigationBarItem(
					selected = vEntry == vTab,
					onClick  = { vTab = vEntry },
					icon     = { Icon(vEntry.icon, contentDescription = null) },
					label    = { Text(vEntry.label) },
					colors   = NavigationBarItemDefaults.colors(
						selectedIconColor   = vColors.accent,
						selectedTextColor   = vColors.text,
						indicatorColor      = vColors.accentSoft,
						unselectedIconColor = vColors.textMuted,
						unselectedTextColor = vColors.textMuted
					)
				)
			}
		}
	}
}
