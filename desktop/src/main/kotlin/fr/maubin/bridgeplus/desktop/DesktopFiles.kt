package fr.maubin.bridgeplus.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.bridgeplus.transfer.sizeText
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

/*
	Valeur qui suit un argument de la ligne de commande ; null s'il est absent.
	inArgs : arguments
	inName : argument cherché (« --envoyer »)
*/
fun argAfter(
	inArgs : Array<String>,
	inName : String
): String? = inArgs.indexOf(inName).takeIf { it >= 0 }?.let { inArgs.getOrNull(it + 1) }

/*
	Fichiers déposés sur une fenêtre (glisser-déposer depuis l'Explorateur) :
	envoyés au téléphone. Les dossiers sont ignorés.
	inWindow : fenêtre
	inApp    : appli PC
*/
fun acceptDroppedFiles(
	inWindow : java.awt.Window,
	inApp    : DesktopApp
)
{
	inWindow.dropTarget = DropTarget(inWindow, DnDConstants.ACTION_COPY, object : DropTargetAdapter()
	{
		/*
			Fichiers déposés.
			inEvent : dépôt
		*/
		override fun drop(inEvent: DropTargetDropEvent)
		{
			if (!inEvent.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { inEvent.rejectDrop(); return }
			inEvent.acceptDrop(DnDConstants.ACTION_COPY)
			val vFiles = (inEvent.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>).orEmpty().filterIsInstance<File>()	// fichiers déposés
			inEvent.dropComplete(true)
			inApp.sendFiles(vFiles)
		}
	}, true)
}

/*
	Choix du téléphone quand plusieurs sont reliés : un bouton par téléphone,
	dans le dialogue de KeePlus.
	inApp   : appli PC
	inFiles : fichiers à envoyer
*/
@Composable
fun PhoneChoiceDialog(
	inApp   : DesktopApp,
	inFiles : List<File>
)
{
	val vType  = bridgeType()																	// styles de texte
	val vState by inApp.link.state.collectAsState()												// état de la liaison
	val vSize  = inFiles.sumOf { it.length() }													// taille totale
	Dialog(onDismissRequest = inApp::cancelChoice) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BridgeColors.surfaceHigh).padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			BasicText("Envoyer à quel téléphone ?", style = vType.headline)
			BasicText("${if (inFiles.size == 1) inFiles.single().name else "${inFiles.size} fichiers"} · ${sizeText(vSize)}", style = vType.bodySmall)
			for (vPhone in vState.phones.filter { it.id in vState.connected }) GhostButton(vPhone.label, { inApp.sendFiles(inFiles, vPhone.id) })
			TextLink("Annuler", inApp::cancelChoice)
		}
	}
}
