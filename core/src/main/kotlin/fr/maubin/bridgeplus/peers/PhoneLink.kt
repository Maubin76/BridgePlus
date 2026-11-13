package fr.maubin.bridgeplus.peers

import fr.maubin.bridgeplus.link.Backoff
import fr.maubin.bridgeplus.link.DeviceInfo
import fr.maubin.bridgeplus.link.FoundPc
import fr.maubin.bridgeplus.link.LinkClient
import fr.maubin.bridgeplus.link.LinkException
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.LinkSession
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.OpenedSession
import fr.maubin.bridgeplus.link.PairingOffer
import fr.maubin.bridgeplus.link.SessionListener
import fr.maubin.bridgeplus.link.discoverPcs
import fr.maubin.bridgeplus.link.fromBase64
import fr.maubin.bridgeplus.link.kPhoneDeadAfterMs
import fr.maubin.bridgeplus.link.kPlatformAndroid
import fr.maubin.bridgeplus.link.toBase64
import fr.maubin.bridgeplus.store.SealedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
	PC appairé avec ce téléphone.
	id        : identifiant du PC
	name      : son nom (tel qu'il se présente)
	alias     : nom choisi sur ce téléphone ; null pour garder le sien
	key       : clé secrète de la paire (base64)
	port      : port de son serveur
	addresses : adresses connues (dernière réussie d'abord)
	lastSeen  : dernière connexion (ouverture ou fermeture d'une session, ms) ; null avant
*/
@Serializable
data class PairedPc(
	val id        : String,
	val name      : String,
	val alias     : String?      = null,
	val key       : String,
	val port      : Int,
	val addresses : List<String> = emptyList(),
	val lastSeen  : Long?        = null
)
{
	// Nom affiché : celui choisi sur ce téléphone, sinon celui du PC.
	val label: String get() = alias ?: name
}

/*
	Fichier des appareils du téléphone (scellé : il contient les clés des
	paires).
	deviceId : identifiant de ce téléphone (tiré au hasard la première fois)
	pcs      : PC appairés
	home     : réseaux de la maison
*/
@Serializable
data class PhonePeers(
	val deviceId : String          = UUID.randomUUID().toString(),
	val pcs      : List<PairedPc>  = emptyList(),
	val home     : List<NetworkId> = emptyList()
)

/*
	État de la liaison avec un PC.
*/
enum class PcStatus
{
	Connected,																			// session ouverte
	Searching,																			// connexion en cours
	Absent,																				// injoignable : nouvel essai plus tard
	Refused																				// le PC refuse ce téléphone (oublié de son côté)
}

/*
	État de la liaison du téléphone.
	running   : boucles de connexion actives (service démarré, à la maison)
	pcs       : PC appairés
	home      : réseaux de la maison
	status    : état de chaque PC, par identifiant
	refusals  : raison du refus, par PC refusé
	forgotten : nom du PC qui vient d'oublier ce téléphone (message) ; null sans
*/
data class PhoneLinkState(
	val running   : Boolean               = false,
	val pcs       : List<PairedPc>        = emptyList(),
	val home      : List<NetworkId>       = emptyList(),
	val status    : Map<String, PcStatus> = emptyMap(),
	val refusals  : Map<String, String>   = emptyMap(),
	val forgotten : String?               = null
)
{
	/*
		État d'un PC ; Absent s'il n'est pas connu.
		inId : PC
	*/
	fun statusOf(inId: String): PcStatus = status[inId] ?: PcStatus.Absent

	// PC reliés en ce moment.
	val connected: List<PairedPc> get() = pcs.filter { statusOf(it.id) == PcStatus.Connected }
}

/*
	Liaison du téléphone : appairage par l'offre d'un QR code, puis, tant
	qu'elle tourne, une boucle par PC appairé qui tient une session ouverte
	(dernières adresses, puis recherche sur le réseau local) et la rouvre
	après un délai progressif quand elle tombe. Les conditions (réseau de la
	maison, service démarré) sont vérifiées par l'appelant.
	fFile        : fichier scellé des appareils
	fName        : nom du téléphone
	fVersion     : version de l'appli
	fDiscover    : recherche des PC sur le réseau local
	fOnMessage   : message reçu d'un PC (étapes suivantes)
	fNow         : horloge (ms)
	fDeadAfterMs : délai sans nouvelles au bout duquel une session est morte
	fBackoff     : délai progressif d'une boucle (un neuf par PC)
*/
class PhoneLink(
	private val fFile        : SealedFile,
	private val fName        : String,
	private val fVersion     : String,
	private val fDiscover    : () -> List<FoundPc>             = { discoverPcs() },
	private val fOnMessage   : (PairedPc, LinkMessage) -> Unit = { _, _ -> },
	private val fNow         : () -> Long                      = System::currentTimeMillis,
	private val fDeadAfterMs : Long                            = kPhoneDeadAfterMs,
	private val fBackoff     : () -> Backoff                   = { Backoff() }
)
{
	private var fPeers    = load()																// appareils enregistrés
	private val fState    = MutableStateFlow(PhoneLinkState(pcs = fPeers.pcs, home = fPeers.home))	// état
	private val fLoops    = ConcurrentHashMap<String, PcLoop>()									// boucles de connexion, par PC
	private var fRunning  = false																// boucles actives

	/*
		État de la liaison.
	*/
	val state: StateFlow<PhoneLinkState> = fState

	/*
		Présentation de ce téléphone.
	*/
	fun info(): DeviceInfo = DeviceInfo(fPeers.deviceId, fName, kPlatformAndroid, fVersion)

	/*
		Appareils enregistrés.
	*/
	fun peers(): PhonePeers = fPeers

	/*
		Appairage avec le PC d'une offre scannée : ouvre une session avec la
		clé de l'offre (adresses de l'offre essayées l'une après l'autre), puis
		enregistre le PC et les réseaux de la maison. Rend null si l'appairage
		a réussi, sinon la raison.
		inOffer : offre lue dans le QR code
		inWifi  : Wi-Fi actuel du téléphone (réseau de la maison)
	*/
	fun pair(
		inOffer : PairingOffer,
		inWifi  : NetworkId
	): String?
	{
		if (inOffer.expires < fNow()) return "Ce QR code a expiré : affichez-en un nouveau sur le PC."
		val vClient = LinkClient(info(), fNow)													// client
		var vError  = "PC injoignable"															// dernière erreur
		for (vHost in inOffer.addresses)
		{
			val vOpened = try
			{
				vClient.open(vHost, inOffer.port, inOffer.pcId, inOffer.key, listOf(inWifi), fDeadAfterMs)
			}
			catch (inError: LinkException)
			{
				return inError.message ?: "appairage refusé"
			}
			catch (inError: IOException)
			{
				vError = inError.message ?: "PC injoignable"; continue
			}																					// session d'appairage
			vOpened.session.close("appairage terminé")
			synchronized(this) {
				val vOld = fPeers.pcs.firstOrNull { it.id == inOffer.pcId }						// PC déjà connu (nouvel appairage)
				val vPc  = PairedPc(inOffer.pcId, vOpened.reply.device.name, vOld?.alias, toBase64(inOffer.key), inOffer.port, listOf(vHost) + inOffer.addresses.filter { it != vHost }, fNow())	// PC appairé
				fLoops.remove(vPc.id)?.stop()
				save(fPeers.copy(pcs = fPeers.pcs.filter { it.id != vPc.id } + vPc, home = (fPeers.home + listOf(inWifi) + vOpened.reply.home).distinct()))
				fState.update { it.copy(refusals = it.refusals - vPc.id) }
				syncLoops()
			}
			return null
		}
		return "Appairage impossible : $vError. Vérifiez que les deux appareils sont sur le même Wi-Fi et que le QR code est encore valable."
	}

	/*
		Démarre les boucles de connexion (service démarré, à la maison).
	*/
	@Synchronized
	fun start()
	{
		fRunning = true
		fState.update { it.copy(running = true) }
		syncLoops()
	}

	/*
		Arrête les boucles et ferme les sessions (hors de la maison, arrêt du
		service).
	*/
	@Synchronized
	fun stop()
	{
		fRunning = false
		fLoops.values.forEach { it.stop() }
		fLoops.clear()
		fState.update { it.copy(running = false, status = emptyMap()) }
	}

	/*
		Le réseau a changé : les PC absents sont réessayés tout de suite.
	*/
	fun nudge() = fLoops.values.forEach { it.wake() }

	/*
		Oublie un PC : il est prévenu s'il est relié, sa clé est détruite.
		inId : PC
	*/
	@Synchronized
	fun forget(inId: String)
	{
		fLoops.remove(inId)?.let { it.session()?.send(LinkMessage.Forgotten); it.stop() }
		drop(inId)
	}

	/*
		Renomme un PC sur ce téléphone.
		inId    : PC
		inAlias : nouveau nom ; vide pour reprendre le sien
	*/
	@Synchronized
	fun rename(
		inId    : String,
		inAlias : String
	) = update(inId) { it.copy(alias = inAlias.trim().ifEmpty { null }) }

	/*
		Retire un réseau de la maison.
		inNetwork : réseau
	*/
	@Synchronized
	fun removeHome(inNetwork: NetworkId) = save(fPeers.copy(home = fPeers.home - inNetwork))

	/*
		Oublie le message « PC qui a oublié ce téléphone ».
	*/
	fun clearForgotten() = fState.update { it.copy(forgotten = null) }

	/*
		Envoie un message à un PC relié ; faux s'il ne l'est pas.
		inId      : PC
		inMessage : message
	*/
	fun send(
		inId      : String,
		inMessage : LinkMessage
	): Boolean = fLoops[inId]?.session()?.send(inMessage) ?: false

	/*
		Envoie un message à tous les PC reliés ; rend les noms de ceux qui
		l'ont reçu.
		inMessage : message
	*/
	fun sendAll(inMessage: LinkMessage): List<String> =
		fLoops.entries.toList().filter { it.value.session()?.send(inMessage) == true }.mapNotNull { vEntry -> fPeers.pcs.firstOrNull { it.id == vEntry.key }?.label }

	/*
		Une boucle par PC appairé tant que la liaison tourne, aucune sinon.
	*/
	private fun syncLoops()
	{
		if (!fRunning) return
		fLoops.keys.filter { vId -> fPeers.pcs.none { it.id == vId } }.forEach { fLoops.remove(it)?.stop() }
		fPeers.pcs.filter { !fLoops.containsKey(it.id) }.forEach { vPc -> fLoops[vPc.id] = PcLoop(vPc.id).also { it.begin() } }
	}

	/*
		Note l'état d'un PC.
		inId     : PC
		inStatus : état
		inReason : raison d'un refus
	*/
	private fun status(
		inId     : String,
		inStatus : PcStatus,
		inReason : String? = null
	) = fState.update {
		it.copy(
			status   = it.status + (inId to inStatus),
			refusals = when
			{
				inReason != null              -> it.refusals + (inId to inReason)
				inStatus == PcStatus.Connected -> it.refusals - inId
				else                          -> it.refusals
			}
		)
	}

	/*
		Le PC a oublié ce téléphone : sa clé est détruite ici aussi.
		inId : PC
	*/
	@Synchronized
	private fun forgottenBy(inId: String)
	{
		val vName = fPeers.pcs.firstOrNull { it.id == inId }?.label ?: return					// nom du PC
		fLoops.remove(inId)?.stop()
		drop(inId)
		fState.update { it.copy(forgotten = vName) }
	}

	/*
		Retire un PC des appareils enregistrés.
		inId : PC
	*/
	private fun drop(inId: String)
	{
		save(fPeers.copy(pcs = fPeers.pcs.filter { it.id != inId }))
		fState.update { it.copy(status = it.status - inId, refusals = it.refusals - inId) }
	}

	/*
		Modifie un PC appairé.
		inId    : PC
		inBlock : modification
	*/
	@Synchronized
	private fun update(
		inId    : String,
		inBlock : (PairedPc) -> PairedPc
	) = save(fPeers.copy(pcs = fPeers.pcs.map { if (it.id == inId) inBlock(it) else it }))

	/*
		Appareils enregistrés ; un fichier neuf (identifiant tiré) s'il est
		absent ou illisible.
	*/
	private fun load(): PhonePeers =
		fFile.read()?.let { vText -> runCatching { kPeersJson.decodeFromString(PhonePeers.serializer(), vText) }.getOrNull() } ?: PhonePeers().also { fFile.write(kPeersJson.encodeToString(PhonePeers.serializer(), it)) }

	/*
		Enregistre les appareils et met l'état à jour.
		inPeers : appareils
	*/
	@Synchronized
	private fun save(inPeers: PhonePeers)
	{
		fPeers = inPeers
		fFile.write(kPeersJson.encodeToString(PhonePeers.serializer(), inPeers))
		fState.update { it.copy(pcs = inPeers.pcs, home = inPeers.home) }
	}

	/*
		Boucle de connexion à un PC, sur son propre fil : ouvre une session,
		attend qu'elle tombe, patiente selon le délai progressif, recommence.
		inId : PC
	*/
	private inner class PcLoop(private val inId: String) : SessionListener
	{
		private val fSignals = LinkedBlockingQueue<String>()								// réveils : changement de réseau, session fermée, arrêt
		private val fBackoff = this@PhoneLink.fBackoff()									// délai progressif
		@Volatile private var fStopped = false												// boucle arrêtée
		@Volatile private var fSession : LinkSession? = null								// session ouverte ; null sans

		/*
			Session ouverte ; null sans.
		*/
		fun session(): LinkSession? = fSession?.takeIf { it.isOpen }

		/*
			Démarre la boucle.
		*/
		fun begin() = Thread(::run, "BridgePlus-PC-$inId").apply { isDaemon = true }.start()

		/*
			Réessaie tout de suite si le PC est absent.
		*/
		fun wake()
		{
			fBackoff.reset()
			fSignals.offer("réveil")
		}

		/*
			Arrête la boucle et ferme la session.
		*/
		fun stop()
		{
			fStopped = true
			fSignals.offer("arrêt")
			fSession?.close("liaison arrêtée")
		}

		override fun onMessage(inSession: LinkSession, inMessage: LinkMessage)
		{
			if (inMessage is LinkMessage.Forgotten) { Thread({ forgottenBy(inId) }, "BridgePlus-oubli").apply { isDaemon = true }.start(); return }
			fPeers.pcs.firstOrNull { it.id == inId }?.let { fOnMessage(it, inMessage) }
		}

		override fun onClosed(inSession: LinkSession, inReason: String)
		{
			fSignals.offer("fermée")
		}

		/*
			Boucle : connexion, attente de la fermeture, délai, et ainsi de suite.
		*/
		private fun run()
		{
			while (!fStopped)
			{
				status(inId, PcStatus.Searching)
				val vOpened = connect()																// session ouverte ; null si injoignable ou refusé
				if (fStopped) { vOpened?.session?.close("liaison arrêtée"); break }
				if (vOpened != null)
				{
					fBackoff.reset()
					fSignals.clear()
					fSession = vOpened.session
					status(inId, PcStatus.Connected)
					vOpened.session.start(this)
					while (!fStopped && vOpened.session.isOpen) fSignals.poll(1, TimeUnit.MINUTES)
					fSession = null
					synchronized(this@PhoneLink) { if (fPeers.pcs.any { it.id == inId }) update(inId) { it.copy(lastSeen = fNow()) } }
					if (fStopped) break
					status(inId, PcStatus.Absent)
				}
				fSignals.poll(fBackoff.next(), TimeUnit.MILLISECONDS)
			}
		}

		/*
			Ouvre une session avec le PC : dernières adresses connues, puis
			recherche sur le réseau local. Null si le PC est injoignable ou
			refuse (état noté).
		*/
		private fun connect(): OpenedSession?
		{
			val vPc     = fPeers.pcs.firstOrNull { it.id == inId } ?: return null				// PC
			val vKey    = fromBase64(vPc.key) ?: return null										// clé de la paire
			val vClient = LinkClient(info(), fNow)													// client
			val vFound  by lazy { runCatching { fDiscover() }.getOrDefault(emptyList()).filter { it.beacon.id == inId } }	// PC trouvé sur le réseau local
			for (vTry in 0..1)
			{
				val vHosts = if (vTry == 0) vPc.addresses.map { it to vPc.port } else vFound.map { it.address to it.beacon.port }.filter { it !in vPc.addresses.map { vAddr -> vAddr to vPc.port } }	// adresses à essayer
				for ((vHost, vPort) in vHosts)
				{
					if (fStopped) return null
					try
					{
						val vOpened = vClient.open(vHost, vPort, inId, vKey, null, fDeadAfterMs)	// session ouverte
						update(inId) { it.copy(name = vOpened.reply.device.name, port = vPort, addresses = (listOf(vHost) + it.addresses).distinct().take(4), lastSeen = fNow()) }
						return vOpened
					}
					catch (inError: LinkException)
					{
						status(inId, PcStatus.Refused, refusal(inError.message.orEmpty()))
						return null
					}
					catch (inError: IOException)
					{
						continue
					}
				}
			}
			status(inId, PcStatus.Absent)
			return null
		}

		/*
			Raison d'un refus, en clair pour l'utilisateur.
			inMessage : refus du PC
		*/
		private fun refusal(inMessage: String): String =
			if (inMessage.contains("non appairé")) "Ce PC ne reconnaît plus ce téléphone : oubliez-le ici, puis appairez-le de nouveau." else "Refusé par le PC : $inMessage"
	}
}
