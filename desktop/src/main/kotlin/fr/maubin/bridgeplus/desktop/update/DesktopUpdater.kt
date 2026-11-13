package fr.maubin.bridgeplus.desktop.update

import fr.maubin.bridgeplus.ui.update.InstallStep
import fr.maubin.bridgeplus.ui.update.UpdateInstaller
import fr.maubin.bridgeplus.update.ReleaseAsset
import fr.maubin.bridgeplus.update.UpdatePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipInputStream

const val kExeName = "BridgePlus.exe"										// lanceur de l'appli portable

/*
	Version de l'appli, lue dans la ressource version.properties écrite à la
	construction ; null si elle est absente.
*/
fun appVersion(): String? =
	Thread.currentThread().contextClassLoader.getResourceAsStream("version.properties")?.use { inStream ->
		Properties().apply { load(inStream) }.getProperty("version")?.trim()?.takeIf { it.isNotEmpty() }
	}

/*
	Dossier de l'appli portable (celui de BridgePlus.exe), d'après le
	lanceur jpackage ; null si l'appli n'a pas été lancée par lui
	(développement, tests).
*/
fun appFolder(): Path? =
	System.getProperty("jpackage.app-path")?.let { inPath -> runCatching { Paths.get(inPath).toAbsolutePath().parent }.getOrNull() }
		?.takeIf { Files.isRegularFile(it.resolve(kExeName)) }

/*
	Vrai si le dossier de l'appli peut être remplacé : son dossier parent
	accepte l'écriture (création du dossier neuf, renommages).
	inFolder : dossier de l'appli
*/
fun canReplace(inFolder: Path): Boolean
{
	val vParent = inFolder.parent ?: return false									// dossier qui contient l'appli
	return runCatching {
		val vProbe = Files.createTempFile(vParent, ".bridgeplus-", ".tmp")	// fichier d'essai
		Files.delete(vProbe)
		true
	}.getOrDefault(false)
}

/*
	Décompresse le zip d'une nouvelle version à côté du dossier de l'appli,
	dans « <nom>.new », et renvoie ce dossier. Le zip contient un dossier
	BridgePlus (ou directement l'appli) ; un chemin qui sortirait du
	dossier de destination est refusé. Lève IOException si le zip est
	illisible ou sans BridgePlus.exe ; rien ne reste alors sur le disque.
	inZip    : zip téléchargé et vérifié
	inFolder : dossier de l'appli installée
*/
fun prepareUpdate(
	inZip    : File,
	inFolder : Path
): Path
{
	val vTarget = inFolder.resolveSibling("${inFolder.fileName}.new")				// dossier neuf
	val vRoot   = inFolder.resolveSibling("${inFolder.fileName}.unzip")			// décompression brute
	try
	{
		deleteTree(vTarget)
		deleteTree(vRoot)
		Files.createDirectories(vRoot)
		ZipInputStream(inZip.inputStream().buffered()).use { inZipStream ->
			while (true)
			{
				val vEntry = inZipStream.nextEntry ?: break							// élément du zip
				val vPath  = vRoot.resolve(vEntry.name).normalize()					// destination de l'élément
				if (!vPath.startsWith(vRoot)) throw IOException("chemin refusé dans le zip : ${vEntry.name}")
				if (vEntry.isDirectory) Files.createDirectories(vPath)
				else
				{
					vPath.parent?.let { Files.createDirectories(it) }
					Files.copy(inZipStream, vPath, StandardCopyOption.REPLACE_EXISTING)
				}
			}
		}
		val vApp = when
		{
			Files.isRegularFile(vRoot.resolve(kExeName)) -> vRoot
			else -> Files.list(vRoot).use { inList -> inList.filter { Files.isRegularFile(it.resolve(kExeName)) }.findFirst().orElse(null) }
		} ?: throw IOException("$kExeName absent du zip")							// dossier de l'appli dans le zip
		Files.move(vApp, vTarget)
		deleteTree(vRoot)
		return vTarget
	}
	catch (inError: Exception)
	{
		deleteTree(vRoot)
		deleteTree(vTarget)
		throw inError as? IOException ?: IOException(inError.message, inError)
	}
}

