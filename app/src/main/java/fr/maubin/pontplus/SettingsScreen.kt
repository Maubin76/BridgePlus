package fr.maubin.pontplus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.peers.PhoneLinkState
import fr.maubin.pontplus.ui.components.PontCard
import fr.maubin.pontplus.ui.components.SectionTitle
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
	Onglet Réglages : réseaux de la maison (appris à l'appairage, retirables),
	autorisations, à propos. Les réglages des étapes suivantes (presse-papiers,
	notifications, dossier de réception) viendront ici.
	inState   : état de la liaison
	inNetwork : réseau du téléphone
	inResumes : retours à l'écran (relecture des autorisations)
*/
@Composable
fun SettingsScreen(
	inState   : PhoneLinkState,
	inNetwork : PhoneNetwork,
	inResumes : Int
)
{
	val vContext = LocalContext.current																// contexte
	val vScope   = rememberCoroutineScope()															// enregistrement
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		Text("Réglages", style = Pont.type.display)
		Column {
			SectionTitle("Réseau de la maison")
			PontCard {
				Text("PontPlus ne fonctionne que sur ces Wi-Fi, appris lors de l'appairage. Ailleurs, il se met en veille.", style = Pont.type.bodySmall)
				if (inState.home.isEmpty()) Text("Aucun réseau pour l'instant.", style = Pont.type.body.copy(color = Pont.colors.textMuted))
				for (vNetwork in inState.home)
				{
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(LineIcons.Wifi, contentDescription = null, tint = Pont.colors.textMuted, modifier = Modifier.size(20.dp))
						Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
							Text(vNetwork.label, style = Pont.type.title)
							if (vNetwork == inNetwork.wifi) Text("Wi-Fi actuel", style = Pont.type.bodySmall)
						}
						IconButton(onClick = { vScope.launch(Dispatchers.IO) { vContext.pontLink.removeHome(vNetwork) } }) {
							Icon(LineIcons.Close, contentDescription = "Retirer ${vNetwork.label}", tint = Pont.colors.textMuted)
						}
					}
				}
			}
		}
		Column {
			SectionTitle("Autorisations")
			MissingPermissions(inResumes)
			Text("Les autorisations utiles sont accordées quand rien n'est proposé ici.", style = Pont.type.bodySmall)
		}
		Column {
			SectionTitle("À propos")
			PontCard {
				Text("PontPlus ${BuildConfig.VERSION_NAME}", style = Pont.type.title)
				Text("Tout passe directement par le Wi-Fi de la maison, chiffré avec la clé de chaque paire. Aucun compte, aucun serveur, aucune télémétrie.", style = Pont.type.bodySmall)
			}
		}
	}
}
