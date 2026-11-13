package fr.maubin.bridgeplus

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import fr.maubin.bridgeplus.link.PairingOffer
import fr.maubin.bridgeplus.peers.PairedPc
import fr.maubin.bridgeplus.peers.PcStatus
import fr.maubin.bridgeplus.peers.PhoneLinkState
import fr.maubin.bridgeplus.text.relativeTime
import fr.maubin.bridgeplus.ui.components.DeviceRow
import fr.maubin.bridgeplus.ui.components.ForgetDialog
import fr.maubin.bridgeplus.ui.components.LinkLook
import fr.maubin.bridgeplus.ui.components.RenameDialog
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.screens.ErrorLine
import fr.maubin.bridgeplus.ui.screens.SettingsSection
import fr.maubin.bridgeplus.ui.screens.WorkScreen
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
	Écran Appareils, écran de travail à la manière de KeePlus : PC appairés (état, dernière connexion, renommer,
	oublier) et appairage d'un PC par son QR code. Avant de scanner, la
	localisation est demandée : sans elle, le Wi-Fi de la maison ne peut pas
	être reconnu.
	inState  : état de la liaison
	inOnBack : retour à l'accueil
*/
@Composable
fun DevicesScreen(
	inState  : PhoneLinkState,
	inOnBack : () -> Unit
)
{
	val vContext  = LocalContext.current															// contexte
	val vScope    = rememberCoroutineScope()														// appairage (réseau)
	val vLink     = vContext.bridgeLink																// liaison
	var vScanning by remember { mutableStateOf(false) }												// lecteur de QR code ouvert
	var vBusy     by remember { mutableStateOf(false) }												// appairage en cours
	var vMessage  by remember { mutableStateOf<Pair<String, Boolean>?>(null) }						// résultat (texte, réussite)
	var vRename   by remember { mutableStateOf<PairedPc?>(null) }									// PC à renommer
	var vForget   by remember { mutableStateOf<PairedPc?>(null) }									// PC à oublier
	val vAsk      = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vResult ->
		if (vResult[Manifest.permission.ACCESS_FINE_LOCATION] == true) vScanning = true
		else vMessage = "Sans la localisation, BridgePlus ne peut pas reconnaître le Wi-Fi de la maison." to false
	}																								// demande de la localisation
	/*
		Appaire le PC du QR code lu.
		inText : contenu du QR code
	*/
	fun pair(inText: String)
	{
		vScanning = false
		val vOffer = PairingOffer.decode(inText) ?: run { vMessage = "Ce QR code n'est pas un QR code d'appairage de BridgePlus." to false; return }	// offre
		val vWifi  = currentPhoneNetwork(vContext).wifi ?: run { vMessage = "Reliez le téléphone au Wi-Fi de la maison, celui du PC." to false; return }	// Wi-Fi actuel
		vBusy = true
		vScope.launch {
			val vError = withContext(Dispatchers.IO) { vLink.pair(vOffer, vWifi) }					// échec ; null si appairé
			vBusy    = false
			vMessage = if (vError == null) "${vOffer.pcName} est appairé." to true else vError to false
			if (vError == null) LinkService.startIfHome(vContext)
		}
	}
	val vType = bridgeType()																			// styles de texte
	WorkScreen("Appareils", inOnBack) {
		SettingsSection("PC appairés") {
			if (inState.pcs.isEmpty()) BasicText("Aucun PC appairé.", style = vType.bodySmall.copy(color = BridgeColors.textFaint))
			for (vPc in inState.pcs)
			{
				val vStatus = inState.statusOf(vPc.id)															// état du PC
				DeviceRow(
					inIcon     = LineIcons.Computer,
					inName     = vPc.label,
					inLook     = when (vStatus) { PcStatus.Connected -> LinkLook.Connected; PcStatus.Searching -> LinkLook.Searching; PcStatus.Refused -> LinkLook.Refused; PcStatus.Absent -> LinkLook.Absent },
					inStatus   = when (vStatus) { PcStatus.Connected -> "Relié"; PcStatus.Searching -> "Recherche…"; PcStatus.Refused -> "Refusé"; PcStatus.Absent -> "Absent" },
					inLastSeen = vPc.lastSeen?.let { relativeTime(it) },
					inOnRename = { vRename = vPc },
					inOnForget = { vForget = vPc }
				)
				ErrorLine(inState.refusals[vPc.id])
			}
		}
		vMessage?.let { (vText, vOk) -> if (vOk) BasicText(vText, style = vType.body.copy(color = BridgeColors.success)) else ErrorLine(vText) }
		SilverButton(
			inLabel   = if (vBusy) "Appairage…" else "Appairer un PC",
			inOnClick = {
				vMessage = null
				if (hasWifiNamePermission(vContext)) vScanning = true
				else vAsk.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
			},
			inEnabled = !vBusy
		)
		BasicText("Sur le PC, ouvrez BridgePlus et choisissez « Appairer un téléphone » : un QR code s'affiche. Les deux appareils doivent être sur le même Wi-Fi.", style = vType.bodySmall)
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
