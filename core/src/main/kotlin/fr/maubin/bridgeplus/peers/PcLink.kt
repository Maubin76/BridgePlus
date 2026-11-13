package fr.maubin.bridgeplus.peers

import fr.maubin.bridgeplus.link.Beacon
import fr.maubin.bridgeplus.link.DeviceInfo
import fr.maubin.bridgeplus.link.DiscoveryResponder
import fr.maubin.bridgeplus.link.LinkHub
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.LinkSession
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.OpenCall
import fr.maubin.bridgeplus.link.OpenVerdict
import fr.maubin.bridgeplus.link.PairingOffer
import fr.maubin.bridgeplus.link.SessionListener
import fr.maubin.bridgeplus.link.fromBase64
import fr.maubin.bridgeplus.link.isHomeNetwork
import fr.maubin.bridgeplus.link.kDiscoveryPort
import fr.maubin.bridgeplus.link.kLinkPort
import fr.maubin.bridgeplus.link.kPairValidityMs
import fr.maubin.bridgeplus.link.kPcDeadAfterMs
import fr.maubin.bridgeplus.link.kPingEveryMs
import fr.maubin.bridgeplus.link.kPlatformWindows
import fr.maubin.bridgeplus.link.newLinkKey
import fr.maubin.bridgeplus.link.toBase64
import fr.maubin.bridgeplus.store.SealedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
	Téléphone appairé avec ce PC.
	id          : identifiant du téléphone
	name        : son nom (tel qu'il se présente)
	alias       : nom choisi sur ce PC ; null pour garder le sien
	key         : clé secrète de la paire (base64)
	pairedAt    : instant de l'appairage (ms)
	lastSeen    : dernière connexion (ouverture ou fermeture d'une session, ms) ; null avant
	lastAddress : dernière adresse d'où il s'est connecté ; null avant
*/
@Serializable
data class PairedPhone(
	val id          : String,
	val name        : String,
	val alias       : String? = null,
	val key         : String,
	val pairedAt    : Long,
	val lastSeen    : Long?   = null,
	val lastAddress : String? = null
)
{
	// Nom affiché : celui choisi sur ce PC, sinon celui du téléphone.
	val label: String get() = alias ?: name
}

/*
	Fichier des appareils du PC (scellé : il contient les clés des paires).
	deviceId : identifiant de ce PC (tiré au hasard la première fois)
	phones   : téléphones appairés
	home     : réseaux de la maison (nom du Wi-Fi, box)
*/
@Serializable
data class PcPeers(
	val deviceId : String            = UUID.randomUUID().toString(),
	val phones   : List<PairedPhone> = emptyList(),
	val home     : List<NetworkId>   = emptyList()
)

/*
	État de la liaison du PC.
	running   : serveur à l'écoute
	onHome    : PC sur le réseau de la maison
	networks  : réseaux auxquels le PC est relié
	home      : réseaux de la maison
	phones    : téléphones appairés
	connected : téléphones reliés en ce moment (identifiants)
	offer     : offre d'appairage affichée (QR code) ; null sans
	paired    : téléphone appairé à l'instant (message) ; null sans
	error     : échec de l'écoute ; null sinon
*/
data class PcLinkState(
	val running   : Boolean           = false,
	val onHome    : Boolean           = false,
	val networks  : List<NetworkId>   = emptyList(),
	val home      : List<NetworkId>   = emptyList(),
	val phones    : List<PairedPhone> = emptyList(),
	val connected : Set<String>       = emptySet(),
	val offer     : PairingOffer?     = null,
	val paired    : String?           = null,
	val error     : String?           = null
)

/*
	Liaison du PC : serveur des sessions et répondeur de découverte,
	**seulement sur les interfaces du réseau de la maison** (et, le temps d'un
	appairage, sur celles du réseau actuel) ; appairage par une offre affichée
	en QR code, valable cinq minutes ; une session permanente par téléphone
	relié, avec battement de cœur.
	fFile        : fichier scellé des appareils
	fName        : nom de ce PC
	fVersion     : version de l'appli
	fNetworks    : réseaux du PC et adresses de leurs interfaces
	fOnMessage   : message reçu d'un téléphone
	fOnChunk     : morceau de fichier reçu d'un téléphone
	fOnClosed    : session d'un téléphone fermée (transferts interrompus)
	fNow         : horloge (ms)
	fPort        : port du serveur (0 : port libre, tests)
	fBeacon      : port de découverte (0 : port libre, tests)
	fPingEveryMs : intervalle du battement de cœur
	fDeadAfterMs : délai sans nouvelles au bout duquel une session est morte
*/
class PcLink(
	private val fFile        : SealedFile,
	private val fName        : String,
	private val fVersion     : String,
	private val fNetworks    : () -> List<LocalNetwork>,
	private val fOnMessage   : (PairedPhone, LinkMessage) -> Unit = { _, _ -> },
	private val fOnChunk     : (PairedPhone, ByteArray) -> Unit   = { _, _ -> },
	private val fOnClosed    : (PairedPhone) -> Unit              = {},
	private val fNow         : () -> Long = System::currentTimeMillis,
	private val fPort        : Int  = kLinkPort,
	private val fBeacon      : Int  = kDiscoveryPort,
	private val fPingEveryMs : Long = kPingEveryMs,
	private val fDeadAfterMs : Long = kPcDeadAfterMs
)
{
	private var fPeers    = load()															// appareils enregistrés
	private val fState    = MutableStateFlow(PcLinkState(phones = fPeers.phones, home = fPeers.home))	// état
	private val fSessions = ConcurrentHashMap<String, LinkSession>()						// sessions ouvertes, par téléphone
	private val fHub      = LinkHub(::info, ::keyOf, ::offerKey, ::accept, ::opened, inPingEveryMs = fPingEveryMs, inDeadAfterMs = fDeadAfterMs, inNow = fNow)	// serveur
	private var fFinder   : DiscoveryResponder? = null										// répondeur démarré
	private var fListen   : List<LocalNetwork> = emptyList()								// réseaux d'écoute

	/*
		État de la liaison.
	*/
	val state: StateFlow<PcLinkState> = fState

	/*
		Port du serveur ; 0 à l'arrêt.
	*/
	val port: Int get() = fHub.port

	/*
		Présentation de ce PC.
	*/
	fun info(): DeviceInfo = DeviceInfo(fPeers.deviceId, fName, kPlatformWindows, fVersion)

	/*
		Relit les réseaux du PC ; écoute sur les interfaces du réseau de la
		maison s'il y a un téléphone appairé, plus celles du réseau actuel
		pendant un appairage ; sinon arrête l'écoute. Une offre expirée est
		retirée.
	*/
	@Synchronized
	fun refresh()
	{
		val vNetworks = fNetworks()																// réseaux du PC
		val vOffer    = fState.value.offer?.takeIf { it.expires > fNow() }						// offre encore valable
		val vHome     = isHomeNetwork(vNetworks.map { it.id }, fPeers.home)						// sur le réseau de la maison
		val vListen   = vNetworks.filter { vOffer != null || (it.id in fPeers.home && fPeers.phones.isNotEmpty()) }	// réseaux d'écoute
		fState.update { it.copy(networks = vNetworks.map { vNet -> vNet.id }, onHome = vHome, offer = vOffer) }
		listen(vListen)
	}

	/*
		Affiche une offre d'appairage (QR code) valable cinq minutes ;
		l'écoute s'ouvre sur le réseau actuel le temps de l'appairage.
	*/
	@Synchronized
	fun startPairing()
	{
		val vNetworks = fNetworks()																// réseaux du PC
		listen(vNetworks)
		val vAddresses = vNetworks.flatMap { it.addresses }.map { it.address }.distinct()		// adresses du PC
		val vOffer     = PairingOffer(info().id, fName, vAddresses, port.takeIf { it > 0 } ?: fPort, newLinkKey(), fNow() + kPairValidityMs)	// offre
		fState.update { it.copy(offer = vOffer, paired = null, networks = vNetworks.map { vNet -> vNet.id }) }
	}

	/*
		Retire l'offre d'appairage affichée.
	*/
	@Synchronized
	fun cancelPairing()
	{
		fState.update { it.copy(offer = null) }
		refresh()
	}

	/*
		Oublie le message « téléphone appairé ».
	*/
	fun clearPaired() = fState.update { it.copy(paired = null) }

	/*
		Oublie un téléphone : sa clé est détruite, il est prévenu s'il est
		relié, puis sa session est fermée. Il ne peut plus se connecter.
		inId : téléphone
	*/
	@Synchronized
	fun forget(inId: String)
	{
		fSessions.remove(inId)?.let { it.send(LinkMessage.Forgotten); it.close("téléphone oublié") }
		drop(inId)
	}

	/*
		Renomme un téléphone sur ce PC.
		inId    : téléphone
		inAlias : nouveau nom ; vide pour reprendre le sien
	*/
	@Synchronized
	fun rename(
		inId    : String,
		inAlias : String
	) = update(inId) { it.copy(alias = inAlias.trim().ifEmpty { null }) }

	/*
		Ajoute un réseau aux réseaux de la maison, ou en retire un.
		inNetwork : réseau
		inAdd     : vrai pour l'ajouter
	*/
	@Synchronized
	fun setHome(
		inNetwork : NetworkId,
		inAdd     : Boolean
	)
	{
		save(fPeers.copy(home = if (inAdd) (fPeers.home + inNetwork).distinct() else fPeers.home - inNetwork))
		refresh()
	}

	/*
		Envoie un message à un téléphone relié ; faux s'il ne l'est pas.
		inId      : téléphone
		inMessage : message
	*/
	fun send(
		inId      : String,
		inMessage : LinkMessage
	): Boolean = fSessions[inId]?.send(inMessage) ?: false

	/*
		Envoie un morceau de fichier à un téléphone relié ; faux s'il ne l'est
		pas.
		inId    : téléphone
		inChunk : morceau
	*/
	fun sendChunk(
		inId    : String,
		inChunk : ByteArray
	): Boolean = fSessions[inId]?.sendChunk(inChunk) ?: false

	/*
		Envoie un message à tous les téléphones reliés ; rend les noms de ceux
		qui l'ont reçu.
		inMessage : message
	*/
	fun sendAll(inMessage: LinkMessage): List<String> =
		fSessions.entries.toList().filter { it.value.send(inMessage) }.mapNotNull { vEntry -> fPeers.phones.firstOrNull { it.id == vEntry.key }?.label }

	/*
		Arrête l'écoute et ferme les sessions (fermeture de l'appli).
	*/
	@Synchronized
	fun stop() = listen(emptyList())

	/*
		Écoute sur les interfaces de ces réseaux exactement ; aucune : arrêt.
		inNetworks : réseaux d'écoute
	*/
	private fun listen(inNetworks: List<LocalNetwork>)
	{
		fListen = inNetworks
		val vAddresses = inNetworks.flatMap { it.addresses }.mapNotNull { runCatching { InetAddress.getByName(it.address) }.getOrNull() }.distinct()	// adresses d'écoute
		val vErrors    = fHub.bind(vAddresses, fPort)												// écoutes impossibles
		if (vAddresses.isEmpty())
		{
			fFinder?.stop()
			fFinder = null
			fState.update { it.copy(running = false, error = null) }
			return
		}
		if (fFinder == null)
		{
			val vFinder = DiscoveryResponder({ Beacon(info().id, fName, port) }, ::fromHome)		// répondeur de découverte
			runCatching { vFinder.start(fBeacon) }.onSuccess { fFinder = vFinder }
		}
		val vError = vErrors.entries.firstOrNull()?.let { "Écoute impossible sur ${it.key} : ${it.value}. Une autre copie de BridgePlus est peut-être ouverte." }	// échec
		fState.update { it.copy(running = fHub.addresses.isNotEmpty(), error = vError) }
	}

	/*
		Vrai si une demande de découverte vient d'un sous-réseau d'écoute.
		inAddress : adresse de l'appareil qui demande
	*/
	private fun fromHome(inAddress: InetAddress): Boolean =
		fListen.any { vNet -> vNet.addresses.any { it.contains(inAddress) } }

	/*
		Clé d'un téléphone appairé ; null s'il ne l'est pas.
		inId : téléphone
	*/
	private fun keyOf(inId: String): ByteArray? = fPeers.phones.firstOrNull { it.id == inId }?.key?.let(::fromBase64)

	/*
		Clé de l'offre d'appairage en cours ; null sans offre valable.
	*/
	private fun offerKey(): ByteArray? = fState.value.offer?.takeIf { it.expires > fNow() }?.key

	/*
		Décision sur une ouverture : appairage d'un téléphone qui a scanné
		l'offre (enregistré, réseaux de la maison réunis, offre retirée), ou
		session d'un téléphone appairé (nom et adresse mis à jour).
		inCall : ouverture vérifiée
	*/
	@Synchronized
	private fun accept(inCall: OpenCall): OpenVerdict
	{
		if (inCall.withOffer)
		{
			val vOffer  = fState.value.offer?.takeIf { it.expires > fNow() } ?: return OpenVerdict("offre d'appairage expirée : affichez un nouveau QR code sur le PC")	// offre en cours
			val vHomes  = inCall.pairing ?: return OpenVerdict("demande d'appairage attendue")		// réseaux vus par le téléphone
			val vPhone  = PairedPhone(inCall.peer.id, inCall.peer.name, fPeers.phones.firstOrNull { it.id == inCall.peer.id }?.alias, toBase64(vOffer.key), fNow(), fNow(), inCall.address.hostAddress)	// nouveau téléphone
			save(fPeers.copy(
				phones = fPeers.phones.filter { it.id != vPhone.id } + vPhone,
				home   = (fPeers.home + vHomes + fState.value.networks).distinct()
			))
			fState.update { it.copy(offer = null, paired = vPhone.label) }
			Thread({ refresh() }, "BridgePlus-écoute").apply { isDaemon = true }.start()
			return OpenVerdict(home = fPeers.home)
		}
		if (fPeers.phones.none { it.id == inCall.peer.id }) return OpenVerdict("appareil non appairé")
		update(inCall.peer.id) { it.copy(name = inCall.peer.name, lastSeen = fNow(), lastAddress = inCall.address.hostAddress) }
		return OpenVerdict(home = fPeers.home)
	}

	/*
		Session ouverte : elle remplace l'éventuelle session précédente du même
		téléphone.
		inSession : session
		inCall    : ouverture
	*/
	private fun opened(
		inSession : LinkSession,
		inCall    : OpenCall
	): SessionListener
	{
		fSessions.put(inCall.peer.id, inSession)?.close("remplacée par une nouvelle connexion")
		fState.update { it.copy(connected = fSessions.keys.toSet()) }
		return object : SessionListener
		{
			override fun onMessage(inSession: LinkSession, inMessage: LinkMessage)
			{
				val vPhone = fPeers.phones.firstOrNull { it.id == inCall.peer.id } ?: return	// téléphone
				if (inMessage is LinkMessage.Forgotten) { forgottenBy(vPhone.id, inSession); return }
				if (inMessage !is LinkMessage.Pong) fOnMessage(vPhone, inMessage)
			}

			override fun onChunk(inSession: LinkSession, inChunk: ByteArray)
			{
				fPeers.phones.firstOrNull { it.id == inCall.peer.id }?.let { fOnChunk(it, inChunk) }
			}

			override fun onClosed(inSession: LinkSession, inReason: String)
			{
				fPeers.phones.firstOrNull { it.id == inCall.peer.id }?.let { runCatching { fOnClosed(it) } }
				fSessions.remove(inCall.peer.id, inSession)
				fState.update { it.copy(connected = fSessions.keys.toSet()) }
				synchronized(this@PcLink) { if (fPeers.phones.any { it.id == inCall.peer.id }) update(inCall.peer.id) { it.copy(lastSeen = fNow()) } }
			}
		}
	}

	/*
		Le téléphone a oublié ce PC : sa clé est détruite ici aussi.
		inId      : téléphone
		inSession : sa session
	*/
	@Synchronized
	private fun forgottenBy(
		inId      : String,
		inSession : LinkSession
	)
	{
		fSessions.remove(inId, inSession)
		inSession.close("le téléphone a oublié ce PC")
		drop(inId)
	}

	/*
		Retire un téléphone des appareils enregistrés.
		inId : téléphone
	*/
	private fun drop(inId: String)
	{
		save(fPeers.copy(phones = fPeers.phones.filter { it.id != inId }))
		fState.update { it.copy(connected = fSessions.keys.toSet()) }
		refresh()
	}

	/*
		Modifie un téléphone appairé.
		inId    : téléphone
		inBlock : modification
	*/
	private fun update(
		inId    : String,
		inBlock : (PairedPhone) -> PairedPhone
	) = save(fPeers.copy(phones = fPeers.phones.map { if (it.id == inId) inBlock(it) else it }))

	/*
		Appareils enregistrés ; un fichier neuf (identifiant tiré) s'il est
		absent ou illisible.
	*/
	private fun load(): PcPeers =
		fFile.read()?.let { vText -> runCatching { kPeersJson.decodeFromString(PcPeers.serializer(), vText) }.getOrNull() } ?: PcPeers().also { fFile.write(kPeersJson.encodeToString(PcPeers.serializer(), it)) }

	/*
		Enregistre les appareils et met l'état à jour.
		inPeers : appareils
	*/
	@Synchronized
	private fun save(inPeers: PcPeers)
	{
		fPeers = inPeers
		fFile.write(kPeersJson.encodeToString(PcPeers.serializer(), inPeers))
		fState.update { it.copy(phones = inPeers.phones, home = inPeers.home) }
	}
}
