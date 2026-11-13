package fr.maubin.bridgeplus.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.screens.ErrorLine
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.BridgeLogo
import fr.maubin.bridgeplus.ui.theme.bridgeType
import fr.maubin.bridgeplus.update.sizeLabel
import kotlin.math.roundToInt

/*
	Fenêtre « nouvelle version disponible » (téléphone et PC) : version
	proposée et installée, taille du fichier, rappel de mettre à jour l'autre
	appareil (appuyé si le protocole de liaison change), explication
	propre à l'appareil, puis « Mettre à jour », « Plus tard » et « Ignorer
	cette version ». Pendant le téléchargement : barre d'avancement et
	« Annuler ».
	inManager : mises à jour (état et actions)
*/
@Composable
fun UpdateDialog(inManager: UpdateManager)
{
	val vState   = inManager.state ?: return												// fenêtre affichée
	val vOffer   = vState.offer																// mise à jour
	val vType    = bridgeType()																// styles de texte
	val vLoading = vState.progress != null && vState.error == null							// téléchargement en cours
	Dialog(onDismissRequest = { if (!vLoading) inManager.later() }) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BridgeColors.surfaceHigh).padding(22.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
				BridgeLogo(inSize = 44.dp)
				BasicText("BridgePlus ${vOffer.version} est disponible", style = vType.headline)
			}
			BasicText("Vous avez la version ${vOffer.current}. Téléchargement : ${sizeLabel(vOffer.asset.size)}.", style = vType.body)
			if (vOffer.protocolChanged)
			{
				BasicText(
					"Cette version change la liaison : mettez à jour le téléphone et le PC ensemble, sinon ils ne pourront plus se relier.",
					style = vType.label.copy(color = BridgeColors.text)
				)
			}
			else BasicText("Pensez à mettre aussi à jour votre autre appareil.", style = vType.bodySmall)
			vState.notice?.let { BasicText(it, style = vType.bodySmall) }
			if (vLoading)
			{
				val vPercent = (vState.progress * 100).roundToInt()								// avancement en pour cent
				Box(
					Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(BridgeColors.rule)
						.semantics { contentDescription = "Téléchargement à $vPercent %" }
				) {
					Box(Modifier.fillMaxWidth(vState.progress).height(4.dp).background(BridgeColors.text))
				}
				BasicText(if (vPercent >= 100) "Installation…" else "Téléchargement… $vPercent %", style = vType.bodySmall)
			}
			ErrorLine(vState.error)
			if (vLoading) GhostButton("Annuler", inManager::cancel)
			else
			{
				SilverButton(if (vState.error != null) "Réessayer" else vState.action, inManager::update)
				Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
					TextLink("Ignorer cette version", inManager::ignore)
					TextLink("Plus tard", inManager::later)
				}
			}
		}
	}
}
