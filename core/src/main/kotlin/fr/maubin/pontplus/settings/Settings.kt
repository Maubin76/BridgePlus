package fr.maubin.pontplus.settings

import fr.maubin.pontplus.store.writeAtomic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val kSettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }	// fichier des réglages

/*
	Réglages de l'appli, propres à l'appareil (non échangés). Les réglages
	des étapes suivantes (presse-papiers, notifications…) s'ajoutent ici.
	launchAtStartup   : PC : PontPlus démarre avec Windows
	firewallExplained : PC : l'explication du pare-feu a été lue
	updates           : nouvelle version cherchée au lancement
	ignoredUpdate     : version que l'utilisateur ne veut plus se voir proposer ; vide sans
*/
@Serializable
data class PontSettings(
	val launchAtStartup   : Boolean = true,
	val firewallExplained : Boolean = false,
	val updates           : Boolean = true,
	val ignoredUpdate     : String  = ""
)

/*
	Fichier des réglages (reglages.json). Un fichier absent ou illisible
	donne les réglages par défaut.
	fFile : fichier
*/
class SettingsStore(private val fFile: File)
{
	/*
		Réglages enregistrés.
	*/
	fun load(): PontSettings = runCatching { kSettingsJson.decodeFromString(PontSettings.serializer(), fFile.readText()) }.getOrDefault(PontSettings())

	/*
		Enregistre les réglages.
		inSettings : réglages
	*/
	fun save(inSettings: PontSettings) = writeAtomic(fFile, kSettingsJson.encodeToString(PontSettings.serializer(), inSettings).toByteArray(Charsets.UTF_8))
}
