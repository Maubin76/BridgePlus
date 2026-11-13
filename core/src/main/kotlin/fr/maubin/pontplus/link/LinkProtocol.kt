package fr.maubin.pontplus.link

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

const val kLinkProtocol   = 1														// version du protocole entre appareils
const val kLinkPort       = 47841													// port TCP du serveur du PC (KeePlus : 47821)
const val kDiscoveryPort  = 47840													// port UDP de la découverte (KeePlus : 47820)
const val kMaxFrameBytes  = 64 * 1024 * 1024										// taille maximale d'une trame (64 Mo, comme KeePlus)
const val kMaxClockSkewMs = 5L * 60 * 1000											// écart d'horloge toléré entre deux appareils (5 min)

const val kKindHello = "hello"														// présentation, en clair (diagnostic, avant appairage)
const val kKindOpen  = "open"														// ouverture d'une session, scellée avec la clé de la paire
const val kKindError = "error"														// refus, en clair

const val kPlatformWindows = "windows"												// plateforme d'un PC
const val kPlatformAndroid = "android"												// plateforme d'un téléphone

private const val kWireEnvelope : Byte = 1											// trame enveloppe (JSON) : présentation, ouverture, refus
private const val kWireData     : Byte = 2											// trame d'une session : numéro d'ordre puis contenu scellé

internal val kLinkJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }	// messages entre appareils (versions voisines tolérées)

/*
	Présentation d'un appareil.
	id         : identifiant de l'appareil (tiré au hasard à la première ouverture)
	name       : nom affiché (« PC du bureau », « Pixel de Mathieu »)
	platform   : kPlatformWindows ou kPlatformAndroid
	appVersion : version de l'appli
	features   : fonctions connues de l'appli (presse-papiers, fichiers…), pour
	             que deux versions voisines s'entendent
*/
@Serializable
data class DeviceInfo(
	val id         : String,
	val name       : String,
	val platform   : String,
	val appVersion : String,
	val features   : List<String> = emptyList()
)

/*
	Trame enveloppe, échangée avant la session : présentation et refus en
	clair, ouverture scellée avec la clé de la paire.
	protocol : version du protocole
	from     : identifiant de l'expéditeur
	kind     : nature (kKindHello, kKindOpen, kKindError)
	time     : date d'envoi (ms) ; protège contre le rejeu
	nonce    : numéro à usage unique (base64) ; protège contre le rejeu
	body     : contenu : présentation (hello), ouverture scellée en base64 (open), raison (error)
*/
@Serializable
data class LinkFrame(
	val protocol : Int    = kLinkProtocol,
	val from     : String,
	val kind     : String,
	val time     : Long   = 0,
	val nonce    : String = "",
	val body     : String = ""
)

/*
	Trame lue sur une connexion : enveloppe, ou trame scellée d'une session.
*/
sealed interface Wire
{
	/*
		Trame enveloppe.
		frame : trame
	*/
	data class Envelope(val frame: LinkFrame) : Wire

	/*
		Trame d'une session.
		seq    : numéro d'ordre annoncé
		sealed : contenu scellé avec la clé de la session
	*/
	class Data(
		val seq    : Long,
		val sealed : ByteArray
	) : Wire
}

/*
	Données associées authentifiées d'une ouverture scellée : expéditeur,
	date, numéro à usage unique et, pour une réponse, numéro de la demande
	(une réponse ne peut servir qu'à sa demande).
	inFrame   : trame
	inRequest : numéro de la demande (réponse) ; vide pour une demande
*/
internal fun sealedAad(
	inFrame   : LinkFrame,
	inRequest : String = ""
): ByteArray = "${inFrame.protocol}|${inFrame.kind}|${inFrame.from}|${inFrame.time}|${inFrame.nonce}|$inRequest".toByteArray(Charsets.UTF_8)

/*
	Écrit une trame enveloppe : longueur sur 4 octets, nature sur 1 octet,
	puis le JSON.
	inOut   : flux de sortie
	inFrame : trame
*/
fun writeFrame(
	inOut   : OutputStream,
	inFrame : LinkFrame
) = writeWire(inOut, kWireEnvelope, kLinkJson.encodeToString(LinkFrame.serializer(), inFrame).toByteArray(Charsets.UTF_8))

