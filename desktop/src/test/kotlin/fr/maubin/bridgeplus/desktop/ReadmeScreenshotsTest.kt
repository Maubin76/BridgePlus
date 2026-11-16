package fr.maubin.bridgeplus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import fr.maubin.bridgeplus.history.ClipHistory
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.LocalAddress
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.newLinkKey
import fr.maubin.bridgeplus.notice.kReasonBank
import fr.maubin.bridgeplus.notice.kReasonPasswords
import fr.maubin.bridgeplus.peers.PairedPhone
import fr.maubin.bridgeplus.peers.PcLinkState
import fr.maubin.bridgeplus.store.KeyBox
import fr.maubin.bridgeplus.transfer.Transfer
import fr.maubin.bridgeplus.transfer.TransferState
import fr.maubin.bridgeplus.ui.components.ClipRow
import fr.maubin.bridgeplus.ui.components.FilterPill
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.HistoryRow
import fr.maubin.bridgeplus.ui.components.IconAction
import fr.maubin.bridgeplus.ui.components.LinkHero
import fr.maubin.bridgeplus.ui.components.LinkLook
import fr.maubin.bridgeplus.ui.components.LocalAnimationsEnabled
import fr.maubin.bridgeplus.ui.components.OneLine
import fr.maubin.bridgeplus.ui.components.SearchField
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.components.TransferRow
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.KeyScreen
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.screens.WorkScreen
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.BridgePlusTheme
import fr.maubin.bridgeplus.ui.theme.bridgeType
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private const val kMinute     = 60_000L															// une minute (ms)
private const val kPcDensity  = 2f																// densité des rendus du PC (écran à 200 %)
private const val kTelDensity = 2.625f															// densité des rendus du téléphone (1080 px de large)
private const val kTelWidth   = 1080															// largeur du téléphone (px)
private const val kTelHeight  = 2340															// hauteur du téléphone (px)

/*
	Rendus bruts des visuels du README, dans desktop/build/captures/readme :
	la fenêtre du PC et la carte de l'historique, remplies de données
	d'exemple (un téléphone relié, sa batterie, des transferts en cours), et
	les écrans du téléphone reconstitués avec les composants partagés.
	ci/vitrines.py les pose ensuite dans des cadres, sur un fond gris, dans
	doc/images. Aucun réseau : tout est rendu hors écran.
*/
class ReadmeScreenshotsTest
{
	@get:Rule val fTemp = TemporaryFolder()											// données de l'appli rendue

	private val fNow     = System.currentTimeMillis()										// maintenant (ms)
	private var fClock   = fNow																// horloge de l'historique d'exemple
	private val fOut     = File("build/captures/readme").apply { mkdirs() }				// dossier des rendus
	private lateinit var fApp     : DesktopApp												// appli PC, jamais démarrée
	private lateinit var fHistory : ClipHistory												// historique d'exemple
	private lateinit var fShown   : PcShown													// ce que montrent les pages du PC

	/*
		Prépare l'appli et les données d'exemple : deux téléphones, dont un
		relié, sa batterie, un historique varié et des transferts.
	*/
	@Before
	fun setUp()
	{
		val vNetworks = { listOf(LocalNetwork(NetworkId.wifi("Maison")!!, listOf(LocalAddress("127.0.0.1", 8)))) }	// réseau simulé
		fApp     = DesktopApp(fTemp.newFolder("appli"), KeyBox(newLinkKey()), vNetworks)
		fApp.updateSettings { it.copy(firewallExplained = true, onboardingDone = true, receiveFolder = "D:\\Photos\\Du téléphone") }
		fHistory = ClipHistory(fTemp.newFolder("historique"), KeyBox(newLinkKey()), { fClock })
		addText(26 * 60, "12 rue des Étés, 69007 Lyon", false).also { fHistory.pin(it.id, true) }
		fClock   = fNow - 3 * 60 * kMinute
		fHistory.addFile("Facture électricité novembre.pdf", 412_000, false, listOf("Pixel 9"), "C:\\exemple.pdf")
		addText(52, "Réunion déplacée à 14 h 30, salle Garonne", true)
		addText(18, "https://www.sncf-connect.com/billet/7Q4K2M", true)
		addText(4, "Code Wi-Fi des invités : sur la carte, à côté du frigo", false)
		addText(0, "Merci, je regarde ça ce soir !", true)
		val vPhones = listOf(
			PairedPhone("pixel", "Pixel 9", key = "", pairedAt = fNow - 40 * 24 * 60 * kMinute, lastSeen = fNow),
			PairedPhone("galaxy", "Galaxy Tab S9", key = "", pairedAt = fNow - 90 * 24 * 60 * kMinute, lastSeen = fNow - 2 * 24 * 60 * kMinute)
		)																							// téléphones appairés
		val vTransfers = listOf(
			Transfer("t1", "IMG_20261114_183205.jpg", 4_400_000, 2_730_000, false, "pixel", "Pixel 9", TransferState.Running, at = fNow),
			Transfer("t2", "Vacances en Ardèche.mp4", 812_000_000, 0, false, "pixel", "Pixel 9", TransferState.Waiting, at = fNow),
			Transfer("t3", "Devis cuisine.pdf", 1_250_000, 1_250_000, true, "pixel", "Pixel 9", TransferState.Done, at = fNow - 12 * kMinute, location = "C:\\Devis cuisine.pdf"),
			Transfer("t4", "Facture électricité novembre.pdf", 412_000, 412_000, false, "pixel", "Pixel 9", TransferState.Done, at = fNow - 3 * 60 * kMinute, location = "D:\\Photos\\Du téléphone\\Facture.pdf")
		)																							// transferts
		fShown = PcShown(
			link      = PcLinkState(running = true, onHome = true, phones = vPhones, connected = setOf("pixel")),
			battery   = mapOf("pixel" to LinkMessage.Battery(78, false)),
			transfers = vTransfers,
			history   = fHistory
		)
	}

