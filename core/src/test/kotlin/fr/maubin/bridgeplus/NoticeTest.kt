package fr.maubin.bridgeplus

import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.notice.NoticeCandidate
import fr.maubin.bridgeplus.notice.NoticeOutbox
import fr.maubin.bridgeplus.notice.isAppAllowed
import fr.maubin.bridgeplus.notice.isOneTimeCode
import fr.maubin.bridgeplus.settings.BridgeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
	Notifications du téléphone, sans réseau : codes à usage unique repérés,
	applis exclues d'office, liste blanche par défaut, filtre de l'envoi,
	icône envoyée une fois, retrait signalé.
*/
class NoticeTest
{
	private var fSettings = BridgeSettings()													// réglages
	private var fPeers    = listOf("Bureau")													// PC reliés
	private val fSent     = mutableListOf<LinkMessage>()										// messages envoyés
	private val fOutbox   = NoticeOutbox({ vMessage -> fSent += vMessage; fPeers }, { fSettings }, { byteArrayOf(1, 2, 3) })	// envoi

	/*
		Notification d'une messagerie.
		inKey  : clé
		inText : texte
		inPkg  : appli
	*/
	private fun notice(
		inKey  : String = "k1",
		inText : String = "On se voit à 19 h ?",
		inPkg  : String = "com.whatsapp"
	) = NoticeCandidate(inKey, inPkg, "WhatsApp", "Marie", inText, 0)

	/*
		Codes à usage unique : reconnus avec les mots qui les accompagnent,
		en français et en anglais ; pas les numéros de commande, les heures,
		les dates ni les prix.
	*/
	@Test
	fun oneTimeCodes()
	{
		assertTrue(isOneTimeCode("Banque", "Votre code de vérification est 482913"))
		assertTrue(isOneTimeCode("", "123 456 est votre code de connexion. Ne le communiquez à personne."))
		assertTrue(isOneTimeCode("Google", "G-728311 is your Google verification code"))
		assertTrue(isOneTimeCode("Service", "Code : 4821, valable 10 minutes"))
		assertTrue(isOneTimeCode("Amazon", "Your OTP is 99812345"))
		assertFalse(isOneTimeCode("Marie", "On se voit à 19:30 au 12 rue Garibaldi ?"))
		assertFalse(isOneTimeCode("Boutique", "Commande 4482913 expédiée le 13/11/2026"))
		assertFalse(isOneTimeCode("Paul", "Ça fait 1250,50 € en tout"))
		assertFalse(isOneTimeCode("Paul", "Tu as le code du portail ?"))
	}

	/*
		Liste blanche : messageries par défaut, le reste sur choix ; exclues
		d'office même si on les active (KeePlus, banque, authentification).
	*/
	@Test
	fun allowedApps()
	{
		assertTrue(isAppAllowed("com.whatsapp", fSettings))
		assertFalse(isAppAllowed("com.example.jeu", fSettings))
		val vChosen = BridgeSettings(noticeApps = mapOf("com.example.jeu" to true, "com.whatsapp" to false, "fr.maubin.keeplus" to true, "com.boursorama.android.clients" to true))	// choix
		assertTrue(isAppAllowed("com.example.jeu", vChosen))
		assertFalse(isAppAllowed("com.whatsapp", vChosen))
		assertFalse(isAppAllowed("fr.maubin.keeplus", vChosen))
		assertFalse(isAppAllowed("com.boursorama.android.clients", vChosen))
		assertFalse(isAppAllowed("com.google.android.apps.authenticator2", BridgeSettings(noticeApps = mapOf("com.google.android.apps.authenticator2" to true))))
	}

	/*
		Envoi : icône une seule fois par session, puis la notification ; la
		même notification republiée à l'identique ne repart pas ; une mise à
		jour repart.
	*/
	@Test
	fun sendsWithIconOnce()
	{
		assertTrue(fOutbox.posted(notice("k1")))
		assertTrue(fOutbox.posted(notice("k2", "Et toi ?")))
		assertFalse(fOutbox.posted(notice("k2", "Et toi ?")))
		assertTrue(fOutbox.posted(notice("k2", "Et toi ? (modifié)")))
		assertEquals(1, fSent.count { it is LinkMessage.AppIcon })
		assertEquals(3, fSent.count { it is LinkMessage.PhoneNotice })
		fOutbox.newSession()
		fOutbox.posted(notice("k3", "Nouveau"))
		assertEquals(2, fSent.count { it is LinkMessage.AppIcon })
	}

	/*
		Filtre : réglage général coupé, appli non autorisée, notification
		permanente, résumé de groupe, secrète, silencieuse, code, vide : rien
		ne part.
	*/
	@Test
	fun filters()
	{
		assertFalse(fOutbox.posted(notice(inPkg = "com.example.jeu")))
		assertFalse(fOutbox.posted(notice().copy(ongoing = true)))
		assertFalse(fOutbox.posted(notice().copy(groupSummary = true)))
		assertFalse(fOutbox.posted(notice().copy(secret = true)))
		assertFalse(fOutbox.posted(notice().copy(silent = true)))
		assertFalse(fOutbox.posted(notice(inText = "Votre code de connexion : 552311")))
		assertFalse(fOutbox.posted(notice().copy(title = " ", text = "")))
		assertFalse(fOutbox.posted(notice(inPkg = "fr.maubin.bridgeplus")))
		fSettings = BridgeSettings(noticesSend = false)
		assertFalse(fOutbox.posted(notice()))
		assertTrue(fSent.isEmpty())
	}

	/*
		Retrait : signalé pour une notification envoyée, pas pour une autre ;
		sans PC relié, rien n'est retenu.
	*/
	@Test
	fun removals()
	{
		fOutbox.posted(notice("k1"))
		fOutbox.removed("k1")
		fOutbox.removed("inconnue")
		assertEquals(listOf(LinkMessage.NoticeRemoved("k1")), fSent.filterIsInstance<LinkMessage.NoticeRemoved>())
		fPeers = emptyList()
		assertFalse(fOutbox.posted(notice("k9")))
		fOutbox.removed("k9")
		assertEquals(1, fSent.count { it is LinkMessage.NoticeRemoved })
	}
}
