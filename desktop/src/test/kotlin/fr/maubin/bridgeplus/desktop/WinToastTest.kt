package fr.maubin.bridgeplus.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
	Notifications réelles de Windows : deux notifications suivies sont
	affichées ; celle que l'appli retire n'est pas signalée comme fermée,
	celle qui disparaît sans elle (effacée du centre de notifications, comme
	le ferait l'utilisateur) l'est. Ce test affiche de vraies notifications :
	il ne tourne que sous Windows et sur demande
	(./gradlew :desktop:test -Pbridgeplus.testPressePapiers).
*/
class WinToastTest
{
	/*
		Affichage, retrait par l'appli, fermeture signalée.
	*/
	@Test
	fun showRemoveAndDismiss()
	{
		assumeTrue(WinBridge.available && System.getProperty("bridgeplus.testPressePapiers") == "true")
		val vDismissed = LinkedBlockingQueue<String>()												// fermetures signalées
		WinToast.onDismissed = { vDismissed.add(it) }
		WinToast.setup(File.createTempFile("bridgeplus", ".png"))
		try
		{
			assertTrue(WinToast.show("Essai de BridgePlus", "Retirée par l'appli (à ignorer)", inTag = "essai-retiree", inAttribution = "Test"))
			assertTrue(WinToast.show("Essai de BridgePlus", "Effacée « par l'utilisateur » (à ignorer)", inTag = "essai-fermee"))
			Thread.sleep(1_500)
			assertTrue(WinToast.remove("essai-retiree"))
			val vClear = ProcessBuilder("powershell", "-NoProfile", "-Command",
				"[void][Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]; " +
				"[Windows.UI.Notifications.ToastNotificationManager]::History.Remove('essai-fermee', 'telephone', '$kAppId')").start()	// effacement extérieur
			vClear.waitFor(30, TimeUnit.SECONDS)
			assertEquals("essai-fermee", vDismissed.poll(8, TimeUnit.SECONDS))
			assertNull(vDismissed.poll(2, TimeUnit.SECONDS))
		}
		finally
		{
			WinToast.stop()
		}
	}
}
