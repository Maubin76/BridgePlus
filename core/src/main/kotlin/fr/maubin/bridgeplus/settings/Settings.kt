package fr.maubin.bridgeplus.settings

import fr.maubin.bridgeplus.store.writeAtomic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val kSettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }	// fichier des réglages

/*
	Réglages de l'appli, propres à l'appareil (non échangés). Les réglages
	des étapes suivantes (presse-papiers, notifications…) s'ajoutent ici.
	clipSend          : les copies de cet appareil partent d'elles-mêmes vers l'autre
	                    (les gestes explicites, tuile et partage, envoient toujours)
	clipReceive       : les textes reçus de l'autre appareil vont dans le presse-papiers
	historyHotkey     : PC : raccourci global de la carte de l'historique (« Ctrl+Alt+V »)
	receiveFolder     : PC : dossier de réception des fichiers ; vide pour Téléchargements\BridgePlus
	launchAtStartup   : PC : BridgePlus démarre avec Windows
	firewallExplained : PC : l'explication du pare-feu a été lue
	updates           : nouvelle version cherchée au lancement
	ignoredUpdate     : version que l'utilisateur ne veut plus se voir proposer ; vide sans
*/
@Serializable
data class BridgeSettings(
	val clipSend          : Boolean = true,
	val clipReceive       : Boolean = true,
	val historyHotkey     : String  = "Ctrl+Alt+V",
	val receiveFolder     : String  = "",
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
	fun load(): BridgeSettings = runCatching { kSettingsJson.decodeFromString(BridgeSettings.serializer(), fFile.readText()) }.getOrDefault(BridgeSettings())

	/*
		Enregistre les réglages.
		inSettings : réglages
	*/
	fun save(inSettings: BridgeSettings) = writeAtomic(fFile, kSettingsJson.encodeToString(BridgeSettings.serializer(), inSettings).toByteArray(Charsets.UTF_8))
}
