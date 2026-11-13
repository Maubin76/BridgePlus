package fr.maubin.bridgeplus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.maubin.bridgeplus.peers.PhoneLinkState
import fr.maubin.bridgeplus.ui.components.IconAction
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.OnOffSetting
import fr.maubin.bridgeplus.ui.screens.SettingsDivider
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.screens.WorkScreen
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
	Écran Réglages, écran de travail à la manière de KeePlus : presse-papiers
	(chaque sens), réseaux de la maison (appris à l'appairage, retirables),
	autorisations, à propos. Les réglages des étapes suivantes
	(notifications, dossier de réception) viendront ici.
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
	val vType    = bridgeType()																		// styles de texte
	val vSettings by vContext.bridgeApp.settings.collectAsState()									// réglages
	WorkScreen("Réglages", inOnBack) {
		SettingsSection("Presse-papiers") {
			OnOffSetting("Du PC vers le téléphone", "Les textes copiés sur le PC sont placés dans le presse-papiers du téléphone.", vSettings.clipReceive) { vOn ->
				vContext.bridgeApp.updateSettings { it.copy(clipReceive = vOn) }
			}
			SettingsDivider()
			OnOffSetting("Du téléphone vers le PC", "Quand BridgePlus est ouvert, chaque copie part vers le PC. Ailleurs, Android l'interdit : utilisez la tuile ou le menu de partage « Envoyer au PC ». Les contenus marqués sensibles ne partent jamais.", vSettings.clipSend) { vOn ->
				vContext.bridgeApp.updateSettings { it.copy(clipSend = vOn) }
			}
		}
		SettingsSection("Fichiers reçus") {
			BasicText("Les fichiers envoyés par le PC arrivent dans Téléchargements › $kReceiveFolder ; les photos et vidéos apparaissent aussi dans la galerie.", style = vType.bodySmall)
			TextLink("Ouvrir le dossier", { openDownloads(vContext) })
		}
		SettingsSection("Réseau de la maison") {
			BasicText("BridgePlus ne fonctionne que sur ces Wi-Fi, appris lors de l'appairage. Ailleurs, il se met en veille.", style = vType.bodySmall)
			if (inState.home.isEmpty()) BasicText("Aucun réseau pour l'instant.", style = vType.bodySmall.copy(color = BridgeColors.textFaint))
			inState.home.forEachIndexed { vIndex, vNetwork ->
				if (vIndex > 0) SettingsDivider()
				Row(verticalAlignment = Alignment.CenterVertically) {
					Icon(LineIcons.Wifi, contentDescription = null, tint = BridgeColors.textMuted, modifier = Modifier.size(20.dp))
					Spacer(Modifier.width(12.dp))
					Column(Modifier.weight(1f)) {
						BasicText(vNetwork.label, style = vType.body)
						if (vNetwork == inNetwork.wifi) BasicText("Wi-Fi actuel", style = vType.bodySmall)
					}
					IconAction(LineIcons.Close, "Retirer ${vNetwork.label}", { vScope.launch(Dispatchers.IO) { vContext.bridgeLink.removeHome(vNetwork) } })
				}
			}
		}
		SettingsSection("Autorisations") {
			BasicText("Les autorisations utiles sont accordées quand rien n'est proposé ici.", style = vType.bodySmall)
			MissingPermissions(inResumes)
		}
		SettingsSection("À propos") {
			BasicText("BridgePlus ${BuildConfig.VERSION_NAME}", style = vType.title)
			BasicText("Tout passe directement par le Wi-Fi de la maison, chiffré avec la clé de chaque paire. Aucun compte, aucun serveur, aucune télémétrie.", style = vType.bodySmall)
		}
	}
}
