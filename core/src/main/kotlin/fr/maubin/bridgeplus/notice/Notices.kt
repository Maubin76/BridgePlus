package fr.maubin.bridgeplus.notice

import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.link.toBase64
import fr.maubin.bridgeplus.settings.BridgeSettings
import java.text.Normalizer

const val kReasonPasswords = "Gestionnaire de mots de passe : jamais envoyé"		// raison d'une exclusion
const val kReasonBank      = "Appli bancaire : jamais envoyée"						// raison d'une exclusion
const val kReasonCodes     = "Codes de connexion : jamais envoyés"					// raison d'une exclusion

/*
	Applis exclues d'office, et non modifiables : leurs notifications ne
	quittent jamais le téléphone (paquet → raison).
*/
val kExcludedApps: Map<String, String> = buildMap {
	listOf(
		"fr.maubin.keeplus", "com.x8bit.bitwarden", "com.kunzisoft.keepass.free", "com.kunzisoft.keepass.libre", "proton.android.pass",
		"com.agilebits.onepassword", "com.dashlane", "com.lastpass.lpandroid", "keepass2android.keepass2android", "com.nordpass.android.app",
		"com.google.android.gms.credentials"
	).forEach { put(it, kReasonPasswords) }
	listOf(
		"net.bnpparibas.mescomptes", "com.bnpp.hellobank", "mobi.societegenerale.mobile.lappli", "fr.creditagricole.androidapp",
		"fr.lcl.android.customerarea", "fr.caisseepargne.android.mobilebanking", "fr.banquepopulaire.cyberplus", "com.fortuneo.android",
		"com.boursorama.android.clients", "com.labanquepostale.android", "fr.creditmutuel.mobile", "com.cic_prod.bad", "fr.axa.monaxa",
		"com.revolut.revolut", "de.number26.android", "com.lydia", "com.paypal.android.p2pmobile", "fr.ing.mobile", "com.qonto.qonto",
		"com.monabanq", "fr.bred.fr", "com.ca_sudrhonealpes.android", "fr.cmb.mobile", "fr.arkea.mobile", "com.shinekeeper.nickel",
		"fr.laposte.idn", "com.banquepopulaire.bpvf"
	).forEach { put(it, kReasonBank) }
	listOf(
		"com.google.android.apps.authenticator2", "com.azure.authenticator", "com.beemdevelopment.aegis", "com.authy.authy",
		"com.twofasapp", "org.fedorahosted.freeotp", "com.yubico.yubioath", "me.mattrubin.authenticator"
	).forEach { put(it, kReasonCodes) }
}

/*
	Messageries : leurs notifications partent par défaut (le reste ne part
	que si on l'active).
*/
val kMessagingApps: Set<String> = setOf(
	"com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.whatsapp", "com.whatsapp.w4b",
	"org.thoughtcrime.securesms", "org.telegram.messenger", "org.telegram.messenger.web", "com.facebook.orca", "com.discord",
	"com.Slack", "com.microsoft.teams", "io.olvid.messenger", "ch.threema.app", "im.vector.app", "com.google.android.apps.dynamite",
	"com.google.android.dialer", "com.samsung.android.dialer"
)

/*
	Mots qui accompagnent un code à usage unique (sans accents, minuscules).
*/
private val kCodeWords = Regex(
	"\\b(code|codes|otp|2fa|mfa|verification|verifier|confirmation|authentification|authentication|connexion|login|sign.?in|" +
	"securite|security|passcode|mot de passe|password|pin|ne (le )?(communiquez|partagez|transmettez)|don.?t share|do not share|expire)"
)

/*
	Code de 4 à 8 chiffres (éventuellement en deux groupes : « 123 456 »,
	« 123-456 »), ou code alphanumérique typique « G-123456 ».
*/
private val kCodeNumber = Regex("(?<!\\d)(?<!\\d[.,/:])(?:[A-Z]-)?\\d{3,4}[ -]?\\d{1,4}(?!\\d)(?![.,/:]\\d)|(?<!\\d)(?<!\\d[.,/:])\\d{4,8}(?!\\d)(?![.,/:]\\d)")

