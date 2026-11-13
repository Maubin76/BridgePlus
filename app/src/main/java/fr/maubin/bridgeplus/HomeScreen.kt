package fr.maubin.bridgeplus

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.maubin.bridgeplus.peers.PhoneLinkState
import fr.maubin.bridgeplus.text.relativeTime
import fr.maubin.bridgeplus.ui.components.ClipRow
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.IconAction
import fr.maubin.bridgeplus.ui.components.LinkHero
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.KeyScreen
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType
import kotlinx.coroutines.delay

/*
	Accueil, écran clé à la manière de KeePlus : décor complet (brume,
	grille, grand pont flou), marque en haut à gauche, Historique, Appareils
	et Réglages en haut à droite, et en bas l'état de la liaison en très
	grand. Suivent l'action rapide « Envoyer le presse-papiers », les autorisations qui
	manquent, puis les cinq derniers échanges de l'historique (chiffré).
	Sans PC appairé, un seul bouton : appairer.
	inState    : état de la liaison
	inLook     : état en clair
	inResumes  : retours à l'écran (relecture des autorisations)
	inOnScreen : ouverture d'un écran de travail
*/
@Composable
fun HomeScreen(
	inState    : PhoneLinkState,
	inLook     : PhoneLook,
	inResumes  : Int,
	inOnScreen : (PhoneScreen) -> Unit
)
{
	val vType    = bridgeType()																	// styles de texte
	val vContext = LocalContext.current																// contexte
	val vClips   by vContext.bridgeApp.history.items.collectAsState()								// historique
	var vMessage by remember { mutableStateOf<String?>(null) }										// résultat de l'envoi
	var vNow     by remember { mutableLongStateOf(System.currentTimeMillis()) }						// horloge (« il y a… »)
	LaunchedEffect(Unit) { while (true) { vNow = System.currentTimeMillis(); delay(30_000) } }
	KeyScreen(
		inActions = {
			IconAction(LineIcons.History, "Historique", { inOnScreen(PhoneScreen.History) }, inTint = BridgeColors.text)
			IconAction(LineIcons.Devices, "Appareils", { inOnScreen(PhoneScreen.Devices) }, inTint = BridgeColors.text)
			IconAction(LineIcons.Settings, "Réglages", { inOnScreen(PhoneScreen.Settings) }, inTint = BridgeColors.text)
		}
	) {
		LinkHero(inLook.look, inLook.muted, inLook.strong, inLook.detail)
		if (inState.pcs.isEmpty())
		{
			SilverButton("Appairer un PC", { inOnScreen(PhoneScreen.Devices) })
			return@KeyScreen
		}
		GhostButton("Envoyer le presse-papiers", {
			readClipboard(vContext).let { vClip -> vContext.bridgeApp.sendClip(vClip.text, vClip.sensitive, true) { vMessage = it } }
		})
		vMessage?.let { BasicText(it, style = vType.bodySmall) }
		MissingPermissions(inResumes)
		inState.forgotten?.let { BasicText("$it a oublié ce téléphone : la paire est effacée des deux côtés.", style = vType.bodySmall) }
		SettingsSection("Derniers échanges") {
			if (vClips.isEmpty())
			{
				BasicText("Rien d'échangé pour l'instant.", style = vType.body)
				BasicText("Les textes copiés passent d'eux-mêmes du PC au téléphone ; dans l'autre sens, ouvrez BridgePlus, ou utilisez la tuile ou le menu de partage « Envoyer au PC ».", style = vType.bodySmall)
			}
			for (vClip in vClips.take(5)) ClipRow(vClip.outgoing, vClip.preview, vClip.peers.joinToString(", "), relativeTime(vClip.at, vNow))
			if (vClips.size > 5) TextLink("Tout voir (${vClips.size})", { inOnScreen(PhoneScreen.History) })
		}
	}
}
