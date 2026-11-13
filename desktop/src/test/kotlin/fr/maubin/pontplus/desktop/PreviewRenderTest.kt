package fr.maubin.pontplus.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import fr.maubin.pontplus.link.LocalAddress
import fr.maubin.pontplus.link.LocalNetwork
import fr.maubin.pontplus.link.NetworkId
import fr.maubin.pontplus.link.newLinkKey
import fr.maubin.pontplus.store.KeyBox
import androidx.compose.foundation.text.BasicText
import fr.maubin.pontplus.ui.components.IconAction
import fr.maubin.pontplus.ui.components.LinkHero
import fr.maubin.pontplus.ui.components.LinkLook
import fr.maubin.pontplus.ui.components.SilverButton
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.screens.KeyScreen
import fr.maubin.pontplus.ui.screens.SettingsSection
import fr.maubin.pontplus.ui.theme.PontColors
import fr.maubin.pontplus.ui.theme.PontPlusTheme
import fr.maubin.pontplus.ui.theme.pontType
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/*
	Rendu hors écran de la fenêtre du PC, dans
	desktop/build/captures : contrôle visuel de la mise en page sans lancer
	l'appli.
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
		val vOut      = File("build/captures").apply { mkdirs() }								// dossier des rendus
		for (vPage in PcPage.entries)
		{
			ImageComposeScene(880, 720, Density(1f)) {
				PontPlusTheme { PcWindow(vApp, vPage) {} }
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
		val vOut = File("build/captures").apply { mkdirs() }										// dossier des rendus
		for ((vName, vLook) in listOf("relie" to Triple(LinkLook.Connected, "Relié à ", "Bureau."), "vide" to Triple(LinkLook.Off, "Votre téléphone et votre PC, ", "reliés.")))
		{
			ImageComposeScene(412, 900, Density(1f)) {
				PontPlusTheme {
					KeyScreen(inActions = {
						IconAction(LineIcons.Devices, "Appareils", {}, inTint = PontColors.text)
						IconAction(LineIcons.Settings, "Réglages", {}, inTint = PontColors.text)
					}) {
						LinkHero(vLook.first, vLook.second, vLook.third, if (vName == "vide") "Presse-papiers, fichiers et notifications passent par le Wi-Fi de la maison. Appairez un PC pour commencer." else null)
						if (vName == "vide") SilverButton("Appairer un PC", {})
						else SettingsSection("Derniers échanges") { BasicText("Rien d'échangé pour l'instant.", style = pontType().body) }
					}
				}
			}.use { vScene ->
				vScene.render()
				File(vOut, "telephone-accueil-$vName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
			}
		}
	}
}
