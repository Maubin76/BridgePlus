package fr.maubin.bridgeplus.desktop

import fr.maubin.bridgeplus.link.LocalAddress
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.link.NetworkId
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

private const val kCommandTimeoutS = 5L											// délai maximal d'une commande Windows
private val kVirtualAdapter = Regex("Hyper-V|vEthernet|WSL|VirtualBox|VMware|TAP-|WireGuard|Tailscale|ZeroTier", RegexOption.IGNORE_CASE)	// cartes virtuelles à ignorer

/*
	Wi-Fi connecté d'une carte, lu dans « netsh wlan show interfaces ».
	description : description de la carte (celle que donne Java pour l'interface)
	ssid        : nom du Wi-Fi
*/
data class WlanLink(
	val description : String,
	val ssid        : String
)

/*
	Nom du Wi-Fi connecté, lu dans la sortie de « netsh wlan show interfaces »
	(Windows en français ou en anglais, espace insécable avant les deux-points
	compris : la ligne « SSID », pas « BSSID ») ;
	null sans Wi-Fi connecté.
	inText : sortie de la commande
*/
fun parseSsid(inText: String): String? = parseWlans(inText).firstOrNull()?.ssid

/*
	Cartes Wi-Fi connectées, lues dans la sortie de « netsh wlan show
	interfaces » : un bloc par carte, avec sa description et le nom du Wi-Fi.
	inText : sortie de la commande
*/
fun parseWlans(inText: String): List<WlanLink> =
	inText.replace(Char(0x00A0), ' ').replace(Char(0x202F), ' ').replace(Char(13).toString(), "")
		.split(Regex("\n\\s*\n"))
		.mapNotNull { vBlock ->
			val vLines = vBlock.lines()																	// lignes du bloc
			val vSsid  = vLines.firstNotNullOfOrNull { Regex("""^\s*SSID\s*:\s*(.+?)\s*$""").find(it)?.groupValues?.get(1) }?.takeIf { it.isNotEmpty() }	// nom du Wi-Fi
			val vDesc  = vLines.firstNotNullOfOrNull { Regex("""^\s*Description\s*:\s*(.+?)\s*$""").find(it)?.groupValues?.get(1) }.orEmpty()	// carte
			vSsid?.let { WlanLink(vDesc, it) }
		}

/*
	Route par défaut, lue dans la sortie de « route print -4 0.0.0.0 » :
	ligne « 0.0.0.0  0.0.0.0  passerelle  interface  métrique », celle de plus
	petite métrique.
	gateway : adresse de la passerelle (box)
	adapter : adresse de l'interface du PC qui y mène
*/
data class DefaultRoute(
	val gateway : String,
	val adapter : String
)

/*
	Passerelle par défaut, lue dans la sortie de « route print -4 0.0.0.0 » ;
	null sans passerelle.
	inText : sortie de la commande
*/
fun parseGateway(inText: String): String? = parseRoute(inText)?.gateway

/*
	Route par défaut (passerelle et interface), lue dans la sortie de
	« route print -4 0.0.0.0 » ; null sans passerelle.
	inText : sortie de la commande
*/
fun parseRoute(inText: String): DefaultRoute? =
	inText.lines()
		.mapNotNull { Regex("""^\s*0\.0\.0\.0\s+0\.0\.0\.0\s+(\d+\.\d+\.\d+\.\d+)\s+(\d+\.\d+\.\d+\.\d+)\s+(\d+)""").find(it) }
		.minByOrNull { it.groupValues[3].toInt() }
		?.let { DefaultRoute(it.groupValues[1], it.groupValues[2]) }

/*
	Adresse matérielle d'une adresse IP, lue dans la sortie de « arp -a ip » ;
	null si elle n'y figure pas.
	inText : sortie de la commande
	inIp   : adresse IP cherchée
*/
fun parseMac(
	inText : String,
	inIp   : String
): String? =
	inText.lines().firstNotNullOfOrNull { inLine ->
		Regex("""^\s*${Regex.escape(inIp)}\s+([0-9a-fA-F]{2}(?:-[0-9a-fA-F]{2}){5})\b""").find(inLine)?.groupValues?.get(1)
	}

