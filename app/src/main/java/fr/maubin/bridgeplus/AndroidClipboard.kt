package fr.maubin.bridgeplus

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

const val kChannelClips      = "textes"											// canal des textes reçus qu'Android n'a pas laissé copier
const val kMaxAndroidClip    = 250_000												// caractères au plus dans le presse-papiers d'Android (limite des échanges du système)
private const val kReceivedId = 2													// notification « Texte reçu du PC »

/*
	Contenu du presse-papiers du téléphone : texte et marque « sensible ».
	text      : texte ; null sans texte
	sensitive : contenu marqué sensible par l'appli qui l'a copié
*/
data class PhoneClip(
	val text      : String?,
	val sensitive : Boolean
)

/*
	Lit le presse-papiers. Android ne le permet qu'à l'appli au premier plan
	(écran affiché, ou activité transparente de la tuile) ; ailleurs, rend
	un contenu vide. Le texte d'un contenu sensible n'est pas lu.
	inContext : contexte (activité au premier plan)
*/
fun readClipboard(inContext: Context): PhoneClip
{
	val vClip      = inContext.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return PhoneClip(null, false)	// contenu
	val vExtras    = vClip.description?.extras														// marques de l'appli qui a copié
	val vSensitive = vExtras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true					// contenu sensible
	if (vSensitive) return PhoneClip(null, true)
	val vText      = (0 until vClip.itemCount).firstNotNullOfOrNull { vClip.getItemAt(it).coerceToText(inContext)?.toString()?.takeIf { vText -> vText.isNotEmpty() } }	// premier texte
	return PhoneClip(vText, false)
}

/*
	Place un texte dans le presse-papiers ; faux si Android refuse ou si le
	texte est trop long pour lui.
	inContext : contexte
	inText    : texte
*/
fun writeClipboard(
	inContext : Context,
	inText    : String
): Boolean =
	inText.length <= kMaxAndroidClip && runCatching {
		inContext.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("BridgePlus", inText)) != null
	}.getOrDefault(false)

/*
	Texte reçu qu'Android n'a pas laissé copier : notification « Texte reçu
	du PC » avec l'action « Copier », qui ouvre un instant l'activité
	transparente (au premier plan, la copie est permise). Un texte trop long
	pour le presse-papiers d'Android est seulement signalé.
	inContext : contexte
	inPeer    : appareil qui l'a envoyé
	inText    : texte
*/
fun notifyReceived(
	inContext : Context,
	inPeer    : String,
	inText    : String
)
{
	val vManager = inContext.getSystemService(NotificationManager::class.java) ?: return				// notifications
	val vTooLong = inText.length > kMaxAndroidClip														// trop long pour Android
	val vBuilder = Notification.Builder(inContext, kChannelClips)
		.setSmallIcon(R.drawable.ic_launcher_monochrome)
		.setContentTitle("Texte reçu de $inPeer")
		.setContentText(if (vTooLong) "Trop long pour le presse-papiers d'Android." else inText.take(120))
		.setAutoCancel(true)																			// notification
	if (!vTooLong)
	{
		val vCopy = PendingIntent.getActivity(
			inContext, 1,
			Intent(inContext, ClipActivity::class.java).setAction(ClipActivity.kActionCopy),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)																								// action « Copier »
		vBuilder.setContentIntent(vCopy).addAction(Notification.Action.Builder(null, "Copier", vCopy).build())
	}
	vManager.notify(kReceivedId, vBuilder.build())
}

/*
	Retire la notification « Texte reçu du PC ».
	inContext : contexte
*/
fun cancelReceived(inContext: Context) = inContext.getSystemService(NotificationManager::class.java)?.cancel(kReceivedId)
