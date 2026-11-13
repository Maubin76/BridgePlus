package fr.maubin.bridgeplus.transfer

import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.fromBase64
import fr.maubin.bridgeplus.link.randomBytes
import fr.maubin.bridgeplus.link.toBase64
import fr.maubin.bridgeplus.text.relativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

const val kChunkBytes          = 1024 * 1024										// taille d'un morceau (1 Mo)
const val kSpaceMarginBytes    = 16L * 1024 * 1024								// marge d'espace libre gardée en plus du fichier
const val kKeptTransfers       = 20												// transferts terminés gardés dans la liste
private const val kAnswerTimeoutMs  = 30_000L									// attente de la réponse à une proposition
private const val kVerdictTimeoutMs = 120_000L									// attente du verdict (vérification, rangement)

/*
	État d'un transfert.
*/
enum class TransferState
{
	Waiting,																			// en file, ou en attente de la réponse
	Running,																			// morceaux en cours
	Verifying,																			// empreinte en vérification chez le destinataire
	Done,																				// fichier rangé et vérifié
	Failed,																				// échec (raison)
	Cancelled																			// annulé par l'un ou l'autre côté
}

/*
	Transfert d'un fichier, dans un sens ou dans l'autre.
	id       : identifiant
	name     : nom du fichier
	size     : taille (octets)
	done     : octets passés
	outgoing : vrai pour un envoi
	peerId   : appareil concerné
	peer     : son nom
	state    : état
	reason   : raison d'un échec ou d'une annulation ; null sinon
	at       : début (ms)
	location : emplacement du fichier reçu (chemin, adresse) ; vide avant la fin
*/
data class Transfer(
	val id       : String,
	val name     : String,
	val size     : Long,
	val done     : Long,
	val outgoing : Boolean,
	val peerId   : String,
	val peer     : String,
	val state    : TransferState,
	val reason   : String? = null,
	val at       : Long,
	val location : String  = ""
)
{
	// Vrai tant que le transfert n'est pas terminé.
	val active: Boolean get() = state == TransferState.Waiting || state == TransferState.Running || state == TransferState.Verifying

	// Avancement, de 0 à 1.
	val progress: Float get() = if (size <= 0) (if (state == TransferState.Done) 1f else 0f) else (done.toFloat() / size).coerceIn(0f, 1f)
}

