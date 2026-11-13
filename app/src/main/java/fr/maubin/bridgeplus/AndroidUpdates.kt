package fr.maubin.bridgeplus

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.provider.Settings
import androidx.core.net.toUri
import fr.maubin.bridgeplus.ui.update.InstallStep
import fr.maubin.bridgeplus.ui.update.UpdateInstaller
import fr.maubin.bridgeplus.update.ReleaseAsset
import fr.maubin.bridgeplus.update.UpdatePlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val kInstallAction  = "fr.maubin.bridgeplus.action.INSTALL_STATUS"				// résultat de l'installeur du système
private const val kNeedPermission = "Android doit d'abord autoriser BridgePlus à installer des applis : activez « Autoriser depuis cette source » dans l'écran qui s'ouvre, puis revenez ici."	// explication de l'autorisation

/*
	Mises à jour du téléphone : APK de la release téléchargé dans le cache,
	puis confié à l'installeur du système (session PackageInstaller).
	L'autorisation « installer des applis » est demandée la première fois ;
	ensuite, sur Android 12 et plus, l'installation se fait sans écran de
	confirmation. Vérification au lancement en version publiée seulement
	(une version de développement n'a pas la même signature).
	fContext : contexte de l'application
*/
class AndroidUpdateInstaller(private val fContext: Context) : UpdateInstaller
{
	/*
		Fichier attendu dans la release : APK.
	*/
	override val platform: UpdatePlatform = UpdatePlatform.ANDROID

	/*
		Version installée.
	*/
	override val version: String = BuildConfig.VERSION_NAME

	/*
		Vérification au lancement : version publiée seulement.
	*/
	override val autoCheck: Boolean = !BuildConfig.DEBUG

	/*
		Pas d'explication particulière au départ.
	*/
	override val notice: String? = null

	/*
		Bouton principal.
	*/
	override val action: String = "Mettre à jour"

	/*
		APK téléchargé dans le cache de l'appli.
		inAsset : fichier de la release
	*/
	override fun target(inAsset: ReleaseAsset): File = File(File(fContext.cacheDir, "mises-a-jour"), inAsset.name)

	/*
		L'installation peut reprendre si l'autorisation a été donnée.
	*/
	override fun canResume(): Boolean = fContext.packageManager.canRequestPackageInstalls()

	/*
		Installe l'APK si l'autorisation est donnée ; sinon ouvre l'écran
		d'Android où la donner.
		inFile : APK vérifié
	*/
	override suspend fun install(inFile: File): InstallStep
	{
		if (!fContext.packageManager.canRequestPackageInstalls())
		{
			fContext.startActivity(
				Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${fContext.packageName}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			)
			return InstallStep.NeedPermission(kNeedPermission)
		}
		withContext(Dispatchers.IO) {
			val vInstaller = fContext.packageManager.packageInstaller										// installeur du système
			val vParams    = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
				setAppPackageName(fContext.packageName)
				setSize(inFile.length())
				setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
			}																							// réglages de la session
			val vId = vInstaller.createSession(vParams)														// session
			vInstaller.openSession(vId).use { vSession ->
				vSession.openWrite("BridgePlus.apk", 0, inFile.length()).use { vOutput ->
					inFile.inputStream().use { it.copyTo(vOutput) }
					vSession.fsync(vOutput)
				}
				val vIntent  = Intent(kInstallAction).setPackage(fContext.packageName)							// rappel du résultat
				val vPending = PendingIntent.getBroadcast(fContext, vId, vIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)	// rempli par l'installeur
				vSession.commit(vPending.intentSender)
			}
		}
		return InstallStep.Started
	}

	/*
		Transmet les échecs rapportés par l'installeur du système.
		inOnFailure : message d'échec
	*/
	override fun onFailure(inOnFailure: (String) -> Unit)
	{
		CoroutineScope(SupervisorJob() + Dispatchers.Main).launch { InstallFailures.flow.collect { inOnFailure(it) } }
	}
}

/*
	Canal des échecs d'installation, du récepteur vers l'interface.
*/
object InstallFailures
{
	/*
		Messages d'échec (le dernier est rejoué à un abonné qui arrive après lui).
	*/
	val flow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
}

/*
	Reçoit le résultat de l'installeur du système : confirmation demandée
	(ouvre son écran) ou échec (transmis à l'interface). Un succès n'arrive
	guère : Android ferme l'appli pour la remplacer.
*/
class UpdateInstallReceiver : BroadcastReceiver()
{
	/*
		Résultat reçu.
		inContext : contexte
		inIntent  : résultat (statut, message, écran de confirmation)
	*/
	override fun onReceive(
		inContext : Context,
		inIntent  : Intent
	)
	{
		when (val vStatus = inIntent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE))	// statut
		{
			PackageInstaller.STATUS_PENDING_USER_ACTION ->
			{
				@Suppress("DEPRECATION")
				val vConfirm = inIntent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)	// écran de confirmation
				vConfirm?.let { inContext.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
			}
			PackageInstaller.STATUS_SUCCESS             -> Unit
			else                                        -> InstallFailures.flow.tryEmit(installMessage(vStatus, inIntent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
		}
	}
}

/*
	Message lisible d'un échec de l'installeur.
	inStatus  : statut
	inMessage : message technique ; null sans
*/
internal fun installMessage(
	inStatus  : Int,
	inMessage : String?
): String = when (inStatus)
{
	PackageInstaller.STATUS_FAILURE_ABORTED      -> "installation annulée"
	PackageInstaller.STATUS_FAILURE_CONFLICT     -> "l'APK n'est pas signé avec la même clé que l'appli installée (version de développement ?)"
	PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK incompatible avec ce téléphone"
	PackageInstaller.STATUS_FAILURE_STORAGE      -> "pas assez de place sur le téléphone"
	PackageInstaller.STATUS_FAILURE_BLOCKED      -> "installation bloquée par le téléphone"
	else                                         -> inMessage ?: "échec de l'installation"
}
