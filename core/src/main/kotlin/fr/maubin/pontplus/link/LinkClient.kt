package fr.maubin.pontplus.link

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

private const val kConnectTimeoutMs = 4_000										// délai de connexion (réseau local)
private const val kReplyTimeoutMs   = 10_000										// délai de réponse à une présentation ou une ouverture

/*
	Session ouverte avec un PC, pas encore démarrée (voir LinkSession.start).
	session : session
	reply   : réponse du PC à l'ouverture
*/
class OpenedSession(
	val session : LinkSession,
	val reply   : OpenReply
)

/*
	Client des échanges avec un PC (téléphone).
	inSelf : présentation de cet appareil
	inNow  : horloge (ms)
*/
class LinkClient(
	private val inSelf : DeviceInfo,
	private val inNow  : () -> Long = System::currentTimeMillis
)
{
	/*
		Présentation : rend celle du PC (en clair, sans appairage) ; lève une
		exception si le PC est injoignable ou refuse.
		inHost : adresse du PC
		inPort : port du PC
	*/
	fun hello(
		inHost : String,
		inPort : Int = kLinkPort
	): DeviceInfo =
		Socket().use { vSocket ->
			vSocket.connect(InetSocketAddress(inHost, inPort), kConnectTimeoutMs)
			vSocket.soTimeout = kReplyTimeoutMs
			writeFrame(vSocket.getOutputStream(), LinkFrame(from = inSelf.id, kind = kKindHello, body = kLinkJson.encodeToString(DeviceInfo.serializer(), inSelf)))
			val vReply = readFrame(vSocket.getInputStream())									// réponse
			if (vReply.kind != kKindHello) throw LinkException(vReply.body.ifEmpty { "réponse inattendue" })
			runCatching { kLinkJson.decodeFromString(DeviceInfo.serializer(), vReply.body) }.getOrElse { throw LinkException("présentation illisible") }
		}

	/*
		Ouvre une session avec un PC : demande scellée avec la clé de la paire
		(ou de l'offre d'appairage), réponse vérifiée (PC attendu, scellée avec
		la même clé, liée à cette demande), puis clés de la session dérivées.
		Lève une exception si le PC est injoignable, refuse, ou si la réponse
		ne se vérifie pas.
		inHost        : adresse du PC
		inPort        : port du PC
		inPeerId      : identifiant attendu du PC
		inKey         : clé secrète de la paire
		inPairing     : réseaux de la maison pour un appairage ; null sinon
		inDeadAfterMs : délai sans nouvelles au bout duquel la session est morte
	*/
	fun open(
		inHost        : String,
		inPort        : Int,
		inPeerId      : String,
		inKey         : ByteArray,
		inPairing     : List<NetworkId>? = null,
		inDeadAfterMs : Long             = kPhoneDeadAfterMs
	): OpenedSession
	{
		val vSocket = Socket()																// connexion
		try
		{
			vSocket.connect(InetSocketAddress(inHost, inPort), kConnectTimeoutMs)
			vSocket.soTimeout = kReplyTimeoutMs
			val vCipher    = LinkCipher(inKey)												// chiffrement de la paire
			val vChallenge = randomBytes(kChallengeBytes)										// défi du téléphone
			val vBody      = kLinkJson.encodeToString(OpenRequest.serializer(), OpenRequest(inSelf, toBase64(vChallenge), inPairing)).toByteArray(Charsets.UTF_8)	// demande en clair
			val vFrame     = LinkFrame(from = inSelf.id, kind = kKindOpen, time = inNow(), nonce = toBase64(randomBytes(12)))	// demande, sans contenu
			writeFrame(vSocket.getOutputStream(), vFrame.copy(body = toBase64(vCipher.seal(vBody, sealedAad(vFrame)))))
			val vReply     = readFrame(vSocket.getInputStream())								// réponse
			if (vReply.kind == kKindError) throw LinkException(vReply.body)
			if (vReply.kind != kKindOpen || vReply.from != inPeerId) throw LinkException("réponse d’un autre appareil")
			val vPlain     = fromBase64(vReply.body)?.let { vCipher.open(it, sealedAad(vReply, vFrame.nonce)) } ?: throw LinkException("réponse illisible : clé différente")	// réponse en clair
			val vOpen      = runCatching { kLinkJson.decodeFromString(OpenReply.serializer(), String(vPlain, Charsets.UTF_8)) }.getOrElse { throw LinkException("réponse illisible") }	// réponse
			val vPcChallenge = fromBase64(vOpen.challenge)?.takeIf { it.size == kChallengeBytes } ?: throw LinkException("réponse illisible")	// défi du PC
			if (vOpen.device.id != inPeerId) throw LinkException("réponse d’un autre appareil")
			val vKeys      = deriveSessionKeys(inKey, vChallenge, vPcChallenge)					// clés de la session
			return OpenedSession(LinkSession(vSocket, vOpen.device, vKeys.toPc, vKeys.toPhone, inDeadAfterMs, inNow), vOpen)
		}
		catch (inError: IOException)
		{
			runCatching { vSocket.close() }
			throw inError
		}
	}
}
