package fr.maubin.pontplus.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.ui.components.FilterPill
import fr.maubin.pontplus.ui.screens.ErrorLine
import fr.maubin.pontplus.ui.screens.OnOffSetting
import fr.maubin.pontplus.ui.screens.SettingsSection
import fr.maubin.pontplus.ui.theme.PontColors
import fr.maubin.pontplus.ui.theme.pontType

/*
	Page Réglages du PC : démarrage avec Windows, réseaux de la maison, à
	propos.
	inApp : appli PC
*/
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(inApp: DesktopApp)
{
	val vType     = pontType()																	// styles de texte
	val vState    by inApp.link.state.collectAsState()											// état de la liaison
	val vSettings by inApp.settings.collectAsState()											// réglages
	var vStartErr by remember { mutableStateOf<String?>(null) }								// échec du réglage du démarrage
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		SettingsSection("Démarrage") {
			OnOffSetting("Lancer PontPlus avec Windows", "PontPlus démarre discrètement dans la zone de notification.", vSettings.launchAtStartup) { vOn ->
				inApp.updateSettings { it.copy(launchAtStartup = vOn) }
				vStartErr = if (WindowsStartup.exe != null) WindowsStartup.set(vOn) else null
			}
			ErrorLine(vStartErr?.let { "Réglage impossible : $it" })
		}
		SettingsSection("Réseau de la maison") {
			BasicText("PontPlus n'écoute que sur ces réseaux. Ils sont ajoutés à l'appairage ; retirez ceux qui ne sont pas chez vous.", style = vType.bodySmall)
			if (vState.home.isEmpty() && vState.networks.isEmpty()) BasicText("Aucun réseau reconnu.", style = vType.bodySmall.copy(color = PontColors.textFaint))
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				for (vNetwork in vState.home)
				{
					FilterPill("${vNetwork.label}  ✕", true) { inApp.background { setHome(vNetwork, false) } }
				}
				for (vNetwork in vState.networks.filter { it !in vState.home })
				{
					FilterPill("+ ${vNetwork.label}", false) { inApp.background { setHome(vNetwork, true) } }
				}
			}
		}
		SettingsSection("À propos") {
			BasicText("PontPlus ${appVersion() ?: "(développement)"}", style = vType.title)
			BasicText("Tout passe directement par le réseau de la maison, chiffré avec la clé de chaque paire. Aucun compte, aucun serveur, aucune télémétrie.", style = vType.bodySmall)
		}
	}
}
