package fr.maubin.bridgeplus.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import fr.maubin.bridgeplus.ui.components.ClearHistoryDialog
import fr.maubin.bridgeplus.ui.components.FilterPill
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.screens.ErrorLine
import fr.maubin.bridgeplus.ui.screens.OnOffSetting
import fr.maubin.bridgeplus.ui.screens.SettingsDivider
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
	Page Réglages du PC : presse-papiers (chaque sens), notifications du
	téléphone, historique
	(raccourci de la carte, « Tout effacer »), démarrage avec Windows,
	réseaux de la maison, à propos.
	inApp : appli PC
*/
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(inApp: DesktopApp)
{
	val vType     = bridgeType()																	// styles de texte
	val vState    by inApp.link.state.collectAsState()											// état de la liaison
	val vSettings by inApp.settings.collectAsState()											// réglages
	var vStartErr by remember { mutableStateOf<String?>(null) }								// échec du réglage du démarrage
	val vHotkeyOk by inApp.hotkeyOk.collectAsState()											// raccourci accepté par Windows
	var vClear    by remember { mutableStateOf(false) }											// confirmation de « Tout effacer »
	val vScope    = rememberCoroutineScope()														// effacement
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		SettingsSection("Presse-papiers") {
			OnOffSetting("Du PC vers le téléphone", "Chaque texte copié sur le PC part vers le téléphone. Les contenus marqués sensibles (KeePlus, gestionnaires de mots de passe) ne partent jamais.", vSettings.clipSend) { vOn ->
				inApp.updateSettings { it.copy(clipSend = vOn) }
			}
			SettingsDivider()
			OnOffSetting("Du téléphone vers le PC", "Les textes envoyés par le téléphone sont placés dans le presse-papiers du PC.", vSettings.clipReceive) { vOn ->
				inApp.updateSettings { it.copy(clipReceive = vOn) }
			}
		}
		SettingsSection("Notifications du téléphone") {
			OnOffSetting("Afficher les notifications du téléphone", "Les applis choisies sur le téléphone (les messageries par défaut) s'affichent ici en notifications de Windows, qui respectent « Ne pas déranger ». Les fermer ici les masque sur le téléphone. Rien n'est enregistré.", vSettings.noticesShow) { vOn ->
				inApp.updateSettings { it.copy(noticesShow = vOn) }
			}
		}
		SettingsSection("Historique") {
			if (inApp.hasHotkey) HotkeySetting(vSettings.historyHotkey, vHotkeyOk, inApp::setHotkey)
			BasicText("Les 50 derniers textes échangés, et ceux que vous épinglez, sont gardés chiffrés sur ce PC (DPAPI).", style = vType.bodySmall)
			GhostButton("Tout effacer", { vClear = true }, Modifier.widthIn(max = 220.dp))
		}
		SettingsSection("Démarrage") {
			OnOffSetting("Lancer BridgePlus avec Windows", "BridgePlus démarre discrètement dans la zone de notification.", vSettings.launchAtStartup) { vOn ->
				inApp.updateSettings { it.copy(launchAtStartup = vOn) }
				vStartErr = if (WindowsStartup.exe != null) WindowsStartup.set(vOn) else null
			}
			ErrorLine(vStartErr?.let { "Réglage impossible : $it" })
		}
		SettingsSection("Réseau de la maison") {
			BasicText("BridgePlus n'écoute que sur ces réseaux. Ils sont ajoutés à l'appairage ; retirez ceux qui ne sont pas chez vous.", style = vType.bodySmall)
			if (vState.home.isEmpty() && vState.networks.isEmpty()) BasicText("Aucun réseau reconnu.", style = vType.bodySmall.copy(color = BridgeColors.textFaint))
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
			BasicText("BridgePlus ${appVersion() ?: "(développement)"}", style = vType.title)
			BasicText("Tout passe directement par le réseau de la maison, chiffré avec la clé de chaque paire. Aucun compte, aucun serveur, aucune télémétrie.", style = vType.bodySmall)
		}
	}
	if (vClear)
	{
		ClearHistoryDialog(
			inOnClear   = { vClear = false; vScope.launch(Dispatchers.IO) { inApp.history.clear() } },
			inOnDismiss = { vClear = false }
		)
	}
}

/*
	Raccourci de la carte de l'historique (repris de KeePlus) : combinaison
	actuelle ; « Modifier » attend la combinaison suivante (Échap annule).
	Une combinaison refusée par Windows (déjà prise par une autre appli) est
	signalée.
	inHotkey   : raccourci actuel (texte des réglages)
	inHotkeyOk : raccourci enregistré auprès de Windows
	inOnHotkey : nouveau raccourci
*/
@Composable
private fun HotkeySetting(
	inHotkey   : String,
	inHotkeyOk : Boolean,
	inOnHotkey : (String) -> Unit
)
{
	val vType   = bridgeType()																	// styles de texte
	var vListen by remember { mutableStateOf(false) }											// attente d'une combinaison
	val vFocus  = remember { FocusRequester() }												// focus de la zone d'écoute
	LaunchedEffect(vListen) { if (vListen) runCatching { vFocus.requestFocus() } }
	Row(verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			BasicText("Raccourci de la carte", style = vType.body)
			BasicText(if (vListen) "Appuyez sur la combinaison…" else Hotkey.parse(inHotkey).label(), style = vType.bodySmall)
		}
		if (!vListen) GhostButton("Modifier", { vListen = true }, Modifier.width(140.dp))
	}
	if (vListen)
	{
		Box(
			Modifier
				.focusRequester(vFocus)
				.focusable()
				.onPreviewKeyEvent { inEvent ->
					if (inEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
					if (inEvent.key == Key.Escape)
					{
						vListen = false
						return@onPreviewKeyEvent true
					}
					val vKey = inEvent.key.nativeKeyCode														// touche (code AWT)
					if (!Hotkey.isKey(vKey)) return@onPreviewKeyEvent true
					val vHotkey = Hotkey(inEvent.isCtrlPressed, inEvent.isAltPressed, inEvent.isShiftPressed, inEvent.isMetaPressed, vKey)	// combinaison
					if (vHotkey.usable)
					{
						inOnHotkey(vHotkey.spec())
						vListen = false
					}
					true
				}
		)
	}
	if (!inHotkeyOk) ErrorLine("Ce raccourci est déjà utilisé par une autre appli : choisissez-en un autre.")
}
