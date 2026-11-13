package fr.maubin.bridgeplus.transfer

import fr.maubin.bridgeplus.link.randomBytes
import fr.maubin.bridgeplus.link.toBase64
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

private const val kMaxNameChars = 180												// longueur maximale d'un nom de fichier reçu
private val kReserved = Regex("^(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?$", RegexOption.IGNORE_CASE)	// noms réservés de Windows
private val kForbidden = Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]")						// caractères interdits (Windows) et séparateurs de chemin

/*
	Fichier à envoyer.
*/
interface FileSource
{
	/*
		Nom du fichier.
	*/
	val name: String

	/*
		Taille (octets).
	*/
	val size: Long

	/*
		Type du contenu ; vide s'il est inconnu.
	*/
	val mime: String

	/*
		Ouvre le contenu (une seule fois).
	*/
	fun open(): InputStream
}

/*
	Fichier du disque à envoyer (PC, tests).
	inFile : fichier
*/
class DiskSource(private val inFile: File) : FileSource
{
	override val name: String get() = inFile.name
	override val size: Long get() = inFile.length()
	override val mime: String get() = runCatching { Files.probeContentType(inFile.toPath()) }.getOrNull().orEmpty()
	override fun open(): InputStream = inFile.inputStream()
}

/*
	Fichier en cours de réception, écrit sous un nom provisoire invisible :
	il n'apparaît sous son vrai nom qu'une fois vérifié (commit).
*/
interface IncomingFile
{
	/*
		Flux d'écriture du contenu.
	*/
	val output: OutputStream

	/*
		Range le fichier sous son nom définitif (sans jamais écraser) ; rend
		son emplacement (chemin ou adresse), pour l'ouvrir.
	*/
	fun commit(): String

	/*
		Abandonne le fichier : rien ne reste.
	*/
	fun abort()
}

/*
	Dossier de réception d'une plateforme.
*/
interface FileSink
{
	/*
		Espace libre (octets).
	*/
	fun freeBytes(): Long

	/*
		Prépare la réception d'un fichier.
		inName : nom proposé (déjà nettoyé)
		inSize : taille annoncée
		inMime : type du contenu
	*/
	fun create(
		inName : String,
		inSize : Long,
		inMime : String
	): IncomingFile
}

/*
	Dossier de réception du disque (PC, tests) : écriture dans
	« .bridgeplus-<hasard>.part », caché, dans le dossier même, puis
	renommage sous un nom libre (« photo (2).jpg »).
	fDir : dossier de réception (relu à chaque fichier : il peut changer dans les réglages)
*/
class FolderSink(private val fDir: () -> File) : FileSink
{
	override fun freeBytes(): Long = fDir().apply { mkdirs() }.usableSpace

	override fun create(
		inName : String,
		inSize : Long,
		inMime : String
	): IncomingFile
	{
		val vDir  = fDir().apply { mkdirs() }														// dossier
		val vTemp = File(vDir, ".bridgeplus-${toBase64(randomBytes(9))}.part")					// fichier provisoire
		val vOut  = BufferedOutputStream(vTemp.outputStream(), 256 * 1024)						// écriture
		runCatching { Files.setAttribute(vTemp.toPath(), "dos:hidden", true) }
		return object : IncomingFile
		{
			override val output: OutputStream = vOut

			override fun commit(): String
			{
				vOut.close()
				runCatching { Files.setAttribute(vTemp.toPath(), "dos:hidden", false) }
				while (true)
				{
					val vFinal = File(vDir, uniqueName(inName) { File(vDir, it).exists() })				// nom libre
					try
					{
						Files.move(vTemp.toPath(), vFinal.toPath(), StandardCopyOption.ATOMIC_MOVE)
						return vFinal.absolutePath
					}
					catch (inError: java.nio.file.FileAlreadyExistsException)
					{
						continue
					}
				}
			}

			override fun abort()
			{
				runCatching { vOut.close() }
				vTemp.delete()
			}
		}
	}
}

/*
	Nom de fichier sûr, à partir du nom envoyé par l'autre appareil : sans
	chemin (seul le dernier élément compte), sans caractère interdit par
	Windows ni de contrôle, sans point ni espace à la fin, jamais un nom
	réservé (CON, NUL…), 180 caractères au plus ; « fichier » à défaut.
	inName : nom reçu
*/
fun safeFileName(inName: String): String
{
	val vLast  = inName.substringAfterLast('/').substringAfterLast('\\')						// dernier élément du chemin
	var vClean = kForbidden.replace(vLast, "_").trim().trimEnd('.', ' ')						// caractères permis
	if (vClean.isEmpty() || vClean.all { it == '.' }) vClean = "fichier"
	if (kReserved.matches(vClean)) vClean = "_$vClean"
	if (vClean.length > kMaxNameChars)
	{
		val vExt = vClean.substringAfterLast('.', "").takeIf { it.length in 1..12 }?.let { ".$it" }.orEmpty()	// extension gardée
		vClean = vClean.take(kMaxNameChars - vExt.length) + vExt
	}
	return vClean
}

/*
	Nom libre dans un dossier : le nom tel quel s'il est libre, sinon
	« nom (2).ext », « nom (3).ext »… Jamais d'écrasement.
	inName  : nom voulu
	inTaken : vrai si un nom est déjà pris
*/
fun uniqueName(
	inName  : String,
	inTaken : (String) -> Boolean
): String
{
	if (!inTaken(inName)) return inName
	val vDot  = inName.lastIndexOf('.').takeIf { it > 0 } ?: inName.length					// début de l'extension
	val vBase = inName.substring(0, vDot)														// nom sans extension
	val vExt  = inName.substring(vDot)															// extension (avec le point)
	var vN    = 2																				// numéro essayé
	while (inTaken("$vBase ($vN)$vExt")) vN++
	return "$vBase ($vN)$vExt"
}

/*
	Taille lisible : « 812 o », « 4,2 Ko », « 18,6 Mo », « 3,1 Go ».
	inBytes : taille (octets)
*/
fun sizeText(inBytes: Long): String =
	when
	{
		inBytes < 1024L                -> "$inBytes o"
		inBytes < 1024L * 1024         -> String.format(Locale.FRENCH, "%.1f Ko", inBytes / 1024.0)
		inBytes < 1024L * 1024 * 1024  -> String.format(Locale.FRENCH, "%.1f Mo", inBytes / (1024.0 * 1024))
		else                           -> String.format(Locale.FRENCH, "%.1f Go", inBytes / (1024.0 * 1024 * 1024))
	}
