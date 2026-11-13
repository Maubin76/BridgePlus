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

	/*
		Proposition d'un fichier : le destinataire vérifie l'espace disque et
		répond (FileAnswer) avant le premier morceau.
		id   : identifiant du transfert
		name : nom du fichier
		size : taille (octets)
		mime : type du contenu ; vide s'il est inconnu
	*/
	@Serializable
	@SerialName("fichier")
	data class FileOffer(
		val id   : String,
		val name : String,
		val size : Long,
		val mime : String = ""
	) : LinkMessage

	/*
		Réponse à une proposition de fichier.
		id       : identifiant du transfert
		accepted : vrai si les morceaux peuvent partir
		reason   : raison d'un refus ; vide sinon
	*/
	@Serializable
	@SerialName("fichier-reponse")
	data class FileAnswer(
		val id       : String,
		val accepted : Boolean,
		val reason   : String = ""
	) : LinkMessage

	/*
		Tous les morceaux sont partis : empreinte du fichier à vérifier.
		id     : identifiant du transfert
		sha256 : empreinte SHA-256 du contenu (base64)
	*/
	@Serializable
	@SerialName("fichier-fin")
	data class FileEnd(
		val id     : String,
		val sha256 : String
	) : LinkMessage

	/*
		Verdict du destinataire : fichier vérifié et rangé, ou refusé.
		id     : identifiant du transfert
		ok     : vrai si le fichier est rangé
		reason : raison d'un échec ; vide sinon
	*/
	@Serializable
	@SerialName("fichier-verdict")
	data class FileVerdict(
		val id     : String,
		val ok     : Boolean,
		val reason : String = ""
	) : LinkMessage

	/*
		Transfert annulé par l'un ou l'autre côté.
		id     : identifiant du transfert
		reason : raison, en clair
	*/
	@Serializable
	@SerialName("fichier-annule")
	data class FileCancel(
		val id     : String,
		val reason : String = ""
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
