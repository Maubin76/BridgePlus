package fr.maubin.bridgeplus.update

import fr.maubin.bridgeplus.link.kLinkProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

const val kLatestReleaseUrl = "https://api.github.com/repos/Maubin76/BridgePlus/releases/latest"	// dernière release publiée
private const val kAgent     = "BridgePlus"									// agent demandé par l'API GitHub
private const val kTimeoutMs = 5_000											// délai maximal de la vérification
private const val kReadMs    = 30_000											// délai maximal entre deux paquets du téléchargement
private val kProtocolMark    = Regex("""Protocole de liaison\s*:\s*(\d+)""")	// repère du protocole écrit par la CI dans les notes
private val kJson            = Json { ignoreUnknownKeys = true }				// lecteur de la réponse de GitHub

/*
	Fichier joint à une release.
	name   : nom (BridgePlus-1.3.apk, BridgePlus-1.3-windows.zip)
	size   : taille en octets
	url    : adresse de téléchargement
	sha256 : empreinte SHA-256 en hexadécimal, donnée par GitHub ; null si absente
*/
data class ReleaseAsset(
	val name   : String,
	val size   : Long,
	val url    : String,
	val sha256 : String?
)

/*
	Release publiée sur GitHub.
	version  : numéro, sans le « v » du tag (1.3)
	protocol : version du protocole de liaison qu'elle parle ; null si les notes ne la donnent pas
	assets   : fichiers joints
*/
data class Release(
	val version  : String,
	val protocol : Int?,
	val assets   : List<ReleaseAsset>
)

/*
	Plateforme qui cherche sa mise à jour, et le fichier qui lui correspond.
	fSuffix : fin du nom du fichier de la release
*/
enum class UpdatePlatform(val fSuffix: String)
{
	ANDROID(".apk"),
	WINDOWS("-windows.zip")
}

/*
	Mise à jour à proposer.
	version       : version disponible
	current       : version installée
	asset         : fichier à télécharger
	protocolChanged : vrai si la nouvelle version change le protocole de liaison (les deux appareils doivent alors être mis à jour)
*/
data class UpdateOffer(
	val version       : String,
	val current       : String,
	val asset         : ReleaseAsset,
	val protocolChanged : Boolean
)

/*
	Lit la réponse de l'API GitHub pour une release ; null si elle est
	illisible ou sans tag.
	inJson : texte de la réponse
*/
fun parseRelease(inJson: String): Release?
{
	val vRoot = runCatching { kJson.parseToJsonElement(inJson).jsonObject }.getOrNull() ?: return null	// objet de la release
	val vTag  = vRoot.text("tag_name")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: return null	// numéro
	val vAssets = runCatching { vRoot["assets"]?.jsonArray.orEmpty() }.getOrDefault(emptyList()).mapNotNull { inItem ->
		val vAsset = inItem as? JsonObject ?: return@mapNotNull null								// fichier joint
		ReleaseAsset(
			name   = vAsset.text("name") ?: return@mapNotNull null,
			size   = vAsset["size"]?.jsonPrimitive?.longOrNull ?: 0L,
			url    = vAsset.text("browser_download_url") ?: return@mapNotNull null,
			sha256 = vAsset.text("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
		)
	}																					// fichiers joints lisibles
	val vProtocol = vRoot.text("body")?.let { kProtocolMark.find(it)?.groupValues?.get(1)?.toIntOrNull() }	// protocole annoncé
	return Release(vTag, vProtocol, vAssets)
}

/*
	Texte d'un champ d'un objet JSON ; null s'il est absent ou n'est pas un texte.
	inKey : nom du champ
*/
private fun JsonObject.text(inKey: String): String? = runCatching { this[inKey]?.jsonPrimitive?.contentOrNull }.getOrNull()

/*
	Vrai si une version est plus récente qu'une autre, nombre par nombre
	(1.10 est plus récente que 1.9 ; 1.2 et 1.2.0 sont égales). Un « v » en
	tête est accepté ; une partie non numérique compte pour 0.
	inCandidate : version proposée
	inCurrent   : version installée
*/
fun isNewer(
	inCandidate : String,
	inCurrent   : String
): Boolean
{
	fun parts(inVersion: String) = inVersion.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }	// nombres
	val vNew = parts(inCandidate)														// version proposée
	val vOld = parts(inCurrent)															// version installée
	for (inIndex in 0 until maxOf(vNew.size, vOld.size))
	{
		val vA = vNew.getOrElse(inIndex) { 0 }											// nombre proposé
		val vB = vOld.getOrElse(inIndex) { 0 }											// nombre installé
		if (vA != vB) return vA > vB
	}
	return false
}

