package fr.maubin.bridgeplus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import fr.maubin.bridgeplus.ui.components.InfoCard
import fr.maubin.bridgeplus.ui.icons.LineIcons

/*
	Encadrés des autorisations qui manquent pour que la liaison tienne seule,
	chacun expliqué en une phrase et suivi de son action. Rien n'est affiché
	quand tout est accordé.
	inResumes : retours à l'écran (relecture des autorisations)
	BatteryLife : la demande d'exclusion de l'optimisation est la seule façon
	de relancer la liaison au retour à la maison ; l'appli n'est pas publiée
	sur le Play Store.
*/
@SuppressLint("BatteryLife")
@Composable
fun MissingPermissions(inResumes: Int)
{
	val vContext   = LocalContext.current														// contexte
	var vAsked     by remember { mutableIntStateOf(0) }											// réponses reçues (relecture)
	val vLocation  = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vAsked++ }	// localisation et notifications
	val vAlways    = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vAsked++ }	// localisation « toujours »
	val vMissing   = remember(inResumes, vAsked) {
		listOfNotNull(
			"wifi".takeIf { !hasWifiNamePermission(vContext) },
			"toujours".takeIf { hasWifiNamePermission(vContext) && !hasBackgroundWifiName(vContext) },
			"batterie".takeIf { !isBatteryUnrestricted(vContext) },
			"notifications".takeIf { !hasNotificationPermission(vContext) },
			"ecoute".takeIf { vContext.bridgeApp.settings.value.noticesSend && !NoticeListener.granted(vContext) }
		)
	}																							// autorisations manquantes
	Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
		for (vWhat in vMissing)
		{
			when (vWhat)
			{
				"wifi"          -> InfoCard(LineIcons.Wifi, "BridgePlus lit le nom du Wi-Fi pour reconnaître la maison ; Android range cette lecture sous « localisation ». Aucune position n'est lue.", "Autoriser", {
					vLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
				})
				"toujours"      -> InfoCard(LineIcons.Wifi, "Pour reconnaître le Wi-Fi de la maison appli fermée, choisissez « Toujours autoriser » pour la localisation.", "Ouvrir le réglage", {
					vAlways.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
				})
				"batterie"      -> InfoCard(LineIcons.Info, "Pour que la liaison reprenne seule à votre retour, laissez BridgePlus fonctionner en arrière-plan. Sa consommation reste négligeable.", "Autoriser", {
					vContext.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${vContext.packageName}".toUri()))
				})
				"ecoute"        -> InfoCard(
					inIcon      = LineIcons.Info,
					inText      = "Pour voir les notifications du téléphone sur le PC, BridgePlus doit pouvoir les lire. Android 16 protège ce réglage pour une appli installée hors du Play Store : 1. Infos de l'appli › ⋮ (en haut à droite) › Autoriser les paramètres restreints ; 2. Accès aux notifications › BridgePlus › Autoriser.",
					inAction    = "Infos de l'appli",
					inOnAction  = { vContext.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${vContext.packageName}".toUri())) },
					inAction2   = "Accès aux notifications",
					inOnAction2 = { runCatching { vContext.startActivity(NoticeListener.accessIntent(vContext)) }.onFailure { vContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
				)
				"notifications" -> InfoCard(LineIcons.Info, "Autorisez les notifications pour voir « Relié à <PC> » et, plus tard, les textes et fichiers reçus.", "Autoriser", {
					vLocation.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
				})
			}
		}
	}
}