	/*
		Arrête l'appli.
	*/
	@After
	fun tearDown() = fApp.stop()

	/*
		Fenêtre du PC : pages Appareils (relié, batterie), Transferts (en
		cours et récents) et Réglages.
	*/
	@Test
	fun renderPcWindow()
	{
		for (vPage in PcPage.entries)
		{
			render("pc-${vPage.name.lowercase()}", 2000, 1640, kPcDensity) { PcWindow(fApp, vPage, fShown) {} }
		}
	}

	/*
		Carte de l'historique seule, sur fond transparent (le script la pose
		en haut d'une fenêtre).
	*/
	@Test
	fun renderHistoryCard()
	{
		render("pc-carte-historique", 1500, 1100, kPcDensity) {
			Box(Modifier.fillMaxSize()) {
				Box(Modifier.align(Alignment.TopCenter)) { NotchFrame { HistoryCardContent(fApp, {}, {}, fShown) } }
			}
		}
	}

	/*
		Accueil du téléphone, relié : envois, fichier en cours, derniers
		échanges.
	*/
	@Test
	fun renderPhoneHome()
	{
		renderPhone("telephone-accueil") {
			KeyScreen(inActions = {
				IconAction(LineIcons.History, "Historique", {}, inTint = BridgeColors.text)
				IconAction(LineIcons.Devices, "Appareils", {}, inTint = BridgeColors.text)
				IconAction(LineIcons.Settings, "Réglages", {}, inTint = BridgeColors.text)
			}) {
				LinkHero(LinkLook.Connected, "Relié à ", "Bureau.", null)
				GhostButton("Envoyer le presse-papiers", {})
				GhostButton("Envoyer un fichier", {})
				SettingsSection("Fichiers en cours") {
					TransferRow(true, "IMG_20261114_183205.jpg", "Vers Bureau · 2,7 Mo sur 4,4 Mo", 0.62f, false) {
						IconAction(LineIcons.Close, "Annuler", {})
					}
				}
				SettingsSection("Derniers échanges") {
					ClipRow(false, "Merci, je regarde ça ce soir !", "Bureau", "à l'instant")
					ClipRow(true, "Code Wi-Fi des invités : sur la carte, à côté du frigo", "Bureau", "il y a 4 min")
					ClipRow(false, "https://www.sncf-connect.com/billet/7Q4K2M", "Bureau", "il y a 18 min")
				}
			}
		}
	}

	/*
		Historique du téléphone : recherche, élément épinglé, fichier reçu.
	*/
	@Test
	fun renderPhoneHistory()
	{
		renderPhone("telephone-historique") {
			WorkScreen("Historique", {}) {
				SearchField(rememberTextFieldState(), inPlaceholder = "Rechercher dans l'historique")
				SettingsSection("6 éléments") {
					HistoryRow(true, "12 rue des Étés, 69007 Lyon", "Bureau", "hier", true, false, {}, {}, {})
					HistoryRow(false, "Merci, je regarde ça ce soir !", "Bureau", "à l'instant", false, false, {}, {}, {})
					HistoryRow(true, "Code Wi-Fi des invités : sur la carte, à côté du frigo", "Bureau", "il y a 4 min", false, false, {}, {}, {})
					HistoryRow(false, "https://www.sncf-connect.com/billet/7Q4K2M", "Bureau", "il y a 18 min", false, false, {}, {}, {})
					HistoryRow(false, "Réunion déplacée à 14 h 30, salle Garonne", "Bureau", "il y a 52 min", false, false, {}, {}, {})
					HistoryRow(true, "Facture électricité novembre.pdf · 412 ko", "Bureau", "il y a 3 h", false, false, {}, {}, {})
				}
				TextLink("Tout effacer", {}, inDanger = true)
			}
		}
	}

