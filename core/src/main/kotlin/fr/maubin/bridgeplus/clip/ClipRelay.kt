package fr.maubin.bridgeplus.clip

import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.randomBytes
import fr.maubin.bridgeplus.link.toBase64
import fr.maubin.bridgeplus.settings.BridgeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.security.MessageDigest

const val kMaxClipBytes   = 1024 * 1024											// taille maximale d'un texte (1 Mo en UTF-8)
const val kEchoWindowMs   = 5L * 60 * 1000										// durée pendant laquelle un texte échangé n'est pas renvoyé
const val kRecentClips    = 20													// derniers échanges gardés en mémoire

/*
	Échange du presse-papiers, pour la liste des derniers échanges (en
	mémoire seulement à cette étape ; l'historique chiffré viendra ensuite).
	id       : identifiant de l'envoi
	text     : texte
	outgoing : vrai pour un texte envoyé, faux pour un texte reçu
	peers    : appareils concernés (destinataires, ou expéditeur)
	at       : date (ms)
*/
data class ClipEntry(
	val id       : String,
	val text     : String,
	val outgoing : Boolean,
	val peers    : List<String>,
	val at       : Long
)

/*
	Issue d'une copie proposée à l'envoi.
*/
enum class ClipOutcome
{
	Sent,																				// envoyé à au moins un appareil
	NoPeer,																				// aucun appareil relié
	Disabled,																			// envoi automatique désactivé
	Sensitive,																			// contenu marqué sensible : jamais envoyé
	TooLarge,																			// plus de 1 Mo
	Empty,																				// rien à envoyer
	Echo																				// texte qui vient d'être échangé : pas renvoyé
}

/*
	Résultat d'une copie proposée à l'envoi.
	outcome : issue
	peers   : appareils qui l'ont reçue
	bytes   : taille du texte en UTF-8
*/
data class ClipResult(
	val outcome : ClipOutcome,
	val peers   : List<String> = emptyList(),
	val bytes   : Int          = 0
)

