package fr.maubin.bridgeplus.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.bridgeplus.transfer.TransferState
import fr.maubin.bridgeplus.transfer.transferDetail
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.IconAction
import fr.maubin.bridgeplus.ui.components.InfoCard
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.components.TransferRow
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.ErrorLine
import fr.maubin.bridgeplus.ui.screens.OnOffSetting
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/*
	Page Transferts du PC : envoyer un fichier (bouton, ou glisser-déposer
	sur la fenêtre et sur la carte), transferts en cours (avancement,
	annuler) et récents (ouvrir, ouvrir le dossier), dossier de réception,
	entrée du menu de l'Explorateur.
	inApp : appli PC
*/
@Composable
fun TransfersPanel(inApp: DesktopApp)
{
	val vType      = bridgeType()																// styles de texte
	val vTransfers by inApp.files.transfers.collectAsState()									// transferts
	val vSettings  by inApp.settings.collectAsState()											// réglages
	val vScope     = rememberCoroutineScope()													// boîtes de dialogue, registre
	var vNow       by remember { mutableLongStateOf(System.currentTimeMillis()) }			// horloge (« il y a… »)
	var vMenuOn    by remember { mutableStateOf(false) }										// entrée de l'Explorateur présente
	var vMenuError by remember { mutableStateOf<String?>(null) }								// échec du réglage de l'Explorateur
	LaunchedEffect(Unit) { vMenuOn = withContext(Dispatchers.IO) { ExplorerMenu.isOn() } }
	LaunchedEffect(Unit) { while (true) { vNow = System.currentTimeMillis(); delay(30_000) } }
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		InfoCard(LineIcons.Transfers, "Glissez des fichiers sur cette fenêtre, ou sur la carte de l'historique, pour les envoyer au téléphone. Aucune limite de taille, hormis l'espace libre du téléphone.")
		SilverButton("Envoyer un fichier…", { vScope.launch { chooseFiles()?.let { inApp.sendFiles(it) } } }, Modifier.widthIn(max = 360.dp))
		SettingsSection("En cours et récents") {
			if (vTransfers.isEmpty()) BasicText("Aucun transfert pour l'instant.", style = vType.bodySmall.copy(color = BridgeColors.textFaint))
			for (vTransfer in vTransfers)
			{
				TransferRow(
					inOutgoing = vTransfer.outgoing,
					inName     = vTransfer.name,
					inDetail   = transferDetail(vTransfer, vNow),
					inProgress = vTransfer.progress.takeIf { vTransfer.active },
					inFailed   = vTransfer.state == TransferState.Failed
				) {
					when
					{
						vTransfer.active                                                     -> IconAction(LineIcons.Close, "Annuler ${vTransfer.name}", { inApp.files.cancel(vTransfer.id) })
						vTransfer.state == TransferState.Done && vTransfer.location.isNotEmpty() -> Row {
							TextLink("Ouvrir", { inApp.openFile(File(vTransfer.location)) })
							TextLink("Dossier", { inApp.showInFolder(File(vTransfer.location)) })
						}
					}
				}
			}
		}
		SettingsSection("Réception") {
			BasicText("Les fichiers reçus du téléphone arrivent dans :", style = vType.bodySmall)
			BasicText(inApp.receiveDir().absolutePath, style = vType.body)
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				GhostButton("Changer…", { vScope.launch { chooseFolder()?.let { vDir -> inApp.updateSettings { it.copy(receiveFolder = vDir.absolutePath) } } } }, Modifier.widthIn(max = 180.dp))
				GhostButton("Ouvrir le dossier", { inApp.openFile(inApp.receiveDir().apply { mkdirs() }) }, Modifier.widthIn(max = 220.dp))
			}
			if (vSettings.receiveFolder.isNotBlank()) TextLink("Revenir à Téléchargements\\BridgePlus", { inApp.updateSettings { it.copy(receiveFolder = "") } })
		}
		SettingsSection("Explorateur de fichiers") {
			OnOffSetting("« Envoyer au téléphone » dans le menu contextuel", "Clic droit sur un fichier dans l'Explorateur. Rien d'autre n'est modifié ; désactiver retire l'entrée.", vMenuOn) { vOn ->
				vScope.launch {
					vMenuError = withContext(Dispatchers.IO) { ExplorerMenu.set(vOn) }
					vMenuOn    = withContext(Dispatchers.IO) { ExplorerMenu.isOn() }
				}
			}
			ErrorLine(vMenuError?.let { "Réglage impossible : $it" })
		}
	}
}

/*
	Choix de fichiers à envoyer (boîte « Ouvrir » de Windows, plusieurs
	fichiers) ; null si annulé.
*/
suspend fun chooseFiles(): List<File>? = withContext(Dispatchers.Main) {
	val vDialog = FileDialog(null as Frame?, "Envoyer au téléphone", FileDialog.LOAD).apply {
		isMultipleMode = true
		isVisible      = true
	}																							// boîte de dialogue (modale)
	vDialog.files?.toList()?.takeIf { it.isNotEmpty() }
}

/*
	Choix d'un dossier, à l'allure de Windows ; null si annulé.
*/
suspend fun chooseFolder(): File? = withContext(Dispatchers.Main) {
	runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
	val vChooser = JFileChooser().apply {
		dialogTitle               = "Dossier de réception des fichiers"
		fileSelectionMode         = JFileChooser.DIRECTORIES_ONLY
		isAcceptAllFileFilterUsed = false
	}																							// boîte de dialogue (modale)
	if (vChooser.showDialog(null, "Choisir ce dossier") == JFileChooser.APPROVE_OPTION) vChooser.selectedFile else null
}