	/*
		Liste blanche des notifications envoyées au PC.
	*/
	@Test
	fun renderPhoneNotices()
	{
		val vApps = listOf(
			Triple("Gmail", true, null),
			Triple("Messages", true, null),
			Triple("Signal", true, null),
			Triple("WhatsApp", true, null),
			Triple("Doctolib", false, null),
			Triple("Leboncoin", false, null),
			Triple("KeePlus", false, kReasonPasswords),
			Triple("Ma Banque", false, kReasonBank)
		)																							// applis : nom, activée, raison d'une exclusion
		renderPhone("telephone-notifications") {
			val vType = bridgeType()																// styles de texte
			WorkScreen("Notifications sur le PC", {}) {
				BasicText("Seules les applis activées envoient leurs notifications au PC. Les codes de connexion ne partent jamais, quelle que soit l'appli.", style = vType.bodySmall)
				SearchField(rememberTextFieldState(), inPlaceholder = "Rechercher une appli")
				SettingsSection("4 appli(s) activée(s)") {
					for ((vName, vOn, vExcluded) in vApps)
					{
						Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
							AppTile(vName)
							Spacer(Modifier.width(12.dp))
							Column(Modifier.weight(1f)) {
								OneLine(vName, vType.body.copy(color = if (vExcluded != null) BridgeColors.textFaint else BridgeColors.text))
								vExcluded?.let { OneLine(it, vType.bodySmall) }
							}
							if (vExcluded == null) FilterPill(if (vOn) "Activée" else "Désactivée", vOn) {}
						}
					}
				}
			}
		}
	}

	/*
		Trois étapes de l'assistant de premier lancement (textes repris de
		l'appli Android).
	*/
	@Test
	fun renderPhoneOnboarding()
	{
		val vSteps = listOf(
			listOf("1", "Les ", "notifications.", "Pour la notification discrète « Relié à votre PC », les fichiers reçus et la sonnerie.", "Autoriser"),
			listOf("2", "Le Wi-Fi de ", "la maison.", "BridgePlus lit le nom du Wi-Fi pour ne fonctionner que chez vous ; Android range cette lecture sous « localisation », choisissez « Toujours ». Aucune position n'est lue.", "Autoriser"),
			listOf("5", "Votre téléphone et votre PC, ", "reliés.", "Sur le PC, ouvrez BridgePlus et choisissez « Appairer un téléphone » : un QR code s'affiche.", "Appairer un PC")
		)																							// étapes : numéro, titre en deux tons, texte, action
		for ((vIndex, vStep) in vSteps.withIndex())
		{
			renderPhone("telephone-assistant-${vIndex + 1}") {
				val vType = bridgeType()															// styles de texte
				KeyScreen {
					BasicText("Étape ${vStep[0]} sur 5", style = vType.bodySmall)
					BasicText(
						buildAnnotatedString {
							withStyle(SpanStyle(color = BridgeColors.textMuted)) { append(vStep[1]) }
							append(vStep[2])
						},
						style = vType.display
					)
					BasicText(vStep[3], style = vType.bodySmall)
					SilverButton(vStep[4], {})
					TextLink(if (vStep[0] == "5") "Plus tard" else "Passer", {})
				}
			}
		}
	}

	/*
		Ajoute un texte à l'historique d'exemple, daté dans le passé.
		inMinutesAgo : ancienneté (min)
		inText       : texte
		inOutgoing   : vrai pour un envoi du PC
	*/
	private fun addText(
		inMinutesAgo : Long,
		inText       : String,
		inOutgoing   : Boolean
	) = run {
		fClock = fNow - inMinutesAgo * kMinute
		fHistory.add(inText, inOutgoing, listOf("Pixel 9"))
	}

	/*
		Rendu d'un écran du téléphone (1080 × 2340 px).
		inName    : nom du fichier, sans extension
		inContent : écran
	*/
	private fun renderPhone(
		inName    : String,
		inContent : @Composable () -> Unit
	) = render(inName, kTelWidth, kTelHeight, kTelDensity, inContent)

	/*
		Rendu hors écran, animations coupées, enregistré en PNG.
		inName    : nom du fichier, sans extension
		inWidth   : largeur (px)
		inHeight  : hauteur (px)
		inDensity : densité
		inContent : contenu
	*/
	private fun render(
		inName    : String,
		inWidth   : Int,
		inHeight  : Int,
		inDensity : Float,
		inContent : @Composable () -> Unit
	)
	{
		ImageComposeScene(inWidth, inHeight, Density(inDensity)) {
			BridgePlusTheme { CompositionLocalProvider(LocalAnimationsEnabled provides false) { inContent() } }
		}.use { vScene ->
			vScene.render()
			File(fOut, "$inName.png").writeBytes(vScene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
		}
	}
}

/*
	Icône d'appli d'exemple : initiale sur une pastille.
	inName : nom de l'appli
*/
@Composable
private fun AppTile(inName: String)
{
	Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(BridgeColors.surfaceHigh), contentAlignment = Alignment.Center) {
		BasicText(inName.take(1), style = bridgeType().label)
	}
}
