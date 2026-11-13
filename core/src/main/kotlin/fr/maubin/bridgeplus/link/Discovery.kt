package fr.maubin.bridgeplus.link

import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

private const val kQuery = "BRIDGEPLUS?"										// demande de découverte (suivie de la version du protocole)

/*
	Réponse d'un PC à la découverte.
	id   : identifiant du PC
	name : nom du PC
	port : port de son serveur
*/
@Serializable
data class Beacon(
	val id   : String,
	val name : String,
	val port : Int
)

/*
	PC trouvé sur le réseau local.
	address : adresse du PC
	beacon  : sa réponse
*/
data class FoundPc(
	val address : String,
	val beacon  : Beacon
)

/*
	Répondeur de découverte (PC) : écoute les demandes diffusées sur le réseau
	local et répond directement à l'appareil qui demande, avec l'identifiant,
	le nom et le port du serveur. Ne répond qu'aux demandes de ce protocole,
	venues d'une adresse admise (réseau de la maison) : l'écoute porte sur
	toutes les interfaces, car Windows ne remet pas toujours les diffusions à
	une écoute liée à une seule adresse.
	inBeacon : réponse à donner
	inAllow  : vrai si une demande venue de cette adresse mérite une réponse
*/
class DiscoveryResponder(
	private val inBeacon : () -> Beacon,
	private val inAllow  : (InetAddress) -> Boolean = { true }
)
{
	private var fSocket : DatagramSocket? = null									// écoute ; null à l'arrêt

	// Port d'écoute ; 0 à l'arrêt.
	val port: Int get() = fSocket?.localPort ?: 0

	/*
		Démarre l'écoute ; sans effet si elle tourne déjà.
		inPort    : port ; 0 pour un port libre quelconque (tests)
		inAddress : adresse d'écoute ; null pour toutes les interfaces (diffusions reçues)
	*/
	@Synchronized
	fun start(
		inPort    : Int          = kDiscoveryPort,
		inAddress : InetAddress? = null
	)
	{
		if (fSocket != null) return
		val vSocket = DatagramSocket(null).apply { reuseAddress = true; broadcast = true; bind(InetSocketAddress(inAddress, inPort)) }	// écoute
		fSocket = vSocket
		Thread({
			val vBuffer = ByteArray(512)												// demande reçue
			while (!vSocket.isClosed)
			{
				val vPacket = DatagramPacket(vBuffer, vBuffer.size)						// paquet
				runCatching { vSocket.receive(vPacket) }.getOrNull() ?: break
				if (String(vPacket.data, 0, vPacket.length, Charsets.UTF_8) != "$kQuery$kLinkProtocol") continue
				if (!runCatching { inAllow(vPacket.address) }.getOrDefault(false)) continue
				val vReply = kLinkJson.encodeToString(Beacon.serializer(), inBeacon()).toByteArray(Charsets.UTF_8)	// réponse
				runCatching { vSocket.send(DatagramPacket(vReply, vReply.size, vPacket.socketAddress)) }
			}
		}, "BridgePlus-découverte").apply { isDaemon = true }.start()
	}

	/*
		Arrête l'écoute.
	*/
	@Synchronized
	fun stop()
	{
		runCatching { fSocket?.close() }
		fSocket = null
	}
}

/*
	Adresses de diffusion du réseau local : celles des interfaces IPv4 actives
	(hors boucle locale), puis la diffusion générale.
*/
fun broadcastAddresses(): List<InetAddress> =
	(runCatching {
		NetworkInterface.getNetworkInterfaces().toList()
			.filter { it.isUp && !it.isLoopback }
			.flatMap { it.interfaceAddresses }
			.filter { it.address is Inet4Address }
			.mapNotNull { it.broadcast }
	}.getOrDefault(emptyList()) + InetAddress.getByName("255.255.255.255")).distinct()

/*
	Cherche les PC du réseau local : diffuse la demande, puis recueille les
	réponses pendant le délai donné. Un PC qui répond plusieurs fois (plusieurs
	interfaces) n'est compté qu'une fois.
	inTargets : adresses où envoyer la demande (diffusion ; adresse précise en test)
	inPort    : port de découverte
	inWaitMs  : durée d'écoute des réponses
*/
fun discoverPcs(
	inTargets : List<InetAddress> = broadcastAddresses(),
	inPort    : Int               = kDiscoveryPort,
	inWaitMs  : Int               = 1_500
): List<FoundPc>
{
	val vFound = linkedMapOf<String, FoundPc>()										// PC trouvés, par identifiant
	DatagramSocket().use { vSocket ->
		vSocket.broadcast = true
		val vQuery = "$kQuery$kLinkProtocol".toByteArray(Charsets.UTF_8)			// demande
		inTargets.forEach { inTarget -> runCatching { vSocket.send(DatagramPacket(vQuery, vQuery.size, inTarget, inPort)) } }
		val vEnd    = System.currentTimeMillis() + inWaitMs							// fin de l'écoute
		val vBuffer = ByteArray(1024)													// réponse reçue
		while (true)
		{
			val vLeft = (vEnd - System.currentTimeMillis()).toInt()					// temps restant
			if (vLeft <= 0) break
			vSocket.soTimeout = vLeft
			val vPacket = DatagramPacket(vBuffer, vBuffer.size)							// paquet
			try { vSocket.receive(vPacket) } catch (inError: SocketTimeoutException) { break }
			runCatching { kLinkJson.decodeFromString(Beacon.serializer(), String(vPacket.data, 0, vPacket.length, Charsets.UTF_8)) }.getOrNull()?.let { inBeacon ->
				vFound.putIfAbsent(inBeacon.id, FoundPc(vPacket.address.hostAddress, inBeacon))
			}
		}
	}
	return vFound.values.toList()
}
