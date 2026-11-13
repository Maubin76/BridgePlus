package fr.maubin.bridgeplus

import fr.maubin.bridgeplus.link.Backoff
import fr.maubin.bridgeplus.link.DeviceInfo
import fr.maubin.bridgeplus.link.LinkClient
import fr.maubin.bridgeplus.link.LinkException
import fr.maubin.bridgeplus.link.LinkHub
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.LinkSession
import fr.maubin.bridgeplus.link.LocalAddress
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.OpenVerdict
import fr.maubin.bridgeplus.link.SessionCipher
import fr.maubin.bridgeplus.link.SessionListener
import fr.maubin.bridgeplus.link.Wire
import fr.maubin.bridgeplus.link.newLinkKey
import fr.maubin.bridgeplus.link.readWire
import fr.maubin.bridgeplus.link.writeData
import fr.maubin.bridgeplus.peers.PcLink
import fr.maubin.bridgeplus.peers.PcStatus
import fr.maubin.bridgeplus.peers.PhoneLink
import fr.maubin.bridgeplus.store.KeyBox
import fr.maubin.bridgeplus.store.SealedFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
	Sessions sur la boucle locale de la machine (sans réseau réel) :
	ouverture, battement de cœur, ordre des trames, appairage par QR code,
	reconnexion, oubli d'un appareil, plusieurs téléphones et plusieurs PC,
	écoute limitée aux adresses données.
*/
class SessionTest
{
	@get:Rule val fTemp = TemporaryFolder()											// dossier des fichiers des appareils

	private val kLocal  = InetAddress.getByName("127.0.0.1")							// boucle locale
	private val kWifi   = NetworkId.wifi("Maison")!!									// Wi-Fi de la maison
	private val kHome   = listOf(LocalNetwork(kWifi, listOf(LocalAddress("127.0.0.1", 8))))	// réseau du PC, sur la boucle locale
	private val kBoxKey = newLinkKey()													// clé des fichiers scellés
	private val fStops  = CopyOnWriteArrayList<() -> Unit>()						// arrêts à faire après chaque test

	/*
		Arrête serveurs, liaisons et sessions.
	*/
	@After
	fun stopAll()
	{
		fStops.reversed().forEach { runCatching { it() } }
	}

	/*
		Ouverture directe (serveur et client) : la session s'ouvre, un Ping du
		téléphone reçoit Pong ; une mauvaise clé et un appareil inconnu sont
		refusés.
	*/
	@Test
	fun openAndPing()
	{
		val vKey     = newLinkKey()																// clé de la paire
		val vHub     = hub(mapOf("tel-1" to vKey))												// serveur
		val vPongs   = LinkedBlockingQueue<LinkMessage>()										// messages reçus par le téléphone
		val vOpened  = LinkClient(phoneInfo("tel-1")).open("127.0.0.1", vHub.port, "pc-1", vKey)	// session
		vOpened.session.start(listener({ vPongs.add(it) }))
		fStops += { vOpened.session.close("fin du test") }
		assertEquals("PC du séjour", vOpened.session.peer.name)
		assertTrue(vOpened.session.send(LinkMessage.Ping(42)))
		assertEquals(LinkMessage.Pong(42), vPongs.poll(5, TimeUnit.SECONDS))
		assertRefused { LinkClient(phoneInfo("tel-1")).open("127.0.0.1", vHub.port, "pc-1", newLinkKey()) }
		assertRefused { LinkClient(phoneInfo("inconnu")).open("127.0.0.1", vHub.port, "pc-1", vKey) }
		assertRefused { LinkClient(phoneInfo("tel-1")).open("127.0.0.1", vHub.port, "autre-pc", vKey) }
	}

