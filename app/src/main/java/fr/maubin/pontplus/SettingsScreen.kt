package fr.maubin.pontplus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.peers.PhoneLinkState
import fr.maubin.pontplus.ui.components.IconAction
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.screens.SettingsDivider
import fr.maubin.pontplus.ui.screens.SettingsSection
import fr.maubin.pontplus.ui.screens.WorkScreen
import fr.maubin.pontplus.ui.theme.PontColors
import fr.maubin.pontplus.ui.theme.pontType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
	Écran Réglages, écran de travail à la manière de KeePlus : réseaux de la
	maison (appris à l'appairage, retirables), autorisations, à propos. Les
	réglages des étapes suivantes (presse-papiers, notifications, dossier de
	réception) viendront ici.
	inState   : état de la liaison
	inNetwork : réseau du téléphone
	inResumes : retours à l'écran (relecture des autorisations)
	inOnBack  : retour à l'accueil
*/
@Composable
fun SettingsScreen(
	inState   : PhoneLinkState,
	inNetwork : PhoneNetwork,
	inResumes : Int,
	inOnBack  : () -> Unit
)
{
	val vContext = LocalContext.current																// contexte
	val vScope   = rememberCoroutineScope()															// enregistrement
	val vType    = pontType()																		// styles de texte
	WorkScreen("Réglages", inOnBack) {
		SettingsSection("Réseau de la maison") {
			BasicText("PontPlus ne fonctionne que sur ces Wi-Fi, appris lors de l'appairage. Ailleurs, il se met en veille.", style = vType.bodySmall)
			if (inState.home.isEmpty()) BasicText("Aucun réseau pour l'instant.", style = vType.bodySmall.copy(color = PontColors.textFaint))
			inState.home.forEachIndexed { vIndex, vNetwork ->
				if (vIndex > 0) SettingsDivider()
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(LineIcons.Wifi, contentDescription = null, tint = PontColors.textMuted, modifier = Modifier.size(20.dp))
					Spacer(Modifier.width(12.dp))
					Column(Modifier.weight(1f)) {
						BasicText(vNetwork.label, style = vType.body)
						if (vNetwork == inNetwork.wifi) BasicText("Wi-Fi actuel", style = vType.bodySmall)
					}
					IconAction(LineIcons.Close, "Retirer ${vNetwork.label}", { vScope.launch(Dispatchers.IO) { vContext.pontLink.removeHome(vNetwork) } })
				}
			}
		}
		SettingsSection("Autorisations") {
			BasicText("Les autorisations utiles sont accordées quand rien n'est proposé ici.", style = vType.bodySmall)
			MissingPermissions(inResumes)
		}
		SettingsSection("À propos") {
			BasicText("PontPlus ${BuildConfig.VERSION_NAME}", style = vType.title)
			BasicText("Tout passe directement par le Wi-Fi de la maison, chiffré avec la clé de chaque paire. Aucun compte, aucun serveur, aucune télémétrie.", style = vType.bodySmall)
		}
	}
}
