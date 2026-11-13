package fr.maubin.bridgeplus.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
	Messages JSON échangés dans une session, toujours scellés. Le champ « t »
	porte la nature du message ; une nature inconnue (version plus récente de
	l'autre appareil) est ignorée par le destinataire.
*/
internal val kMessageJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "t" }	// messages d'une session

/*
	Message d'une session.
*/
@Serializable
sealed interface LinkMessage
{
	/*
		Battement de cœur du PC : le téléphone répond Pong. Le PC sait ainsi que
		la connexion vit ; le téléphone, que le PC est encore là.
		at : date d'envoi (ms)
	*/
	@Serializable
	@SerialName("ping")
	data class Ping(val at: Long) : LinkMessage

	/*
		Réponse au battement de cœur.
		at : date du Ping auquel il répond (ms)
	*/
	@Serializable
	@SerialName("pong")
	data class Pong(val at: Long) : LinkMessage

	/*
		L'expéditeur a oublié le destinataire : la clé de la paire est détruite
		des deux côtés, la session se ferme.
	*/
	@Serializable
	@SerialName("oublie")
	data object Forgotten : LinkMessage

	/*
		Texte copié sur l'expéditeur, à placer dans le presse-papiers du
		destinataire. Jamais un contenu marqué sensible.
		id   : identifiant de l'envoi (tiré au hasard)
		text : texte (1 Mo au plus en UTF-8)
		at   : date de la copie (ms)
	*/
	@Serializable
	@SerialName("presse-papiers")
	data class Clip(
		val id   : String,
		val text : String,
		val at   : Long
	) : LinkMessage
}

/*
	Message en texte.
	inMessage : message
*/
internal fun encodeMessage(inMessage: LinkMessage): ByteArray =
	kMessageJson.encodeToString(LinkMessage.serializer(), inMessage).toByteArray(Charsets.UTF_8)

/*
	Message lu ; null s'il est illisible ou d'une nature inconnue.
	inBytes : message en texte
*/
internal fun decodeMessage(inBytes: ByteArray): LinkMessage? =
	runCatching { kMessageJson.decodeFromString(LinkMessage.serializer(), String(inBytes, Charsets.UTF_8)) }.getOrNull()
