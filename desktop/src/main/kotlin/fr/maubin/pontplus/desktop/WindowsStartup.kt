package fr.maubin.pontplus.desktop

import java.util.concurrent.TimeUnit

private const val kRunKey   = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"	// démarrage avec la session Windows
private const val kRunValue = "PontPlus"												// nom de l'entrée
const val kStartupArg       = "--demarrage"													// argument du lancement avec Windows

/*
	Démarrage avec Windows : entrée « PontPlus » de la clé Run de
	l'utilisateur (pas d'autorisation d'administrateur), qui lance
	PontPlus.exe avec --demarrage. Seulement pour l'appli installée par
	son lanceur (chemin de l'exécutable connu).
*/
object WindowsStartup
{
	// Chemin de PontPlus.exe ; null hors du lanceur (développement, tests).
	val exe: String? get() = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }

	/*
		Vrai si l'entrée de démarrage avec Windows existe.
	*/
	fun isOn(): Boolean =
		runCatching {
			val vProcess = ProcessBuilder("reg", "query", kRunKey, "/v", kRunValue).redirectErrorStream(true).start()	// commande lancée
			vProcess.inputStream.readBytes()
			vProcess.waitFor(10, TimeUnit.SECONDS) && vProcess.exitValue() == 0
		}.getOrDefault(false)

	/*
		Inscrit ou retire le démarrage avec Windows ; rend null si c'est fait,
		sinon la raison.
		inOn : vrai pour démarrer avec Windows
	*/
	fun set(inOn: Boolean): String?
	{
		val vExe = exe ?: return "chemin de PontPlus.exe inconnu (appli lancée hors de son dossier)"	// exécutable
		val vCommand = if (inOn) listOf("reg", "add", kRunKey, "/v", kRunValue, "/t", "REG_SZ", "/d", "\"$vExe\" $kStartupArg", "/f")
			else listOf("reg", "delete", kRunKey, "/v", kRunValue, "/f")												// commande
		return runCatching {
			val vProcess = ProcessBuilder(vCommand).redirectErrorStream(true).start()								// commande lancée
			val vText    = vProcess.inputStream.bufferedReader().readText()										// sortie
			if (!vProcess.waitFor(10, TimeUnit.SECONDS)) "délai dépassé"
			else if (vProcess.exitValue() != 0 && inOn) vText.trim().ifEmpty { "erreur ${vProcess.exitValue()}" }
			else null
		}.getOrElse { it.message ?: "erreur inconnue" }
	}
}
