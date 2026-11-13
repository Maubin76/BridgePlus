package fr.maubin.bridgeplus.history

import fr.maubin.bridgeplus.link.randomBytes
import fr.maubin.bridgeplus.link.toBase64
import fr.maubin.bridgeplus.store.SealedFile
import fr.maubin.bridgeplus.store.SecretBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

const val kHistoryLimit   = 50														// éléments non épinglés gardés
const val kPreviewChars   = 200														// longueur de l'aperçu gardé dans l'index
const val kKindText       = "texte"													// élément : texte du presse-papiers
private const val kSearchCacheChars = 64 * 1024										// textes gardés en mémoire pour la recherche (au-delà : aperçu seul)

private val kHistoryJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }	// index de l'historique (versions voisines tolérées)

/*
	Élément de l'historique : un échange, avec un aperçu ; le texte complet
	est dans son propre fichier scellé.
	id          : identifiant (nom de son fichier)
	kind        : nature (kKindText ; les fichiers viendront à l'étape 4)
	outgoing    : vrai pour un envoi, faux pour une réception
	peers       : appareils concernés
	at          : date du dernier échange (ms)
	pinned      : épinglé : gardé au-delà des 50 derniers
	preview     : début du texte (200 caractères au plus)
	length      : longueur du texte complet (caractères)
	fingerprint : empreinte du texte (SHA-256), pour reconnaître un texte déjà présent
*/
@Serializable
data class HistoryItem(
	val id          : String,
	val kind        : String       = kKindText,
	val outgoing    : Boolean,
	val peers       : List<String> = emptyList(),
	val at          : Long,
	val pinned      : Boolean      = false,
	val preview     : String,
	val length      : Int,
	val fingerprint : String
)

/*
	Index de l'historique (scellé).
	items : éléments, du plus récent au plus ancien
*/
@Serializable
data class HistoryIndex(val items: List<HistoryItem> = emptyList())

