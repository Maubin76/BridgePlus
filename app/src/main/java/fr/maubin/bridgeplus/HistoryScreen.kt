package fr.maubin.bridgeplus

import android.widget.Toast
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import fr.maubin.bridgeplus.text.relativeTime
import fr.maubin.bridgeplus.ui.components.ClearHistoryDialog
import fr.maubin.bridgeplus.ui.components.HistoryRow
import fr.maubin.bridgeplus.ui.components.SearchField
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.screens.WorkScreen
import fr.maubin.bridgeplus.ui.theme.bridgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
	Écran Historique, écran de travail à la manière de KeePlus : recherche
	(sans majuscules ni accents), puis les textes échangés, épinglés
	d'abord. Toucher un élément le copie (il ne repart pas vers le PC) ;
	épingle et croix sur chaque ligne ; « Tout effacer » demande une
	confirmation. L'historique est chiffré par une clé du Keystore.
	inOnBack : retour à l'accueil
*/
@Composable
fun HistoryScreen(inOnBack: () -> Unit)
{
	val vType    = bridgeType()																		// styles de texte
	val vContext = LocalContext.current																// contexte
	val vApp     = vContext.bridgeApp																// appli
	val vItems   by vApp.history.items.collectAsState()												// éléments
	val vQuery   = rememberTextFieldState()															// recherche
	var vText    by remember { mutableStateOf("") }													// recherche en cours
	var vClear   by remember { mutableStateOf(false) }												// confirmation de « Tout effacer »
	val vScope   = rememberCoroutineScope()															// écritures
	val vNow     = remember { System.currentTimeMillis() }											// horloge (« il y a… »)
	val vFound   = remember(vItems, vText) { vApp.history.search(vText) }							// éléments affichés
	LaunchedEffect(vQuery) { snapshotFlow { vQuery.text.toString() }.collect { vText = it } }
	WorkScreen("Historique", inOnBack) {
		SearchField(vQuery, inPlaceholder = "Rechercher dans l'historique")
		SettingsSection(if (vItems.isEmpty()) "Presse-papiers" else "${vItems.size} élément${if (vItems.size > 1) "s" else ""}") {
			when
			{
				vItems.isEmpty() -> BasicText("Rien d'échangé pour l'instant : les textes copiés entre le téléphone et le PC apparaîtront ici.", style = vType.bodySmall)
				vFound.isEmpty() -> BasicText("Rien ne correspond à « $vText ».", style = vType.bodySmall)
			}
			for (vItem in vFound)
			{
				HistoryRow(
					inOutgoing = vItem.outgoing,
					inText     = vItem.preview,
					inPeers    = vItem.peers.joinToString(", ").ifEmpty { "?" },
					inWhen     = relativeTime(vItem.at, vNow),
					inPinned   = vItem.pinned,
					inSelected = false,
					inOnCopy   = { vApp.copyFromHistory(vItem.id) { Toast.makeText(vContext, it, Toast.LENGTH_SHORT).show() } },
					inOnPin    = { vScope.launch(Dispatchers.IO) { vApp.history.pin(vItem.id, !vItem.pinned) } },
					inOnDelete = { vScope.launch(Dispatchers.IO) { vApp.history.delete(vItem.id) } }
				)
			}
		}
		if (vItems.isNotEmpty()) TextLink("Tout effacer", { vClear = true }, inDanger = true)
		BasicText("Les 50 derniers textes échangés, et ceux que vous épinglez, sont gardés chiffrés sur ce téléphone.", style = vType.bodySmall)
	}
	if (vClear)
	{
		ClearHistoryDialog(
			inOnClear   = { vClear = false; vScope.launch(Dispatchers.IO) { vApp.history.clear() } },
			inOnDismiss = { vClear = false }
		)
	}
}
