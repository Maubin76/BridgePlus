package fr.maubin.pontplus.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import fr.maubin.pontplus.link.LocalAddress
import fr.maubin.pontplus.link.LocalNetwork
import fr.maubin.pontplus.link.NetworkId
import fr.maubin.pontplus.link.newLinkKey
import fr.maubin.pontplus.store.KeyBox
import fr.maubin.pontplus.ui.theme.PontPlusTheme
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/*
	Rendu hors écran de la fenêtre du PC, en clair et en sombre, dans
	desktop/build/captures : contrôle visuel de la mise en page sans lancer
	l'appli.
*/
class PreviewRenderTest
{
	@get:Rule val fTemp = TemporaryFolder()											// données de l'appli rendue

	/*
		Rend les pages Appareils et Réglages dans les deux thèmes.
	*/
	@Test
	fun renderWindow()
	{
		val vNetworks = { listOf(LocalNetwork(NetworkId.wifi("Maison")!!, listOf(LocalAddress("127.0.0.1", 8)))) }	// réseau simulé
		val vApp      = DesktopApp(fTemp.root, KeyBox(newLinkKey()), vNetworks)				// appli sans téléphone
		val vOut      = File("build/captures").apply { mkdirs() }								// dossier des rendus
		for (vDark in listOf(false, true))
		{
			for (vPage in PcPage.entries)
			{
				ImageComposeScene(880, 720, Density(1f)) {
					PontPlusTheme(inDark = vDark) { PcWindow(vApp, vPage) {} }
				}.use { vScene ->
					vScene.render()
					val vImage = vScene.render(1_000_000_000L)												// image après les premières animations
					File(vOut, "fenetre-${vPage.name.lowercase()}-${if (vDark) "sombre" else "clair"}.png").writeBytes(vImage.encodeToData(EncodedImageFormat.PNG)!!.bytes)
				}
			}
		}
		vApp.stop()
	}
}