/*
	Historique des échanges du presse-papiers, conservé chiffré : un index
	scellé (aperçus, dates, épingles) et un fichier scellé par texte, pour ne
	jamais réécrire 50 Mo à chaque échange. Les 50 derniers éléments non
	épinglés sont gardés, les épinglés en plus ; un texte échangé de nouveau
	remonte en tête (sans doublon, épingle conservée). Rien de sensible n'y
	arrive : un contenu marqué sensible n'est jamais échangé.
	fDir   : dossier de l'historique
	fBox   : coffret (Keystore sur Android, DPAPI sur Windows)
	fNow   : horloge (ms)
	fLimit : éléments non épinglés gardés
*/
class ClipHistory(
	private val fDir   : File,
	private val fBox   : SecretBox,
	private val fNow   : () -> Long = System::currentTimeMillis,
	private val fLimit : Int        = kHistoryLimit
)
{
	private val fIndex  = SealedFile(File(fDir, "index.bin"), fBox)					// index scellé
	private val fCache  = HashMap<String, String>()									// textes déjà lus (recherche), par identifiant
	private val fItems  = MutableStateFlow(load())										// éléments

	/*
		Éléments, du plus récent au plus ancien.
	*/
	val items: StateFlow<List<HistoryItem>> = fItems

	/*
		Ajoute un échange en tête ; un texte déjà présent remonte (date et
		appareils mis à jour, épingle gardée). Les plus anciens non épinglés
		au-delà de la limite sont effacés.
		inText     : texte échangé
		inOutgoing : vrai pour un envoi
		inPeers    : appareils concernés
	*/
	@Synchronized
	fun add(
		inText     : String,
		inOutgoing : Boolean,
		inPeers    : List<String>
	): HistoryItem
	{
		val vPrint = fingerprint(inText)															// empreinte
		val vOld   = fItems.value.firstOrNull { it.fingerprint == vPrint }							// texte déjà présent
		val vItem  = HistoryItem(
			id          = vOld?.id ?: toBase64(randomBytes(12)),
			outgoing    = inOutgoing,
			peers       = inPeers,
			at          = fNow(),
			pinned      = vOld?.pinned ?: false,
			preview     = inText.take(kPreviewChars),
			length      = inText.length,
			fingerprint = vPrint
		)																							// élément
		if (vOld == null) textFile(vItem.id).write(inText)
		if (inText.length <= kSearchCacheChars) fCache[vItem.id] = inText
		val vAll   = listOf(vItem) + fItems.value.filter { it.id != vItem.id }						// nouvel ordre
		val vKept  = trim(vAll)																	// éléments gardés
		save(vKept)
		return vItem
	}

	/*
		Texte complet d'un élément ; null s'il n'existe plus ou est illisible.
		inId : élément
	*/
	@Synchronized
	fun text(inId: String): String?
	{
		fCache[inId]?.let { return it }
		if (fItems.value.none { it.id == inId }) return null
		return textFile(inId).read()?.also { if (it.length <= kSearchCacheChars) fCache[inId] = it }
	}

	/*
		Épingle ou désépingle un élément.
		inId : élément
		inOn : vrai pour épingler
	*/
	@Synchronized
	fun pin(
		inId : String,
		inOn : Boolean
	) = save(trim(fItems.value.map { if (it.id == inId) it.copy(pinned = inOn) else it }))

	/*
		Supprime un élément et son texte.
		inId : élément
	*/
	@Synchronized
	fun delete(inId: String)
	{
		textFile(inId).delete()
		fCache.remove(inId)
		save(fItems.value.filter { it.id != inId })
	}

	/*
		Efface tout l'historique, épingles comprises.
	*/
	@Synchronized
	fun clear()
	{
		fItems.value.forEach { textFile(it.id).delete() }
		fCache.clear()
		save(emptyList())
	}

	/*
		Éléments qui contiennent la recherche (sans tenir compte des
		majuscules ni des accents), épinglés d'abord puis du plus récent au
		plus ancien. Une recherche vide rend tout.
		inQuery : recherche
	*/
	@Synchronized
	fun search(inQuery: String): List<HistoryItem>
	{
		val vQuery = normalize(inQuery.trim())													// recherche normalisée
		val vFound = if (vQuery.isEmpty()) fItems.value else fItems.value.filter { vItem ->
			normalize(searchable(vItem)).contains(vQuery)
		}																						// éléments trouvés
		return vFound.sortedWith(compareByDescending<HistoryItem> { it.pinned }.thenByDescending { it.at })
	}

	/*
		Texte où chercher : le texte complet s'il est court (gardé en mémoire),
		sinon l'aperçu.
		inItem : élément
	*/
	private fun searchable(inItem: HistoryItem): String =
		fCache[inItem.id] ?: if (inItem.length <= kSearchCacheChars) text(inItem.id) ?: inItem.preview else inItem.preview

	/*
		Garde les épinglés et les fLimit plus récents non épinglés ; efface les
		textes des autres.
		inItems : éléments, du plus récent au plus ancien
	*/
	private fun trim(inItems: List<HistoryItem>): List<HistoryItem>
	{
		val vDropped = inItems.filter { !it.pinned }.drop(fLimit).map { it.id }.toSet()			// éléments en trop
		vDropped.forEach { textFile(it).delete(); fCache.remove(it) }
		return inItems.filter { it.id !in vDropped }
	}

	/*
		Enregistre l'index et publie les éléments.
		inItems : éléments
	*/
	private fun save(inItems: List<HistoryItem>)
	{
		fIndex.write(kHistoryJson.encodeToString(HistoryIndex.serializer(), HistoryIndex(inItems)))
		fItems.value = inItems
	}

	/*
		Index enregistré ; vide s'il est absent ou illisible.
	*/
	private fun load(): List<HistoryItem> =
		fIndex.read()?.let { vText -> runCatching { kHistoryJson.decodeFromString(HistoryIndex.serializer(), vText).items }.getOrNull() }.orEmpty()

	/*
		Fichier scellé du texte d'un élément.
		inId : élément
	*/
	private fun textFile(inId: String): SealedFile = SealedFile(File(fDir, "$inId.bin"), fBox)

	companion object
	{
		/*
			Texte comparable : minuscules, sans accents.
			inText : texte
		*/
		fun normalize(inText: String): String =
			Normalizer.normalize(inText, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

		/*
			Empreinte d'un texte (SHA-256, base64).
			inText : texte
		*/
		fun fingerprint(inText: String): String =
			toBase64(MessageDigest.getInstance("SHA-256").digest(inText.toByteArray(Charsets.UTF_8)))
	}
}
