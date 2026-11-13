package fr.maubin.pontplus

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import fr.maubin.pontplus.peers.PhoneLinkState
import fr.maubin.pontplus.ui.components.IconAction
import fr.maubin.pontplus.ui.components.LinkHero
import fr.maubin.pontplus.ui.components.SilverButton
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.screens.KeyScreen
import fr.maubin.pontplus.ui.screens.SettingsSection
import fr.maubin.pontplus.ui.theme.PontColors
import fr.maubin.pontplus.ui.theme.pontType

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
	val vType = pontType()																		// styles de texte
	KeyScreen(
		inActions = {
			IconAction(LineIcons.Devices, "Appareils", { inOnScreen(PhoneScreen.Devices) }, inTint = PontColors.text)
			IconAction(LineIcons.Settings, "Réglages", { inOnScreen(PhoneScreen.Settings) }, inTint = PontColors.text)
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
