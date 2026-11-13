package fr.maubin.bridgeplus

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import fr.maubin.bridgeplus.notice.NoticeCandidate
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val kIconPixels = 64													// côté de l'icône d'une appli envoyée au PC

/*
	Écoute des notifications du téléphone (accès aux notifications accordé
	par l'utilisateur ; paramètre restreint sur Android 16 pour une appli
	installée hors du Play Store). Chaque notification publiée ou retirée
	est confiée, sur un fil à part (le réseau est interdit sur le fil
	principal), à l'envoi de BridgeApp, qui applique le filtre (liste
	blanche, exclusions, codes). Une notification fermée sur le PC est
	masquée ici. Rien n'est enregistré.
*/
class NoticeListener : NotificationListenerService()
{
	private val fWorker = Executors.newSingleThreadExecutor { inTask -> Thread(inTask, "BridgePlus-notifications").apply { isDaemon = true } }	// envois

	override fun onListenerConnected()
	{
		super.onListenerConnected()
		sInstance = this
	}

	override fun onListenerDisconnected()
	{
		sInstance = null
		super.onListenerDisconnected()
	}

	override fun onNotificationPosted(inNotice: StatusBarNotification)
	{
		val vCandidate = runCatching { candidate(inNotice) }.getOrNull() ?: return			// notification lue
		fWorker.execute { runCatching { bridgeApp.notices.posted(vCandidate) } }
	}

	override fun onNotificationRemoved(inNotice: StatusBarNotification)
	{
		val vKey = inNotice.key																	// clé
		fWorker.execute { runCatching { bridgeApp.notices.removed(vKey) } }
	}

	override fun onDestroy()
	{
		if (sInstance === this) sInstance = null
		fWorker.shutdown()
		super.onDestroy()
	}

	/*
		Notification telle que le filtre la voit : appli, titre, texte, et
		ce qui la ferait écarter (permanente, résumé, secrète, silencieuse).
		inNotice : notification publiée
	*/
	private fun candidate(inNotice: StatusBarNotification): NoticeCandidate
	{
		val vNotif   = inNotice.notification																// notification
		val vExtras  = vNotif.extras																		// contenu
		val vText    = vExtras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: vExtras.getCharSequence(Notification.EXTRA_TEXT)	// texte
		val vRanking = Ranking()																			// classement du système
		val vSilent  = currentRanking?.getRanking(inNotice.key, vRanking) == true && vRanking.importance < NotificationManager.IMPORTANCE_DEFAULT	// silencieuse
		return NoticeCandidate(
			key          = inNotice.key,
			pkg          = inNotice.packageName,
			app          = appName(this, inNotice.packageName),
			title        = vExtras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
			text         = vText?.toString().orEmpty(),
			at           = inNotice.postTime,
			ongoing      = inNotice.isOngoing || !inNotice.isClearable,
			groupSummary = vNotif.flags and Notification.FLAG_GROUP_SUMMARY != 0,
			secret       = vNotif.visibility == Notification.VISIBILITY_SECRET,
			silent       = vSilent
		)
	}

	companion object
	{
		@Volatile private var sInstance: NoticeListener? = null									// écoute en cours ; null sans

		/*
			Masque une notification du téléphone (fermée sur le PC) ; sans
			effet si l'écoute ne tourne pas.
			inKey : clé de la notification
		*/
		fun dismiss(inKey: String)
		{
			runCatching { sInstance?.cancelNotification(inKey) }
		}

		/*
			Vrai si l'accès aux notifications est accordé à BridgePlus.
			inContext : contexte
		*/
		fun granted(inContext: Context): Boolean =
			inContext.getSystemService(NotificationManager::class.java)?.isNotificationListenerAccessGranted(ComponentName(inContext, NoticeListener::class.java)) == true

		/*
			Écran d'Android qui accorde l'accès aux notifications à BridgePlus.
			inContext : contexte
		*/
		fun accessIntent(inContext: Context): Intent =
			Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
				.putExtra(android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(inContext, NoticeListener::class.java).flattenToString())
	}
}

/*
	Nom d'une appli ; son paquet s'il est illisible.
	inContext : contexte
	inPkg     : paquet
*/
fun appName(
	inContext : Context,
	inPkg     : String
): String =
	runCatching { inContext.packageManager.let { it.getApplicationLabel(it.getApplicationInfo(inPkg, 0)).toString() } }.getOrDefault(inPkg)

/*
	Icône d'une appli en PNG de 64 px (envoyée au PC) ; null si illisible.
	inContext : contexte
	inPkg     : paquet
*/
fun appIconPng(
	inContext : Context,
	inPkg     : String
): ByteArray? =
	runCatching {
		val vBitmap = inContext.packageManager.getApplicationIcon(inPkg).toBitmap(kIconPixels, kIconPixels)	// icône en image
		ByteArrayOutputStream().use { vOut -> vBitmap.compress(Bitmap.CompressFormat.PNG, 100, vOut); vOut.toByteArray() }
	}.getOrNull()

/*
	Appli de l'écran d'accueil (liste blanche des notifications).
	pkg  : paquet
	name : nom
*/
data class LauncherApp(
	val pkg  : String,
	val name : String
)

/*
	Applis de l'écran d'accueil, par nom (sans BridgePlus).
	inContext : contexte
*/
fun launcherApps(inContext: Context): List<LauncherApp>
{
	val vManager = inContext.packageManager																// paquets
	val vIntent  = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)					// applis de l'écran d'accueil
	return vManager.queryIntentActivities(vIntent, PackageManager.ResolveInfoFlags.of(0))
		.map { LauncherApp(it.activityInfo.packageName, it.loadLabel(vManager).toString()) }
		.filter { it.pkg != inContext.packageName }
		.distinctBy { it.pkg }
		.sortedBy { it.name.lowercase() }
}
