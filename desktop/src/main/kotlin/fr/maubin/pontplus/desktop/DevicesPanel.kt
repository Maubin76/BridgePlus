package fr.maubin.pontplus.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.peers.PairedPhone
import fr.maubin.pontplus.text.relativeTime
import fr.maubin.pontplus.ui.components.DeviceRow
import fr.maubin.pontplus.ui.components.ForgetDialog
import fr.maubin.pontplus.ui.components.InfoCard
import fr.maubin.pontplus.ui.components.LinkHero
import fr.maubin.pontplus.ui.components.LinkLook
import fr.maubin.pontplus.ui.components.PontCard
import fr.maubin.pontplus.ui.components.PrimaryButton
import fr.maubin.pontplus.ui.components.RenameDialog
import fr.maubin.pontplus.ui.components.SecondaryButton
import fr.maubin.pontplus.ui.components.SectionTitle
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont
import kotlinx.coroutines.delay

/*
	Page Appareils du PC : état de la liaison en grand, explication du
	pare-feu au premier lancement, téléphones appairés (renommer, oublier),
	appairage d'un téléphone par QR code.
	inApp : appli PC
*/
@Composable
fun DevicesPanel(inApp: DesktopApp)
{
	val vState    by inApp.link.state.collectAsState()											// état de la liaison
	val vSettings by inApp.settings.collectAsState()											// réglages
	var vRename   by remember { mutableStateOf<PairedPhone?>(null) }							// téléphone à renommer
	var vForget   by remember { mutableStateOf<PairedPhone?>(null) }							// téléphone à oublier
	var vNow      by remember { mutableStateOf(System.currentTimeMillis()) }					// horloge (compte à rebours, « vu il y a »)
	LaunchedEffect(Unit) {
		while (true) { vNow = System.currentTimeMillis(); delay(1_000) }
	}
	val vLook = pcLook(vState)																	// état en clair
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		LinkHero(vLook.look, vLook.title, vLook.detail)
		if (!vSettings.firewallExplained)
		{
			InfoCard(
				inIcon     = LineIcons.Info,
				inText     = "Au premier appairage, Windows demandera d'autoriser PontPlus sur le réseau. Cochez seulement « Réseaux privés » : PontPlus n'écoute que sur le réseau de la maison. Si le téléphone ne trouve pas le PC, vérifiez que ce réseau est bien « Privé » dans Paramètres › Réseau et Internet.",
				inAction   = "Compris",
				inOnAction = { inApp.updateSettings { it.copy(firewallExplained = true) } }
			)
		}
		Column {
			SectionTitle("Téléphones")
			PontCard {
				if (vState.phones.isEmpty()) Text("Aucun téléphone appairé.", style = Pont.type.body.copy(color = Pont.colors.textMuted))
				for (vPhone in vState.phones)
				{
					val vOn = vPhone.id in vState.connected													// téléphone relié
					DeviceRow(
						inIcon     = LineIcons.Phone,
						inName     = vPhone.label,
						inLook     = if (vOn) LinkLook.Connected else LinkLook.Absent,
						inStatus   = if (vOn) "Relié" else "Absent",
						inLastSeen = vPhone.lastSeen?.let { relativeTime(it, vNow) },
						inOnRename = { vRename = vPhone },
						inOnForget = { vForget = vPhone }
					)
				}
			}
		}
		val vOffer = vState.offer?.takeIf { it.expires > vNow }										// offre affichée
		if (vOffer != null)
		{
			PontCard {
				Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
					QrCode(vOffer.encode(), 200.dp, "QR code d'appairage")
					Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
						Text("Scannez ce QR code avec le téléphone", style = Pont.type.title)
						Text("Dans PontPlus sur le téléphone : Appareils › Appairer un PC. Les deux appareils doivent être sur le même Wi-Fi.", style = Pont.type.body.copy(color = Pont.colors.textMuted))
						val vLeft = ((vOffer.expires - vNow) / 1000).coerceAtLeast(0)						// secondes restantes
						Text("Valable encore ${vLeft / 60} min ${(vLeft % 60).toString().padStart(2, '0')} s", style = Pont.type.bodySmall)
						Spacer(Modifier.height(4.dp))
						SecondaryButton("Annuler", { inApp.background { cancelPairing() } })
					}
				}
			}
		}
		else
		{
			vState.paired?.let { Text("« $it » est appairé.", style = Pont.type.body.copy(color = Pont.colors.success)) }
			PrimaryButton("Appairer un téléphone", { inApp.background { startPairing() } }, inIcon = LineIcons.Qr)
		}
	}
	vRename?.let { vPhone ->
		RenameDialog(
			inCurrent   = vPhone.label,
			inOriginal  = vPhone.name,
			inOnRename  = { vName -> vRename = null; inApp.background { rename(vPhone.id, vName) } },
			inOnDismiss = { vRename = null }
		)
	}
	vForget?.let { vPhone ->
		ForgetDialog(
			inName      = vPhone.label,
			inOnForget  = { vForget = null; inApp.background { forget(vPhone.id) } },
			inOnDismiss = { vForget = null }
		)
	}
}
