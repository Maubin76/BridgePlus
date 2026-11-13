package fr.maubin.bridgeplus.link

import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val kHandshakeTimeoutMs = 20_000										// délai maximal de l'ouverture d'une session

/*
	Demande d'ouverture d'une session (téléphone → PC), scellée avec la clé de
	la paire.
	device    : présentation du téléphone
	challenge : défi du téléphone (base64), pour dériver les clés de la session
	pairing   : réseaux de la maison vus par le téléphone, pour un appairage ;
	            null pour une session ordinaire
*/
@Serializable
data class OpenRequest(
	val device    : DeviceInfo,
	val challenge : String,
	val pairing   : List<NetworkId>? = null
)

/*
	Réponse du PC à l'ouverture, scellée avec la même clé et liée à la demande.
	device    : présentation du PC
	challenge : défi du PC (base64)
	home      : réseaux de la maison connus du PC (après un appairage)
*/
@Serializable
data class OpenReply(
	val device    : DeviceInfo,
	val challenge : String,
	val home      : List<NetworkId> = emptyList()
)

/*
	Ouverture reçue d'un appareil, déjà ouverte et vérifiée.
	peer      : présentation de l'appareil
	pairing   : réseaux de la maison d'une demande d'appairage ; null sinon
	withOffer : vrai si la demande est scellée avec la clé de l'offre
	            d'appairage en cours (et non celle d'un appareil appairé)
	address   : adresse de l'appareil
*/
data class OpenCall(
	val peer      : DeviceInfo,
	val pairing   : List<NetworkId>?,
	val withOffer : Boolean,
	val address   : InetAddress
)

/*
	Décision sur une ouverture.
	error : raison du refus ; null si la session est acceptée
	home  : réseaux de la maison à renvoyer à l'appareil
*/
data class OpenVerdict(
	val error : String?         = null,
	val home  : List<NetworkId> = emptyList()
)

