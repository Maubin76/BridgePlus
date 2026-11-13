package fr.maubin.bridgeplus.link

import kotlinx.serialization.Serializable
import java.net.Inet4Address
import java.net.InetAddress

/*
	Réseau auquel l'appareil est relié, tel qu'on peut le reconnaître : nom du
	Wi-Fi (téléphone, PC portable), ou identifiant de la box (adresse matérielle
	de la passerelle) pour un PC branché par câble.
	kind  : « wifi » ou « box »
	value : nom du Wi-Fi, ou adresse matérielle de la box (minuscules, « aa:bb:… »)
*/
@Serializable
data class NetworkId(
	val kind  : String,
	val value : String
)
{
	// Libellé affiché (« Wi-Fi « Maison » », « Box aa:bb:cc:… »).
	val label: String get() = if (kind == kKindWifi) "Wi-Fi « $value »" else "Box $value"

	companion object
	{
		const val kKindWifi = "wifi"												// réseau reconnu par le nom du Wi-Fi
		const val kKindBox  = "box"													// réseau reconnu par la box (câble)

		/*
			Wi-Fi reconnu par son nom ; null si le nom est vide ou masqué
			(« <unknown ssid> » sans autorisation sur Android).
			inSsid : nom du Wi-Fi (guillemets d'Android acceptés)
		*/
		fun wifi(inSsid: String?): NetworkId? =
			inSsid?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }?.let { NetworkId(kKindWifi, it) }

		/*
			Box reconnue par l'adresse matérielle de la passerelle ; null si elle
			est illisible ou nulle.
			inMac : adresse matérielle (« AA-BB-CC-DD-EE-FF » ou « aa:bb:… »)
		*/
		fun box(inMac: String?): NetworkId?
		{
			val vMac = inMac?.trim()?.lowercase()?.replace('-', ':') ?: return null	// adresse normalisée
			return if (Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}").matches(vMac) && vMac != "00:00:00:00:00:00" && vMac != "ff:ff:ff:ff:ff:ff") NetworkId(kKindBox, vMac) else null
		}
	}
}

/*
	Vrai si l'appareil est sur le réseau familial : un des réseaux courants
	figure parmi les réseaux reconnus. Sans réseau reconnu, ou sans réseau
	courant lisible, BridgePlus reste inactif.
	inCurrent : réseaux courants de l'appareil (un PC peut avoir Wi-Fi et câble)
	inHome    : réseaux familiaux enregistrés
*/
fun isHomeNetwork(
	inCurrent : Collection<NetworkId>,
	inHome    : Collection<NetworkId>
): Boolean = inCurrent.any { it in inHome }

/*
	Adresse IPv4 d'une interface de l'appareil, avec la longueur de son
	préfixe (sous-réseau).
	address : adresse (« 192.168.1.20 »)
	prefix  : longueur du préfixe (24 pour 255.255.255.0)
*/
data class LocalAddress(
	val address : String,
	val prefix  : Int
)
{
	/*
		Vrai si l'adresse donnée est dans le même sous-réseau (IPv4 seulement).
		inOther : adresse à tester
	*/
	fun contains(inOther: InetAddress): Boolean
	{
		val vMine  = runCatching { InetAddress.getByName(address) }.getOrNull() as? Inet4Address ?: return false	// cette adresse
		val vOther = inOther as? Inet4Address ?: return false								// adresse à tester
		val vMask  = if (prefix <= 0) 0 else (-1 shl (32 - prefix.coerceAtMost(32)))		// masque du sous-réseau
		return (toInt(vMine) and vMask) == (toInt(vOther) and vMask)
	}

	/*
		Adresse IPv4 en entier.
		inAddress : adresse
	*/
	private fun toInt(inAddress: Inet4Address): Int = inAddress.address.fold(0) { vAcc, vByte -> (vAcc shl 8) or (vByte.toInt() and 0xFF) }
}

/*
	Réseau auquel l'appareil est relié, avec les adresses de l'interface qui
	y mène : le PC n'écoute que sur celles du réseau de la maison.
	id        : réseau reconnu (Wi-Fi ou box)
	addresses : adresses de l'interface
*/
data class LocalNetwork(
	val id        : NetworkId,
	val addresses : List<LocalAddress>
)
