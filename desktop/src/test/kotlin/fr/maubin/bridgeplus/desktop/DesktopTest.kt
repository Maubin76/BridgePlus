package fr.maubin.bridgeplus.desktop

import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.peers.PairedPhone
import fr.maubin.bridgeplus.peers.PcLinkState
import fr.maubin.bridgeplus.ui.components.LinkLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Appli PC sans fenêtre : lecture des sorties de Windows (Wi-Fi, route par
	défaut), état de la liaison affiché, raccourci de la carte.
*/
class DesktopTest
{
	/*
		Wi-Fi : sortie française et anglaise, BSSID ignoré, carte retrouvée par
		sa description ; aucun Wi-Fi connecté.
	*/
	@Test
	fun wlans()
	{
		val vFrench = """

			Il existe 1 interface sur le système :

			    Nom                    : Wi-Fi
			    Description            : Intel(R) Wi-Fi 6 AX201 160MHz
			    État                   : connecté
			    SSID                   : Maison
			    BSSID                  : aa:bb:cc:dd:ee:ff

			    État du réseau hébergé  : Non disponible
		""".trimIndent()																			// sortie de netsh (Windows en français)
		assertEquals(listOf(WlanLink("Intel(R) Wi-Fi 6 AX201 160MHz", "Maison")), parseWlans(vFrench))
		assertEquals("Maison", parseSsid(vFrench))
		val vEnglish = "    Name                   : Wi-Fi\n    Description            : Realtek 8822CE\n    State                  : connected\n    SSID                   : Home Net\n"	// Windows en anglais
		assertEquals("Home Net", parseSsid(vEnglish))
		assertNull(parseSsid("Il existe 1 interface sur le système :\n\n    Nom : Wi-Fi\n    État : déconnecté\n"))
	}

	/*
		Route par défaut : la plus petite métrique l'emporte ; interface lue.
	*/
	@Test
	fun defaultRoute()
	{
		val vText = """
			IPv4 Table de routage
			Itinéraires actifs :
			Destination réseau    Masque réseau  Adr. passerelle   Adr. interface Métrique
			          0.0.0.0          0.0.0.0      192.168.1.1     192.168.1.20     35
			          0.0.0.0          0.0.0.0       10.0.0.254        10.0.0.12     50
		""".trimIndent()																			// sortie de route print
		assertEquals(DefaultRoute("192.168.1.1", "192.168.1.20"), parseRoute(vText))
		assertEquals("192.168.1.1", parseGateway(vText))
		assertEquals("aa-bb-cc-dd-ee-ff", parseMac("  192.168.1.1          aa-bb-cc-dd-ee-ff     dynamique", "192.168.1.1"))
	}

	/*
		État affiché : aucun téléphone, hors de la maison, relié, absent,
		erreur d'écoute ; toujours un texte.
	*/
	@Test
	fun lookOfState()
	{
		val vPhone = PairedPhone("t1", "Pixel", key = "k", pairedAt = 0)								// téléphone appairé
		val vHome  = PcLinkState(onHome = true, phones = listOf(vPhone), home = listOf(NetworkId.wifi("Maison")!!))	// à la maison
		assertEquals(LinkLook.Off, pcLook(PcLinkState()).look)
		assertEquals(LinkLook.AwayFromHome, pcLook(vHome.copy(onHome = false)).look)
		assertEquals("Relié à Pixel", pcLook(vHome.copy(connected = setOf("t1"))).title)
		assertEquals("Pixel est absent", pcLook(vHome).title)
		assertEquals(LinkLook.Refused, pcLook(vHome.copy(error = "port pris")).look)
	}

	/*
		Raccourci : Ctrl+Alt+V par défaut, permis (AltGr+V ne tape rien en
		AZERTY) ; Ctrl+Alt+E (« € ») et Ctrl+Alt+2 (« ~ ») refusés, comme une
		touche sans modificateur ; un raccourci illisible revient au défaut.
	*/
	@Test
	fun hotkeys()
	{
		val vDefault = Hotkey.parse(kDefaultHotkey)													// raccourci par défaut
		assertTrue(vDefault.usable)
		assertEquals("Ctrl + Alt + V", vDefault.label())
		assertEquals(0x0001 or 0x0002, vDefault.modifiers)
		assertFalse(Hotkey(ctrl = true, alt = true, shift = false, win = false, key = 'E'.code).usable)
		assertFalse(Hotkey(ctrl = true, alt = true, shift = false, win = false, key = '2'.code).usable)
		assertFalse(Hotkey(ctrl = false, alt = false, shift = true, win = false, key = 'V'.code).usable)
		assertTrue(Hotkey(ctrl = false, alt = true, shift = false, win = true, key = 'V'.code).usable)
		assertEquals("Win+Alt+V".let(Hotkey::parse).spec(), "Alt+Win+V")
		assertEquals(vDefault, Hotkey.parse("n'importe quoi"))
	}

	/*
		Lancements par un bouton de notification ou par l'Explorateur :
		argument lu ; lien ne portant qu'un identifiant (encodé), jamais un
		chemin.
	*/
	@Test
	fun launchArguments()
	{
		assertEquals("C:/Users/moi/photo.jpg", argAfter(arrayOf("--envoyer", "C:/Users/moi/photo.jpg"), ExplorerMenu.kSendArg))
		assertNull(argAfter(arrayOf("--envoyer"), ExplorerMenu.kSendArg))
		assertNull(argAfter(arrayOf("--demarrage"), kLinkArg))
		assertEquals("bridgeplus:ouvrir?id=a%2Bb%3D", WinToast.link("ouvrir", "a+b="))
	}
}
