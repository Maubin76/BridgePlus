package fr.maubin.bridgeplus.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.W32APIOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
	Fonctions du presse-papiers utilisées par le test pour jouer le rôle
	d'une autre appli (user32.dll, kernel32.dll).
*/
private interface TestClipboard : Library
{
	/*
		Ouvre le presse-papiers.
		hwnd : fenêtre ; null pour le fil courant
	*/
	fun OpenClipboard(hwnd: Pointer?): Boolean

	/*
		Ferme le presse-papiers.
	*/
	fun CloseClipboard(): Boolean

	/*
		Vide le presse-papiers.
	*/
	fun EmptyClipboard(): Boolean

	/*
		Dépose une donnée.
		format : format
		data   : mémoire globale
	*/
	fun SetClipboardData(
		format : Int,
		data   : Pointer?
	): Pointer?

	/*
		Donnée d'un format.
		format : format
	*/
	fun GetClipboardData(format: Int): Pointer?

	/*
		Enregistre un format nommé.
		name : nom
	*/
	fun RegisterClipboardFormat(name: String): Int
}

/*
	Mémoire globale (kernel32.dll).
*/
private interface TestMemory : Library
{
	/*
		Réserve un bloc.
		flags : options
		bytes : taille
	*/
	fun GlobalAlloc(
		flags : Int,
		bytes : Long
	): Pointer?

	/*
		Adresse d'un bloc.
		mem : bloc
	*/
	fun GlobalLock(mem: Pointer): Pointer?

	/*
		Libère l'adresse d'un bloc.
		mem : bloc
	*/
	fun GlobalUnlock(mem: Pointer): Boolean
}

/*
	Presse-papiers réel de Windows : une copie ordinaire est lue, une copie
	marquée sensible (KeePlus, gestionnaires de mots de passe) ne l'est pas,
	un texte reçu y est placé sans repartir. Ce test remplace le contenu du
	presse-papiers (puis remet le texte d'origine) : il ne tourne que sous
	Windows et sur demande (./gradlew :desktop:test -Pbridgeplus.testPressePapiers).
*/
class WinClipboardTest
{
	private val fClip   by lazy { Native.load("user32", TestClipboard::class.java, W32APIOptions.DEFAULT_OPTIONS) }	// presse-papiers
	private val fMemory by lazy { Native.load("kernel32", TestMemory::class.java) }							// mémoire globale

	/*
		Copies ordinaires et sensibles, puis écriture d'un texte reçu.
	*/
	@Test
	fun readsPlainSkipsSensitive()
	{
		assumeTrue(WinClipboard.available && System.getProperty("bridgeplus.testPressePapiers") == "true")
		val vBefore = readText()																	// contenu d'origine
		val vSeen   = LinkedBlockingQueue<Pair<String?, Boolean>>()									// copies vues
		val vBoard  = WinClipboard { vText, vSensitive -> vSeen.add(vText to vSensitive) }			// presse-papiers suivi
		vBoard.start()
		try
		{
			copy("copie ordinaire")
			assertEquals("copie ordinaire" to false, vSeen.poll(3, TimeUnit.SECONDS))
			copy("secret de KeePlus", "ExcludeClipboardContentFromMonitorProcessing" to 0)
			assertEquals(null to true, vSeen.poll(3, TimeUnit.SECONDS))
			copy("secret hors historique", "CanIncludeInClipboardHistory" to 0)
			assertEquals(null to true, vSeen.poll(3, TimeUnit.SECONDS))
			copy("secret KeePass", "Clipboard Viewer Ignore" to 0)
			assertEquals(null to true, vSeen.poll(3, TimeUnit.SECONDS))
			copy("historique permis", "CanIncludeInClipboardHistory" to 1)
			assertEquals("historique permis" to false, vSeen.poll(3, TimeUnit.SECONDS))
			vBoard.write("reçu du téléphone")
			Thread.sleep(500)
			assertEquals("reçu du téléphone", readText())
			assertNull(vSeen.poll(1, TimeUnit.SECONDS))
		}
		finally
		{
			copy(vBefore ?: "")
		}
	}

	/*
		Copie comme le ferait une autre appli : texte, et éventuellement un
		format nommé portant un entier.
		inText   : texte
		inMarker : format nommé et sa valeur ; null sans
	*/
	private fun copy(
		inText   : String,
		inMarker : Pair<String, Int>? = null
	)
	{
		open()
		fClip.EmptyClipboard()
		fClip.SetClipboardData(13, global((inText + Char(0)).toByteArray(Charsets.UTF_16LE)))
		inMarker?.let { (vName, vValue) -> fClip.SetClipboardData(fClip.RegisterClipboardFormat(vName), global(java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(vValue).array())) }
		fClip.CloseClipboard()
	}

	/*
		Ouvre le presse-papiers, en réessayant un court instant s'il est occupé.
	*/
	private fun open()
	{
		repeat(20)
		{
			if (fClip.OpenClipboard(null)) return
			Thread.sleep(20)
		}
	}

	/*
		Texte du presse-papiers ; null sans texte.
	*/
	private fun readText(): String?
	{
		open()
		try
		{
			val vData = fClip.GetClipboardData(13) ?: return null										// bloc du texte
			val vPtr  = fMemory.GlobalLock(vData) ?: return null										// adresse
			return vPtr.getWideString(0).also { fMemory.GlobalUnlock(vData) }
		}
		finally
		{
			fClip.CloseClipboard()
		}
	}

	/*
		Bloc de mémoire globale avec ces octets.
		inBytes : contenu
	*/
	private fun global(inBytes: ByteArray): Pointer?
	{
		val vMem = fMemory.GlobalAlloc(2, inBytes.size.toLong()) ?: return null						// bloc
		fMemory.GlobalLock(vMem)?.write(0, inBytes, 0, inBytes.size)
		fMemory.GlobalUnlock(vMem)
		return vMem
	}
}
