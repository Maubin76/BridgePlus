package fr.maubin.pontplus

import fr.maubin.pontplus.link.Backoff
import fr.maubin.pontplus.link.Beacon
import fr.maubin.pontplus.link.DiscoveryResponder
import fr.maubin.pontplus.link.LinkCipher
import fr.maubin.pontplus.link.LinkFrame
import fr.maubin.pontplus.link.LocalAddress
import fr.maubin.pontplus.link.NetworkId
import fr.maubin.pontplus.link.PairingOffer
import fr.maubin.pontplus.link.ReplayGuard
import fr.maubin.pontplus.link.SequenceGuard
import fr.maubin.pontplus.link.SessionCipher
import fr.maubin.pontplus.link.Wire
import fr.maubin.pontplus.link.deriveSessionKeys
import fr.maubin.pontplus.link.discoverPcs
import fr.maubin.pontplus.link.isHomeNetwork
import fr.maubin.pontplus.link.kKindHello
import fr.maubin.pontplus.link.kMaxClockSkewMs
import fr.maubin.pontplus.link.kMaxFrameBytes
import fr.maubin.pontplus.link.newLinkKey
import fr.maubin.pontplus.link.randomBytes
import fr.maubin.pontplus.link.readFrame
import fr.maubin.pontplus.link.readWire
import fr.maubin.pontplus.link.writeData
import fr.maubin.pontplus.link.writeFrame
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import kotlin.random.Random

/*
	Socle de la liaison, sans connexion : trames, chiffrement de l'ouverture
	et des sessions, gardes contre le rejeu et le désordre, offre
	d'appairage, délai progressif, découverte, réseau de la maison.
*/
class LinkTest
{
	private val kKey    = newLinkKey()													// clé de la paire
	private var fBeacon : DiscoveryResponder? = null									// répondeur démarré

	/*
		Arrête le répondeur.
	*/
	@After
	fun stop()
	{
		fBeacon?.stop()
	}

	/*
		Trame enveloppe : aller-retour ; deux trames à la suite se lisent dans
		l'ordre.
	*/
	@Test
	fun envelopeRoundTrip()
	{
		val vOut   = ByteArrayOutputStream()												// flux
		val vFrame = LinkFrame(from = "pc-1", kind = kKindHello, time = 42, nonce = "n", body = "corps é")	// trame
		writeFrame(vOut, vFrame)
		writeFrame(vOut, vFrame.copy(body = "deuxième"))
		val vIn    = ByteArrayInputStream(vOut.toByteArray())									// lecture
		assertEquals(vFrame, readFrame(vIn))
		assertEquals("deuxième", readFrame(vIn).body)
	}

	/*
		Trame de session : numéro d'ordre et contenu binaire intacts.
	*/
	@Test
	fun dataRoundTrip()
	{
		val vOut  = ByteArrayOutputStream()													// flux
		val vBody = randomBytes(5000)														// contenu
		writeData(vOut, 123456789012L, vBody)
		val vWire = readWire(ByteArrayInputStream(vOut.toByteArray())) as Wire.Data		// trame lue
		assertEquals(123456789012L, vWire.seq)
		assertArrayEquals(vBody, vWire.sealed)
	}

	/*
		Trames invalides : flux coupé, taille annoncée trop grande, nature
		inconnue, JSON illisible, trame de session lue comme enveloppe.
	*/
	@Test
	fun invalidFrames()
	{
		assertIoError(byteArrayOf())
		assertIoError(intBytes(0))
		assertIoError(intBytes(kMaxFrameBytes + 1) + byteArrayOf(1))
		assertIoError(intBytes(3) + byteArrayOf(9, 0, 0))
		assertIoError(intBytes(3) + byteArrayOf(1, '{'.code.toByte(), 'x'.code.toByte()))
		assertIoError(intBytes(10) + byteArrayOf(1, 2, 3))
		val vOut = ByteArrayOutputStream()													// trame de session
		writeData(vOut, 0, byteArrayOf(1, 2, 3))
		try { readFrame(ByteArrayInputStream(vOut.toByteArray())); fail("trame de session lue comme enveloppe") } catch (inError: IOException) { }
	}

