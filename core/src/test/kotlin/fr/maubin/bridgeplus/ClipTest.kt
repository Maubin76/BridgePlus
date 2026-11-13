package fr.maubin.bridgeplus

import fr.maubin.bridgeplus.clip.ClipOutcome
import fr.maubin.bridgeplus.clip.ClipRelay
import fr.maubin.bridgeplus.clip.clipMessage
import fr.maubin.bridgeplus.clip.kEchoWindowMs
import fr.maubin.bridgeplus.clip.kMaxClipBytes
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.settings.BridgeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Relais du presse-papiers, sans réseau : contenus sensibles jamais
	envoyés, taille maximale, réglages de chaque sens, garde contre l'écho,
	échanges remis à l'historique.
*/
class ClipTest
{
	private var fNow      = 1_000_000L														// horloge
	private var fSettings = BridgeSettings()													// réglages
	private var fPeers    = listOf("Bureau")													// appareils reliés
	private val fSent     = mutableListOf<LinkMessage.Clip>()								// messages envoyés
	private val fApplied  = mutableListOf<String>()											// textes placés dans le presse-papiers
	private val fRecorded = mutableListOf<Pair<String, Boolean>>()							// échanges remis à l'historique
	private val fRelay    = ClipRelay(
		fSend     = { vMessage -> (vMessage as? LinkMessage.Clip)?.let { fSent += it }; fPeers },
		fApply    = { fApplied += it; true },
		fSettings = { fSettings },
		fRecord   = { vText, vOut, _ -> fRecorded += vText to vOut },
		fNow      = { fNow }
	)																							// relais

	/*
		Copie ordinaire : envoyée à tous les appareils reliés, remise à
		l'historique.
	*/
	@Test
	fun sendsCopy()
	{
		val vResult = fRelay.offer("bonjour", false)											// résultat
		assertEquals(ClipOutcome.Sent, vResult.outcome)
		assertEquals(listOf("Bureau"), vResult.peers)
		assertEquals("bonjour", fSent.single().text)
		assertEquals(listOf("bonjour" to true), fRecorded)
	}

	/*
		Contenu marqué sensible : jamais envoyé, même par un geste explicite,
		et jamais remis à l'historique.
	*/
	@Test
	fun neverSendsSensitive()
	{
		assertEquals(ClipOutcome.Sensitive, fRelay.offer("mot-de-passe", true).outcome)
		assertEquals(ClipOutcome.Sensitive, fRelay.offer("mot-de-passe", true, inExplicit = true).outcome)
		assertTrue(fSent.isEmpty())
		assertTrue(fRecorded.isEmpty())
	}

	/*
		Taille : 1 Mo exactement passe, un octet de plus non (UTF-8 compté :
		« é » vaut deux octets) ; un texte vide ou blanc ne part pas.
	*/
	@Test
	fun sizeLimit()
	{
		assertEquals(ClipOutcome.Sent, fRelay.offer("a".repeat(kMaxClipBytes), false).outcome)
		val vTooBig = fRelay.offer("é".repeat(kMaxClipBytes / 2) + "x", false)					// 1 Mo + 1 octet
		assertEquals(ClipOutcome.TooLarge, vTooBig.outcome)
		assertEquals(kMaxClipBytes + 1, vTooBig.bytes)
		assertEquals(ClipOutcome.Empty, fRelay.offer("  \n", false).outcome)
		assertEquals(ClipOutcome.Empty, fRelay.offer(null, false).outcome)
	}

	/*
		Réglages : envoi automatique désactivé, mais un geste explicite passe ;
		réception désactivée, rien n'est placé.
	*/
	@Test
	fun settingsOfEachDirection()
	{
		fSettings = BridgeSettings(clipSend = false, clipReceive = false)
		assertEquals(ClipOutcome.Disabled, fRelay.offer("auto", false).outcome)
		assertEquals(ClipOutcome.Sent, fRelay.offer("tuile", false, inExplicit = true).outcome)
		assertFalse(fRelay.receive("Bureau", LinkMessage.Clip("1", "reçu", fNow)))
		assertTrue(fApplied.isEmpty())
	}

	/*
		Écho : un texte reçu, placé dans le presse-papiers, ne repart pas ; une
		copie déjà envoyée non plus ; après cinq minutes, il peut repartir.
	*/
	@Test
	fun noEcho()
	{
		assertTrue(fRelay.receive("Bureau", LinkMessage.Clip("1", "du PC", fNow)))
		assertEquals(listOf("du PC"), fApplied)
		assertEquals(ClipOutcome.Echo, fRelay.offer("du PC", false).outcome)
		assertEquals(ClipOutcome.Sent, fRelay.offer("à moi", false).outcome)
		assertEquals(ClipOutcome.Echo, fRelay.offer("à moi", false).outcome)
		assertEquals(ClipOutcome.Sent, fRelay.offer("à moi", false, inExplicit = true).outcome)
		fNow += kEchoWindowMs + 1
		assertEquals(ClipOutcome.Sent, fRelay.offer("du PC", false).outcome)
	}

	/*
		Aucun appareil relié : rien n'est retenu, la copie pourra repartir.
	*/
	@Test
	fun noPeer()
	{
		fPeers = emptyList()
		assertEquals(ClipOutcome.NoPeer, fRelay.offer("seul", false).outcome)
		fPeers = listOf("Bureau")
		assertEquals(ClipOutcome.Sent, fRelay.offer("seul", false).outcome)
	}

	/*
		Texte recopié depuis l'historique : il ne repart pas (écho), sauf par
		un geste explicite ; un texte reçu est remis à l'historique.
	*/
	@Test
	fun historyCopyIsNotResent()
	{
		fRelay.markSeen("ancien texte")
		assertEquals(ClipOutcome.Echo, fRelay.offer("ancien texte", false).outcome)
		assertEquals(ClipOutcome.Sent, fRelay.offer("ancien texte", false, inExplicit = true).outcome)
		fRelay.receive("Bureau", LinkMessage.Clip("2", "du PC", fNow))
		assertEquals("du PC" to false, fRecorded.last())
	}

	/*
		Messages en clair : envoi, aucun appareil, sensible, trop long ;
		rien à dire pour un écho.
	*/
	@Test
	fun messages()
	{
		assertEquals("Envoyé à Bureau", clipMessage(fRelay.offer("salut", false)))
		assertEquals("Contenu sensible : il n'est jamais envoyé.", clipMessage(fRelay.offer("x", true)))
		assertEquals("Texte trop long (2,0 Mo) : 1 Mo au plus.", clipMessage(fRelay.offer("a".repeat(2 * kMaxClipBytes), false)))
		assertEquals(null, clipMessage(fRelay.offer("salut", false)))
		fPeers = emptyList()
		assertEquals("Aucun appareil relié : le texte n'est pas parti.", clipMessage(fRelay.offer("autre", false)))
	}
}
