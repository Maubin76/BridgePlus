package fr.maubin.pontplus.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.ui.components.PontCard
import fr.maubin.pontplus.ui.components.SectionTitle
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont

/*
	Page Réglages du PC : démarrage avec Windows, réseaux de la maison, à
	propos.
	inApp : appli PC
*/
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(inApp: DesktopApp)
{
	val vState    by inApp.link.state.collectAsState()											// état de la liaison
	val vSettings by inApp.settings.collectAsState()											// réglages
	var vStartErr by remember { mutableStateOf<String?>(null) }								// échec du réglage du démarrage
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		Column {
			SectionTitle("Démarrage")
			PontCard {
				Row(verticalAlignment = Alignment.CenterVertically) {
					Column(Modifier.weight(1f)) {
						Text("Lancer PontPlus avec Windows", style = Pont.type.title)
						Text("PontPlus démarre discrètement dans la zone de notification.", style = Pont.type.bodySmall)
					}
					Switch(
						checked         = vSettings.launchAtStartup,
						onCheckedChange = { vOn ->
							inApp.updateSettings { it.copy(launchAtStartup = vOn) }
							vStartErr = if (WindowsStartup.exe != null) WindowsStartup.set(vOn) else null
						}
					)
				}
				vStartErr?.let { Text("Réglage impossible : $it", style = Pont.type.bodySmall.copy(color = Pont.colors.danger)) }
			}
		}
		Column {
			SectionTitle("Réseau de la maison")
			PontCard {
				Text("PontPlus n'écoute que sur ces réseaux. Ils sont ajoutés à l'appairage ; retirez ceux qui ne sont pas chez vous.", style = Pont.type.bodySmall)
				Spacer(Modifier.height(12.dp))
				if (vState.home.isEmpty() && vState.networks.isEmpty()) Text("Aucun réseau reconnu.", style = Pont.type.body.copy(color = Pont.colors.textMuted))
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					for (vNetwork in vState.home)
					{
						FilterChip(
							selected    = true,
							onClick     = { inApp.background { setHome(vNetwork, false) } },
							label       = { Text(vNetwork.label) },
							leadingIcon = { Icon(LineIcons.Check, contentDescription = "Réseau de la maison, retirer", modifier = Modifier.size(16.dp)) }
						)
					}
					for (vNetwork in vState.networks.filter { it !in vState.home })
					{
						FilterChip(
							selected    = false,
							onClick     = { inApp.background { setHome(vNetwork, true) } },
							label       = { Text(vNetwork.label) },
							leadingIcon = { Icon(LineIcons.Plus, contentDescription = "Ajouter aux réseaux de la maison", modifier = Modifier.size(16.dp)) }
						)
					}
				}
			}
		}
		Column {
			SectionTitle("À propos")
			PontCard {
				Text("PontPlus ${appVersion() ?: "(développement)"}", style = Pont.type.title)
				Text("Tout passe directement par le réseau de la maison, chiffré avec la clé de chaque paire. Aucun compte, aucun serveur, aucune télémétrie.", style = Pont.type.bodySmall)
			}
		}
	}
}