	/*
		Chiffrement de l'ouverture : aller-retour ; message modifié, autre clé
		ou autres données associées : refusé.
	*/
	@Test
	fun cipher()
	{
		val vCipher = LinkCipher(kKey)														// chiffrement
		val vSealed = vCipher.seal("bonjour".toByteArray(), "aad".toByteArray())			// message scellé
		assertArrayEquals("bonjour".toByteArray(), vCipher.open(vSealed, "aad".toByteArray()))
		assertNull(vCipher.open(vSealed, "autre".toByteArray()))
		assertNull(LinkCipher(newLinkKey()).open(vSealed, "aad".toByteArray()))
		vSealed[vSealed.size - 1] = (vSealed[vSealed.size - 1] + 1).toByte()
		assertNull(vCipher.open(vSealed, "aad".toByteArray()))
	}

	/*
		Clés de session : propres à chaque sens et à chaque session ; les deux
		côtés obtiennent les mêmes avec les mêmes défis.
	*/
	@Test
	fun sessionKeys()
	{
		val vPhone = randomBytes(32)														// défi du téléphone
		val vPc    = randomBytes(32)														// défi du PC
		val vOne   = deriveSessionKeys(kKey, vPhone, vPc)									// clés d'une session
		val vSame  = deriveSessionKeys(kKey, vPhone, vPc)									// mêmes défis
		val vOther = deriveSessionKeys(kKey, vPhone, randomBytes(32))						// autre session
		assertArrayEquals(vOne.toPc, vSame.toPc)
		assertArrayEquals(vOne.toPhone, vSame.toPhone)
		assertFalse(vOne.toPc.contentEquals(vOne.toPhone))
		assertFalse(vOne.toPc.contentEquals(vOther.toPc))
		assertFalse(vOne.toPc.contentEquals(deriveSessionKeys(newLinkKey(), vPhone, vPc).toPc))
	}

	/*
		Chiffrement de session : un contenu ne s'ouvre que sous son numéro
		d'ordre, avec la clé de son sens, et s'il n'a pas été modifié.
	*/
	@Test
	fun sessionCipher()
	{
		val vKeys   = deriveSessionKeys(kKey, randomBytes(32), randomBytes(32))			// clés de la session
		val vSend   = SessionCipher(vKeys.toPc)												// côté téléphone
		val vSealed = vSend.seal(7, "texte".toByteArray())									// contenu scellé
		assertArrayEquals("texte".toByteArray(), SessionCipher(vKeys.toPc).open(7, vSealed))
		assertNull(SessionCipher(vKeys.toPc).open(8, vSealed))
		assertNull(SessionCipher(vKeys.toPhone).open(7, vSealed))
		vSealed[0] = (vSealed[0] + 1).toByte()
		assertNull(SessionCipher(vKeys.toPc).open(7, vSealed))
	}

	/*
		Garde contre le rejeu des ouvertures : un numéro déjà vu, une date trop
		ancienne ou trop en avance sont refusés ; un numéro vu il y a longtemps
		est oublié.
	*/
	@Test
	fun replayGuard()
	{
		var vNow   = 1_000_000_000L																// horloge
		val vGuard = ReplayGuard { vNow }														// garde
		assertTrue(vGuard.accept(vNow, "a"))
		assertFalse(vGuard.accept(vNow, "a"))
		assertFalse(vGuard.accept(vNow - kMaxClockSkewMs - 1, "b"))
		assertFalse(vGuard.accept(vNow + kMaxClockSkewMs + 1, "c"))
		assertFalse(vGuard.accept(vNow, ""))
		assertTrue(vGuard.accept(vNow - kMaxClockSkewMs + 1, "d"))
		vNow += 3 * kMaxClockSkewMs
		assertTrue(vGuard.accept(vNow, "a"))
	}

	/*
		Garde de l'ordre : 0, 1, 2… acceptés ; une trame rejouée, en avance ou
		en retard est refusée.
	*/
	@Test
	fun sequenceGuard()
	{
		val vGuard = SequenceGuard()															// garde
		assertTrue(vGuard.accept(0))
		assertTrue(vGuard.accept(1))
		assertFalse(vGuard.accept(1))
		assertFalse(vGuard.accept(0))
		assertFalse(vGuard.accept(3))
		assertTrue(vGuard.accept(2))
		assertEquals(3, vGuard.expected)
	}

