package fr.maubin.bridgeplus

import fr.maubin.bridgeplus.link.LocalAddress
import fr.maubin.bridgeplus.link.LocalNetwork
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.newLinkKey
import fr.maubin.bridgeplus.peers.PcLink
import fr.maubin.bridgeplus.store.KeyBox
import fr.maubin.bridgeplus.store.SealedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/*
	Fichiers scellés : rien de lisible sur le disque, un fichier scellé avec
	une autre clé est mis de côté, les appareils survivent à un redémarrage.
*/
class StoreTest
{
	@get:Rule val fTemp = TemporaryFolder()											// dossier des fichiers

	private val kKey = newLinkKey()													// clé du coffret

	/*
		Contenu scellé : aller-retour ; illisible sur le disque.
	*/
	@Test
	fun sealedRoundTrip()
	{
		val vFile = fTemp.root.resolve("appareils.json")									// fichier
		SealedFile(vFile, KeyBox(kKey)).write("""{"cle":"secret-de-la-paire"}""")
		assertFalse(String(vFile.readBytes(), Charsets.ISO_8859_1).contains("secret"))
		assertEquals("""{"cle":"secret-de-la-paire"}""", SealedFile(vFile, KeyBox(kKey)).read())
	}

	/*
		Autre clé (autre appareil, clé du Keystore perdue) : le fichier est
		mis de côté, jamais écrasé sans trace.
	*/
	@Test
	fun wrongKeyPutsFileAside()
	{
		val vFile = fTemp.root.resolve("appareils.json")									// fichier
		SealedFile(vFile, KeyBox(kKey)).write("contenu")
		assertNull(SealedFile(vFile, KeyBox(newLinkKey())).read())
		assertFalse(vFile.exists())
		assertTrue(fTemp.root.resolve("appareils.json.illisible").exists())
	}

	/*
		Appareils du PC : l'identifiant, les réseaux de la maison et les noms
		choisis survivent à un redémarrage ; sans le fichier, un nouvel
		identifiant est tiré.
	*/
	@Test
	fun pcPeersPersist()
	{
		val vFile    = SealedFile(fTemp.root.resolve("pc.json"), KeyBox(kKey))				// fichier des appareils
		val vNetwork = { listOf(LocalNetwork(NetworkId.wifi("Maison")!!, listOf(LocalAddress("127.0.0.1", 8)))) }	// réseaux du PC
		val vFirst   = PcLink(vFile, "PC", "1.0", vNetwork, fPort = 0, fBeacon = 0)		// premier lancement
		vFirst.setHome(NetworkId.wifi("Maison")!!, true)
		vFirst.stop()
		val vSecond  = PcLink(vFile, "PC", "1.0", vNetwork, fPort = 0, fBeacon = 0)		// second lancement
		assertEquals(vFirst.info().id, vSecond.info().id)
		assertEquals(listOf(NetworkId.wifi("Maison")!!), vSecond.state.value.home)
		vFile.delete()
		assertNotEquals(vFirst.info().id, PcLink(vFile, "PC", "1.0", vNetwork, fPort = 0, fBeacon = 0).info().id)
	}
}