/*
	Mise à jour à proposer, ou null : release pas plus récente, version
	ignorée par l'utilisateur, ou aucun fichier pour cette plateforme.
	inRelease  : dernière release
	inCurrent  : version installée
	inPlatform : plateforme
	inIgnored  : version que l'utilisateur a choisi d'ignorer ; vide sans
	inProtocol : protocole de liaison de la version installée
*/
fun updateOffer(
	inRelease  : Release,
	inCurrent  : String,
	inPlatform : UpdatePlatform,
	inIgnored  : String = "",
	inProtocol : Int    = kLinkProtocol
): UpdateOffer?
{
	if (!isNewer(inRelease.version, inCurrent) || inRelease.version == inIgnored) return null
	val vAsset = inRelease.assets.firstOrNull { it.name.endsWith(inPlatform.fSuffix) } ?: return null	// fichier de la plateforme
	return UpdateOffer(inRelease.version, inCurrent, vAsset, inRelease.protocol != null && inRelease.protocol != inProtocol)
}

/*
	Taille lisible d'un fichier : « 5 Mo », « 78 Mo », « 850 ko ».
	inBytes : taille en octets
*/
fun sizeLabel(inBytes: Long): String =
	if (inBytes >= 1_000_000) "${(inBytes + 500_000) / 1_000_000} Mo" else "${maxOf(1, (inBytes + 500) / 1_000)} ko"

/*
	Source de la dernière release.
*/
fun interface ReleaseSource
{
	/*
		Dernière release ; null si elle est introuvable ou illisible. Lève
		IOException si le service est injoignable.
	*/
	suspend fun latest(): Release?
}

/*
	Dernière release du dépôt GitHub de l'appli (API publique, sans jeton : le dépôt doit être public).
	Seule l'adresse IP de l'appareil est connue de GitHub : rien n'est envoyé.
	inUrl : adresse de l'API (remplaçable pour les tests)
*/
class GitHubReleaseSource(private val inUrl: String = kLatestReleaseUrl) : ReleaseSource
{
	/*
		Dernière release.
	*/
	override suspend fun latest(): Release? =
		withContext(Dispatchers.IO) {
			val vConnection = URI(inUrl).toURL().openConnection() as HttpURLConnection		// connexion
			try
			{
				vConnection.connectTimeout = kTimeoutMs
				vConnection.readTimeout    = kTimeoutMs
				vConnection.setRequestProperty("User-Agent", kAgent)
				vConnection.setRequestProperty("Accept", "application/vnd.github+json")
				when (val vCode = vConnection.responseCode)
				{
					HttpURLConnection.HTTP_OK        -> parseRelease(vConnection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
					HttpURLConnection.HTTP_NOT_FOUND -> null
					else                             -> throw IOException("réponse $vCode de GitHub")
				}
			}
			finally
			{
				vConnection.disconnect()
			}
		}
}

/*
	Résultat d'une vérification.
*/
sealed interface UpdateCheck
{
	// Mise à jour disponible.
	data class Available(val offer: UpdateOffer) : UpdateCheck

	// Rien de plus récent (ou version ignorée).
	data object UpToDate : UpdateCheck

