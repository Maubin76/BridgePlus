package fr.maubin.bridgeplus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import fr.maubin.bridgeplus.ui.components.LocalAnimationsEnabled
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.WorkScreen
import fr.maubin.bridgeplus.ui.theme.BridgeBrand
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.BridgePlusTheme
import fr.maubin.bridgeplus.ui.theme.bridgeType
import fr.maubin.bridgeplus.ui.theme.logoImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Dimension
import kotlin.system.exitProcess

/*
	Page de la fenêtre principale.
*/
enum class PcPage(val label: String, val icon: ImageVector)
{
	Devices("Appareils", LineIcons.Devices),												// téléphones, appairage
	Transfers("Transferts", LineIcons.Transfers),											// envoi de fichiers, réception
	Settings("Réglages", LineIcons.Settings)												// presse-papiers, historique, démarrage, réseaux
}

/*
	Point d'entrée de l'appli PC. Une seule instance (une seconde la ramène au
	premier plan). L'appli vit dans la zone de notification : son icône
	montre l'état de la liaison (forme et info-bulle) ; fermer la fenêtre la
	cache seulement, « Quitter » la ferme vraiment. Lancée avec Windows
	(--demarrage), elle reste cachée. Le démarrage avec Windows est inscrit
	au premier lancement de l'appli installée, sauf s'il a été désactivé.
	Lancée par un bouton de notification (--lien bridgeplus:…) ou par le menu
	de l'Explorateur (--envoyer fichier), elle transmet la demande à
	l'instance déjà ouverte, ou la traite elle-même après son démarrage.
*/
fun main(inArgs: Array<String>)
{
	val vStartup  = inArgs.contains(kStartupArg)												// lancé avec Windows : fenêtre cachée
	val vCommand  = argAfter(inArgs, kLinkArg)?.let { "lien $it" } ?: argAfter(inArgs, ExplorerMenu.kSendArg)?.let { "envoyer $it" }	// demande transmise
	val vInstance = SingleInstance()															// une seule appli à la fois
	if (!vInstance.claim(inNotify = !vStartup && vCommand == null, inCommand = vCommand)) exitProcess(0)
	val vApp      = DesktopApp().also { it.start() }											// appli
	vCommand?.let(vApp::command)
	application {
		val vScope    = rememberCoroutineScope()														// boîtes de dialogue
		val vState    by vApp.link.state.collectAsState()											// état de la liaison
		val vSettings by vApp.settings.collectAsState()											// réglages
		val vLook     = pcLook(vState)																// état en clair
		val vTray     = rememberTrayState()														// icône de la zone de notification
		val vIcon     = remember(vLook.look) { BitmapPainter(trayImage(vLook.look, 64)) }		// icône selon l'état
		val vLogo     = remember { BitmapPainter(logoImage(64)) }									// logo de la fenêtre
		var vVisible  by remember { mutableStateOf(!vStartup) }									// fenêtre affichée
		var vPage     by remember { mutableStateOf(PcPage.Devices) }							// page affichée
		var vTold     by remember { mutableStateOf(false) }										// notification « toujours là » déjà montrée
		val vShow     by vInstance.requests.collectAsState()										// demandes d'une seconde instance
		LaunchedEffect(vShow) { if (vShow > 0) vVisible = true }
		LaunchedEffect(Unit) { vApp.notices.collect { vTray.sendNotification(Notification("BridgePlus", it, Notification.Type.Warning)) } }
		var vCard     by remember { mutableStateOf<CardRequest?>(null) }						// carte de l'historique affichée ; null sans
		var vSerial   by remember { mutableIntStateOf(0) }											// numéro de la dernière demande
		LaunchedEffect(Unit) { vApp.hotkeyPresses.collect { vSerial++; vCard = CardRequest(it, vSerial) } }
		LaunchedEffect(Unit) { vInstance.commands.collect { vApp.command(it) } }
		val vChoice   by vApp.pendingChoice.collectAsState()										// fichiers en attente du choix du téléphone
		LaunchedEffect(vChoice) { if (vChoice != null) vVisible = true }
		CompositionLocalProvider(LocalAnimationsEnabled provides remember { SystemPrefs.animationsEnabled() }) {
			HistoryWindow(
				inRequest  = vCard,
				inApp      = vApp,
				inOnClosed = { vRestore -> val vRequest = vCard; vCard = null; if (vRestore) vRequest?.active?.restore() },
				inIcon     = vLogo
			)
		}
		LaunchedEffect(Unit) {
			if (vSettings.launchAtStartup && WindowsStartup.exe != null) withContext(Dispatchers.IO) { if (!WindowsStartup.isOn()) WindowsStartup.set(true) }
		}
		Tray(
			icon     = vIcon,
			state    = vTray,
			tooltip  = "BridgePlus — ${vLook.title}",
			onAction = { vVisible = true },
			menu     = {
				Item("Ouvrir BridgePlus", onClick = { vVisible = true })
				Item("Historique du presse-papiers", onClick = { vSerial++; vCard = CardRequest(ActiveWindow(null, null), vSerial) })
				Item("Envoyer un fichier…", onClick = { vScope.launch { chooseFiles()?.let { vApp.sendFiles(it) } } })
				Item("Appairer un téléphone", onClick = { vPage = PcPage.Devices; vVisible = true; vApp.background { startPairing() } })
				Item("Réglages", onClick = { vPage = PcPage.Settings; vVisible = true })
				Separator()
				Item("Quitter", onClick = { vApp.stop(); vInstance.release(); exitApplication() })
			}
		)
		val vWindow = rememberWindowState(size = DpSize(880.dp, 720.dp), position = WindowPosition.Aligned(Alignment.Center))	// fenêtre
		Window(
			visible        = vVisible,
			onCloseRequest = {
				vVisible = false
				if (!vTold)
				{
					vTold = true
					vTray.sendNotification(Notification("BridgePlus reste ouvert", "Il tient la liaison avec le téléphone, près de l'horloge. « Quitter » dans le menu de l'icône pour le fermer.", Notification.Type.Info))
				}
			},
			state          = vWindow,
			title          = "BridgePlus",
			icon           = vLogo
		) {
			window.minimumSize = Dimension(640, 560)
			LaunchedEffect(Unit) { acceptDroppedFiles(window, vApp) }
			BridgePlusTheme {
				LaunchedEffect(Unit) { TitleBar.apply(window, BridgeColors.void, BridgeColors.text, true) }
				LaunchedEffect(vVisible, vShow) {
					if (!vVisible) return@LaunchedEffect
					if (window.isMinimized) window.isMinimized = false
					withTimeoutOrNull(2_000) { while (!window.isShowing) delay(16) }
					if (window.isShowing) WindowFront.bring(window)
				}
				val vAnimations = remember { SystemPrefs.animationsEnabled() }						// animations de Windows
				CompositionLocalProvider(LocalAnimationsEnabled provides vAnimations) {
					PcWindow(vApp, vPage) { vPage = it }
					vChoice?.let { vFiles -> PhoneChoiceDialog(vApp, vFiles) }
				}
			}
		}
	}
	exitProcess(0)
}

