package fr.maubin.pontplus.desktop

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
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
import fr.maubin.pontplus.ui.components.LocalAnimationsEnabled
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont
import fr.maubin.pontplus.ui.theme.PontLogo
import fr.maubin.pontplus.ui.theme.PontPlusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
	Settings("Réglages", LineIcons.Settings)												// démarrage, réseaux, à propos
}

/*
	Point d'entrée de l'appli PC. Une seule instance (une seconde la ramène au
	premier plan). L'appli vit dans la zone de notification : son icône
	montre l'état de la liaison (forme et info-bulle) ; fermer la fenêtre la
	cache seulement, « Quitter » la ferme vraiment. Lancée avec Windows
	(--demarrage), elle reste cachée. Le démarrage avec Windows est inscrit
	au premier lancement de l'appli installée, sauf s'il a été désactivé.
*/
fun main(inArgs: Array<String>)
{
	val vStartup  = inArgs.contains(kStartupArg)												// lancé avec Windows : fenêtre cachée
	val vInstance = SingleInstance()															// une seule appli à la fois
	if (!vInstance.claim(inNotify = !vStartup)) exitProcess(0)
	val vApp      = DesktopApp().also { it.start() }											// appli
	application {
		val vState    by vApp.link.state.collectAsState()											// état de la liaison
		val vSettings by vApp.settings.collectAsState()											// réglages
		val vLook     = pcLook(vState)																// état en clair
		val vTray     = rememberTrayState()														// icône de la zone de notification
		val vIcon     = remember(vLook.look) { BitmapPainter(logoImage(vLook.look, 64)) }		// icône selon l'état
		val vLogo     = remember { BitmapPainter(logoImage(null, 64)) }							// logo de la fenêtre
		var vVisible  by remember { mutableStateOf(!vStartup) }									// fenêtre affichée
		var vPage     by remember { mutableStateOf(PcPage.Devices) }							// page affichée
		var vTold     by remember { mutableStateOf(false) }										// notification « toujours là » déjà montrée
		val vShow     by vInstance.requests.collectAsState()										// demandes d'une seconde instance
		LaunchedEffect(vShow) { if (vShow > 0) vVisible = true }
		LaunchedEffect(Unit) {
			if (vSettings.launchAtStartup && WindowsStartup.exe != null) withContext(Dispatchers.IO) { if (!WindowsStartup.isOn()) WindowsStartup.set(true) }
		}
		Tray(
			icon     = vIcon,
			state    = vTray,
			tooltip  = "PontPlus — ${vLook.title}",
			onAction = { vVisible = true },
			menu     = {
				Item("Ouvrir PontPlus", onClick = { vVisible = true })
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
					vTray.sendNotification(Notification("PontPlus reste ouvert", "Il tient la liaison avec le téléphone, près de l'horloge. « Quitter » dans le menu de l'icône pour le fermer.", Notification.Type.Info))
				}
			},
			state          = vWindow,
			title          = "PontPlus",
			icon           = vLogo
		) {
			window.minimumSize = Dimension(640, 560)
			val vDark = isSystemInDarkTheme()															// thème du système
			PontPlusTheme(inDark = vDark) {
				val vColors = Pont.colors																// palette
				LaunchedEffect(vDark) { TitleBar.apply(window, vColors.ground, vColors.text, vDark) }
				LaunchedEffect(vVisible, vShow) {
					if (!vVisible) return@LaunchedEffect
					if (window.isMinimized) window.isMinimized = false
					withTimeoutOrNull(2_000) { while (!window.isShowing) delay(16) }
					if (window.isShowing) WindowFront.bring(window)
				}
				val vAnimations = remember { SystemPrefs.animationsEnabled() }						// animations de Windows
				CompositionLocalProvider(LocalAnimationsEnabled provides vAnimations) {
					PcWindow(vApp, vPage) { vPage = it }
				}
			}
		}
	}
	exitProcess(0)
}

/*
	Contenu de la fenêtre : colonne de navigation à gauche, page à droite.
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
	val vColors = Pont.colors																	// palette
	Row(Modifier.fillMaxSize().background(vColors.ground)) {
		Column(Modifier.width(220.dp).fillMaxHeight().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				PontLogo(28.dp)
				Spacer(Modifier.width(10.dp))
				Text("PontPlus", style = Pont.type.title)
			}
			Spacer(Modifier.height(22.dp))
			for (vPage in PcPage.entries) NavEntry(vPage, vPage == inPage) { inOnPage(vPage) }
		}
		Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp)) {
			Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
				when (inPage)
				{
					PcPage.Devices  -> DevicesPanel(inApp)
					PcPage.Settings -> SettingsPanel(inApp)
				}
			}
		}
	}
}

/*
	Entrée de la colonne de navigation.
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
	val vColors = Pont.colors																	// palette
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(12.dp))
			.background(if (inSelected) vColors.surfaceHigh else vColors.ground)
			.clickable(role = Role.Tab, onClick = inOnClick)
			.padding(horizontal = 12.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(inPage.icon, contentDescription = null, tint = if (inSelected) vColors.accent else vColors.textMuted, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(12.dp))
		Text(inPage.label, style = Pont.type.label.copy(color = if (inSelected) vColors.text else vColors.textMuted))
	}
}