	// Service injoignable ou réponse illisible.
	data object Unreachable : UpdateCheck
}

/*
	Vérifie s'il existe une mise à jour, sans jamais lever d'exception.
	inSource   : source de la release
	inCurrent  : version installée
	inPlatform : plateforme
	inIgnored  : version ignorée ; vide sans
*/
suspend fun checkUpdate(
	inSource   : ReleaseSource,
	inCurrent  : String,
	inPlatform : UpdatePlatform,
	inIgnored  : String = ""
): UpdateCheck
{
	val vRelease = try { inSource.latest() } catch (inError: IOException) { null } ?: return UpdateCheck.Unreachable	// dernière release
	return updateOffer(vRelease, inCurrent, inPlatform, inIgnored)?.let { UpdateCheck.Available(it) } ?: UpdateCheck.UpToDate
}

/*
	Mise à jour à proposer, ou null (rien de nouveau, service injoignable,
	réponse illisible).
	inSource   : source de la release
	inCurrent  : version installée
	inPlatform : plateforme
	inIgnored  : version ignorée ; vide sans
*/
suspend fun findUpdate(
	inSource   : ReleaseSource,
	inCurrent  : String,
	inPlatform : UpdatePlatform,
	inIgnored  : String = ""
): UpdateOffer? = (checkUpdate(inSource, inCurrent, inPlatform, inIgnored) as? UpdateCheck.Available)?.offer

/*
	Télécharge le fichier d'une mise à jour et vérifie son empreinte. Le
	fichier est écrit à côté de la cible puis renommé : la cible n'existe que
	complète et vérifiée. Annulable (coroutine) ; en cas d'échec ou
	d'annulation, rien ne reste sur le disque.
	inAsset      : fichier à télécharger
	inTarget     : fichier de destination
	inOnProgress : avancement, de 0 à 1
*/
suspend fun downloadAsset(
	inAsset      : ReleaseAsset,
	inTarget     : File,
	inOnProgress : (Float) -> Unit = {}
): File = withContext(Dispatchers.IO) {
	val vPart       = File(inTarget.path + ".part")								// fichier en cours
	val vConnection = URI(inAsset.url).toURL().openConnection() as HttpURLConnection		// connexion
	try
	{
		vConnection.connectTimeout = kTimeoutMs
		vConnection.readTimeout    = kReadMs
		vConnection.setRequestProperty("User-Agent", kAgent)
		vConnection.setRequestProperty("Accept", "application/octet-stream")
		if (vConnection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("réponse ${vConnection.responseCode} au téléchargement")
		val vTotal  = inAsset.size.takeIf { it > 0 } ?: vConnection.contentLengthLong	// taille attendue
		val vDigest = MessageDigest.getInstance("SHA-256")							// empreinte calculée au fil de l'eau
		var vRead   = 0L																// octets reçus
		inTarget.parentFile?.mkdirs()
		vConnection.inputStream.use { inInput ->
			vPart.outputStream().use { inOutput ->
				val vBuffer = ByteArray(64 * 1024)										// paquet
				while (true)
				{
					coroutineContext.ensureActive()
					val vCount = inInput.read(vBuffer)									// octets du paquet
					if (vCount < 0) break
					inOutput.write(vBuffer, 0, vCount)
					vDigest.update(vBuffer, 0, vCount)
					vRead += vCount
					if (vTotal > 0) inOnProgress((vRead.toFloat() / vTotal).coerceAtMost(1f))
				}
			}
		}
		val vHash = vDigest.digest().joinToString("") { "%02x".format(it) }			// empreinte du fichier reçu
		if (inAsset.sha256 != null && vHash != inAsset.sha256) throw IOException("fichier altéré (empreinte différente)")
		if (inAsset.size > 0 && vRead != inAsset.size) throw IOException("fichier incomplet ($vRead octets sur ${inAsset.size})")
		inTarget.delete()
		if (!vPart.renameTo(inTarget)) throw IOException("impossible d'écrire ${inTarget.name}")
		inOnProgress(1f)
		inTarget
	}
	catch (inError: Throwable)
	{
		vPart.delete()
		throw inError
	}
	finally
	{
		vConnection.disconnect()
	}
}