	/*
		Ordre des trames : une trame rejouée ferme la session ; une trame
		modifiée aussi.
	*/
	@Test
	fun replayedOrTamperedFrameClosesSession()
	{
		for (vTamper in listOf(false, true))
		{
			val (vSession, vRaw) = rawSession()														// session et autre bout brut
			val vClosed = LinkedBlockingQueue<String>()												// raisons de fermeture
			vSession.start(listener(inOnClosed = { vClosed.add(it) }))
			val vPing   = SessionCipher(kRawToSession).seal(0, byteArrayOf(0) + """{"t":"ping","at":5}""".toByteArray())	// Ping scellé, n° 0
			writeData(vRaw.getOutputStream(), 0, vPing)
			val vReply  = readWire(vRaw.getInputStream()) as Wire.Data								// Pong
			assertEquals(0, vReply.seq)
			assertNotNull(SessionCipher(kSessionToRaw).open(0, vReply.sealed))
			if (vTamper) vPing[3] = (vPing[3] + 1).toByte()
			writeData(vRaw.getOutputStream(), if (vTamper) 1 else 0, vPing)
			val vReason = vClosed.poll(5, TimeUnit.SECONDS)											// raison
			assertNotNull(vReason)
			assertTrue(vReason, if (vTamper) vReason.contains("illisible") else vReason.contains("rejouée"))
			assertFalse(vSession.isOpen)
		}
	}

	/*
		Battement de cœur : une session qui reçoit des Ping reste ouverte au-delà
		du délai de mort ; un téléphone qui ne répond plus est déclaré mort.
	*/
	@Test
	fun heartbeat()
	{
		val vKey    = newLinkKey()																	// clé de la paire
		val vClosed = LinkedBlockingQueue<String>()													// fermetures côté PC
		val vHub    = hub(mapOf("tel-1" to vKey), inPingMs = 100, inDeadMs = 600, inClosed = { vClosed.add(it) })	// serveur
		val vOpened = LinkClient(phoneInfo("tel-1")).open("127.0.0.1", vHub.port, "pc-1", vKey, inDeadAfterMs = 600)	// session
		vOpened.session.start(listener())
		fStops += { vOpened.session.close("fin du test") }
		Thread.sleep(1_500)
		assertTrue(vOpened.session.isOpen)
		assertNull(vClosed.poll(0, TimeUnit.SECONDS))
		val (vSilent, vRaw) = rawSession(inDeadMs = 400)											// session dont l'autre bout se tait
		val vDead   = LinkedBlockingQueue<String>()													// fermeture
		vSilent.start(listener(inOnClosed = { vDead.add(it) }))
		assertTrue(vDead.poll(5, TimeUnit.SECONDS)!!.contains("plus de nouvelles"))
		vRaw.close()
	}

	/*
		Parcours complet : appairage par l'offre, session permanente, coupure
		du PC puis reconnexion seule, renommage, oubli par le PC (le téléphone
		l'apprend et détruit sa clé).
	*/
	@Test
	fun pairConnectReconnectForget()
	{
		val vPort  = freePort()																		// port fixe du PC (reconnexion)
		val vPc    = pcLink("pc.json", vPort)														// liaison du PC
		val vPhone = phoneLink("tel.json")															// liaison du téléphone
		vPc.startPairing()
		val vOffer = vPc.state.value.offer!!														// offre (QR code)
		assertEquals(listOf("127.0.0.1"), vOffer.addresses)
		assertNull(vPhone.pair(vOffer, kWifi))
		val vPhoneId = vPhone.info().id																// identifiant du téléphone
		awaitTrue { vPc.state.value.phones.any { it.id == vPhoneId } && vPc.state.value.offer == null && vPc.state.value.running }
		assertEquals(listOf(kWifi), vPhone.peers().home)
		assertEquals("Pixel", vPc.state.value.paired)

		vPhone.start()
		awaitTrue { vPhone.state.value.statusOf(vOffer.pcId) == PcStatus.Connected && vPhoneId in vPc.state.value.connected }

		vPc.stop()
		awaitTrue { vPhone.state.value.statusOf(vOffer.pcId) != PcStatus.Connected }
		vPc.refresh()
		awaitTrue { vPhone.state.value.statusOf(vOffer.pcId) == PcStatus.Connected && vPhoneId in vPc.state.value.connected }

		vPc.rename(vPhoneId, "Téléphone de Mathieu")
		vPhone.rename(vOffer.pcId, "Bureau")
		assertEquals("Téléphone de Mathieu", vPc.state.value.phones.single().label)
		assertEquals("Bureau", vPhone.peers().pcs.single().label)

		vPc.forget(vPhoneId)
		awaitTrue { vPhone.state.value.forgotten == "Bureau" && vPhone.peers().pcs.isEmpty() }
		assertTrue(vPc.state.value.phones.isEmpty())
	}

