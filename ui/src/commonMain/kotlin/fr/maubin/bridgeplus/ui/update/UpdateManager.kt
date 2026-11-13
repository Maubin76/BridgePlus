package fr.maubin.bridgeplus.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.maubin.bridgeplus.update.GitHubReleaseSource
import fr.maubin.bridgeplus.update.ReleaseAsset
import fr.maubin.bridgeplus.update.ReleaseSource
import fr.maubin.bridgeplus.update.UpdateCheck
import fr.maubin.bridgeplus.update.UpdateOffer
import fr.maubin.bridgeplus.update.UpdatePlatform
import fr.maubin.bridgeplus.update.checkUpdate
import fr.maubin.bridgeplus.update.downloadAsset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/*
	Suite d'une installation confiée à la plateforme.
*/
sealed interface InstallStep
{
	/*
		Installation lancée : l'appli va être remplacée (fermée sur PC,
		installeur du système sur Android) ; un échec éventuel arrive par
		UpdateInstaller.failures.
	*/
	data object Started : InstallStep

	/*
		Autorisation à donner d'abord (Android : installer des applis) ; le
		fichier est gardé, l'installation reprend au retour dans l'appli.
		notice : explication
	*/
	data class NeedPermission(val notice: String) : InstallStep

	/*
		Fichier seulement enregistré (l'appli ne peut pas se remplacer elle-même).
		message : où le trouver
	*/
	data class Saved(val message: String) : InstallStep
}

/*
	Ce que chaque plateforme fournit pour ses mises à jour.
*/
interface UpdateInstaller
{
	/*
		Plateforme (choix du fichier de la release).
	*/
	val platform: UpdatePlatform

	/*
		Version installée ; null si inconnue (pas de vérification possible).
	*/
	val version: String?

	/*
		Vérification au lancement permise (faux pour une version de
		développement, que la version publiée ne pourrait pas remplacer).
	*/
	val autoCheck: Boolean

	/*
		Explication propre à l'appareil, montrée dans la fenêtre ; null sans.
	*/
	val notice: String?

	/*
		Libellé du bouton principal (« Mettre à jour », « Télécharger »).
	*/
	val action: String

	/*
		Fichier où télécharger un fichier de release.
		inAsset : fichier de la release
	*/
	fun target(inAsset: ReleaseAsset): File

	/*
		Vrai si une installation en attente d'autorisation peut maintenant reprendre.
	*/
	fun canResume(): Boolean = true

	/*
		Installe le fichier téléchargé et vérifié.
		inFile : fichier
	*/
	suspend fun install(inFile: File): InstallStep

	/*
		Reçoit les échecs d'installation signalés après coup (installeur du
		système).
		inOnFailure : message d'échec
	*/
	fun onFailure(inOnFailure: (String) -> Unit) {}
}

/*
	État de la fenêtre de mise à jour.
	offer    : mise à jour proposée
	progress : avancement du téléchargement, de 0 à 1 ; null avant ou après
	error    : échec ; null sans
	notice   : explication propre à l'appareil ; null sans
	action   : libellé du bouton principal
*/
data class UpdateUiState(
	val offer    : UpdateOffer,
	val progress : Float?  = null,
	val error    : String? = null,
	val notice   : String? = null,
	val action   : String  = "Mettre à jour"
)