/*
	Serveur des sessions (PC) : écoute sur les adresses données seulement (le
	réseau de la maison), répond en clair à une présentation (hello), ouvre
	une session pour une ouverture scellée avec la clé d'un appareil appairé
	ou celle de l'offre d'appairage en cours, refuse tout le reste. Envoie un
	battement de cœur à toutes les sessions ouvertes. Tourne sur des fils en
	tâche de fond.
	inInfo        : présentation de ce PC
	inKeyOf       : clé secrète d'un appareil appairé ; null s'il ne l'est pas
	inOfferKey    : clé de l'offre d'appairage en cours ; null sans offre valable
	inAccept      : décision sur une ouverture (appairage, appareil connu)
	inOnSession   : session ouverte ; rend son destinataire
	inOnHello     : appelé à chaque présentation reçue (diagnostic)
	inPingEveryMs : intervalle du battement de cœur
	inDeadAfterMs : délai sans nouvelles au bout duquel une session est morte
	inNow         : horloge (ms)
*/
class LinkHub(
	private val inInfo        : () -> DeviceInfo,
	private val inKeyOf       : (String) -> ByteArray?,
	private val inOfferKey    : () -> ByteArray?,
	private val inAccept      : (OpenCall) -> OpenVerdict,
	private val inOnSession   : (LinkSession, OpenCall) -> SessionListener,
	private val inOnHello     : (DeviceInfo, InetAddress) -> Unit = { _, _ -> },
	private val inPingEveryMs : Long = kPingEveryMs,
	private val inDeadAfterMs : Long = kPcDeadAfterMs,
	private val inNow         : () -> Long = System::currentTimeMillis
)
{
	private val fGuard    = ReplayGuard(inNow)										// garde contre le rejeu des ouvertures
	private val fSockets  = linkedMapOf<String, ServerSocket>()						// écoutes, par adresse
	private val fSessions = ConcurrentHashMap.newKeySet<LinkSession>()				// sessions ouvertes
	private var fPool     : ExecutorService? = null									// fils des connexions
	private var fTimer    : ScheduledExecutorService? = null						// battement de cœur

	/*
		Port d'écoute (celui de la première adresse) ; 0 à l'arrêt.
	*/
	val port: Int @Synchronized get() = fSockets.values.firstOrNull()?.localPort ?: 0

	/*
		Adresses d'écoute.
	*/
	val addresses: List<String> @Synchronized get() = fSockets.keys.toList()

	/*
		Écoute sur ces adresses exactement : les écoutes des adresses retirées
		sont fermées (leurs sessions aussi), les nouvelles ouvertes. Rend les
		adresses impossibles à écouter, avec la raison. Une liste vide arrête
		l'écoute.
		inAddresses : adresses d'écoute
		inPort      : port ; 0 pour un port libre quelconque (tests)
	*/
	@Synchronized
	fun bind(
		inAddresses : List<InetAddress>,
		inPort      : Int = kLinkPort
	): Map<String, String>
	{
		val vWanted = inAddresses.associateBy { it.hostAddress }						// adresses voulues
		fSockets.keys.filter { it !in vWanted }.forEach { vGone ->
			runCatching { fSockets.remove(vGone)?.close() }
			fSessions.filter { it.isOpen && runCatching { InetAddress.getByName(vGone) }.getOrNull()?.let { vAddr -> it.localAddress == vAddr } == true }
				.forEach { it.close("le PC n'écoute plus sur $vGone") }
		}
		if (vWanted.isEmpty()) { shutdown(); return emptyMap() }
		val vPool   = fPool ?: Executors.newCachedThreadPool { inTask -> Thread(inTask, "BridgePlus-lien").apply { isDaemon = true } }.also { fPool = it }	// fils
		if (fTimer == null) fTimer = Executors.newSingleThreadScheduledExecutor { inTask -> Thread(inTask, "BridgePlus-battement").apply { isDaemon = true } }.also {
			it.scheduleWithFixedDelay(::beat, inPingEveryMs, inPingEveryMs, TimeUnit.MILLISECONDS)
		}
		val vErrors = linkedMapOf<String, String>()										// écoutes impossibles
		for ((vName, vAddress) in vWanted)
		{
			if (vName in fSockets) continue
			val vSocket = try
			{
				ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(vAddress, inPort)) }
			}
			catch (inError: IOException)
			{
				vErrors[vName] = inError.message ?: "port déjà utilisé"; continue
			}																			// écoute
			fSockets[vName] = vSocket
			vPool.execute {
				while (!vSocket.isClosed)
				{
					val vClient = runCatching { vSocket.accept() }.getOrNull() ?: break	// connexion entrante
					vPool.execute { serve(vClient) }
				}
			}
		}
		return vErrors
	}

	/*
		Arrête l'écoute et ferme toutes les sessions.
	*/
	@Synchronized
	fun stop() = shutdown()

	/*
		Ferme les écoutes, les sessions, le battement de cœur et les fils.
	*/
	private fun shutdown()
	{
		fSockets.values.forEach { runCatching { it.close() } }
		fSockets.clear()
		fSessions.toList().forEach { it.close("le PC a arrêté l'écoute") }
		fTimer?.shutdownNow()
		fPool?.shutdown()
		fTimer = null
		fPool  = null
	}

	/*
		Battement de cœur : un Ping à chaque session ouverte. Une session qui ne
		répond plus se ferme d'elle-même (délai de lecture).
	*/
	private fun beat()
	{
		val vNow = inNow()																// maintenant
		fSessions.toList().forEach { it.send(LinkMessage.Ping(vNow)) }
	}

	/*
		Répond à une connexion : présentation, ou ouverture d'une session.
		inClient : connexion
	*/
	private fun serve(inClient: Socket)
	{
		try
		{
			inClient.soTimeout = kHandshakeTimeoutMs
			val vFrame = readFrame(inClient.getInputStream())							// trame reçue
			if (vFrame.protocol != kLinkProtocol) { refuse(inClient, "protocole ${vFrame.protocol} inconnu (ce PC parle le protocole $kLinkProtocol)"); return }
			when (vFrame.kind)
			{
				kKindHello ->
				{
					runCatching { kLinkJson.decodeFromString(DeviceInfo.serializer(), vFrame.body) }.getOrNull()?.let { inOnHello(it, inClient.inetAddress) }
					writeFrame(inClient.getOutputStream(), LinkFrame(from = inInfo().id, kind = kKindHello, body = kLinkJson.encodeToString(DeviceInfo.serializer(), inInfo())))
					inClient.close()
				}
				kKindOpen  -> open(inClient, vFrame)
				else       -> refuse(inClient, "demande « ${vFrame.kind} » inconnue")
			}
		}
		catch (inError: IOException)
		{
			refuse(inClient, inError.message ?: "trame illisible")
		}
	}

	/*
		Ouvre une session : vérifie la demande scellée (clé de l'appareil, ou de
		l'offre d'appairage), la date et le numéro à usage unique, fait décider
		le propriétaire, répond, puis remet la session.
		inClient : connexion
		inFrame  : demande d'ouverture
	*/
	private fun open(
		inClient : Socket,
		inFrame  : LinkFrame
	)
	{
		val vKnown   = inKeyOf(inFrame.from)												// clé d'un appareil appairé
		val vOffer   = inOfferKey()															// clé de l'offre en cours
		if (vKnown == null && vOffer == null) { refuse(inClient, "appareil non appairé"); return }
		val vSealed  = fromBase64(inFrame.body) ?: run { refuse(inClient, "demande illisible"); return }	// demande scellée
		val vAad     = sealedAad(inFrame)														// données associées
		var vKey     : ByteArray? = null													// clé qui ouvre la demande
		var vPlain   : ByteArray? = null													// demande en clair
		var vByOffer = false																// ouverte avec la clé de l'offre
		for ((vCandidate, vIsOffer) in listOfNotNull(vKnown?.let { it to false }, vOffer?.let { it to true }))
		{
			vPlain = LinkCipher(vCandidate).open(vSealed, vAad) ?: continue
			vKey = vCandidate; vByOffer = vIsOffer; break
		}
		if (vKey == null || vPlain == null) { refuse(inClient, if (vKnown == null) "appareil non appairé" else "demande illisible : clé différente"); return }
		if (!fGuard.accept(inFrame.time, inFrame.nonce)) { refuse(inClient, "demande trop ancienne ou déjà reçue (vérifiez l'heure des deux appareils)"); return }
		val vRequest = runCatching { kLinkJson.decodeFromString(OpenRequest.serializer(), String(vPlain, Charsets.UTF_8)) }.getOrNull()	// demande
		val vPhoneChallenge = vRequest?.challenge?.let(::fromBase64)?.takeIf { it.size == kChallengeBytes }	// défi du téléphone
		if (vRequest == null || vPhoneChallenge == null || vRequest.device.id != inFrame.from) { refuse(inClient, "demande illisible"); return }
		val vCall    = OpenCall(vRequest.device, vRequest.pairing, vByOffer, inClient.inetAddress)	// ouverture vérifiée
		val vVerdict = runCatching { inAccept(vCall) }.getOrElse { OpenVerdict(it.message ?: "erreur du PC") }	// décision
		if (vVerdict.error != null) { refuse(inClient, vVerdict.error); return }
		val vPcChallenge = randomBytes(kChallengeBytes)											// défi du PC
		val vReply   = LinkFrame(from = inInfo().id, kind = kKindOpen, time = inNow(), nonce = toBase64(randomBytes(12)))	// réponse, sans contenu
		val vBody    = kLinkJson.encodeToString(OpenReply.serializer(), OpenReply(inInfo(), toBase64(vPcChallenge), vVerdict.home)).toByteArray(Charsets.UTF_8)	// réponse en clair
		writeFrame(inClient.getOutputStream(), vReply.copy(body = toBase64(LinkCipher(vKey).seal(vBody, sealedAad(vReply, inFrame.nonce)))))
		val vKeys    = deriveSessionKeys(vKey, vPhoneChallenge, vPcChallenge)					// clés de la session
		val vSession = LinkSession(inClient, vRequest.device, vKeys.toPhone, vKeys.toPc, inDeadAfterMs, inNow)	// session
		val vOwner   = inOnSession(vSession, vCall)													// destinataire
		fSessions += vSession
		vSession.start(object : SessionListener
		{
			override fun onMessage(inSession: LinkSession, inMessage: LinkMessage) = vOwner.onMessage(inSession, inMessage)
			override fun onChunk(inSession: LinkSession, inChunk: ByteArray) = vOwner.onChunk(inSession, inChunk)
			override fun onClosed(inSession: LinkSession, inReason: String)
			{
				fSessions -= inSession
				vOwner.onClosed(inSession, inReason)
			}
		})
	}

	/*
		Refuse une connexion : trame de refus en clair, puis fermeture.
		inClient  : connexion
		inMessage : raison
	*/
	private fun refuse(
		inClient  : Socket,
		inMessage : String
	)
	{
		runCatching { writeFrame(inClient.getOutputStream(), LinkFrame(from = inInfo().id, kind = kKindError, body = inMessage)) }
		runCatching { inClient.close() }
	}
}
