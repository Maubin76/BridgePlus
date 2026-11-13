package fr.maubin.pontplus.link

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val kLinkKeyBytes       = 32													// clé secrète d'une paire (AES-256)
const val kChallengeBytes     = 32													// défi de chaque côté à l'ouverture d'une session
private const val kNonceBytes = 12													// numéro à usage unique d'AES-GCM
private const val kTagBits    = 128													// étiquette d'authentification

private val kRandom = SecureRandom()												// aléa cryptographique

/*
	Nouvelle clé secrète d'une paire PC ↔ téléphone, tirée au hasard.
*/
fun newLinkKey(): ByteArray = ByteArray(kLinkKeyBytes).also { kRandom.nextBytes(it) }

/*
	Octets au hasard (numéros à usage unique, identifiants, défis).
	inSize : nombre d'octets
*/
fun randomBytes(inSize: Int): ByteArray = ByteArray(inSize).also { kRandom.nextBytes(it) }

/*
	Octets en texte (base64 sans remplissage, sûr dans un QR code et en JSON).
	inBytes : octets
*/
fun toBase64(inBytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(inBytes)

/*
	Octets lus d'un texte base64 ; null s'il est illisible.
	inText : texte
*/
fun fromBase64(inText: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(inText) }.getOrNull()

/*
	Chiffrement authentifié des messages d'une paire (AES-GCM) : un message
	scellé ne peut être lu ni modifié sans la clé ; les données associées
	(expéditeur, date, numéro à usage unique) sont authentifiées sans être
	chiffrées, ce qui empêche de rejouer un message dans un autre contexte.
	Sert à l'ouverture d'une session ; la suite passe par SessionCipher.
	inKey : clé secrète de la paire
*/
class LinkCipher(inKey: ByteArray)
{
	private val fKey = SecretKeySpec(inKey.copyOf(), "AES")							// clé AES

	init
	{
		require(inKey.size == kLinkKeyBytes) { "clé de ${inKey.size} octets" }
	}

	/*
		Scelle un message : numéro à usage unique suivi du chiffré.
		inPlain : message en clair
		inAad   : données associées authentifiées
	*/
	fun seal(
		inPlain : ByteArray,
		inAad   : ByteArray
	): ByteArray
	{
		val vNonce  = randomBytes(kNonceBytes)											// numéro à usage unique
		val vCipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, fKey, GCMParameterSpec(kTagBits, vNonce)); updateAAD(inAad) }	// chiffreur
		return vNonce + vCipher.doFinal(inPlain)
	}

	/*
		Ouvre un message scellé ; null s'il a été modifié, scellé avec une autre
		clé ou d'autres données associées.
		inSealed : message scellé
		inAad    : données associées attendues
	*/
	fun open(
		inSealed : ByteArray,
		inAad    : ByteArray
	): ByteArray?
	{
		if (inSealed.size < kNonceBytes + kTagBits / 8) return null
		return try
		{
			val vCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
				init(Cipher.DECRYPT_MODE, fKey, GCMParameterSpec(kTagBits, inSealed, 0, kNonceBytes))
				updateAAD(inAad)
			}																		// déchiffreur
			vCipher.doFinal(inSealed, kNonceBytes, inSealed.size - kNonceBytes)
		}
		catch (inError: AEADBadTagException)
		{
			null
		}
	}
}

/*
	Clés d'une session, une par sens : ce qu'envoie le téléphone (vers le PC)
	et ce qu'envoie le PC (vers le téléphone).
	toPc    : clé des trames envoyées par le téléphone
	toPhone : clé des trames envoyées par le PC
*/
class SessionKeys(
	val toPc    : ByteArray,
	val toPhone : ByteArray
)

/*
	Clés d'une session, dérivées de la clé de la paire et des deux défis tirés
	à l'ouverture (HKDF-SHA256, RFC 5869) : chaque session a ses propres clés,
	différentes dans chaque sens. Une trame enregistrée dans une session ne
	s'ouvre dans aucune autre.
	inPairKey        : clé secrète de la paire
	inPhoneChallenge : défi du téléphone
	inPcChallenge    : défi du PC
*/
fun deriveSessionKeys(
	inPairKey        : ByteArray,
	inPhoneChallenge : ByteArray,
	inPcChallenge    : ByteArray
): SessionKeys
{
	val vPrk = hmac(inPhoneChallenge + inPcChallenge, inPairKey)						// clé pseudo-aléatoire (extraction)
	return SessionKeys(
		toPc    = hmac(vPrk, "PontPlus $kLinkProtocol vers le PC".toByteArray(Charsets.UTF_8) + byteArrayOf(1)),
		toPhone = hmac(vPrk, "PontPlus $kLinkProtocol vers le téléphone".toByteArray(Charsets.UTF_8) + byteArrayOf(1))
	)
}

/*
	HMAC-SHA256.
	inKey  : clé
	inData : données
*/
private fun hmac(
	inKey  : ByteArray,
	inData : ByteArray
): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(inKey, "HmacSHA256")); doFinal(inData) }

/*
	Chiffrement d'un sens d'une session (AES-GCM) : le numéro d'ordre de la
	trame sert de numéro à usage unique (jamais deux fois le même avec une clé
	de session) et de donnée associée. Une trame rejouée, déplacée ou venue
	d'une autre session ne s'ouvre pas.
	inKey : clé de ce sens de la session
*/
class SessionCipher(inKey: ByteArray)
{
	private val fKey = SecretKeySpec(inKey.copyOf(), "AES")							// clé AES

	init
	{
		require(inKey.size == kLinkKeyBytes) { "clé de ${inKey.size} octets" }
	}

	/*
		Scelle le contenu d'une trame.
		inSeq   : numéro d'ordre de la trame
		inPlain : contenu en clair
	*/
	fun seal(
		inSeq   : Long,
		inPlain : ByteArray
	): ByteArray =
		Cipher.getInstance("AES/GCM/NoPadding").run {
			init(Cipher.ENCRYPT_MODE, fKey, GCMParameterSpec(kTagBits, nonce(inSeq)))
			updateAAD(seqBytes(inSeq))
			doFinal(inPlain)
		}

	/*
		Ouvre le contenu d'une trame ; null s'il a été modifié, scellé avec une
		autre clé ou sous un autre numéro d'ordre.
		inSeq    : numéro d'ordre attendu
		inSealed : contenu scellé
	*/
	fun open(
		inSeq    : Long,
		inSealed : ByteArray
	): ByteArray?
	{
		if (inSealed.size < kTagBits / 8) return null
		return try
		{
			Cipher.getInstance("AES/GCM/NoPadding").run {
				init(Cipher.DECRYPT_MODE, fKey, GCMParameterSpec(kTagBits, nonce(inSeq)))
				updateAAD(seqBytes(inSeq))
				doFinal(inSealed)
			}
		}
		catch (inError: AEADBadTagException)
		{
			null
		}
	}

	/*
		Numéro à usage unique d'une trame : 4 octets nuls puis le numéro d'ordre.
		inSeq : numéro d'ordre
	*/
	private fun nonce(inSeq: Long): ByteArray = ByteBuffer.allocate(kNonceBytes).putInt(0).putLong(inSeq).array()

	/*
		Numéro d'ordre sur 8 octets (donnée associée).
		inSeq : numéro d'ordre
	*/
	private fun seqBytes(inSeq: Long): ByteArray = ByteBuffer.allocate(8).putLong(inSeq).array()
}
