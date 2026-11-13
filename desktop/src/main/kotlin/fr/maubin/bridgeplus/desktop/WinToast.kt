package fr.maubin.bridgeplus.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.BufferedWriter
import java.io.File
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

const val kAppId             = "BridgePlus.BridgePlus"									// identifiant de l'appli pour les notifications de Windows
const val kLinkArg           = "--lien"													// argument d'un lien bridgeplus: (bouton d'une notification)
private const val kAppIdKey  = "Software\\Classes\\AppUserModelId\\$kAppId"				// inscription de l'identifiant
private const val kSchemeKey = "Software\\Classes\\bridgeplus"							// inscription du protocole bridgeplus:

/*
	Bouton d'une notification.
	label : intitulé (« Ouvrir »)
	link  : lien bridgeplus: transmis à l'appli au clic
*/
data class ToastAction(
	val label : String,
	val link  : String
)

/*
	Notifications natives de Windows : elles portent le nom et le logo de
	BridgePlus (identifiant d'appli inscrit dans le registre de
	l'utilisateur) et respectent « Ne pas déranger ». Un hôte PowerShell
	gardé ouvert (ressource toast-host.ps1) les affiche, les retire et
	signale celles que l'utilisateur ferme (notifications suivies, par une
	étiquette ; un battement chaque seconde lui fait relire l'historique). Les boutons lancent un lien « bridgeplus:… » qui ne porte
	qu'un identifiant tiré au hasard (jamais un chemin) : l'appli ouverte
	vérifie l'identifiant avant d'agir, si bien qu'une page web ne peut rien
	ouvrir par ce lien. Les boutons n'existent que pour l'appli installée
	(chemin de BridgePlus.exe connu). Hors Windows ou en cas d'échec, show
	rend faux : l'appelant se rabat sur la notification de la zone de
	notification.
*/
object WinToast
{
	private var fProcess : Process? = null													// hôte PowerShell gardé ouvert
	private var fInput   : BufferedWriter? = null											// lignes vers l'hôte
	private var fReady   = false																// inscriptions faites
	private val fTracked = ConcurrentHashMap.newKeySet<String>()							// notifications suivies (étiquettes)
	private var fBeat    : Thread? = null														// battement vers l'hôte

	/*
		Notification suivie fermée sur le PC (étiquette) ; appelé sur un fil à part.
	*/
	@Volatile var onDismissed: (String) -> Unit = {}

	/*
		Vrai si les boutons des notifications peuvent agir (protocole inscrit).
	*/
	val actionable: Boolean get() = fReady && WindowsStartup.exe != null

