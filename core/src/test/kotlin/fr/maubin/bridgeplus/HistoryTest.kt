package fr.maubin.bridgeplus

import fr.maubin.bridgeplus.history.ClipHistory
import fr.maubin.bridgeplus.link.newLinkKey
import fr.maubin.bridgeplus.store.KeyBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/*
	Historique chiffré du presse-papiers : limite des 50 avec les épingles,
	texte déjà présent qui remonte, recherche sans majuscules ni accents,
	rechargement, rien de lisible sur le disque, clé perdue, suppression,
	« Tout effacer ».
*/
class HistoryTest
{
	@get:Rule val fTemp = TemporaryFolder()											// dossier de l'historique

	private val kKey = newLinkKey()													// clé du coffret
	private var fNow = 1_000L														// horloge

	/*
		Historique dans le dossier temporaire.
		inLimit : éléments non épinglés gardés
		inKey   : clé du coffret
	*/
	private fun history(
		inLimit : Int       = 50,
		inKey   : ByteArray = kKey
	) = ClipHistory(fTemp.root, KeyBox(inKey), { fNow++ }, inLimit)

	/*
		Limite : les plus anciens non épinglés partent (et leur fichier avec) ;
		les épinglés restent en plus.
	*/
	@Test
	fun limitKeepsPinned()
	{
		val vHistory = history(inLimit = 3)														// historique
		val vFirst   = vHistory.add("premier", true, listOf("Bureau"))								// le plus ancien
		vHistory.pin(vFirst.id, true)
		val vSecond  = vHistory.add("deuxième", false, listOf("Bureau"))
		repeat(3) { vHistory.add("texte $it", true, listOf("Bureau")) }
		val vIds     = vHistory.items.value.map { it.id }											// éléments gardés
		assertEquals(4, vIds.size)
		assertTrue(vFirst.id in vIds)
		assertFalse(vSecond.id in vIds)
		assertFalse(File(fTemp.root, "${vSecond.id}.bin").exists())
		assertEquals("premier", vHistory.text(vFirst.id))
	}

	/*
		Texte déjà présent : il remonte en tête sans doublon et garde son
		épingle et son identifiant.
	*/
	@Test
	fun duplicateMovesUp()
	{
		val vHistory = history()																	// historique
		val vOld     = vHistory.add("même texte", true, listOf("Bureau"))							// premier échange
		vHistory.pin(vOld.id, true)
		vHistory.add("autre", true, listOf("Bureau"))
		val vAgain   = vHistory.add("même texte", false, listOf("Portable"))						// nouvel échange
		assertEquals(2, vHistory.items.value.size)
		assertEquals(vOld.id, vAgain.id)
		assertEquals(vOld.id, vHistory.items.value.first().id)
		assertTrue(vAgain.pinned)
		assertEquals(listOf("Portable"), vAgain.peers)
	}

	/*
		Recherche : sans majuscules ni accents, dans le texte complet, épinglés
		d'abord ; vide : tout.
	*/
	@Test
	fun search()
	{
		val vHistory = history()																	// historique
		vHistory.add("Rendez-vous à l'Été", true, emptyList())
		val vPinned  = vHistory.add("adresse : 12 rue des Étés", false, emptyList())				// élément épinglé
		vHistory.pin(vPinned.id, true)
		vHistory.add("x".repeat(300) + " caché après l'aperçu", true, emptyList())
		vHistory.add("rien à voir", true, emptyList())
		assertEquals(listOf(vPinned.id), vHistory.search("RUE DES ETES").map { it.id })
		assertEquals(2, vHistory.search("ete").size)
		assertEquals(vPinned.id, vHistory.search("ete").first().id)
		assertEquals(1, vHistory.search("caché").size)
		assertEquals(4, vHistory.search("  ").size)
	}

	/*
		Rechargement : les éléments, les épingles et les textes survivent à un
		redémarrage ; rien n'est lisible sur le disque.
	*/
	@Test
	fun persistsSealed()
	{
		val vItem = history().let { vHistory -> vHistory.add("secret du presse-papiers", true, listOf("Bureau")).also { vHistory.pin(it.id, true) } }	// élément
		val vBack = history()																		// historique rechargé
		assertTrue(vBack.items.value.single().pinned)
		assertEquals("secret du presse-papiers", vBack.text(vItem.id))
		fTemp.root.listFiles()!!.forEach { assertFalse(String(it.readBytes(), Charsets.ISO_8859_1).contains("secret")) }
	}

	/*
		Clé perdue (autre compte Windows, Keystore réinitialisé) : historique
		vide, sans plantage.
	*/
	@Test
	fun lostKeyGivesEmptyHistory()
	{
		history().add("texte", true, emptyList())
		val vOther = history(inKey = newLinkKey())												// autre clé
		assertTrue(vOther.items.value.isEmpty())
		assertNull(vOther.text("inconnu"))
	}

	/*
		Suppression d'un élément, puis « Tout effacer » (épingles comprises) :
		plus aucun fichier de texte.
	*/
	@Test
	fun deleteAndClear()
	{
		val vHistory = history()																	// historique
		val vOne     = vHistory.add("un", true, emptyList())										// premier élément
		val vTwo     = vHistory.add("deux", true, emptyList())										// second élément
		vHistory.pin(vTwo.id, true)
		vHistory.delete(vOne.id)
		assertEquals(listOf(vTwo.id), vHistory.items.value.map { it.id })
		assertNull(vHistory.text(vOne.id))
		vHistory.clear()
		assertTrue(vHistory.items.value.isEmpty())
		assertEquals(listOf("index.bin"), fTemp.root.listFiles()!!.map { it.name })
	}
}