/*
	Vrai si la notification ressemble à un code de vérification : un nombre
	de 4 à 8 chiffres accompagné d'un mot comme « code », « vérification »,
	« connexion », « ne le communiquez pas »… Ces notifications ne sont
	jamais envoyées, quelle que soit l'appli. Un numéro de commande ou une
	date sans ces mots n'est pas un code.
	inTitle : titre
	inText  : texte
*/
fun isOneTimeCode(
	inTitle : String,
	inText  : String
): Boolean
{
	val vAll   = "$inTitle\n$inText"																		// contenu
	val vPlain = Normalizer.normalize(vAll, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()	// sans accents
	if (!kCodeWords.containsMatchIn(vPlain)) return false
	return kCodeNumber.findAll(vAll).any { vMatch -> vMatch.value.count(Char::isDigit) in 4..8 }
}

/*
	Vrai si l'appli peut envoyer ses notifications au PC : jamais une appli
	exclue d'office ; sinon le choix fait dans les réglages, ou le défaut
	(messageries oui, le reste non).
	inPkg      : paquet de l'appli
	inSettings : réglages
*/
fun isAppAllowed(
	inPkg      : String,
	inSettings : BridgeSettings
): Boolean = inPkg !in kExcludedApps && (inSettings.noticeApps[inPkg] ?: (inPkg in kMessagingApps))

/*
	Notification lue sur le téléphone, telle que le filtre la voit.
	key          : clé de la notification
	pkg          : paquet de l'appli
	app          : nom de l'appli
	title        : titre
	text         : texte
	at           : date de publication (ms)
	ongoing      : permanente (lecteur, service, appel en cours…)
	groupSummary : résumé d'un groupe (les notifications du groupe arrivent à part)
	secret       : marquée « secrète » (rien sur l'écran verrouillé)
	silent       : silencieuse (importance basse)
*/
data class NoticeCandidate(
	val key          : String,
	val pkg          : String,
	val app          : String,
	val title        : String,
	val text         : String,
	val at           : Long,
	val ongoing      : Boolean = false,
	val groupSummary : Boolean = false,
	val secret       : Boolean = false,
	val silent       : Boolean = false
)

/*
	Envoi des notifications du téléphone au PC : filtre (réglage général,
	appli autorisée, ni permanente, ni résumé, ni secrète, ni silencieuse,
	jamais un code), icône de l'appli envoyée une fois par session, retrait
	signalé pour les seules notifications envoyées. Rien n'est gardé sur le
	disque ; une notification republiée à l'identique ne repart pas.
	fSendAll  : envoie un message à tous les PC reliés ; rend leurs noms
	fSettings : réglages en cours
	fIcon     : icône de l'appli (PNG) ; null si illisible
	fOwnPkg   : paquet de BridgePlus (jamais envoyé)
*/
class NoticeOutbox(
	private val fSendAll  : (LinkMessage) -> List<String>,
	private val fSettings : () -> BridgeSettings,
	private val fIcon     : (String) -> ByteArray?,
	private val fOwnPkg   : String = "fr.maubin.bridgeplus"
)
{
	private val fIconsSent = HashSet<String>()										// applis dont l'icône est partie dans cette session
	private val fSent      = LinkedHashMap<String, Int>()							// notifications envoyées (clé → empreinte du contenu)

	/*
		Notification publiée (ou mise à jour) : envoyée si le filtre la
		laisse passer ; rend vrai si elle est partie.
		inNotice : notification
	*/
	@Synchronized
	fun posted(inNotice: NoticeCandidate): Boolean
	{
		val vSettings = fSettings()																		// réglages
		if (!vSettings.noticesSend || inNotice.pkg == fOwnPkg || !isAppAllowed(inNotice.pkg, vSettings)) return false
		if (inNotice.ongoing || inNotice.groupSummary || inNotice.secret || inNotice.silent) return false
		if (inNotice.title.isBlank() && inNotice.text.isBlank()) return false
		if (isOneTimeCode(inNotice.title, inNotice.text)) return false
		val vPrint = (inNotice.pkg + inNotice.title + inNotice.text).hashCode()						// empreinte du contenu
		if (fSent[inNotice.key] == vPrint) return false
		if (inNotice.pkg !in fIconsSent)
		{
			fIcon(inNotice.pkg)?.let { vPng -> if (fSendAll(LinkMessage.AppIcon(inNotice.pkg, toBase64(vPng))).isNotEmpty()) fIconsSent += inNotice.pkg }
		}
		val vPeers = fSendAll(LinkMessage.PhoneNotice(inNotice.key, inNotice.pkg, inNotice.app, inNotice.title, inNotice.text, inNotice.at))	// PC joints
		if (vPeers.isEmpty()) return false
		fSent[inNotice.key] = vPrint
		while (fSent.size > 500) fSent.remove(fSent.keys.first())
		return true
	}

	/*
		Notification retirée sur le téléphone : le PC est prévenu si elle lui
		avait été envoyée.
		inKey : clé de la notification
	*/
	@Synchronized
	fun removed(inKey: String)
	{
		if (fSent.remove(inKey) != null) fSendAll(LinkMessage.NoticeRemoved(inKey))
	}

	/*
		Nouvelle session (PC relié de nouveau) : les icônes repartiront.
	*/
	@Synchronized
	fun newSession() = fIconsSent.clear()
}
