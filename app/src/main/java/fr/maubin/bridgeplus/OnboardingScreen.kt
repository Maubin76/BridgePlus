package fr.maubin.bridgeplus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.core.net.toUri
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.components.SilverButton
import fr.maubin.bridgeplus.ui.components.TextLink
import fr.maubin.bridgeplus.ui.screens.KeyScreen
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType

/*
	Étape de l'assistant : titre en deux tons, une phrase d'explication,
	l'action, et l'état « déjà accordé ».
	muted   : début du titre, en gris
	strong  : fin du titre, en blanc
	text    : explication en une phrase
	action  : intitulé du bouton
*/
private data class Step(
	val muted  : String,
	val strong : String,
	val text   : String,
	val action : String
)

private val kSteps = listOf(
	Step("Les ", "notifications.", "Pour la notification discrète « Relié à votre PC », les fichiers reçus et la sonnerie.", "Autoriser"),
	Step("Le Wi-Fi de ", "la maison.", "BridgePlus lit le nom du Wi-Fi pour ne fonctionner que chez vous ; Android range cette lecture sous « localisation », choisissez « Toujours ». Aucune position n'est lue.", "Autoriser"),
	Step("En ", "arrière-plan.", "Pour que la liaison reprenne seule à votre retour ; la consommation reste négligeable.", "Autoriser"),
	Step("Vos notifications ", "sur le PC.", "Android 16 protège ce réglage : Infos de l'appli › ⋮ › Autoriser les paramètres restreints, puis Accès aux notifications › BridgePlus.", "Infos de l'appli"),
	Step("Votre téléphone et votre PC, ", "reliés.", "Sur le PC, ouvrez BridgePlus et choisissez « Appairer un téléphone » : un QR code s'affiche.", "Appairer un PC")
)																						// étapes de l'assistant

/*
	Assistant de premier lancement, sur le décor de l'accueil : cinq étapes
	courtes (notifications, Wi-Fi de la maison, arrière-plan, accès aux
	notifications, appairage), chacune facultative (« Passer ») et
	expliquée en une phrase ; une étape déjà accordée le dit. Relançable
	depuis les réglages.
	inResumes  : retours à l'écran (relecture des autorisations)
	inOnFinish : assistant terminé (vrai : ouvrir l'appairage)
*/
@SuppressLint("BatteryLife")
@Composable
fun OnboardingScreen(
	inResumes  : Int,
	inOnFinish : (Boolean) -> Unit
)
{
	val vType    = bridgeType()																		// styles de texte
	val vContext = LocalContext.current																// contexte
	var vIndex   by rememberSaveable { mutableIntStateOf(0) }										// étape affichée
	var vAsked   by remember { mutableIntStateOf(0) }												// réponses reçues (relecture)
	val vAsk     = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vAsked++ }	// autorisations
	val vStep    = kSteps[vIndex]																	// étape
	val vDone    = remember(vIndex, inResumes, vAsked) {
		when (vIndex)
		{
			0    -> hasNotificationPermission(vContext)
			1    -> hasBackgroundWifiName(vContext)
			2    -> isBatteryUnrestricted(vContext)
			3    -> NoticeListener.granted(vContext)
			else -> false
		}
	}																								// étape déjà accordée
	/*
		Passe à l'étape suivante, ou termine.
		inToDevices : terminer en ouvrant l'appairage
	*/
	fun next(inToDevices: Boolean = false)
	{
		if (vIndex < kSteps.lastIndex && !inToDevices) vIndex++ else inOnFinish(inToDevices)
	}
	/*
		Action de l'étape.
	*/
	fun act()
	{
		runCatching {
			when (vIndex)
			{
				0    -> vAsk.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
				1    -> vAsk.launch(if (hasWifiNamePermission(vContext)) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
				2    -> vContext.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${vContext.packageName}".toUri()))
				3    -> vContext.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${vContext.packageName}".toUri()))
				else -> next(inToDevices = true)
			}
		}
	}
	KeyScreen {
		BasicText("Étape ${vIndex + 1} sur ${kSteps.size}", style = vType.bodySmall)
		BasicText(
			buildAnnotatedString {
				withStyle(SpanStyle(color = BridgeColors.textMuted)) { append(vStep.muted) }
				append(vStep.strong)
			},
			style = vType.display
		)
		BasicText(vStep.text, style = vType.bodySmall)
		if (vDone) BasicText("Déjà autorisé.", style = vType.body.copy(color = BridgeColors.success))
		if (vDone) SilverButton("Continuer", { next() })
		else
		{
			SilverButton(vStep.action, ::act)
			if (vIndex == 3) GhostButton("Accès aux notifications", { runCatching { vContext.startActivity(NoticeListener.accessIntent(vContext)) } })
			if (vIndex == 1 && hasWifiNamePermission(vContext)) BasicText("Localisation accordée pendant l'utilisation : choisissez maintenant « Toujours autoriser ».", style = vType.bodySmall)
		}
		TextLink(if (vIndex == kSteps.lastIndex) "Plus tard" else "Passer", { next() }, Modifier)
	}
}
