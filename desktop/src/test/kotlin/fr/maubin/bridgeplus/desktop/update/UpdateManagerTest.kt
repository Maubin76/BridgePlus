package fr.maubin.bridgeplus.desktop.update

import com.sun.net.httpserver.HttpServer
import fr.maubin.bridgeplus.ui.update.InstallStep
import fr.maubin.bridgeplus.ui.update.UpdateInstaller
import fr.maubin.bridgeplus.ui.update.UpdateManager
import fr.maubin.bridgeplus.update.Release
import fr.maubin.bridgeplus.update.ReleaseAsset
import fr.maubin.bridgeplus.update.UpdatePlatform
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

/*
	Installeur simulé : enregistre les fichiers reçus et répond selon
	fSteps (puis Started).
	fDir     : dossier des téléchargements
	fAuto    : vérification au lancement permise
	fSteps   : réponses successives à install
	fResume  : l'autorisation est donnée (reprise possible)
	installs : fichiers reçus
*/
private class FakeInstaller(
	private val fDir   : File,
	private val fAuto  : Boolean = true,
	val fSteps         : ArrayDeque<InstallStep> = ArrayDeque(),
	var fResume        : Boolean = false
) : UpdateInstaller
{
	val installs = mutableListOf<File>()

	/*
		Plateforme : PC.
	*/
	override val platform = UpdatePlatform.WINDOWS

	/*
		Version installée.
	*/
	override val version = "1.0"

	/*
		Vérification au lancement.
	*/
	override val autoCheck get() = fAuto

	/*
		Pas d'explication.
	*/
	override val notice: String? = null

	/*
		Bouton principal.
	*/
	override val action = "Mettre à jour"

	/*
		Fichier téléchargé.
		inAsset : fichier de la release
	*/
	override fun target(inAsset: ReleaseAsset) = File(fDir, inAsset.name)

	/*
		Reprise possible.
	*/
	override fun canResume() = fResume

	/*
		Note le fichier et rend la réponse prévue.
		inFile : fichier
	*/
	override suspend fun install(inFile: File): InstallStep
	{
		installs += inFile
		return fSteps.removeFirstOrNull() ?: InstallStep.Started
	}
}

/*
	Mises à jour vues de l'appli : vérification au lancement (réglage,
	version ignorée, version de développement), téléchargement vérifié par
	l'empreinte, autorisation puis reprise, téléchargement seul.
*/
class UpdateManagerTest
{
	@get:Rule
	val fTemp = TemporaryFolder()														// dossiers jetables

	private val fZip    = ByteArray(200_000) { (it * 31).toByte() }							// « zip » de la release
	private var fServer : HttpServer? = null												// faux serveur de téléchargement
	private var fIgnored = ""																// version ignorée mémorisée

