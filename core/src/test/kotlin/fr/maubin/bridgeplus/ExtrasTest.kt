package fr.maubin.bridgeplus

import fr.maubin.bridgeplus.battery.BatteryAlert
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.isWebLink
import fr.maubin.bridgeplus.settings.BridgeSettings
import fr.maubin.bridgeplus.settings.SettingsBackup
import fr.maubin.bridgeplus.settings.decodeBackup
import fr.maubin.bridgeplus.settings.encodeBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Petits plus, sans réseau : liens web seulement, alerte de batterie une
	fois par décharge, sauvegarde des réglages sans aucune clé.
*/
class ExtrasTest
{
	/*
		Liens : http et https d'un seul tenant ; jamais un fichier, un
		programme, un autre protocole ni un texte.
	*/
	@Test
	fun webLinks()
	{
		assertTrue(isWebLink("https://www.sncf-connect.com/billet?id=7Q4K2M"))
		assertTrue(isWebLink("  http://exemple.fr  "))
		assertFalse(isWebLink("file:///C:/Windows/System32/calc.exe"))
		assertFalse(isWebLink("javascript:alert(1)"))
		assertFalse(isWebLink("bridgeplus:ouvrir?id=x"))
		assertFalse(isWebLink("Regarde https://exemple.fr"))
		assertFalse(isWebLink("https://"))
		assertFalse(isWebLink("ms-settings:privacy"))
	}

	/*
		Batterie : alerte une seule fois sous 20 %, pas en charge, réarmée
		par la charge ou au-dessus de 25 %.
	*/
	@Test
	fun batteryAlertOncePerDischarge()
	{
		val vAlert = BatteryAlert()																	// alerte
		assertFalse(vAlert.update(40, false))
		assertTrue(vAlert.update(19, false))
		assertFalse(vAlert.update(15, false))
		assertFalse(vAlert.update(10, true))
		assertTrue(vAlert.update(18, false))
		assertFalse(vAlert.update(22, false))
		assertFalse(vAlert.update(19, false))
		assertFalse(vAlert.update(30, false))
		assertTrue(vAlert.update(12, false))
	}

	/*
		Sauvegarde : aller-retour des réglages et des réseaux de la maison ;
		aucune clé dans le fichier ; un autre fichier est refusé.
	*/
	@Test
	fun settingsBackup()
	{
		val vBackup = SettingsBackup(settings = BridgeSettings(clipSend = false, noticeApps = mapOf("com.example" to true)), home = listOf(NetworkId.wifi("Maison")!!))	// sauvegarde
		val vText   = encodeBackup(vBackup)															// fichier
		assertEquals(vBackup, decodeBackup(vText))
		assertFalse(vText.contains("\"key\""))
		assertNull(decodeBackup("{\"pas\":\"une sauvegarde\"}"))
		assertNull(decodeBackup("n'importe quoi"))
	}
}
