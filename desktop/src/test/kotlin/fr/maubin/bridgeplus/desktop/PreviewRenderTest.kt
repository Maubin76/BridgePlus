package fr.maubin.bridgeplus.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import fr.maubin.bridgeplus.link.LocalAddress
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.newLinkKey
import fr.maubin.bridgeplus.store.KeyBox
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.maubin.bridgeplus.ui.components.IconAction
import fr.maubin.bridgeplus.ui.components.LinkHero
import fr.maubin.bridgeplus.ui.components.LinkLook
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.KeyScreen
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.BridgePlusTheme
import fr.maubin.bridgeplus.ui.theme.bridgeType
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/*
	Rendu hors écran de la fenêtre du PC, de la carte de l'historique et de
	l'accueil du téléphone, dans desktop/build/captures (contrôle visuel sans
	lancer l'appli), ou dans doc/images pour le README
	(-Pbridgeplus.captures).
*/
class PreviewRenderTest
{
	@get:Rule val fTemp = TemporaryFolder()											// données de l'appli rendue

	/*
		Rend les pages Appareils et Réglages.
	*/
	@Test
	fun renderWindow()
	{
		val vNetworks = { listOf(LocalNetwork(NetworkId.wifi("Maison")!!, listOf(LocalAddress("127.0.0.1", 8)))) }	// réseau simulé
		val vApp      = DesktopApp(fTemp.root, KeyBox(newLinkKey()), vNetworks)				// appli sans téléphone
		val vOut      = captureDir()								// dossier des rendus
		for (vPage in PcPage.entries)
		{
			ImageComposeScene(880, 720, Density(1f)) {
				BridgePlusTheme { PcWindow(vApp, vPage) {} }
			}.use { vScene ->
				vScene.render()
				val vImage = vScene.render(1_000_000_000L)												// image après les premières animations
				File(vOut, "fenetre-${vPage.name.lowercase()}.png").writeBytes(vImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
			}
		}
		vApp.stop()
	}

	/*
		Rend l'accueil du téléphone (gabarit des écrans clés, composants
		partagés), au format d'un téléphone : liaison établie, puis sans PC.
	*/
	@Test
	fun renderPhoneHome()
	{
		val vOut = captureDir()										// dossier des rendus
		for ((vName, vLook) in listOf("relie" to Triple(LinkLook.Connected, "Relié à ", "Bureau."), "vide" to Triple(LinkLook.Off, "Votre téléphone et votre PC, ", "reliés.")))
		{
			ImageComposeScene(412, 900, Density(1f)) {
				BridgePlusTheme {
					KeyScreen(inActions = {
						IconAction(LineIcons.Devices, "Appareils", {}, inTint = BridgeColors.text)
						IconAction(LineIcons.Settings, "Réglages", {}, inTint = BridgeColors.text)
					}) {
						LinkHero(vLook.first, vLook.second, vLook.third, if (vName == "vide") "Presse-papiers, fichiers et notifications passent par le Wi-Fi de la maison. Appairez un PC pour commencer." else null)
						if (vName == "vide") SilverButton("Appairer un PC", {})
						else SettingsSection("Derniers échanges") { BasicText("Rien d'échangé pour l'instant.", style = bridgeType().body) }
					}
				}
			}.use { vScene ->
				vScene.render()
				File(vOut, "telephone-accueil-$vName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
			}
		}
	}

	/*
		Rend la carte de l'historique, remplie d'exemples (un épinglé, des
		envois et des réceptions).
	*/
	@Test
	fun renderHistoryCard()
	{
		val vNetworks = { listOf(LocalNetwork(NetworkId.wifi("Maison")!!, listOf(LocalAddress("127.0.0.1", 8)))) }	// réseau simulé
		val vApp      = DesktopApp(fTemp.root, KeyBox(newLinkKey()), vNetworks)				// appli
		vApp.history.add("https://www.sncf-connect.com/billet/7Q4K2M", false, listOf("Pixel"))
		vApp.history.add("Réunion déplacée à 14 h 30, salle Garonne", true, listOf("Pixel"))
		vApp.history.add("12 rue des Étés, 69007 Lyon", false, listOf("Pixel")).also { vApp.history.pin(it.id, true) }
		vApp.history.add("Code du portail : voir le carnet", true, listOf("Pixel"))
		val vOut      = captureDir()								// dossier des rendus
		ImageComposeScene(620, 520, Density(1f)) {
			BridgePlusTheme {
				Box(Modifier.fillMaxSize().background(Color(0xFF3A4A5A))) {
					Box(Modifier.align(Alignment.TopCenter)) { NotchFrame { HistoryCardContent(vApp, {}, {}) } }
				}
			}
		}.use { vScene ->
			vScene.render()
			File(vOut, "carte-historique.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		}
		vApp.stop()
	}

	/*
		Dossier des rendus : desktop/build/captures, ou doc/images (captures du
		README) avec -Pbridgeplus.captures.
	*/
	private fun captureDir(): File =
		(if (System.getProperty("bridgeplus.captures") == "true") File("../doc/images") else File("build/captures")).apply { mkdirs() }
}