/*
	Réseaux auxquels ce PC est relié, avec les adresses de l'interface qui y
	mène : Wi-Fi (nom du réseau, carte Wi-Fi) et box (adresse matérielle de la
	passerelle, interface de la route par défaut). Lus par des commandes de
	Windows ; vide ailleurs ou en cas d'échec. Les cartes virtuelles (VPN,
	machines virtuelles) sont ignorées.
*/
fun currentNetworks(): List<LocalNetwork>
{
	if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return emptyList()
	val vCards  = physicalInterfaces()															// cartes du PC
	val vWlans  = run("cmd", "/c", "chcp 65001 >nul & netsh wlan show interfaces")?.let(::parseWlans).orEmpty()	// Wi-Fi
	val vWifi   = vWlans.mapNotNull { vWlan ->
		val vCard = vCards.firstOrNull { it.displayName.equals(vWlan.description, ignoreCase = true) } ?: vCards.singleOrNull { isWireless(it) }	// carte de ce Wi-Fi
		NetworkId.wifi(vWlan.ssid)?.let { vId -> LocalNetwork(vId, vCard?.let(::addressesOf).orEmpty()) }
	}																							// réseaux Wi-Fi
	val vRoute  = run("route", "print", "-4", "0.0.0.0")?.let(::parseRoute)						// route par défaut
	val vBox    = vRoute?.let { inRoute ->
		val vMac  = run("arp", "-a", inRoute.gateway)?.let { parseMac(it, inRoute.gateway) }	// adresse matérielle de la box
		val vCard = vCards.firstOrNull { vCard -> addressesOf(vCard).any { it.address == inRoute.adapter } }	// carte de la route
		NetworkId.box(vMac)?.let { vId -> LocalNetwork(vId, vCard?.let(::addressesOf).orEmpty()) }
	}																							// box
	return (vWifi + listOfNotNull(vBox)).filter { it.addresses.isNotEmpty() }
}

/*
	Adresses IPv4 de ce PC sur le réseau local (hors boucle locale et cartes
	virtuelles), pour l'affichage.
*/
fun localAddresses(): List<String> = physicalInterfaces().flatMap(::addressesOf).map { it.address }

/*
	Empreinte des adresses du PC : change quand une carte se connecte, se
	déconnecte ou change d'adresse. Lecture rapide (sans commande Windows),
	pour savoir quand relire les réseaux.
*/
fun addressFingerprint(): String = runCatching { physicalInterfaces().flatMap(::addressesOf).sortedBy { it.address }.joinToString(",") { "${it.address}/${it.prefix}" } }.getOrDefault("")

/*
	Cartes réseau actives et physiques (ni boucle locale, ni carte virtuelle).
*/
private fun physicalInterfaces(): List<NetworkInterface> =
	runCatching {
		NetworkInterface.getNetworkInterfaces().toList()
			.filter { it.isUp && !it.isLoopback && !it.isVirtual && !kVirtualAdapter.containsMatchIn(it.displayName.orEmpty()) }
	}.getOrDefault(emptyList())

/*
	Adresses IPv4 privées d'une carte, avec leur préfixe.
	inCard : carte
*/
private fun addressesOf(inCard: NetworkInterface): List<LocalAddress> =
	inCard.interfaceAddresses
		.filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
		.map { LocalAddress(it.address.hostAddress, it.networkPrefixLength.toInt()) }

/*
	Vrai si la carte semble être une carte Wi-Fi (d'après sa description).
	inCard : carte
*/
private fun isWireless(inCard: NetworkInterface): Boolean =
	Regex("Wi-?Fi|Wireless|WLAN|802\\.11", RegexOption.IGNORE_CASE).containsMatchIn(inCard.displayName.orEmpty()) && addressesOf(inCard).isNotEmpty()

/*
	Lance une commande et rend sa sortie ; null si elle échoue ou dépasse le
	délai.
	inCommand : commande et arguments
*/
private fun run(vararg inCommand: String): String? =
	runCatching {
		val vProcess = ProcessBuilder(*inCommand).redirectErrorStream(true).start()	// commande
		val vText    = vProcess.inputStream.bufferedReader(Charsets.UTF_8).readText()	// sortie
		if (!vProcess.waitFor(kCommandTimeoutS, TimeUnit.SECONDS)) { vProcess.destroyForcibly(); null } else vText
	}.getOrNull()
