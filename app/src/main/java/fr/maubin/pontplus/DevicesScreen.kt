package fr.maubin.pontplus

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.link.PairingOffer
import fr.maubin.pontplus.peers.PairedPc
import fr.maubin.pontplus.peers.PcStatus
import fr.maubin.pontplus.peers.PhoneLinkState
import fr.maubin.pontplus.text.relativeTime
import fr.maubin.pontplus.ui.components.DeviceRow
import fr.maubin.pontplus.ui.components.ForgetDialog
import fr.maubin.pontplus.ui.components.LinkLook
import fr.maubin.pontplus.ui.components.PontCard
import fr.maubin.pontplus.ui.components.PrimaryButton
import fr.maubin.pontplus.ui.components.RenameDialog
import fr.maubin.pontplus.ui.components.SectionTitle
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
	Onglet Appareils : PC appairés (état, dernière connexion, renommer,
	oublier) et appairage d'un PC par son QR code. Avant de scanner, la
	localisation est demandée : sans elle, le Wi-Fi de la maison ne peut pas
	être reconnu.
	inState : état de la liaison
*/
@Composable
fun DevicesScreen(inState: PhoneLinkState)
{
	val vContext  = LocalContext.current															// contexte
	val vScope    = rememberCoroutineScope()														// appairage (réseau)
	val vLink     = vContext.pontLink																// liaison
	var vScanning by remember { mutableStateOf(false) }												// lecteur de QR code ouvert
	var vBusy     by remember { mutableStateOf(false) }												// appairage en cours
	var vMessage  by remember { mutableStateOf<Pair<String, Boolean>?>(null) }						// résultat (texte, réussite)
	var vRename   by remember { mutableStateOf<PairedPc?>(null) }									// PC à renommer
	var vForget   by remember { mutableStateOf<PairedPc?>(null) }									// PC à oublier
	val vAsk      = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vResult ->
		if (vResult[Manifest.permission.ACCESS_FINE_LOCATION] == true) vScanning = true
		else vMessage = "Sans la localisation, PontPlus ne peut pas reconnaître le Wi-Fi de la maison." to false
	}																								// demande de la localisation
	/*
		Appaire le PC du QR code lu.
		inText : contenu du QR code
	*/
	fun pair(inText: String)
	{
		vScanning = false
		val vOffer = PairingOffer.decode(inText) ?: run { vMessage = "Ce QR code n'est pas un QR code d'appairage de PontPlus." to false; return }	// offre
		val vWifi  = currentPhoneNetwork(vContext).wifi ?: run { vMessage = "Reliez le téléphone au Wi-Fi de la maison, celui du PC." to false; return }	// Wi-Fi actuel
		vBusy = true
		vScope.launch {
			val vError = withContext(Dispatchers.IO) { vLink.pair(vOffer, vWifi) }					// échec ; null si appairé
			vBusy    = false
			vMessage = if (vError == null) "${vOffer.pcName} est appairé." to true else vError to false
			if (vError == null) LinkService.startIfHome(vContext)
		}
	}
	Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
		Text("Appareils", style = Pont.type.display)
		Column {
			SectionTitle("PC appairés")
			PontCard {
				if (inState.pcs.isEmpty()) Text("Aucun PC appairé.", style = Pont.type.body.copy(color = Pont.colors.textMuted))
				for (vPc in inState.pcs)
				{
					val vStatus = inState.statusOf(vPc.id)													// état du PC
					DeviceRow(
						inIcon     = LineIcons.Computer,
						inName     = vPc.label,
						inLook     = when (vStatus) { PcStatus.Connected -> LinkLook.Connected; PcStatus.Searching -> LinkLook.Searching; PcStatus.Refused -> LinkLook.Refused; PcStatus.Absent -> LinkLook.Absent },
						inStatus   = when (vStatus) { PcStatus.Connected -> "Relié"; PcStatus.Searching -> "Recherche…"; PcStatus.Refused -> "Refusé"; PcStatus.Absent -> "Absent" },
						inLastSeen = vPc.lastSeen?.let { relativeTime(it) },
						inOnRename = { vRename = vPc },
						inOnForget = { vForget = vPc }
					)
					inState.refusals[vPc.id]?.let { Text(it, style = Pont.type.bodySmall.copy(color = Pont.colors.danger)) }
				}
			}
		}
		vMessage?.let { (vText, vOk) -> Text(vText, style = Pont.type.body.copy(color = if (vOk) Pont.colors.success else Pont.colors.danger)) }
		PrimaryButton(
			inText    = if (vBusy) "Appairage…" else "Appairer un PC",
			inOnClick = {
				vMessage = null
				if (hasWifiNamePermission(vContext)) vScanning = true
				else vAsk.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
			},
			inIcon    = LineIcons.Qr,
			inEnabled = !vBusy
		)
		Text("Sur le PC, ouvrez PontPlus et choisissez « Appairer un téléphone » : un QR code s'affiche. Les deux appareils doivent être sur le même Wi-Fi.", style = Pont.type.bodySmall)
	}
	if (vScanning) QrScannerDialog(::pair) { vScanning = false }
	vRename?.let { vPc ->
		RenameDialog(
			inCurrent   = vPc.label,
			inOriginal  = vPc.name,
			inOnRename  = { vName -> vRename = null; vScope.launch(Dispatchers.IO) { vLink.rename(vPc.id, vName) } },
			inOnDismiss = { vRename = null }
		)
	}
	vForget?.let { vPc ->
		ForgetDialog(
			inName      = vPc.label,
			inOnForget  = { vForget = null; vScope.launch(Dispatchers.IO) { vLink.forget(vPc.id) } },
			inOnDismiss = { vForget = null }
		)
	}
}