	/*
		Offre d'appairage : aller-retour par le QR code ; un QR code de KeePlus,
		d'une autre version ou abîmé est refusé.
	*/
	@Test
	fun pairingOffer()
	{
		val vOffer = PairingOffer("pc-1", "PC du séjour", listOf("192.168.1.20", "10.0.0.5"), 47841, kKey, 1_700_000_000_000)	// offre
		val vText  = vOffer.encode()															// QR code
		assertTrue(vText.startsWith("pontplus://appairer?"))
		assertEquals(vOffer, PairingOffer.decode(vText))
		assertNull(PairingOffer.decode(vText.replace("pontplus://", "keeplus://")))
		assertNull(PairingOffer.decode(vText.replace("v=1", "v=2")))
		assertNull(PairingOffer.decode(vText.replace(Regex("cle=[^&]+"), "cle=abc")))
		assertNull(PairingOffer.decode("https://exemple.fr"))
	}

	/*
		Délai progressif : 1, 2, 4… s, plafonné à 60 s, remis à zéro ; la
		variation reste dans ±20 %.
	*/
	@Test
	fun backoff()
	{
		val vBackoff = Backoff(inJitter = 0.0)													// sans variation
		assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), List(8) { vBackoff.next() })
		vBackoff.reset()
		assertEquals(1_000L, vBackoff.next())
		val vJitter = Backoff(inRandom = Random(1))												// avec variation
		repeat(5) { vJitter.next() }
		val vDelay  = vJitter.next()															// sixième palier : 32 s
		assertTrue(vDelay in 25_600L..38_400L)
	}

	/*
		Réseau de la maison et sous-réseaux.
	*/
	@Test
	fun homeNetwork()
	{
		val vHome = listOf(NetworkId.wifi("\"Maison\"")!!, NetworkId.box("AA-BB-CC-DD-EE-FF")!!)	// réseaux de la maison
		assertTrue(isHomeNetwork(listOf(NetworkId.wifi("Maison")!!), vHome))
		assertTrue(isHomeNetwork(listOf(NetworkId.box("aa:bb:cc:dd:ee:ff")!!), vHome))
		assertFalse(isHomeNetwork(listOf(NetworkId.wifi("Café")!!), vHome))
		assertNull(NetworkId.wifi("<unknown ssid>"))
		val vLan  = LocalAddress("192.168.1.20", 24)												// interface du PC
		assertTrue(vLan.contains(InetAddress.getByName("192.168.1.77")))
		assertFalse(vLan.contains(InetAddress.getByName("192.168.2.77")))
		assertTrue(LocalAddress("10.1.2.3", 8).contains(InetAddress.getByName("10.200.0.1")))
	}

	/*
		Découverte : un PC répond aux demandes venues d'une adresse admise, et
		se tait pour les autres.
	*/
	@Test
	fun discovery()
	{
		val vLocal = InetAddress.getLoopbackAddress()											// boucle locale
		var vAllow = true																		// demandes admises
		val vBeacon = DiscoveryResponder({ Beacon("pc-1", "PC", 47841) }, { vAllow }).also { it.start(0, vLocal); fBeacon = it }	// répondeur
		val vFound = discoverPcs(listOf(vLocal), vBeacon.port, 800)								// PC trouvés
		assertEquals(listOf("pc-1"), vFound.map { it.beacon.id })
		vAllow = false
		assertTrue(discoverPcs(listOf(vLocal), vBeacon.port, 500).isEmpty())
	}

	/*
		Entier sur 4 octets (début d'une trame fabriquée à la main).
		inValue : entier
	*/
	private fun intBytes(inValue: Int): ByteArray = java.nio.ByteBuffer.allocate(4).putInt(inValue).array()

	/*
		Vérifie que la lecture de ces octets échoue proprement.
		inBytes : octets reçus
	*/
	private fun assertIoError(inBytes: ByteArray)
	{
		try
		{
			readWire(ByteArrayInputStream(inBytes))
			fail("trame invalide acceptée")
		}
		catch (inError: IOException)
		{
		}
	}
}
