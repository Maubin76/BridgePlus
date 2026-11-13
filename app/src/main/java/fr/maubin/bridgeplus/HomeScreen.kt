package fr.maubin.bridgeplus

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import fr.maubin.bridgeplus.peers.PhoneLinkState
import fr.maubin.bridgeplus.ui.components.IconAction
import fr.maubin.bridgeplus.ui.components.LinkHero
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.KeyScreen
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType

/*
	Accueil, écran clé à la manière de KeePlus : décor complet (brume,
	grille, grand pont flou), marque en haut à gauche, Appareils et Réglages
	en haut à droite, et en bas l'état de la liaison en très grand. Suivent
	les autorisations qui manquent, puis les derniers échanges (vides tant
	que le presse-papiers et les fichiers ne circulent pas : étapes
	suivantes). Sans PC appairé, un seul bouton : appairer.
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
	val vType = bridgeType()																		// styles de texte
	KeyScreen(
		inActions = {
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
		MissingPermissions(inResumes)
		inState.forgotten?.let { BasicText("$it a oublié ce téléphone : la paire est effacée des deux côtés.", style = vType.bodySmall) }
		SettingsSection("Derniers échanges") {
			BasicText("Rien d'échangé pour l'instant.", style = vType.body)
			BasicText("Les textes copiés et les fichiers envoyés entre le téléphone et le PC apparaîtront ici.", style = vType.bodySmall)
		}
	}
}
