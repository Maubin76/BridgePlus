package fr.maubin.pontplus.store

import fr.maubin.pontplus.link.LinkCipher
import java.io.File

/*
	Coffret des fichiers sensibles (clés des paires, historique) : scelle des
	octets avec une clé que l'appareil garde hors du fichier (Keystore sur
	Android, DPAPI sur Windows).
*/
interface SecretBox
{
	/*
		Octets scellés.
		inPlain : octets en clair
	*/
	fun seal(inPlain: ByteArray): ByteArray

	/*
		Octets en clair ; null s'ils ont été modifiés ou scellés avec une autre
		clé (autre appareil, autre compte Windows, clé détruite).
		inSealed : octets scellés
	*/
	fun open(inSealed: ByteArray): ByteArray?
}

/*
	Coffret à clé AES-256 connue (tests, ou clé déjà protégée par ailleurs).
	inKey : clé (32 octets)
*/
class KeyBox(inKey: ByteArray) : SecretBox
{
	private val fCipher = LinkCipher(inKey)											// chiffrement AES-GCM
	private val fAad    = "PontPlus fichier".toByteArray(Charsets.UTF_8)			// données associées

	override fun seal(inPlain: ByteArray): ByteArray = fCipher.seal(inPlain, fAad)

	override fun open(inSealed: ByteArray): ByteArray? = fCipher.open(inSealed, fAad)
}

/*
	Fichier texte scellé par un coffret. Un fichier illisible (clé perdue,
	fichier abîmé) est mis de côté sous « <nom>.illisible » plutôt qu'écrasé.
	inFile : fichier
	inBox  : coffret
*/
class SealedFile(
	private val inFile : File,
	private val inBox  : SecretBox
)
{
	/*
		Contenu en clair ; null si le fichier est absent ou illisible (mis de
		côté).
	*/
	@Synchronized
	fun read(): String?
	{
		if (!inFile.isFile) return null
		val vPlain = runCatching { inBox.open(inFile.readBytes()) }.getOrNull()			// contenu en clair
		if (vPlain == null)
		{
			val vAside = File(inFile.absoluteFile.parentFile, inFile.name + ".illisible")	// fichier mis de côté
			runCatching { vAside.delete(); inFile.renameTo(vAside) }
			return null
		}
		return String(vPlain, Charsets.UTF_8)
	}

	/*
		Enregistre le contenu, scellé.
		inText : contenu en clair
	*/
	@Synchronized
	fun write(inText: String) = writeAtomic(inFile, inBox.seal(inText.toByteArray(Charsets.UTF_8)))

	/*
		Supprime le fichier.
	*/
	@Synchronized
	fun delete()
	{
		inFile.delete()
	}
}