	/*
		Inscrit l'identifiant d'appli (nom, logo) et le protocole bridgeplus:
		dans le registre de l'utilisateur (sans droits administrateur).
		inIcon : logo (PNG) affiché dans les notifications
	*/
	@Synchronized
	fun setup(inIcon: File)
	{
		if (!WinBridge.available || fReady) return
		runCatching {
			Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, kAppIdKey)
			Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, kAppIdKey, "DisplayName", "BridgePlus")
			Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, kAppIdKey, "IconUri", inIcon.absolutePath)
			WindowsStartup.exe?.let { vExe ->
				Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, "$kSchemeKey\\shell\\open\\command")
				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, kSchemeKey, "", "URL:BridgePlus")
				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, kSchemeKey, "URL Protocol", "")
				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, "$kSchemeKey\\shell\\open\\command", "", "\"$vExe\" $kLinkArg \"%1\"")
			}
			fReady = true
		}
	}

	/*
		Affiche une notification ; faux si elle n'a pas pu partir.
		inTitle       : titre
		inBody        : texte
		inLaunch      : lien du clic sur la notification ; null sans
		inActions     : boutons (deux au plus)
		inTag         : étiquette de suivi (retrait, fermeture signalée) ; null sans
		inImage       : icône affichée à gauche (PNG) ; null pour le logo de BridgePlus
		inAttribution : petite ligne d'origine (« WhatsApp · Pixel ») ; null sans
	*/
	@Synchronized
	fun show(
		inTitle       : String,
		inBody        : String,
		inLaunch      : String?           = null,
		inActions     : List<ToastAction> = emptyList(),
		inTag         : String?           = null,
		inImage       : File?             = null,
		inAttribution : String?           = null
	): Boolean
	{
		if (!fReady) return false
		val vLinks   = actionable																	// boutons utilisables
		val vLaunch  = if (vLinks && inLaunch != null) " activationType=\"protocol\" launch=\"${xml(inLaunch)}\"" else ""	// clic sur la notification
		val vImage   = inImage?.takeIf { it.isFile }?.let { "<image placement=\"appLogoOverride\" src=\"${xml(it.toURI().toString())}\"/>" }.orEmpty()	// icône
		val vOrigin  = inAttribution?.let { "<text placement=\"attribution\">${xml(it)}</text>" }.orEmpty()	// origine
		val vActions = if (vLinks && inActions.isNotEmpty())
			"<actions>" + inActions.take(2).joinToString("") { "<action content=\"${xml(it.label)}\" activationType=\"protocol\" arguments=\"${xml(it.link)}\"/>" } + "</actions>"
			else ""																					// boutons
		val vXml     = "<toast$vLaunch><visual><binding template=\"ToastGeneric\">$vImage<text>${xml(inTitle)}</text><text>${xml(inBody)}</text>$vOrigin</binding></visual>$vActions</toast>"	// notification
		val vSent    = send("S|${inTag.orEmpty()}|${Base64.getEncoder().encodeToString(vXml.toByteArray(Charsets.UTF_8))}")	// notification partie
		if (vSent && inTag != null) { fTracked += inTag; beat() }
		return vSent
	}

	/*
		Retire une notification suivie (de l'écran et du centre de
		notifications).
		inTag : étiquette
	*/
	@Synchronized
	fun remove(inTag: String): Boolean
	{
		fTracked -= inTag
		return fReady && send("R|$inTag")
	}

	/*
		Lien d'un bouton : action et identifiant (élément de l'historique).
		inAction : « ouvrir » ou « dossier »
		inId     : identifiant tiré au hasard
	*/
	fun link(
		inAction : String,
		inId     : String
	): String = "bridgeplus:$inAction?id=${URLEncoder.encode(inId, Charsets.UTF_8)}"

	/*
		Arrête l'hôte (fermeture de l'appli).
	*/
	@Synchronized
	fun stop()
	{
		runCatching { fInput?.close() }
		fProcess?.destroy()
		fProcess = null
		fInput   = null
		fTracked.clear()
	}

	/*
		Battement : une ligne « P » chaque seconde tant qu'une notification est
		suivie, pour que l'hôte relise l'historique (sa lecture de l'entrée
		bloque entre deux lignes). Le fil s'arrête quand plus rien n'est suivi.
	*/
	@Synchronized
	private fun beat()
	{
		if (fBeat?.isAlive == true) return
		fBeat = Thread({
			while (fTracked.isNotEmpty())
			{
				Thread.sleep(1_000)
				synchronized(this) { send("P") }
			}
		}, "BridgePlus-battement-notifications").apply { isDaemon = true; start() }
	}

	/*
		Envoie une ligne à l'hôte ; faux en cas d'échec (il sera relancé à la
		prochaine).
		inLine : ligne
	*/
	private fun send(inLine: String): Boolean =
		runCatching {
			val vInput = input() ?: return false															// lignes vers l'hôte
			vInput.write(inLine)
			vInput.newLine()
			vInput.flush()
			true
		}.getOrElse { fProcess = null; fInput = null; false }

	/*
		Flux vers l'hôte, lancé à la première notification (ou relancé s'il
		s'est arrêté), avec un fil qui lit les fermetures signalées ; null
		s'il ne peut pas démarrer.
	*/
	private fun input(): BufferedWriter?
	{
		if (fProcess?.isAlive == true) return fInput
		val vScript  = File(dataDir(), "toast-host.ps1")											// script de l'hôte
		val vText    = WinToast::class.java.getResourceAsStream("toast-host.ps1")?.use { it.readBytes().toString(Charsets.UTF_8) }?.replace("{APPID}", kAppId) ?: return null	// contenu
		vScript.parentFile.mkdirs()
		vScript.writeText(vText, Charsets.UTF_8)
		val vProcess = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", vScript.absolutePath)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start()																				// hôte
		val vInput   = vProcess.outputStream.bufferedWriter(Charsets.UTF_8)						// lignes vers l'hôte
		fProcess = vProcess
		fInput   = vInput
		Thread({
			runCatching {
				vProcess.inputStream.bufferedReader(Charsets.US_ASCII).forEachLine { vLine ->
					if (vLine.startsWith("D|"))
					{
						fTracked -= vLine.substring(2)
						runCatching { onDismissed(vLine.substring(2)) }
					}
				}
			}
		}, "BridgePlus-notifications").apply { isDaemon = true }.start()
		return vInput
	}

	/*
		Texte sûr dans du XML.
		inText : texte
	*/
	private fun xml(inText: String): String =
		inText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}

/*
	Entrée « Envoyer au téléphone » du menu contextuel de l'Explorateur :
	une clé du registre de l'utilisateur (sans droits administrateur), qui
	lance BridgePlus.exe --envoyer « fichier » ; l'appli déjà ouverte reçoit
	le fichier. Ajoutée ou retirée par un réglage ; seulement pour l'appli
	installée (chemin de l'exécutable connu).
*/
object ExplorerMenu
{
	private const val kKey     = "Software\\Classes\\*\\shell\\BridgePlus"					// entrée du menu (tous les fichiers)
	const val kSendArg         = "--envoyer"													// argument d'envoi d'un fichier

	/*
		Vrai si l'entrée existe.
	*/
	fun isOn(): Boolean = WinBridge.available && runCatching { Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, kKey) }.getOrDefault(false)

	/*
		Ajoute ou retire l'entrée ; rend null si c'est fait, sinon la raison.
		inOn : vrai pour l'ajouter
	*/
	fun set(inOn: Boolean): String?
	{
		val vExe = WindowsStartup.exe ?: return "disponible une fois BridgePlus installé"			// exécutable
		return runCatching {
			if (inOn)
			{
				Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, "$kKey\\command")
				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, kKey, "MUIVerb", "Envoyer au téléphone")
				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, kKey, "Icon", "\"$vExe\",0")
				Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, "$kKey\\command", "", "\"$vExe\" $kSendArg \"%1\"")
			}
			else if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, kKey))
			{
				if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, "$kKey\\command")) Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, "$kKey\\command")
				Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, kKey)
			}
			null
		}.getOrElse { it.message ?: "registre inaccessible" }
	}
}