/*
	Supprime le dossier neuf laissé par une mise à jour qui n'a pas pu
	remplacer l'appli (« <nom>.new » à côté du dossier de l'appli) ; vrai s'il
	y en avait un. La mise à jour est simplement reproposée ensuite.
	inFolder : dossier de l'appli installée
*/
fun cleanLeftover(inFolder: Path): Boolean
{
	val vLeft = inFolder.resolveSibling("${inFolder.fileName}.new")				// dossier neuf resté
	if (!Files.isDirectory(vLeft)) return false
	return runCatching { deleteTree(vLeft); true }.getOrDefault(false)
}

/*
	Supprime un dossier et son contenu ; sans effet s'il n'existe pas.
	inPath : dossier
*/
internal fun deleteTree(inPath: Path)
{
	if (!Files.exists(inPath)) return
	Files.walk(inPath).use { inWalk -> inWalk.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
}

/*
	Texte PowerShell entre apostrophes (apostrophes doublées).
	inText : texte
*/
private fun quoted(inText: String) = "'" + inText.replace("'", "''") + "'"

/*
	Script PowerShell qui remplace l'appli une fois fermée : quitte d'abord le
	dossier de l'appli (dossier courant hérité quand l'appli est lancée par
	double-clic : tant qu'un processus s'y trouve, Windows refuse de le
	renommer), attend la fin du processus, renomme le dossier actuel en
	« .old-<instant> » (un ancien dossier déjà présent n'est jamais touché) et
	le dossier neuf à sa place. Tout ce que l'utilisateur a rangé dans le
	dossier de l'appli à côté de l'appli elle-même (BridgePlus.exe, app,
	runtime), par exemple un dossier « files », passe dans le nouveau dossier ;
	l'appli est relancée, puis l'ancien dossier supprimé, seulement si tout a
	pu passer (sinon il reste, code 3). Si un renommage échoue, l'ancien
	dossier est remis en place et l'ancienne version relancée (le dossier
	neuf reste, signalé au lancement suivant).
	inPid      : processus de l'appli à attendre
	inFolder   : dossier de l'appli installée
	inNew      : dossier neuf (voir prepareUpdate)
	inRelaunch : relancer l'appli à la fin (faux pour les tests)
*/
fun swapScript(
	inPid      : Long,
	inFolder   : Path,
	inNew      : Path,
	inRelaunch : Boolean = true
): String
{
	return """
		|# Mise à jour de BridgePlus : remplacement du dossier de l'appli une fois fermée.
		|${'$'}ErrorActionPreference = 'Stop'
		|Set-Location -LiteralPath ([System.IO.Path]::GetTempPath())
		|[System.IO.Directory]::SetCurrentDirectory([System.IO.Path]::GetTempPath())
		|${'$'}app      = ${quoted(inFolder.toString())}
		|${'$'}new      = ${quoted(inNew.toString())}
		|${'$'}old      = ${'$'}app + '.old-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
		|${'$'}relaunch = ${'$'}${inRelaunch}
		|function Start-App { if (${'$'}relaunch) { Start-Process -FilePath (Join-Path ${'$'}app '$kExeName') -WorkingDirectory ${'$'}app } }
		|while (Get-Process -Id $inPid -ErrorAction SilentlyContinue) { Start-Sleep -Milliseconds 300 }
		|Start-Sleep -Milliseconds 500
		|${'$'}moved = ${'$'}false
		|for (${'$'}i = 0; ${'$'}i -lt 20 -and -not ${'$'}moved; ${'$'}i++) {
		|	try { Rename-Item -LiteralPath ${'$'}app -NewName (Split-Path ${'$'}old -Leaf); ${'$'}moved = ${'$'}true } catch { Start-Sleep -Milliseconds 500 }
		|}
		|if (-not ${'$'}moved) { Start-App; exit 1 }
		|try { Rename-Item -LiteralPath ${'$'}new -NewName (Split-Path ${'$'}app -Leaf) }
		|catch { Rename-Item -LiteralPath ${'$'}old -NewName (Split-Path ${'$'}app -Leaf); Start-App; exit 2 }
		|${'$'}kept = ${'$'}true
		|foreach (${'$'}item in Get-ChildItem -LiteralPath ${'$'}old -Force) {
		|	${'$'}dest = Join-Path ${'$'}app ${'$'}item.Name
		|	if (-not (Test-Path -LiteralPath ${'$'}dest)) {
		|		try { Move-Item -LiteralPath ${'$'}item.FullName -Destination ${'$'}dest } catch { ${'$'}kept = ${'$'}false }
		|	}
		|}
		|Start-App
		|if (-not ${'$'}kept) { exit 3 }
		|Remove-Item -LiteralPath ${'$'}old -Recurse -Force -ErrorAction SilentlyContinue
		|exit 0
		|""".trimMargin()
}

/*
	Écrit le script de remplacement dans le dossier temporaire et le lance en
	arrière-plan (fenêtre cachée) ; il attend que l'appli se ferme.
	inFolder : dossier de l'appli installée
	inNew    : dossier neuf
*/
fun launchSwap(
	inFolder : Path,
	inNew    : Path
)
{
	val vScript = Files.createTempFile("bridgeplus-maj-", ".ps1")				// script écrit
	Files.writeString(vScript, "\uFEFF" + swapScript(ProcessHandle.current().pid(), inFolder, inNew))
	ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", vScript.toString())
		.directory(vScript.parent.toFile())														// hors du dossier de l'appli (sinon il ne peut pas être renommé)
		.redirectErrorStream(true)
		.redirectOutput(ProcessBuilder.Redirect.DISCARD)
		.start()
}

/*
	Mises à jour du PC : zip portable de la release, décompressé à côté du
	dossier de l'appli puis échangé avec lui par un script PowerShell, une
	fois l'appli vraiment fermée (pas seulement réduite dans la zone de
	notification : inOnQuit arrête la liaison et le processus).
	Appli lancée hors de son dossier portable, ou dossier non modifiable :
	le zip est seulement enregistré dans les Téléchargements et montré.
	fOnQuit  : ferme vraiment l'appli (le script attend la fin du processus)
	fVersion : version installée ; null si inconnue
	fFolder  : dossier de l'appli, s'il peut être remplacé ; null sinon
*/
class DesktopUpdateInstaller(
	private val fOnQuit  : () -> Unit,
	private val fVersion : String? = appVersion(),
	private val fFolder  : Path?   = appFolder()?.takeIf(::canReplace)
) : UpdateInstaller
{
	/*
		Fichier attendu dans la release : zip de l'appli PC.
	*/
	override val platform: UpdatePlatform = UpdatePlatform.WINDOWS

	/*
		Version installée.
	*/
	override val version: String? get() = fVersion

	/*
		Vérification au lancement : seulement pour l'appli empaquetée (lancée
		depuis les sources, elle n'a pas de dossier à remplacer).
	*/
	override val autoCheck: Boolean get() = appFolder() != null

	/*
		Explication quand l'appli ne peut pas se remplacer elle-même.
	*/
	override val notice: String?
		get() = if (fFolder != null) null else "BridgePlus ne peut pas se remplacer elle-même ici (dossier protégé, ou appli lancée hors de son dossier portable) : le zip sera enregistré dans vos Téléchargements, à décompresser à la place de l'ancien dossier."

	/*
		Bouton principal.
	*/
	override val action: String get() = if (fFolder != null) "Mettre à jour" else "Télécharger"

	/*
		Zip téléchargé dans le dossier temporaire (remplacement) ou dans les
		Téléchargements (téléchargement seul).
		inAsset : fichier de la release
	*/
	override fun target(inAsset: ReleaseAsset): File =
		(if (fFolder != null) File(System.getProperty("java.io.tmpdir")) else File(System.getProperty("user.home"), "Downloads")).resolve(inAsset.name)

	/*
		Prépare le dossier neuf, lance le script de remplacement et ferme
		l'appli ; sans dossier remplaçable, montre le zip dans l'explorateur.
		inFile : zip vérifié
	*/
	override suspend fun install(inFile: File): InstallStep
	{
		val vFolder = fFolder ?: run {
			runCatching { ProcessBuilder("explorer.exe", "/select,", inFile.absolutePath).start() }
			return InstallStep.Saved("Zip enregistré : ${inFile.absolutePath}. Fermez BridgePlus (Quitter), puis décompressez-le à la place de l'ancien dossier.")
		}																					// dossier de l'appli
		withContext(Dispatchers.IO) { launchSwap(vFolder, prepareUpdate(inFile, vFolder)) }
		fOnQuit()
		return InstallStep.Started
	}
}
