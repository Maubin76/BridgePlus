package fr.maubin.bridgeplus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.bridgeplus.peers.PairedPc
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType

/*
	Choix du PC quand plusieurs sont reliés : un bouton par PC, dans le
	dialogue de KeePlus.
	inPcs       : PC reliés
	inCount     : nombre de fichiers à envoyer
	inOnChoose  : PC choisi
	inOnDismiss : envoi abandonné
*/
@Composable
fun PcChoiceDialog(
	inPcs       : List<PairedPc>,
	inCount     : Int,
	inOnChoose  : (PairedPc) -> Unit,
	inOnDismiss : () -> Unit
)
{
	val vType = bridgeType()																	// styles de texte
	Dialog(onDismissRequest = inOnDismiss) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BridgeColors.surfaceHigh).padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			BasicText("Envoyer à quel PC ?", style = vType.headline)
			BasicText(if (inCount == 1) "Un fichier" else "$inCount fichiers", style = vType.bodySmall)
			for (vPc in inPcs) GhostButton(vPc.label, { inOnChoose(vPc) })
			TextLink("Annuler", inOnDismiss)
		}
	}
}