	/*
		Un téléphone oublié par le PC pendant son absence ne peut plus se
		connecter : refusé, avec une raison claire.
	*/
	@Test
	fun forgottenPhoneIsRefused()
	{
		val vPc    = pcLink("pc.json", freePort())													// liaison du PC
		val vPhone = phoneLink("tel.json")															// liaison du téléphone
		vPc.startPairing()
		val vOffer = vPc.state.value.offer!!														// offre
		assertNull(vPhone.pair(vOffer, kWifi))
		vPc.forget(vPhone.info().id)
		vPc.setHome(kWifi, true)
		vPc.startPairing()																			// écoute ouverte (le PC n'a plus de téléphone)
		vPhone.start()
		awaitTrue { vPhone.state.value.statusOf(vOffer.pcId) == PcStatus.Refused }
		assertTrue(vPhone.state.value.refusals[vOffer.pcId]!!.contains("ne reconnaît plus"))
	}

	/*
		Offre expirée : le téléphone la refuse lui-même ; le PC aussi, même si
		l'horloge du téléphone retarde.
	*/
	@Test
	fun expiredOfferIsRefused()
	{
		var vNow   = System.currentTimeMillis()														// horloge commune
		val vPc    = pcLink("pc.json", freePort(), inNow = { vNow })								// liaison du PC
		val vPhone = phoneLink("tel.json", inNow = { vNow })										// liaison du téléphone
		vPc.startPairing()
		val vOffer = vPc.state.value.offer!!														// offre
		vNow += 6 * 60_000L																			// 6 min : offre expirée
		assertTrue(vPhone.pair(vOffer, kWifi)!!.contains("expiré"))
		assertNotNull(vPhone.pair(vOffer.copy(expires = Long.MAX_VALUE), kWifi))					// le PC refuse aussi
		assertTrue(vPc.state.value.phones.isEmpty())
	}

	/*
		Plusieurs appareils : deux téléphones sur un PC, et un téléphone relié
		à deux PC en même temps.
	*/
	@Test
	fun severalDevices()
	{
		val vPcA    = pcLink("pc-a.json", freePort(), inName = "PC A")								// premier PC
		val vPcB    = pcLink("pc-b.json", freePort(), inName = "PC B")								// second PC
		val vPhone1 = phoneLink("tel-1.json", inName = "Pixel")									// premier téléphone
		val vPhone2 = phoneLink("tel-2.json", inName = "Galaxy")									// second téléphone
		vPcA.startPairing(); assertNull(vPhone1.pair(vPcA.state.value.offer!!, kWifi))
		vPcA.startPairing(); assertNull(vPhone2.pair(vPcA.state.value.offer!!, kWifi))
		vPcB.startPairing(); assertNull(vPhone1.pair(vPcB.state.value.offer!!, kWifi))
		vPhone1.start()
		vPhone2.start()
		awaitTrue { vPcA.state.value.connected.size == 2 && vPcB.state.value.connected.size == 1 && vPhone1.state.value.connected.size == 2 }
		assertEquals(setOf("Pixel", "Galaxy"), vPcA.state.value.phones.map { it.label }.toSet())
	}

	/*
		Écoute limitée : un serveur lié à 127.0.0.1 n'accepte rien sur
		127.0.0.2 ; retirer l'adresse ferme l'écoute.
	*/
	@Test
	fun listensOnlyOnGivenAddresses()
	{
		val vHub  = hub(emptyMap())																	// serveur sur 127.0.0.1
		val vPort = vHub.port																		// son port
		Socket("127.0.0.1", vPort).close()
		try { Socket("127.0.0.2", vPort).close(); fail("connexion acceptée hors des adresses d'écoute") } catch (inError: IOException) { }
		vHub.bind(emptyList())
		assertEquals(0, vHub.port)
		try { Socket("127.0.0.1", vPort).close(); fail("écoute encore ouverte") } catch (inError: IOException) { }
	}

	// --- Outils ---------------------------------------------------------------

	private val kRawToSession = newLinkKey()											// clé du bout brut vers la session
	private val kSessionToRaw = newLinkKey()											// clé de la session vers le bout brut

	/*
		Présentation d'un téléphone.
		inId : identifiant
	*/
	private fun phoneInfo(inId: String) = DeviceInfo(inId, "Pixel", "android", "1.0")