/*
	Moteur des transferts de fichiers, le même sur le téléphone et sur le PC.
	Envoi : proposition (nom, taille), réponse du destinataire (espace
	disque), morceaux de 1 Mo, puis empreinte SHA-256 calculée en route ;
	les fichiers d'un même appareil partent l'un après l'autre. Réception :
	espace vérifié avant d'accepter, écriture sous un nom provisoire,
	empreinte vérifiée, puis rangement sous un nom libre ; jamais de fichier
	à moitié écrit visible. L'annulation et la coupure de la connexion ne
	laissent rien. Aucune limite de taille, hormis l'espace disque.
	fSend       : envoie un message à un appareil ; faux s'il n'est pas relié
	fSendChunk  : envoie un morceau à un appareil ; faux s'il n'est pas relié
	fSink       : dossier de réception
	fOnFinished : transfert terminé (notification, historique)
	fChunkBytes : taille d'un morceau
	fNow        : horloge (ms)
*/
class FileTransfers(
	private val fSend       : (String, LinkMessage) -> Boolean,
	private val fSendChunk  : (String, ByteArray) -> Boolean,
	private val fSink       : FileSink,
	private val fOnFinished : (Transfer) -> Unit = {},
	private val fChunkBytes : Int               = kChunkBytes,
	private val fNow        : () -> Long        = System::currentTimeMillis
)
{
	private val fTransfers = ConcurrentHashMap<String, Transfer>()					// transferts, par identifiant
	private val fState     = MutableStateFlow<List<Transfer>>(emptyList())			// transferts publiés
	private val fReplies   = ConcurrentHashMap<String, LinkedBlockingQueue<LinkMessage>>()	// réponses attendues par les envois
	private val fIncoming  = ConcurrentHashMap<String, Incoming>()					// réceptions en cours
	private val fQueues    = ConcurrentHashMap<String, ExecutorService>()			// files d'envoi, par appareil

	/*
		Transferts en cours, puis les derniers terminés, du plus récent au
		plus ancien.
	*/
	val transfers: StateFlow<List<Transfer>> = fState

	/*
		Réception en cours.
		peerId   : appareil qui envoie
		file     : fichier provisoire
		digest   : empreinte calculée en route
		received : octets reçus
	*/
	private class Incoming(
		val peerId   : String,
		val file     : IncomingFile,
		val digest   : MessageDigest,
		var received : Long = 0
	)

	/*
		Met des fichiers en file d'envoi vers un appareil ; rend leurs
		transferts.
		inPeerId  : appareil
		inPeer    : son nom
		inSources : fichiers
	*/
	fun send(
		inPeerId  : String,
		inPeer    : String,
		inSources : List<FileSource>
	): List<Transfer>
	{
		val vQueue = fQueues.computeIfAbsent(inPeerId) { Executors.newSingleThreadExecutor { inTask -> Thread(inTask, "BridgePlus-envoi-fichiers").apply { isDaemon = true } } }	// file de l'appareil
		return inSources.map { vSource ->
			val vTransfer = Transfer(toBase64(randomBytes(12)), vSource.name, vSource.size, 0, true, inPeerId, inPeer, TransferState.Waiting, at = fNow())	// transfert
			fReplies[vTransfer.id] = LinkedBlockingQueue()
			publish(vTransfer)
			vQueue.execute { runSend(vTransfer, vSource) }
			vTransfer
		}
	}

	/*
		Annule un transfert (dans un sens ou dans l'autre) : l'autre appareil
		est prévenu, rien ne reste.
		inId : transfert
	*/
	fun cancel(inId: String)
	{
		val vTransfer = fTransfers[inId]?.takeIf { it.active } ?: return						// transfert en cours
		if (vTransfer.outgoing)
		{
			fReplies[inId]?.add(LinkMessage.FileCancel(inId, "annulé ici"))
			if (vTransfer.state == TransferState.Waiting) finish(vTransfer.copy(state = TransferState.Cancelled, reason = "Annulé"))
			return
		}
		fIncoming.remove(inId)?.file?.abort()
		fSend(vTransfer.peerId, LinkMessage.FileCancel(inId, "annulé par le destinataire"))
		finish(vTransfer.copy(state = TransferState.Cancelled, reason = "Annulé"))
	}

	/*
		Message reçu d'un appareil, qui concerne les fichiers ; faux s'il ne
		les concerne pas.
		inPeerId  : appareil
		inPeer    : son nom
		inMessage : message
	*/
	fun onMessage(
		inPeerId  : String,
		inPeer    : String,
		inMessage : LinkMessage
	): Boolean
	{
		when (inMessage)
		{
			is LinkMessage.FileOffer   -> receiveOffer(inPeerId, inPeer, inMessage)
			is LinkMessage.FileEnd     -> receiveEnd(inMessage)
			is LinkMessage.FileAnswer  -> fReplies[inMessage.id]?.add(inMessage)
			is LinkMessage.FileVerdict -> fReplies[inMessage.id]?.add(inMessage)
			is LinkMessage.FileCancel  ->
			{
				fReplies[inMessage.id]?.add(inMessage)
				fIncoming.remove(inMessage.id)?.let { vIn ->
					vIn.file.abort()
					fTransfers[inMessage.id]?.let { finish(it.copy(state = TransferState.Cancelled, reason = "Annulé par l'expéditeur")) }
				}
			}
			else                       -> return false
		}
		return true
	}

	/*
		Morceau reçu : écrit à la suite, s'il arrive à sa place.
		inPeerId : appareil
		inChunk  : identifiant, position, contenu
	*/
	fun onChunk(
		inPeerId : String,
		inChunk  : ByteArray
	)
	{
		val vBuffer = ByteBuffer.wrap(inChunk)													// lecture du morceau
		val vIdSize = vBuffer.get().toInt() and 0xFF												// longueur de l'identifiant
		if (inChunk.size < 1 + vIdSize + 8) return
		val vId     = String(inChunk, 1, vIdSize, Charsets.UTF_8)									// transfert
		vBuffer.position(1 + vIdSize)
		val vOffset = vBuffer.long																	// position annoncée
		val vIn     = fIncoming[vId]?.takeIf { it.peerId == inPeerId } ?: return					// réception
		val vData   = inChunk.copyOfRange(1 + vIdSize + 8, inChunk.size)							// contenu
		try
		{
			if (vOffset != vIn.received) throw IOException("morceau hors d'ordre")
			vIn.file.output.write(vData)
			vIn.digest.update(vData)
			vIn.received += vData.size
			fTransfers[vId]?.let { publish(it.copy(done = vIn.received, state = TransferState.Running)) }
		}
		catch (inError: IOException)
		{
			fIncoming.remove(vId)
			vIn.file.abort()
			fSend(inPeerId, LinkMessage.FileCancel(vId, "écriture impossible : ${inError.message}"))
			fTransfers[vId]?.let { finish(it.copy(state = TransferState.Failed, reason = "Écriture impossible : ${inError.message ?: "erreur du disque"}")) }
		}
	}

	/*
		La connexion avec un appareil est tombée : ses transferts en cours
		échouent, et rien ne reste des réceptions.
		inPeerId : appareil
	*/
	fun onDisconnected(inPeerId: String)
	{
		fIncoming.entries.filter { it.value.peerId == inPeerId }.forEach { (vId, vIn) ->
			fIncoming.remove(vId)
			vIn.file.abort()
		}
		fTransfers.values.filter { it.peerId == inPeerId && it.active }.forEach { vTransfer ->
			if (vTransfer.outgoing) fReplies[vTransfer.id]?.add(LinkMessage.FileCancel(vTransfer.id, "connexion coupée"))
			else finish(vTransfer.copy(state = TransferState.Failed, reason = "Connexion coupée"))
		}
	}

	/*
		Envoie un fichier : proposition, réponse, morceaux, empreinte, verdict.
		inTransfer : transfert
		inSource   : fichier
	*/
	private fun runSend(
		inTransfer : Transfer,
		inSource   : FileSource
	)
	{
		val vReplies = fReplies[inTransfer.id] ?: return										// réponses attendues
		var vCurrent = fTransfers[inTransfer.id] ?: return										// état courant
		if (!vCurrent.active) return
		var vStream  : InputStream? = null														// contenu
		try
		{
			if (!fSend(inTransfer.peerId, LinkMessage.FileOffer(inTransfer.id, inTransfer.name, inTransfer.size, inSource.mime))) return fail(vCurrent, "Appareil non relié")
			val vAnswer  = vReplies.poll(kAnswerTimeoutMs, TimeUnit.MILLISECONDS)				// réponse
			when (vAnswer)
			{
				is LinkMessage.FileAnswer -> if (!vAnswer.accepted) return fail(vCurrent, vAnswer.reason.ifEmpty { "Refusé" })
				is LinkMessage.FileCancel -> return cancelled(vCurrent, vAnswer)
				else                      -> return fail(vCurrent, "Pas de réponse de ${inTransfer.peer}")
			}
			vStream      = inSource.open()
			val vDigest  = MessageDigest.getInstance("SHA-256")									// empreinte en route
			val vId      = inTransfer.id.toByteArray(Charsets.UTF_8)								// identifiant (en tête des morceaux)
			val vBuffer  = ByteArray(fChunkBytes)													// lecture
			var vSent    = 0L																		// octets partis
			vCurrent     = vCurrent.copy(state = TransferState.Running).also(::publish)
			while (true)
			{
				(vReplies.poll() as? LinkMessage.FileCancel)?.let { return cancelled(vCurrent, it) }
				val vRead = vStream.readNBytes(vBuffer, 0, vBuffer.size)							// octets lus
				if (vRead <= 0) break
				vDigest.update(vBuffer, 0, vRead)
				val vChunk = ByteBuffer.allocate(1 + vId.size + 8 + vRead).put(vId.size.toByte()).put(vId).putLong(vSent).put(vBuffer, 0, vRead).array()	// morceau
				if (!fSendChunk(inTransfer.peerId, vChunk)) return fail(vCurrent, "Connexion coupée")
				vSent   += vRead
				vCurrent = vCurrent.copy(done = vSent).also(::publish)
			}
			if (vSent != inTransfer.size) return fail(vCurrent, "Le fichier a changé pendant l'envoi").also { fSend(inTransfer.peerId, LinkMessage.FileCancel(inTransfer.id, "fichier changé")) }
			vCurrent = vCurrent.copy(state = TransferState.Verifying).also(::publish)
			if (!fSend(inTransfer.peerId, LinkMessage.FileEnd(inTransfer.id, toBase64(vDigest.digest())))) return fail(vCurrent, "Connexion coupée")
			when (val vVerdict = vReplies.poll(kVerdictTimeoutMs, TimeUnit.MILLISECONDS))		// verdict
			{
				is LinkMessage.FileVerdict -> if (vVerdict.ok) finish(vCurrent.copy(state = TransferState.Done)) else fail(vCurrent, vVerdict.reason.ifEmpty { "Refusé à l'arrivée" })
				is LinkMessage.FileCancel  -> cancelled(vCurrent, vVerdict)
				else                       -> fail(vCurrent, "Pas de confirmation de ${inTransfer.peer}")
			}
		}
		catch (inError: IOException)
		{
			fSend(inTransfer.peerId, LinkMessage.FileCancel(inTransfer.id, "lecture impossible"))
			fail(vCurrent, "Lecture impossible : ${inError.message ?: "fichier inaccessible"}")
		}
		finally
		{
			runCatching { vStream?.close() }
			fReplies.remove(inTransfer.id)
		}
	}

	/*
		Proposition reçue : acceptée si l'espace suffit (avec une marge), le
		fichier provisoire préparé.
		inPeerId : appareil
		inPeer   : son nom
		inOffer  : proposition
	*/
	private fun receiveOffer(
		inPeerId : String,
		inPeer   : String,
		inOffer  : LinkMessage.FileOffer
	)
	{
		val vName     = safeFileName(inOffer.name)													// nom sûr
		val vTransfer = Transfer(inOffer.id, vName, inOffer.size, 0, false, inPeerId, inPeer, TransferState.Waiting, at = fNow())	// transfert
		val vFree     = runCatching { fSink.freeBytes() }.getOrDefault(0L)							// espace libre
		if (inOffer.size < 0 || vFree < inOffer.size + kSpaceMarginBytes)
		{
			val vReason = "Espace insuffisant : ${sizeText(inOffer.size)} nécessaires, ${sizeText(vFree)} libres"	// raison du refus
			fSend(inPeerId, LinkMessage.FileAnswer(inOffer.id, false, vReason))
			finish(vTransfer.copy(state = TransferState.Failed, reason = vReason))
			return
		}
		val vFile = runCatching { fSink.create(vName, inOffer.size, inOffer.mime) }.getOrElse {	// fichier provisoire
			fSend(inPeerId, LinkMessage.FileAnswer(inOffer.id, false, "Réception impossible : ${it.message}"))
			finish(vTransfer.copy(state = TransferState.Failed, reason = "Réception impossible : ${it.message}"))
			return
		}
		fIncoming[inOffer.id] = Incoming(inPeerId, vFile, MessageDigest.getInstance("SHA-256"))
		publish(vTransfer.copy(state = TransferState.Running))
		fSend(inPeerId, LinkMessage.FileAnswer(inOffer.id, true))
	}

	/*
		Fin d'une réception : taille et empreinte vérifiées, puis fichier rangé
		sous un nom libre ; sinon effacé.
		inEnd : fin annoncée
	*/
	private fun receiveEnd(inEnd: LinkMessage.FileEnd)
	{
		val vIn       = fIncoming.remove(inEnd.id) ?: return										// réception
		val vTransfer = fTransfers[inEnd.id] ?: return												// transfert
		publish(vTransfer.copy(state = TransferState.Verifying))
		val vExpected = fromBase64(inEnd.sha256)													// empreinte annoncée
		val vOk       = vIn.received == vTransfer.size && vExpected != null && vExpected.contentEquals(vIn.digest.digest())	// contenu intact
		if (!vOk)
		{
			vIn.file.abort()
			fSend(vIn.peerId, LinkMessage.FileVerdict(inEnd.id, false, "Empreinte différente : fichier abîmé en route"))
			finish(vTransfer.copy(state = TransferState.Failed, reason = "Empreinte différente : fichier abîmé en route"))
			return
		}
		val vWhere = runCatching { vIn.file.commit() }.getOrElse {								// emplacement final
			vIn.file.abort()
			fSend(vIn.peerId, LinkMessage.FileVerdict(inEnd.id, false, "Rangement impossible : ${it.message}"))
			finish(vTransfer.copy(state = TransferState.Failed, reason = "Rangement impossible : ${it.message}"))
			return
		}
		fSend(vIn.peerId, LinkMessage.FileVerdict(inEnd.id, true))
		finish(vTransfer.copy(state = TransferState.Done, done = vTransfer.size, location = vWhere))
	}

	/*
		Envoi annulé par l'un ou l'autre côté.
		inTransfer : transfert
		inCancel   : annulation reçue (ou locale)
	*/
	private fun cancelled(
		inTransfer : Transfer,
		inCancel   : LinkMessage.FileCancel
	)
	{
		val vHere = inCancel.reason == "annulé ici"													// annulé de ce côté
		if (vHere) fSend(inTransfer.peerId, LinkMessage.FileCancel(inTransfer.id, "annulé par l'expéditeur"))
		if (inCancel.reason == "connexion coupée") return fail(inTransfer, "Connexion coupée")
		finish(inTransfer.copy(state = TransferState.Cancelled, reason = if (vHere) "Annulé" else "Annulé par ${inTransfer.peer}"))
	}

	/*
		Échec d'un transfert.
		inTransfer : transfert
		inReason   : raison
	*/
	private fun fail(
		inTransfer : Transfer,
		inReason   : String
	) = finish(inTransfer.copy(state = TransferState.Failed, reason = inReason))

	/*
		Transfert terminé (réussi, échoué ou annulé) : publié une dernière
		fois, puis signalé.
		inTransfer : transfert
	*/
	private fun finish(inTransfer: Transfer)
	{
		publish(inTransfer)
		runCatching { fOnFinished(inTransfer) }
	}

	/*
		Publie l'état d'un transfert ; ne garde que les derniers terminés.
		inTransfer : transfert
	*/
	@Synchronized
	private fun publish(inTransfer: Transfer)
	{
		fTransfers[inTransfer.id] = inTransfer
		val vDone = fTransfers.values.filter { !it.active }.sortedByDescending { it.at }			// terminés
		vDone.drop(kKeptTransfers).forEach { fTransfers.remove(it.id) }
		fState.value = fTransfers.values.sortedByDescending { it.at }
	}
}

/*
	État d'un transfert en clair, pour les listes : « Vers Pixel · 12,3 Mo
	sur 50,0 Mo », « De Bureau · 2,1 Mo · il y a 3 min », ou la raison d'un
	échec.
	inTransfer : transfert
	inNow      : maintenant (ms)
*/
fun transferDetail(
	inTransfer : Transfer,
	inNow      : Long = System.currentTimeMillis()
): String
{
	val vWho = "${if (inTransfer.outgoing) "Vers" else "De"} ${inTransfer.peer}"					// sens et appareil
	return when (inTransfer.state)
	{
		TransferState.Waiting   -> "$vWho · en attente"
		TransferState.Running   -> "$vWho · ${sizeText(inTransfer.done)} sur ${sizeText(inTransfer.size)}"
		TransferState.Verifying -> "$vWho · vérification"
		TransferState.Done      -> "$vWho · ${sizeText(inTransfer.size)} · ${relativeTime(inTransfer.at, inNow)}"
		TransferState.Failed,
		TransferState.Cancelled -> "$vWho · ${inTransfer.reason ?: "interrompu"}"
	}
}
