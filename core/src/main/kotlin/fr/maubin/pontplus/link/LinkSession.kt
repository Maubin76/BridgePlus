package fr.maubin.pontplus.link

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

const val kPingEveryMs      = 30_000L												// battement de cœur du PC
const val kPcDeadAfterMs    = 75_000L												// PC : session morte sans rien reçu pendant ce délai
const val kPhoneDeadAfterMs = 90_000L												// téléphone : session morte sans rien reçu pendant ce délai

private const val kPayloadMessage : Byte = 0										// contenu : message JSON
private const val kPayloadChunk   : Byte = 1										// contenu : morceau de fichier (binaire)

/*
	Ce qu'une session remet à son propriétaire. Les appels arrivent sur le fil
	de lecture de la session : ils doivent rendre la main vite.
*/
interface SessionListener
{
	/*
		Message reçu (Ping déjà traité : il n'arrive pas ici).
		inSession : session
		inMessage : message
	*/
	fun onMessage(
		inSession : LinkSession,
		inMessage : LinkMessage
	)

	/*
		Morceau de fichier reçu (envoi de fichiers, étape 4).
		inSession : session
		inChunk   : contenu en clair
	*/
	fun onChunk(
		inSession : LinkSession,
		inChunk   : ByteArray
	)
	{
	}

	/*
		Session fermée (une seule fois), par l'un ou l'autre côté ou faute de
		nouvelles.
		inSession : session
		inReason  : raison, en clair
	*/
	fun onClosed(
		inSession : LinkSession,
		inReason  : String
	)
}

/*
	Session ouverte entre un téléphone et un PC, sur une connexion TCP qui
	reste ouverte : chaque trame est scellée avec la clé de son sens et porte
	un numéro d'ordre ; une trame illisible, rejouée ou déplacée ferme la
	session. Rien reçu pendant inDeadAfterMs : la session est morte. Un Ping
	reçoit Pong d'office.
	inSocket      : connexion
	peer          : présentation de l'autre appareil
	inSendKey     : clé des trames envoyées
	inReceiveKey  : clé des trames reçues
	inDeadAfterMs : délai sans rien recevoir au bout duquel la session est morte
	inNow         : horloge (ms)
*/
class LinkSession(
	private val inSocket      : Socket,
	val peer                  : DeviceInfo,
	inSendKey                 : ByteArray,
	inReceiveKey              : ByteArray,
	private val inDeadAfterMs : Long,
	private val inNow         : () -> Long = System::currentTimeMillis
)
{
	private val fSendCipher    = SessionCipher(inSendKey)							// scellement des trames envoyées
	private val fReceiveCipher = SessionCipher(inReceiveKey)						// ouverture des trames reçues
	private val fGuard         = SequenceGuard()									// ordre des trames reçues
	private val fOut           : OutputStream = BufferedOutputStream(inSocket.getOutputStream(), 64 * 1024)	// sortie
	private val fClosed        = AtomicBoolean(false)								// session fermée
	private var fSendSeq       = 0L													// numéro de la prochaine trame envoyée
	private var fListener      : SessionListener? = null							// propriétaire ; null avant start
	@Volatile private var fLastReceived = inNow()									// date de la dernière trame reçue (ms)

	/*
		Adresse de l'autre appareil.
	*/
	val address: InetAddress get() = inSocket.inetAddress

	/*
		Adresse locale de la connexion (interface du PC qui l'a acceptée).
	*/
	val localAddress: InetAddress get() = inSocket.localAddress

	/*
		Vrai tant que la session est ouverte.
	*/
	val isOpen: Boolean get() = !fClosed.get()

	/*
		Date de la dernière trame reçue (ms).
	*/
	val lastReceived: Long get() = fLastReceived

	/*
		Démarre la lecture des trames sur un fil à part.
		inListener : destinataire des messages et de la fermeture
	*/
	fun start(inListener: SessionListener)
	{
		fListener = inListener
		runCatching { inSocket.soTimeout = inDeadAfterMs.toInt(); inSocket.keepAlive = true; inSocket.tcpNoDelay = true }
		Thread({ readLoop() }, "PontPlus-session-${peer.name}").apply { isDaemon = true }.start()
	}

	/*
		Envoie un message ; faux si la session est fermée (ou se ferme à cause
		de cet envoi).
		inMessage : message
	*/
	fun send(inMessage: LinkMessage): Boolean = sendPayload(kPayloadMessage, encodeMessage(inMessage))

	/*
		Envoie un morceau de fichier ; faux si la session est fermée.
		inChunk : contenu en clair
	*/
	fun sendChunk(inChunk: ByteArray): Boolean = sendPayload(kPayloadChunk, inChunk)

	/*
		Ferme la session ; sans effet si elle l'est déjà.
		inReason : raison, en clair
	*/
	fun close(inReason: String)
	{
		if (!fClosed.compareAndSet(false, true)) return
		runCatching { inSocket.close() }
		runCatching { fListener?.onClosed(this, inReason) }
	}

	/*
		Scelle et envoie un contenu.
		inType : nature du contenu
		inData : contenu en clair
	*/
	private fun sendPayload(
		inType : Byte,
		inData : ByteArray
	): Boolean
	{
		if (fClosed.get()) return false
		try
		{
			synchronized(fOut) {
				val vSeq = fSendSeq++															// numéro de la trame
				writeData(fOut, vSeq, fSendCipher.seal(vSeq, byteArrayOf(inType) + inData))
			}
			return true
		}
		catch (inError: IOException)
		{
			close("envoi impossible : ${inError.message ?: "connexion coupée"}")
			return false
		}
	}

	/*
		Lit les trames jusqu'à la fermeture.
	*/
	private fun readLoop()
	{
		val vIn = BufferedInputStream(runCatching { inSocket.getInputStream() }.getOrElse { close("connexion coupée"); return }, 64 * 1024)	// entrée
		while (!fClosed.get())
		{
			val vWire = try
			{
				readWire(vIn)
			}
			catch (inError: SocketTimeoutException)
			{
				close("plus de nouvelles depuis ${inDeadAfterMs / 1000} s"); return
			}
			catch (inError: IOException)
			{
				close(inError.message ?: "connexion coupée"); return
			}																			// trame reçue
			val vData = vWire as? Wire.Data ?: run { close("trame inattendue dans la session"); return }	// trame de session
			if (!fGuard.accept(vData.seq)) { close("trame hors d'ordre ou rejouée"); return }
			val vPlain = fReceiveCipher.open(vData.seq, vData.sealed) ?: run { close("trame illisible : clé différente ou contenu modifié"); return }	// contenu en clair
			if (vPlain.isEmpty()) { close("trame vide"); return }
			fLastReceived = inNow()
			val vBody  = vPlain.copyOfRange(1, vPlain.size)										// contenu sans sa nature
			when (vPlain[0])
			{
				kPayloadMessage -> decodeMessage(vBody)?.let(::dispatch)
				kPayloadChunk   -> runCatching { fListener?.onChunk(this, vBody) }
				else            -> Unit
			}
		}
	}

	/*
		Remet un message au propriétaire ; répond Pong à un Ping.
		inMessage : message
	*/
	private fun dispatch(inMessage: LinkMessage)
	{
		if (inMessage is LinkMessage.Ping) { send(LinkMessage.Pong(inMessage.at)); return }
		runCatching { fListener?.onMessage(this, inMessage) }
	}
}
