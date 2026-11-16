package fr.maubin.bridgeplus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import fr.maubin.bridgeplus.transfer.sizeText
import fr.maubin.bridgeplus.transfer.transferDetail
import fr.maubin.bridgeplus.ui.components.ClipRow
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.IconAction
import fr.maubin.bridgeplus.ui.components.LinkHero
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.components.TransferRow
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
	val vFiles   by vContext.bridgeApp.files.transfers.collectAsState()								// transferts
	val vRinging by Ringer.ringing.collectAsState()													// sonnerie en cours
	var vChoice  by remember { mutableStateOf<List<OpenedSource>?>(null) }							// fichiers en attente du choix du PC
	val vPick    = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vUris ->
		if (vUris.isEmpty()) return@rememberLauncherForActivityResult
		val vPcs     = vContext.bridgeApp.connectedPcs()													// PC reliés
		val vSources = openSources(vContext, vUris)															// fichiers ouverts
		when
		{
			vPcs.isEmpty()     -> vMessage = "Aucun PC relié : les fichiers ne sont pas partis."
			vSources.isEmpty() -> vMessage = "Impossible de lire ces fichiers."
			vPcs.size == 1     -> vContext.bridgeApp.sendFiles(vPcs.single(), vSources)
			else               -> vChoice = vSources
		}
	}																									// sélecteur de fichiers du système
	LaunchedEffect(Unit) { while (true) { vNow = System.currentTimeMillis(); delay(30_000) } }
	KeyScreen(
		inActions = {
			IconAction(LineIcons.History, "Historique", { inOnScreen(PhoneScreen.History) }, inTint = BridgeColors.text)
			IconAction(LineIcons.Devices, "Appareils", { inOnScreen(PhoneScreen.Devices) }, inTint = BridgeColors.text)
			IconAction(LineIcons.Settings, "Réglages", { inOnScreen(PhoneScreen.Settings) }, inTint = BridgeColors.text)
		}
	) {
		LinkHero(inLook.look, inLook.muted, inLook.strong, inLook.detail)
		if (vRinging) SilverButton("Arrêter la sonnerie", { Ringer.stop(vContext) })
		if (inState.pcs.isEmpty())
		{
			SilverButton("Appairer un PC", { inOnScreen(PhoneScreen.Devices) })
			return@KeyScreen
		}
		GhostButton("Envoyer le presse-papiers", {
			readClipboard(vContext).let { vClip -> vContext.bridgeApp.sendClip(vClip.text, vClip.sensitive, true) { vMessage = it } }
		})
		GhostButton("Envoyer un fichier", { vMessage = null; vPick.launch(arrayOf("*/*")) })
		vMessage?.let { BasicText(it, style = vType.bodySmall) }
		val vActive = vFiles.filter { it.active }															// transferts en cours
		if (vActive.isNotEmpty())
		{
			SettingsSection("Fichiers en cours") {
				for (vTransfer in vActive)
				{
					TransferRow(vTransfer.outgoing, vTransfer.name, transferDetail(vTransfer, vNow), vTransfer.progress, false) {
						IconAction(LineIcons.Close, "Annuler ${vTransfer.name}", { vContext.bridgeApp.files.cancel(vTransfer.id) })
					}
				}
			}
		}
		MissingPermissions(inResumes)
		inState.forgotten?.let { BasicText("$it a oublié ce téléphone : la paire est effacée des deux côtés.", style = vType.bodySmall) }
		SettingsSection("Derniers échanges") {
			if (vClips.isEmpty())
			{
				BasicText("Rien d'échangé pour l'instant.", style = vType.body)
				BasicText("Les textes copiés passent d'eux-mêmes du PC au téléphone ; dans l'autre sens, ouvrez BridgePlus, ou utilisez la tuile ou le menu de partage « Envoyer au PC ».", style = vType.bodySmall)
			}
			for (vClip in vClips.take(5)) ClipRow(vClip.outgoing, if (vClip.isFile) "${vClip.preview} · ${sizeText(vClip.size)}" else vClip.preview, vClip.peers.joinToString(", "), relativeTime(vClip.at, vNow))
			if (vClips.size > 5) TextLink("Tout voir (${vClips.size})", { inOnScreen(PhoneScreen.History) })
		}
	}
	vChoice?.let { vSources ->
		PcChoiceDialog(vContext.bridgeApp.connectedPcs(), vSources.size, { vPc -> vChoice = null; vContext.bridgeApp.sendFiles(vPc, vSources) }, { vSources.forEach { runCatching { it.open().close() } }; vChoice = null })
	}
}
