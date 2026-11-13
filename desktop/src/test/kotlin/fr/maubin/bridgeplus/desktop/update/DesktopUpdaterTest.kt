package fr.maubin.bridgeplus.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
	Mise à jour du PC : préparation du dossier neuf depuis le zip (dossier
	BridgePlus ou appli à la racine, chemin piégé ou exe absent refusés
	sans rien laisser), et script de remplacement exécuté pour de vrai sous
	Windows (remplacement, puis retour à l'ancienne version si le dossier
	neuf manque).
*/
class DesktopUpdaterTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// dossiers jetables

	/*
		Écrit un zip.
		inName    : nom du fichier
		inEntries : chemins et contenus
	*/
	private fun zip(
		inName    : String,
		inEntries : Map<String, String>
	): File
	{
		val vFile = fTemp.root.resolve(inName)											// zip écrit
		ZipOutputStream(vFile.outputStream()).use { inZip ->
			inEntries.forEach { (inPath, inText) ->
				inZip.putNextEntry(ZipEntry(inPath)); inZip.write(inText.toByteArray()); inZip.closeEntry()
			}
		}
		return vFile
	}

	/*
		Crée le dossier de l'appli installée, avec sa version.
		inText : contenu du fichier de version
	*/
	private fun installed(inText: String): Path
	{
		val vFolder = fTemp.root.toPath().resolve("apps").resolve("BridgePlus-1.2")	// dossier choisi par l'utilisateur
		Files.createDirectories(vFolder.resolve("app"))
		Files.writeString(vFolder.resolve(kExeName), "ancien exe")
		Files.writeString(vFolder.resolve("app/version.txt"), inText)
		return vFolder
	}

	/*
		Zip de la CI (dossier BridgePlus) et zip à plat : le dossier neuf
		« <nom>.new » contient l'appli, la décompression brute a disparu.
	*/
	@Test
	fun prepare()
	{
		val vFolder = installed("1.2")													// appli installée
		val vNew    = prepareUpdate(zip("ci.zip", mapOf("BridgePlus/$kExeName" to "nouvel exe", "BridgePlus/app/version.txt" to "1.3")), vFolder)	// dossier neuf
		assertEquals(vFolder.resolveSibling("BridgePlus-1.2.new"), vNew)
		assertEquals("nouvel exe", Files.readString(vNew.resolve(kExeName)))
		assertEquals("1.3", Files.readString(vNew.resolve("app/version.txt")))
		assertFalse(Files.exists(vFolder.resolveSibling("BridgePlus-1.2.unzip")))
		val vFlat = prepareUpdate(zip("plat.zip", mapOf(kExeName to "exe à plat")), vFolder)	// zip sans dossier
		assertEquals("exe à plat", Files.readString(vFlat.resolve(kExeName)))
		assertFalse(Files.exists(vFlat.resolve("app")))
		assertEquals("ancien exe", Files.readString(vFolder.resolve(kExeName)))
	}

	/*
		Zip piégé (chemin qui sort du dossier) ou sans exe : refus, rien ne
		reste à côté de l'appli, qui est intacte.
	*/
	@Test
	fun refusals()
	{
		val vFolder = installed("1.2")													// appli installée
		listOf(
			zip("piege.zip", mapOf("BridgePlus/$kExeName" to "x", "../../evade.txt" to "x")),
			zip("vide.zip", mapOf("BridgePlus/lisez-moi.txt" to "x"))
		).forEach { inZip ->
			try
			{
				prepareUpdate(inZip, vFolder)
				fail("${inZip.name} accepté")
			}
			catch (inError: IOException)
			{
				assertTrue(inError.message!!, inError.message!!.contains("refusé") || inError.message!!.contains("absent"))
			}
			assertEquals(listOf("BridgePlus-1.2"), Files.list(vFolder.parent).use { inList -> inList.map { it.fileName.toString() }.toList() })
		}
		assertFalse(File(fTemp.root, "evade.txt").exists())
		assertEquals("ancien exe", Files.readString(vFolder.resolve(kExeName)))
	}

	/*
		Lance le script de remplacement avec PowerShell, sans relance de
		l'appli, en attendant un processus court ; renvoie son code de sortie.
		inFolder : dossier de l'appli
		inNew    : dossier neuf
		inCwd    : dossier courant du script (comme lancé par l'appli : son propre dossier) ; null : celui des tests
	*/
	private fun runSwap(
		inFolder : Path,
		inNew    : Path,
		inCwd    : Path? = null
	): Int
	{
		val vWaited = ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "Start-Sleep -Milliseconds 800").start()	// « appli » à attendre
		val vScript = fTemp.root.toPath().resolve("maj.ps1")								// script
		Files.writeString(vScript, "\uFEFF" + swapScript(vWaited.pid(), inFolder, inNew, inRelaunch = false))
		val vRun = ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", vScript.toString()).directory(inCwd?.toFile()).redirectErrorStream(true).start()	// script lancé
		val vOutput = vRun.inputStream.bufferedReader().readText()							// sortie (diagnostic)
		assertTrue("script trop long : $vOutput", vRun.waitFor(60, TimeUnit.SECONDS))
		assertFalse("le script n'a pas attendu l'appli", vWaited.isAlive)
		return vRun.exitValue()
	}

	/*
		Anciens dossiers « .old » restés à côté de l'appli.
		inFolder : dossier de l'appli
	*/
	private fun oldFolders(inFolder: Path): List<String> =
		Files.list(inFolder.parent).use { inList -> inList.map { it.fileName.toString() }.filter { it.startsWith("${inFolder.fileName}.old") }.toList() }

	/*
		Remplacement réel (Windows) : le dossier garde son nom et reçoit la
		nouvelle version ; .new et .old ont disparu. Sans dossier neuf :
		l'ancienne version est remise en place.
	*/
	@Test
	fun swap()
	{
		assumeTrue("PowerShell (Windows) nécessaire", System.getProperty("os.name").startsWith("Windows"))
		val vFolder = installed("1.2 (é)")												// appli installée
		val vNew    = prepareUpdate(zip("ci.zip", mapOf("BridgePlus/$kExeName" to "nouvel exe", "BridgePlus/app/version.txt" to "1.3")), vFolder)	// dossier neuf
		assertEquals(0, runSwap(vFolder, vNew))
		assertEquals("1.3", Files.readString(vFolder.resolve("app/version.txt")))
		assertFalse(Files.exists(vNew))
		assertEquals(emptyList<String>(), oldFolders(vFolder))
		assertEquals(2, runSwap(vFolder, vFolder.resolveSibling("absent.new")))
		assertEquals("1.3", Files.readString(vFolder.resolve("app/version.txt")))
		assertEquals(emptyList<String>(), oldFolders(vFolder))
	}

	/*
		Script lancé depuis le dossier de l'appli (double-clic sur
		BridgePlus.exe : c'est le dossier courant qu'il hérite) : il s'en
		détache et le remplacement réussit.
	*/
	@Test
	fun swapFromTheAppFolder()
	{
		assumeTrue("PowerShell (Windows) nécessaire", System.getProperty("os.name").startsWith("Windows"))
		val vFolder = installed("1.5")																// appli installée
		val vNew    = prepareUpdate(zip("ci.zip", mapOf("BridgePlus/$kExeName" to "nouvel exe", "BridgePlus/app/version.txt" to "1.6")), vFolder)	// dossier neuf
		assertEquals(0, runSwap(vFolder, vNew, inCwd = vFolder))
		assertEquals("1.6", Files.readString(vFolder.resolve("app/version.txt")))
		assertFalse(Files.exists(vNew))
	}

	/*
		Dossier neuf resté après une mise à jour ratée : supprimé au lancement
		suivant ; rien sans lui.
	*/
	@Test
	fun leftoverIsCleaned()
	{
		val vFolder = installed("1.5")																// appli installée
		assertFalse(cleanLeftover(vFolder))
		val vNew    = prepareUpdate(zip("ci.zip", mapOf("BridgePlus/$kExeName" to "nouvel exe")), vFolder)	// dossier neuf resté
		assertTrue(cleanLeftover(vFolder))
		assertFalse(Files.exists(vNew))
		assertEquals("ancien exe", Files.readString(vFolder.resolve(kExeName)))
	}

	/*
		Fichiers rangés par l'utilisateur dans le dossier de l'appli (dossier
		« files », fichier à la racine) : ils passent dans la nouvelle version,
		puis l'ancien dossier est supprimé ; un ancien « .old » déjà présent
		n'est jamais touché.
	*/
	@Test
	fun userFilesAreKept()
	{
		assumeTrue("PowerShell (Windows) nécessaire", System.getProperty("os.name").startsWith("Windows"))
		val vFolder = installed("1.5")																// appli installée
		Files.createDirectories(vFolder.resolve("files/2026"))
		Files.writeString(vFolder.resolve("files/2026/sauvegarde.xlsx"), "ma sauvegarde")
		Files.writeString(vFolder.resolve("notes.txt"), "mes notes")
		val vStale  = Files.createDirectories(vFolder.resolveSibling("${vFolder.fileName}.old-ancien")).also { Files.writeString(it.resolve("garde.txt"), "à garder") }	// ancien dossier déjà là
		val vNew    = prepareUpdate(zip("ci.zip", mapOf("BridgePlus/$kExeName" to "nouvel exe", "BridgePlus/app/version.txt" to "1.6")), vFolder)	// dossier neuf
		assertEquals(0, runSwap(vFolder, vNew, inCwd = vFolder))
		assertEquals("1.6", Files.readString(vFolder.resolve("app/version.txt")))
		assertEquals("nouvel exe", Files.readString(vFolder.resolve(kExeName)))
		assertEquals("ma sauvegarde", Files.readString(vFolder.resolve("files/2026/sauvegarde.xlsx")))
		assertEquals("mes notes", Files.readString(vFolder.resolve("notes.txt")))
		assertEquals("à garder", Files.readString(vStale.resolve("garde.txt")))
		assertEquals(listOf(vStale.fileName.toString()), oldFolders(vFolder))
	}
}
