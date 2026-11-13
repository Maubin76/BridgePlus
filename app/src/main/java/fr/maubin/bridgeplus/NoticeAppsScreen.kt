package fr.maubin.bridgeplus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import fr.maubin.bridgeplus.history.ClipHistory
import fr.maubin.bridgeplus.notice.isAppAllowed
import fr.maubin.bridgeplus.notice.kExcludedApps
import fr.maubin.bridgeplus.ui.components.FilterPill
import fr.maubin.bridgeplus.ui.components.OneLine
import fr.maubin.bridgeplus.ui.components.SearchField
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.screens.WorkScreen
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
	Écran de la liste blanche des notifications : les applis de l'écran
	d'accueil, celles qui envoient au PC d'abord (les messageries par
	défaut), avec une recherche ; une pastille par appli pour l'activer ou
	non. Les applis exclues d'office (KeePlus et gestionnaires de mots de
	passe, banques, authentification) sont grisées avec la raison, sans
	pastille.
	inOnBack : retour aux réglages
*/
@Composable
fun NoticeAppsScreen(inOnBack: () -> Unit)
{
	val vType     = bridgeType()																	// styles de texte
	val vContext  = LocalContext.current															// contexte
	val vSettings by vContext.bridgeApp.settings.collectAsState()									// réglages
	val vApps     by produceState(emptyList<LauncherApp>()) { value = withContext(Dispatchers.IO) { launcherApps(vContext) } }	// applis de l'écran d'accueil
	val vQuery    = rememberTextFieldState()														// recherche
	var vText     by remember { mutableStateOf("") }												// recherche en cours
	LaunchedEffect(vQuery) { snapshotFlow { vQuery.text.toString() }.collect { vText = ClipHistory.normalize(it.trim()) } }
	val vShown    = remember(vApps, vText, vSettings) {
		vApps.filter { vText.isEmpty() || ClipHistory.normalize(it.name).contains(vText) }
			.sortedWith(compareBy<LauncherApp>({ it.pkg in kExcludedApps }, { !isAppAllowed(it.pkg, vSettings) }, { it.name.lowercase() }))
	}																								// applis affichées
	WorkScreen("Notifications sur le PC", inOnBack) {
		BasicText("Seules les applis activées envoient leurs notifications au PC. Les codes de connexion ne partent jamais, quelle que soit l'appli.", style = vType.bodySmall)
		SearchField(vQuery, inPlaceholder = "Rechercher une appli")
		SettingsSection(if (vApps.isEmpty()) "Chargement…" else "${vShown.count { isAppAllowed(it.pkg, vSettings) }} appli(s) activée(s)") {
			for (vApp in vShown)
			{
				val vExcluded = kExcludedApps[vApp.pkg]														// raison d'une exclusion
				val vOn       = isAppAllowed(vApp.pkg, vSettings)												// appli activée
				Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
					AppIcon(vApp.pkg)
					Spacer(Modifier.width(12.dp))
					Column(Modifier.weight(1f)) {
						OneLine(vApp.name, vType.body.copy(color = if (vExcluded != null) BridgeColors.textFaint else BridgeColors.text))
						vExcluded?.let { OneLine(it, vType.bodySmall) }
					}
					if (vExcluded == null) FilterPill(if (vOn) "Activée" else "Désactivée", vOn) {
						vContext.bridgeApp.updateSettings { it.copy(noticeApps = it.noticeApps + (vApp.pkg to !vOn)) }
					}
				}
			}
		}
	}
}

/*
	Icône d'une appli (lue une fois).
	inPkg : paquet
*/
@Composable
private fun AppIcon(inPkg: String)
{
	val vContext = LocalContext.current																// contexte
	val vBitmap  = remember(inPkg) { runCatching { vContext.packageManager.getApplicationIcon(inPkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }	// icône
	if (vBitmap != null) Image(vBitmap, contentDescription = null, modifier = Modifier.size(32.dp))
	else Spacer(Modifier.size(32.dp))
}