/*
	Relais du presse-papiers, le même sur le téléphone et sur le PC : décide
	si une copie part (jamais un contenu sensible, 1 Mo au plus, selon le
	réglage), l'envoie à tous les appareils reliés, et place dans le
	presse-papiers les textes reçus. Un texte échangé dans les cinq dernières
	minutes n'est pas renvoyé : poser un texte reçu dans le presse-papiers
	déclenche une « copie » locale qui, sans cette garde, repartirait aussitôt
	(écho). Le texte n'est jamais écrit dans un journal.
	fSend     : envoie un message à tous les appareils reliés ; rend leurs noms
	fApply    : place un texte reçu dans le presse-papiers local ; rend faux si
	            le système refuse (le téléphone propose alors une notification)
	fSettings : réglages en cours
	fNow      : horloge (ms)
*/
class ClipRelay(
	private val fSend     : (LinkMessage) -> List<String>,
	private val fApply    : (String) -> Boolean,
	private val fSettings : () -> BridgeSettings,
	private val fNow      : () -> Long = System::currentTimeMillis
)
{
	private val fSeen   = LinkedHashMap<String, Long>()								// empreintes des textes échangés et leur date
	private val fRecent = MutableStateFlow<List<ClipEntry>>(emptyList())				// derniers échanges

	/*
		Derniers échanges, du plus récent au plus ancien.
	*/
	val recent: StateFlow<List<ClipEntry>> = fRecent

	/*
		Propose une copie locale à l'envoi.
		inText      : texte copié ; null s'il n'y a pas de texte
		inSensitive : contenu marqué sensible par l'appli qui l'a copié
		inExplicit  : geste explicite (tuile, partage, menu) : envoyé même si
		              l'envoi automatique est désactivé
	*/
	@Synchronized
	fun offer(
		inText      : String?,
		inSensitive : Boolean,
		inExplicit  : Boolean = false
	): ClipResult
	{
		if (inSensitive) return ClipResult(ClipOutcome.Sensitive)
		if (inText.isNullOrBlank()) return ClipResult(ClipOutcome.Empty)
		val vBytes = inText.toByteArray(Charsets.UTF_8).size										// taille en UTF-8
		if (vBytes > kMaxClipBytes) return ClipResult(ClipOutcome.TooLarge, bytes = vBytes)
		if (!inExplicit && !fSettings().clipSend) return ClipResult(ClipOutcome.Disabled, bytes = vBytes)
		val vPrint = fingerprint(inText)															// empreinte
		if (!inExplicit && recentlySeen(vPrint)) return ClipResult(ClipOutcome.Echo, bytes = vBytes)
		val vClip  = LinkMessage.Clip(toBase64(randomBytes(9)), inText, fNow())					// message
		val vPeers = fSend(vClip)																	// appareils joints
		if (vPeers.isEmpty()) return ClipResult(ClipOutcome.NoPeer, bytes = vBytes)
		fSeen[vPrint] = fNow()
		remember(ClipEntry(vClip.id, inText, true, vPeers, vClip.at))
		return ClipResult(ClipOutcome.Sent, vPeers, vBytes)
	}

	/*
		Texte reçu d'un appareil : placé dans le presse-papiers si la
		réception est activée. Rend vrai s'il y a été placé.
		inPeer : nom de l'appareil qui l'envoie
		inClip : message reçu
	*/
	@Synchronized
	fun receive(
		inPeer : String,
		inClip : LinkMessage.Clip
	): Boolean
	{
		if (!fSettings().clipReceive) return false
		if (inClip.text.isEmpty() || inClip.text.toByteArray(Charsets.UTF_8).size > kMaxClipBytes) return false
		fSeen[fingerprint(inClip.text)] = fNow()
		remember(ClipEntry(inClip.id, inClip.text, false, listOf(inPeer), fNow()))
		return fApply(inClip.text)
	}

	/*
		Oublie les derniers échanges (rien n'est gardé sur le disque).
	*/
	@Synchronized
	fun clear() = fRecent.update { emptyList() }

	/*
		Vrai si ce texte a été échangé il y a moins de cinq minutes.
		inPrint : empreinte du texte
	*/
	private fun recentlySeen(inPrint: String): Boolean
	{
		val vNow = fNow()																			// maintenant
		fSeen.entries.removeIf { vNow - it.value > kEchoWindowMs }
		return inPrint in fSeen
	}

	/*
		Ajoute un échange en tête des derniers échanges.
		inEntry : échange
	*/
	private fun remember(inEntry: ClipEntry) = fRecent.update { (listOf(inEntry) + it).take(kRecentClips) }

	/*
		Empreinte d'un texte (SHA-256), pour reconnaître un écho sans garder
		le texte.
		inText : texte
	*/
	private fun fingerprint(inText: String): String =
		toBase64(MessageDigest.getInstance("SHA-256").digest(inText.toByteArray(Charsets.UTF_8)))
}

/*
	Résultat d'un envoi en clair, pour une confirmation brève (toast,
	notification) ; null quand il n'y a rien à dire (envoi automatique
	désactivé, écho).
	inResult : résultat
*/
fun clipMessage(inResult: ClipResult): String? =
	when (inResult.outcome)
	{
		ClipOutcome.Sent      -> "Envoyé à ${inResult.peers.joinToString(" et ")}"
		ClipOutcome.NoPeer    -> "Aucun appareil relié : le texte n'est pas parti."
		ClipOutcome.Sensitive -> "Contenu sensible : il n'est jamais envoyé."
		ClipOutcome.TooLarge  -> "Texte trop long (${String.format(java.util.Locale.FRENCH, "%.1f", inResult.bytes / (1024.0 * 1024.0))} Mo) : 1 Mo au plus."
		ClipOutcome.Empty     -> "Le presse-papiers ne contient pas de texte."
		ClipOutcome.Disabled,
		ClipOutcome.Echo      -> null
	}