	/*
		Serveur sur 127.0.0.1, qui connaît ces téléphones.
		inKeys   : clés des téléphones appairés, par identifiant
		inPingMs : battement de cœur
		inDeadMs : délai de mort
		inClosed : fermetures de session
	*/
	private fun hub(
		inKeys   : Map<String, ByteArray>,
		inPingMs : Long = 30_000,
		inDeadMs : Long = 75_000,
		inClosed : (String) -> Unit = {}
	): LinkHub =
		LinkHub(
			inInfo        = { DeviceInfo("pc-1", "PC du séjour", "windows", "1.0") },
			inKeyOf       = { inKeys[it] },
			inOfferKey    = { null },
			inAccept      = { OpenVerdict() },
			inOnSession   = { _, _ -> listener(inOnClosed = inClosed) },
			inPingEveryMs = inPingMs,
			inDeadAfterMs = inDeadMs
		).also { it.bind(listOf(kLocal), 0); fStops += it::stop }

	/*
		Session dont l'autre bout est une connexion brute, pilotée par le test
		(clés connues des deux côtés).
		inDeadMs : délai de mort de la session
	*/
	private fun rawSession(inDeadMs: Long = 5_000): Pair<LinkSession, Socket>
	{
		val vServer = ServerSocket(0, 1, kLocal).also { vSocket -> fStops += { vSocket.close() } }	// écoute
		val vRaw    = Socket(kLocal, vServer.localPort).also { vSocket -> fStops += { vSocket.close() } }	// bout brut
		val vSide   = vServer.accept()															// bout de la session
		val vSession = LinkSession(vSide, phoneInfo("brut"), kSessionToRaw, kRawToSession, inDeadMs)	// session
		fStops += { vSession.close("fin du test") }
		vRaw.soTimeout = 5_000
		return vSession to vRaw
	}

	/*
		Liaison d'un PC sur la boucle locale, battement de cœur rapide.
		inFile : nom du fichier des appareils
		inPort : port du serveur
		inName : nom du PC
		inNow  : horloge
	*/
	private fun pcLink(
		inFile : String,
		inPort : Int,
		inName : String = "PC du séjour",
		inNow  : () -> Long = System::currentTimeMillis
	): PcLink =
		PcLink(SealedFile(fTemp.root.resolve(inFile), KeyBox(kBoxKey)), inName, "1.0", { kHome }, fNow = inNow, fPort = inPort, fBeacon = 0, fPingEveryMs = 200, fDeadAfterMs = 2_000)
			.also { fStops += it::stop }

	/*
		Liaison d'un téléphone, sans découverte (adresses de l'offre seulement),
		délais courts.
		inFile : nom du fichier des appareils
		inName : nom du téléphone
		inNow  : horloge
	*/
	private fun phoneLink(
		inFile : String,
		inName : String = "Pixel",
		inNow  : () -> Long = System::currentTimeMillis
	): PhoneLink =
		PhoneLink(SealedFile(fTemp.root.resolve(inFile), KeyBox(kBoxKey)), inName, "1.0", { emptyList() }, fNow = inNow, fDeadAfterMs = 2_000, fBackoff = { Backoff(50, 300, 0.0) })
			.also { fStops += it::stop }

	/*
		Destinataire de session pour les tests.
		inOnMessage : message reçu
		inOnClosed  : fermeture
	*/
	private fun listener(
		inOnMessage : (LinkMessage) -> Unit = {},
		inOnClosed  : (String) -> Unit      = {}
	) = object : SessionListener
	{
		override fun onMessage(inSession: LinkSession, inMessage: LinkMessage) = inOnMessage(inMessage)
		override fun onClosed(inSession: LinkSession, inReason: String) = inOnClosed(inReason)
	}

	/*
		Port TCP libre sur la boucle locale.
	*/
	private fun freePort(): Int = ServerSocket(0, 1, kLocal).use { it.localPort }

	/*
		Vérifie qu'une ouverture est refusée par le PC.
		inBlock : ouverture
	*/
	private fun assertRefused(inBlock: () -> Unit)
	{
		try { inBlock(); fail("ouverture acceptée") } catch (inError: LinkException) { }
	}

	/*
		Attend qu'une condition devienne vraie (10 s au plus).
		inCondition : condition
	*/
	private fun awaitTrue(inCondition: () -> Boolean)
	{
		val vEnd = System.currentTimeMillis() + 10_000												// fin de l'attente
		while (!inCondition())
		{
			if (System.currentTimeMillis() > vEnd) fail("condition jamais remplie")
			Thread.sleep(20)
		}
	}
}
