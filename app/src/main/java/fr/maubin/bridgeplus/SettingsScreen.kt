package fr.maubin.bridgeplus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.maubin.bridgeplus.peers.PhoneLinkState
import fr.maubin.bridgeplus.settings.SettingsBackup
import fr.maubin.bridgeplus.settings.decodeBackup
import fr.maubin.bridgeplus.settings.encodeBackup
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
	(chaque sens), notifications sur le PC (liste blanche), fichiers reçus,
	réseaux de la maison, mises à jour, sauvegarde et restauration des
	réglages (sans les clés), (appris à l'appairage, retirables),
	autorisations, à propos.
	inState   : état de la liaison
	inNetwork : réseau du téléphone
	inResumes : retours à l'écran (relecture des autorisations)
	inOnBack  : retour à l'accueil
	inOnApps  : ouverture de la liste blanche des notifications
*/
@Composable
fun SettingsScreen(
	inState   : PhoneLinkState,
	inNetwork : PhoneNetwork,
	inResumes : Int,
	inOnBack  : () -> Unit,
	inOnApps  : () -> Unit
)
{
	val vContext = LocalContext.current																// contexte
	val vScope   = rememberCoroutineScope()															// enregistrement
	val vType    = bridgeType()																		// styles de texte
	val vSettings by vContext.bridgeApp.settings.collectAsState()									// réglages
	var vMessage  by remember { mutableStateOf<String?>(null) }										// résultat d'une sauvegarde, d'une restauration, d'une vérification
	val vSave     = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vUri ->
		vUri ?: return@rememberLauncherForActivityResult
		vMessage = runCatching {
			val vBackup = SettingsBackup(settings = vContext.bridgeApp.settings.value, home = vContext.bridgeLink.peers().home)	// sauvegarde
			vContext.contentResolver.openOutputStream(vUri)?.use { it.write(encodeBackup(vBackup).toByteArray(Charsets.UTF_8)) }
			"Réglages sauvegardés (sans les clés des paires)."
		}.getOrElse { "Sauvegarde impossible : ${it.message}" }
	}																									// choix du fichier de sauvegarde
	val vLoad     = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vUri ->
		vUri ?: return@rememberLauncherForActivityResult
		val vBackup = runCatching { vContext.contentResolver.openInputStream(vUri)?.use { it.readBytes().toString(Charsets.UTF_8) }?.let(::decodeBackup) }.getOrNull()	// sauvegarde lue
		vMessage = if (vBackup == null) "Ce fichier n'est pas une sauvegarde de BridgePlus." else {
			vContext.bridgeApp.updateSettings { vBackup.settings.copy(onboardingDone = true) }
			vScope.launch(Dispatchers.IO) { vContext.bridgeLink.setHome(vBackup.home) }
			"Réglages restaurés. Les PC restent à appairer de nouveau s'ils ne le sont pas."
		}
	}																									// choix du fichier à restaurer
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
		SettingsSection("Notifications sur le PC") {
			OnOffSetting("Envoyer les notifications au PC", "Les applis choisies (les messageries par défaut) s'affichent sur le PC. Jamais les banques, les gestionnaires de mots de passe ni les codes de connexion. Rien n'est enregistré.", vSettings.noticesSend) { vOn ->
				vContext.bridgeApp.updateSettings { it.copy(noticesSend = vOn) }
			}
			SettingsDivider()
			BasicText(if (NoticeListener.granted(vContext)) "Accès aux notifications : accordé" else "Accès aux notifications : à accorder (voir Autorisations ci-dessous)", style = vType.bodySmall)
			TextLink("Choisir les applis", inOnApps)
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
		SettingsSection("Mises à jour") {
			OnOffSetting("Chercher une nouvelle version au lancement", "Seule requête vers internet de BridgePlus : la dernière version publiée sur GitHub.", vSettings.updates) { vOn ->
				vContext.bridgeApp.updateSettings { it.copy(updates = vOn) }
			}
			TextLink("Vérifier maintenant", { vContext.bridgeApp.updates.checkNow { vMessage = it } })
		}
		SettingsSection("Sauvegarde") {
			BasicText("Les réglages et les réseaux de la maison, dans un fichier que vous choisissez. Jamais les clés des paires : les PC sont à appairer de nouveau sur un autre téléphone.", style = vType.bodySmall)
			TextLink("Sauvegarder les réglages", { vSave.launch("BridgePlus-reglages.json") })
			TextLink("Restaurer des réglages", { vLoad.launch(arrayOf("application/json", "text/plain", "*/*")) })
			TextLink("Revoir l'assistant de premier lancement", { vContext.bridgeApp.updateSettings { it.copy(onboardingDone = false) } })
		}
		vMessage?.let { BasicText(it, style = vType.bodySmall) }
		SettingsSection("À propos") {
			BasicText("BridgePlus ${BuildConfig.VERSION_NAME}", style = vType.title)
			BasicText("Tout passe directement par le Wi-Fi de la maison, chiffré avec la clé de chaque paire. Aucun compte, aucun serveur, aucune télémétrie.", style = vType.bodySmall)
		}
	}
}