/*
	Contenu de la fenêtre, à la manière de KeePlus sur PC : colonne de
	navigation à gauche (marque, puis les pages), page de travail à droite
	(voile de brume, grille fine).
	inApp    : appli PC
	inPage   : page affichée
	inOnPage : changement de page
*/
@Composable
fun PcWindow(
	inApp    : DesktopApp,
	inPage   : PcPage,
	inOnPage : (PcPage) -> Unit
)
{
	Row(Modifier.fillMaxSize().background(BridgeColors.ground)) {
		Column(Modifier.width(220.dp).fillMaxHeight().background(BridgeColors.void).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			BridgeBrand()
			Spacer(Modifier.height(28.dp))
			for (vPage in PcPage.entries) NavEntry(vPage, vPage == inPage) { inOnPage(vPage) }
		}
		Box(Modifier.weight(1f).fillMaxHeight()) {
			WorkScreen(inPage.label, null) {
				when (inPage)
				{
					PcPage.Devices   -> DevicesPanel(inApp)
					PcPage.Transfers -> TransfersPanel(inApp)
					PcPage.Settings  -> SettingsPanel(inApp)
				}
			}
		}
	}
}

/*
	Entrée de la colonne de navigation : active, fond gris et texte blanc.
	inPage     : page
	inSelected : page affichée
	inOnClick  : choix de la page
*/
@Composable
private fun NavEntry(
	inPage     : PcPage,
	inSelected : Boolean,
	inOnClick  : () -> Unit
)
{
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.background(if (inSelected) BridgeColors.surfaceHigh else BridgeColors.void)
			.clickable(role = Role.Tab, onClick = inOnClick)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(inPage.icon, contentDescription = null, tint = if (inSelected) BridgeColors.text else BridgeColors.textMuted, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(12.dp))
		BasicText(inPage.label, style = bridgeType().label.copy(color = if (inSelected) BridgeColors.text else BridgeColors.textMuted))
	}
}