/*
	Écrit une trame de session : longueur sur 4 octets, nature sur 1 octet,
	numéro d'ordre sur 8 octets, puis le contenu scellé.
	inOut    : flux de sortie
	inSeq    : numéro d'ordre
	inSealed : contenu scellé
*/
fun writeData(
	inOut    : OutputStream,
	inSeq    : Long,
	inSealed : ByteArray
) = writeWire(inOut, kWireData, java.nio.ByteBuffer.allocate(8).putLong(inSeq).array(), inSealed)

/*
	Écrit une trame : longueur, nature, puis les morceaux du corps.
	inOut   : flux de sortie
	inKind  : nature
	inParts : morceaux du corps
*/
private fun writeWire(
	inOut   : OutputStream,
	inKind  : Byte,
	vararg inParts : ByteArray
)
{
	val vSize = 1 + inParts.sumOf { it.size }											// longueur annoncée
	require(vSize <= kMaxFrameBytes) { "trame trop grande" }
	DataOutputStream(inOut).apply {
		writeInt(vSize)
		writeByte(inKind.toInt())
		inParts.forEach { write(it) }
		flush()
	}
}

/*
	Lit une trame enveloppe ; lève IOException si le flux est coupé, si la
	trame est trop grande, illisible ou n'est pas une enveloppe.
	inIn : flux d'entrée
*/
fun readFrame(inIn: InputStream): LinkFrame =
	(readWire(inIn) as? Wire.Envelope)?.frame ?: throw IOException("trame inattendue")

/*
	Lit une trame ; lève IOException si le flux est coupé, si la trame est
	trop grande ou illisible.
	inIn : flux d'entrée
*/
fun readWire(inIn: InputStream): Wire
{
	val vData = DataInputStream(inIn)													// flux
	val vSize = try { vData.readInt() } catch (inError: EOFException) { throw IOException("connexion fermée") }	// longueur annoncée
	if (vSize !in 2..kMaxFrameBytes) throw IOException("trame de taille invalide : $vSize")
	val vKind = vData.readByte()														// nature
	val vBody = ByteArray(vSize - 1).also { vData.readFully(it) }						// corps
	return when (vKind)
	{
		kWireEnvelope -> Wire.Envelope(runCatching { kLinkJson.decodeFromString(LinkFrame.serializer(), String(vBody, Charsets.UTF_8)) }.getOrElse { throw IOException("trame illisible") })
		kWireData     ->
		{
			if (vBody.size < 8) throw IOException("trame de session trop courte")
			Wire.Data(java.nio.ByteBuffer.wrap(vBody, 0, 8).long, vBody.copyOfRange(8, vBody.size))
		}
		else          -> throw IOException("trame de nature inconnue : $vKind")
	}
}

/*
	Garde contre le rejeu des ouvertures : une ouverture n'est acceptée que
	datée de moins de cinq minutes d'écart et avec un numéro jamais vu dans
	cette fenêtre.
	inNow : horloge (ms)
*/
class ReplayGuard(private val inNow: () -> Long = System::currentTimeMillis)
{
	private val fSeen = LinkedHashMap<String, Long>()								// numéros vus et leur date

	/*
		Vrai si le message est neuf (et le retient) ; faux s'il est trop ancien,
		trop en avance ou déjà vu.
		inTime  : date du message (ms)
		inNonce : numéro à usage unique
	*/
	@Synchronized
	fun accept(
		inTime  : Long,
		inNonce : String
	): Boolean
	{
		val vNow = inNow()																// maintenant
		fSeen.entries.removeIf { vNow - it.value > 2 * kMaxClockSkewMs }
		if (kotlin.math.abs(vNow - inTime) > kMaxClockSkewMs || inNonce.isEmpty() || inNonce in fSeen) return false
		fSeen[inNonce] = inTime
		return true
	}
}

/*
	Garde de l'ordre des trames d'une session : chaque trame doit porter le
	numéro qui suit la précédente (0, 1, 2…). Une trame rejouée, déplacée ou
	supprimée en route est donc repérée.
*/
class SequenceGuard
{
	private var fNext = 0L																// numéro attendu

	/*
		Numéro attendu pour la prochaine trame.
	*/
	val expected: Long get() = fNext

	/*
		Vrai si la trame porte le numéro attendu (et passe au suivant).
		inSeq : numéro de la trame
	*/
	@Synchronized
	fun accept(inSeq: Long): Boolean
	{
		if (inSeq != fNext) return false
		fNext++
		return true
	}
}

/*
	Refus d'un appareil distant, ou réponse impossible à vérifier.
	inMessage : raison
*/
class LinkException(inMessage: String) : IOException(inMessage)
