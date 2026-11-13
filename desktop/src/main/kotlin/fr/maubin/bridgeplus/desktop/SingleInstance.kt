package fr.maubin.bridgeplus.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

const val kInstancePort     = 47851											// port du verrou d'instance (boucle locale seulement)
private const val kShowAsk  = "BridgePlus-afficher"						// demande de la seconde instance
private const val kShowDone = "BridgePlus-affiche"						// réponse de la première
private const val kCommand  = "BridgePlus-commande"						// commande transmise (ouvrir un fichier reçu, envoyer un fichier)
private val kSeparator      = Char(9)											// séparateur de la commande (tabulation)
private const val kTimeout  = 2_000											// délai de réponse (ms)

/*
	Une seule appli PC à la fois : la première instance garde un petit
	serveur sur la boucle locale (jamais sur le réseau : pas de fenêtre du
	pare-feu) ; une seconde instance le joint, demande d'afficher la
	première, ou lui transmet une commande (bouton d'une notification, menu
	de l'Explorateur), puis se ferme. Si le port est pris par un autre programme
	(pas de réponse attendue), l'appli se lance normalement.
	inPort : port du verrou (0 : port libre, tests)
*/
class SingleInstance(private val inPort: Int = kInstancePort)
{
	private var fSocket  : ServerSocket? = null									// verrou tenu ; null sinon
	private val fRequests = MutableStateFlow(0)									// demandes d'affichage reçues
	private val fCommands = MutableSharedFlow<String>(extraBufferCapacity = 64)	// commandes reçues d'une seconde instance

	// Commandes transmises par une seconde instance (« ouvrir <id> », « envoyer <chemin> »…).
	val commands: SharedFlow<String> = fCommands

	// Nombre de demandes d'affichage reçues d'une seconde instance (la fenêtre revient au premier plan à chaque nouvelle).
	val requests: StateFlow<Int> = fRequests

	// Port du verrou tenu ; 0 sans.
	val port: Int get() = fSocket?.localPort ?: 0

	/*
		Prend le verrou, ou réveille l'instance qui le tient. Rend vrai si
		cette instance doit se lancer (première, ou verrou impossible), faux
		si une autre instance a été mise au premier plan.
		inNotify  : demander à l'autre instance de s'afficher (faux : démarrage
		            avec Windows, l'appli tourne déjà)
		inCommand : commande à transmettre à l'autre instance ; null sans
	*/
	fun claim(
		inNotify  : Boolean = true,
		inCommand : String? = null
	): Boolean
	{
		val vSocket = runCatching { ServerSocket().apply { bind(InetSocketAddress(InetAddress.getLoopbackAddress(), inPort)) } }.getOrNull()	// verrou
		if (vSocket == null) return !(knock(inPort, inCommand?.let { "$kCommand$kSeparator$it" } ?: if (inNotify) kShowAsk else "$kShowAsk-silencieux"))
		fSocket = vSocket
		Thread({
			while (!vSocket.isClosed)
			{
				val vClient = runCatching { vSocket.accept() }.getOrNull() ?: break		// seconde instance
				runCatching {
					vClient.use { inClient ->
						inClient.soTimeout = kTimeout
						val vAsk = inClient.getInputStream().bufferedReader(Charsets.UTF_8).readLine().orEmpty()	// demande
						val vCommand = vAsk.startsWith("$kCommand$kSeparator")									// commande transmise
						if (vAsk == kShowAsk || vAsk == "$kShowAsk-silencieux" || vCommand)
						{
							inClient.getOutputStream().write("$kShowDone\n".toByteArray(Charsets.UTF_8))
							inClient.getOutputStream().flush()
							if (vAsk == kShowAsk) fRequests.value++
							if (vCommand) fCommands.tryEmit(vAsk.substringAfter(kSeparator))
						}
					}
				}
			}
		}, "BridgePlus-instance").apply { isDaemon = true }.start()
		return true
	}

	/*
		Rend le verrou (fermeture de l'appli, tests).
	*/
	fun release()
	{
		runCatching { fSocket?.close() }
		fSocket = null
	}
}

/*
	Joint l'instance qui tient le verrou et lui transmet une ligne ; vrai si
	c'est bien BridgePlus qui a répondu.
	inPort : port du verrou
	inLine : demande (affichage, ou commande)
*/
private fun knock(
	inPort : Int,
	inLine : String
): Boolean =
	runCatching {
		Socket().use { inSocket ->
			inSocket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), inPort), kTimeout)
			inSocket.soTimeout = kTimeout
			inSocket.getOutputStream().write("$inLine\n".toByteArray(Charsets.UTF_8))
			inSocket.getOutputStream().flush()
			inSocket.getInputStream().bufferedReader(Charsets.UTF_8).readLine() == kShowDone
		}
	}.getOrDefault(false)