	/*
		Faux serveur qui sert le zip.
	*/
	@Before
	fun start()
	{
		fServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
			createContext("/BridgePlus-1.1-windows.zip") { vExchange ->
				vExchange.sendResponseHeaders(200, fZip.size.toLong())
				vExchange.responseBody.use { it.write(fZip) }
			}
			start()
		}
	}

	/*
		Arrêt du faux serveur.
	*/
	@After
	fun stop()
	{
		fServer?.stop(0)
	}

	/*
		Release 1.1 avec le zip du faux serveur.
		inSha : empreinte annoncée (celle du zip par défaut)
	*/
	private fun release(inSha: String = MessageDigest.getInstance("SHA-256").digest(fZip).joinToString("") { "%02x".format(it) }) =
		Release("1.1", null, listOf(ReleaseAsset("BridgePlus-1.1-windows.zip", fZip.size.toLong(), "http://127.0.0.1:${fServer!!.address.port}/BridgePlus-1.1-windows.zip", inSha)))

	/*
		Gestionnaire avec une source qui rend la release.
		inInstaller : installeur
		inEnabled   : vérification au lancement activée
		inRelease   : release publiée
	*/
	private fun manager(
		inInstaller : UpdateInstaller,
		inEnabled   : Boolean = true,
		inRelease   : Release = release()
	) = UpdateManager(inInstaller, { inEnabled }, { fIgnored }, { fIgnored = it }, { inRelease })

	/*
		Attend qu'une condition devienne vraie (coroutines de l'interface).
		inCondition : condition
	*/
	private fun await(inCondition: () -> Boolean) = runBlocking { withTimeout(10_000) { while (!inCondition()) delay(20) } }

	/*
		Vérification au lancement : fenêtre pour 1.1 ; rien si la version est
		ignorée, la vérification désactivée ou la version de développement.
	*/
	@Test
	fun launchCheck()
	{
		val vManager = manager(FakeInstaller(fTemp.root))											// cas normal
		vManager.checkAtLaunch()
		await { vManager.state != null }
		assertEquals("1.1", vManager.state!!.offer.version)
		vManager.ignore()
		assertEquals("1.1", fIgnored)
		assertNull(vManager.state)
		val vIgnored = manager(FakeInstaller(fTemp.root)).apply { checkAtLaunch() }				// version ignorée
		val vOff     = manager(FakeInstaller(fTemp.root), inEnabled = false).apply { checkAtLaunch() }	// vérification désactivée
		val vDev     = manager(FakeInstaller(fTemp.root, fAuto = false)).apply { checkAtLaunch() }		// version de développement
		runBlocking { delay(500) }
		assertNull(vIgnored.state)
		assertNull(vOff.state)
		assertNull(vDev.state)
		var vMessage: String? = null																// vérification à la demande : la version ignorée est proposée
		vIgnored.checkNow { vMessage = it }
		await { vIgnored.state != null || vMessage != null }
		assertNotNull(vIgnored.state)
	}

	/*
		Mise à jour : zip téléchargé et vérifié, confié à l'installeur ;
		autorisation à donner d'abord, puis reprise au retour dans l'appli.
	*/
	@Test
	fun downloadThenPermissionThenResume()
	{
		val vInstaller = FakeInstaller(fTemp.root, fSteps = ArrayDeque(listOf(InstallStep.NeedPermission("Autorisez BridgePlus."))))	// installeur
		val vManager   = manager(vInstaller)																// gestionnaire
		vManager.checkNow {}
		await { vManager.state != null }
		vManager.update()
		await { vManager.state?.action == "Installer" }
		assertEquals("Autorisez BridgePlus.", vManager.state!!.notice)
		assertArrayEquals(fZip, vInstaller.installs.single().readBytes())
		vManager.onResume()
		runBlocking { delay(200) }
		assertEquals(1, vInstaller.installs.size)
		vInstaller.fResume = true
		vManager.onResume()
		await { vInstaller.installs.size == 2 }
	}

	/*
		Fichier altéré (empreinte différente) : échec affiché, rien d'installé ;
		téléchargement seul : message, puis « Fermer » ferme la fenêtre.
	*/
	@Test
	fun alteredAndSaved()
	{
		val vInstaller = FakeInstaller(fTemp.root, fSteps = ArrayDeque(listOf(InstallStep.Saved("Zip enregistré."))))	// installeur
		val vAltered   = manager(vInstaller, inRelease = release("00".repeat(32)))						// empreinte fausse
		vAltered.checkNow {}
		await { vAltered.state != null }
		vAltered.update()
		await { vAltered.state?.error != null }
		assertTrue(vAltered.state!!.error!!, vAltered.state!!.error!!.contains("altéré"))
		assertTrue(vInstaller.installs.isEmpty())
		val vSaved     = manager(vInstaller)																// téléchargement seul
		vSaved.checkNow {}
		await { vSaved.state != null }
		vSaved.update()
		await { vSaved.state?.action == "Fermer" }
		assertEquals("Zip enregistré.", vSaved.state!!.notice)
		vSaved.update()
		assertNull(vSaved.state)
	}
}
