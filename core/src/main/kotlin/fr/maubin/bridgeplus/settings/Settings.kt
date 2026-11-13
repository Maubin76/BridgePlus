package fr.maubin.bridgeplus.settings

import fr.maubin.bridgeplus.link.NetworkId
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
	noticesSend       : téléphone : envoyer les notifications au PC
	noticesShow       : PC : afficher les notifications du téléphone
	noticeApps        : téléphone : choix par appli (paquet → envoyée ou non) ; une appli
	                    absente suit le défaut (messageries oui, le reste non)
	batteryAlert      : PC : alerte quand la batterie du téléphone passe sous 20 %
	onboardingDone    : téléphone : assistant de premier lancement vu
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
	val noticesSend       : Boolean = true,
	val noticesShow       : Boolean = true,
	val noticeApps        : Map<String, Boolean> = emptyMap(),
	val batteryAlert      : Boolean = true,
	val onboardingDone    : Boolean = false,
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

/*
	Sauvegarde des réglages du téléphone (fichier choisi par l'utilisateur) :
	réglages et réseaux de la maison, jamais les clés des paires.
	format   : version du format
	settings : réglages
	home     : réseaux de la maison
*/
@Serializable
data class SettingsBackup(
	val format   : Int              = 1,
	val settings : BridgeSettings,
	val home     : List<NetworkId>  = emptyList()
)

/*
	Sauvegarde en texte (JSON lisible).
	inBackup : sauvegarde
*/
fun encodeBackup(inBackup: SettingsBackup): String = kSettingsJson.encodeToString(SettingsBackup.serializer(), inBackup)

/*
	Sauvegarde lue ; null si le fichier n'en est pas une.
	inText : contenu du fichier
*/
fun decodeBackup(inText: String): SettingsBackup? = runCatching { kSettingsJson.decodeFromString(SettingsBackup.serializer(), inText) }.getOrNull()