/*
	Mises à jour de l'appli, communes au téléphone et au PC : vérification au
	lancement (dernière release GitHub), fenêtre, téléchargement vérifié par
	son empreinte, installation par la plateforme ; « Plus tard », « Ignorer
	cette version », vérification à la demande.
	fInstaller : installation propre à la plateforme
	fEnabled   : vérification au lancement activée (réglage)
	fIgnored   : version ignorée par l'utilisateur ; vide sans
	fOnIgnore  : mémorise une version ignorée
	fSource    : source de la dernière release
*/
class UpdateManager(
	private val fInstaller : UpdateInstaller,
	private val fEnabled   : () -> Boolean,
	private val fIgnored   : () -> String,
	private val fOnIgnore  : (String) -> Unit,
	private val fSource    : ReleaseSource = GitHubReleaseSource()
)
{
	private val fScope    = CoroutineScope(SupervisorJob() + Dispatchers.Main)		// vérifications, téléchargements
	private var fDownload : Job?  = null													// téléchargement en cours
	private var fFile     : File? = null													// fichier prêt, en attente d'autorisation
	private var fChecked  = false															// vérification du lancement déjà faite

	/*
		Fenêtre de mise à jour ; null sans.
	*/
	var state: UpdateUiState? by mutableStateOf(null)
		private set

	/*
		Version installée ; null si inconnue.
	*/
	val version: String? get() = fInstaller.version

	init
	{
		fInstaller.onFailure { vMessage -> state = state?.copy(progress = null, error = "La mise à jour a échoué : $vMessage.") }
	}

	/*
		Vérification du lancement, une seule fois : fenêtre si une version plus
		récente (non ignorée) est publiée ; rien sinon, même si GitHub est
		injoignable.
	*/
	fun checkAtLaunch()
	{
		if (fChecked) return
		fChecked = true
		val vVersion = fInstaller.version ?: return											// version installée
		if (!fInstaller.autoCheck || !fEnabled()) return
		fScope.launch {
			(checkUpdate(fSource, vVersion, fInstaller.platform, fIgnored()) as? UpdateCheck.Available)?.let { state = offerState(it.offer) }
		}
	}

	/*
		Vérification demandée (réglages) : fenêtre, même pour une version
		ignorée, ou message « à jour » ou « injoignable ».
		inOnMessage : reçoit le message à montrer
	*/
	fun checkNow(inOnMessage: (String) -> Unit)
	{
		val vVersion = fInstaller.version ?: return inOnMessage("Version de l'appli inconnue : vérification impossible.")	// version installée
		fScope.launch {
			when (val vCheck = checkUpdate(fSource, vVersion, fInstaller.platform))
			{
				is UpdateCheck.Available -> state = offerState(vCheck.offer)
				UpdateCheck.UpToDate     -> inOnMessage("BridgePlus est à jour (version $vVersion).")
				UpdateCheck.Unreachable  -> inOnMessage("Vérification impossible : GitHub est injoignable.")
			}
		}
	}

	/*
		« Mettre à jour » : télécharge le fichier (empreinte vérifiée), puis le
		confie à la plateforme ; « Réessayer » après un échec.
	*/
	fun update()
	{
		val vState = state ?: return															// fenêtre affichée
		if (vState.action == kCloseAction) return later()
		if (fDownload?.isActive == true) return
		fFile?.takeIf { it.exists() }?.let { return install(it) }
		state = vState.copy(progress = 0f, error = null)
		fDownload = fScope.launch {
			try
			{
				val vFile = downloadAsset(vState.offer.asset, fInstaller.target(vState.offer.asset)) { vProgress ->
					state = state?.copy(progress = vProgress)
				}																			// fichier vérifié
				fFile = vFile
				install(vFile)
			}
			catch (vCancel: CancellationException)
			{
				state = state?.copy(progress = null)
				throw vCancel
			}
			catch (vError: Exception)
			{
				state = state?.copy(progress = null, error = "La mise à jour a échoué : ${vError.message ?: "erreur inconnue"}.")
			}
		}
	}

	/*
		Retour dans l'appli : une installation qui attendait l'autorisation reprend.
	*/
	fun onResume()
	{
		val vFile = fFile ?: return																// fichier en attente
		if (state?.action == kInstallAction && fInstaller.canResume()) install(vFile)
	}

	/*
		Interrompt le téléchargement ; la fenêtre reste ouverte.
	*/
	fun cancel()
	{
		fDownload?.cancel()
		fDownload = null
		state = state?.copy(progress = null)
	}

	/*
		Ferme la fenêtre ; la question reviendra au prochain lancement.
	*/
	fun later()
	{
		cancel()
		state = null
	}

	/*
		Ne plus proposer la version de la fenêtre.
	*/
	fun ignore()
	{
		val vVersion = state?.offer?.version ?: return										// version ignorée
		later()
		fOnIgnore(vVersion)
	}

	/*
		Confie le fichier à la plateforme et suit la réponse.
		inFile : fichier vérifié
	*/
	private fun install(inFile: File)
	{
		state = state?.copy(progress = 1f, error = null)
		fScope.launch {
			try
			{
				when (val vStep = fInstaller.install(inFile))
				{
					InstallStep.Started           -> Unit
					is InstallStep.NeedPermission -> state = state?.copy(progress = null, notice = vStep.notice, action = kInstallAction)
					is InstallStep.Saved          -> state = state?.copy(progress = null, notice = vStep.message, action = kCloseAction)
				}
			}
			catch (vError: Exception)
			{
				state = state?.copy(progress = null, error = "La mise à jour a échoué : ${vError.message ?: "erreur inconnue"}.")
			}
		}
	}

	/*
		État de la fenêtre pour une mise à jour proposée.
		inOffer : mise à jour
	*/
	private fun offerState(inOffer: UpdateOffer): UpdateUiState =
		UpdateUiState(inOffer, notice = fInstaller.notice, action = fInstaller.action)
}

private const val kInstallAction = "Installer"										// bouton après l'autorisation d'installer
private const val kCloseAction   = "Fermer"											// bouton une fois le fichier enregistré (téléchargement seul)
