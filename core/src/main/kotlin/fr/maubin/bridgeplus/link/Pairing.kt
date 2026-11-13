package fr.maubin.bridgeplus.link

import java.net.URLDecoder
import java.net.URLEncoder

private const val kPairScheme = "bridgeplus://appairer"							// début du contenu du QR code
const val kPairValidityMs     = 5L * 60 * 1000										// durée de validité d'une offre d'appairage (5 min)

/*
	Offre d'appairage affichée par le PC dans un QR code : de quoi joindre le
	PC et la clé secrète de la paire, valable cinq minutes. Le téléphone qui
	la scanne ouvre une session avec cette clé ; personne d'autre ne la connaît.
	pcId      : identifiant du PC
	pcName    : nom du PC
	addresses : adresses du PC sur le réseau local
	port      : port du serveur du PC
	key       : clé secrète de la paire
	expires   : fin de validité (ms)
*/
data class PairingOffer(
	val pcId      : String,
	val pcName    : String,
	val addresses : List<String>,
	val port      : Int,
	val key       : ByteArray,
	val expires   : Long
)
{
	/*
		Contenu du QR code.
	*/
	fun encode(): String =
		"$kPairScheme?v=$kLinkProtocol&pc=${enc(pcId)}&nom=${enc(pcName)}&ip=${enc(addresses.joinToString(","))}&port=$port&cle=${toBase64(key)}&fin=$expires"

	override fun equals(other: Any?): Boolean =
		other is PairingOffer && pcId == other.pcId && pcName == other.pcName && addresses == other.addresses && port == other.port && key.contentEquals(other.key) && expires == other.expires

	override fun hashCode(): Int = pcId.hashCode()

	companion object
	{
		/*
			Offre lue dans un QR code ; null si ce n'est pas une offre de
			BridgePlus lisible (un QR code de KeePlus, par exemple), ou d'un autre
			protocole.
			inText : contenu du QR code
		*/
		fun decode(inText: String): PairingOffer?
		{
			if (!inText.startsWith("$kPairScheme?")) return null
			val vParams = inText.substringAfter('?').split('&').mapNotNull { inPair ->
				inPair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to URLDecoder.decode(it[1], Charsets.UTF_8) }
			}.toMap()																	// paramètres
			if (vParams["v"]?.toIntOrNull() != kLinkProtocol) return null
			return PairingOffer(
				pcId      = vParams["pc"]?.takeIf { it.isNotBlank() } ?: return null,
				pcName    = vParams["nom"].orEmpty(),
				addresses = vParams["ip"].orEmpty().split(',').filter { it.isNotBlank() },
				port      = vParams["port"]?.toIntOrNull() ?: return null,
				key       = vParams["cle"]?.let(::fromBase64)?.takeIf { it.size == kLinkKeyBytes } ?: return null,
				expires   = vParams["fin"]?.toLongOrNull() ?: return null
			)
		}

		/*
			Valeur encodée pour une adresse.
			inText : valeur
		*/
		private fun enc(inText: String): String = URLEncoder.encode(inText, Charsets.UTF_8)
	}
}
