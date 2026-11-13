package fr.maubin.bridgeplus.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.BufferedWriter
import java.io.File
import java.net.URLEncoder
import java.util.Base64

const val kAppId             = "BridgePlus.BridgePlus"									// identifiant de l'appli pour les notifications de Windows
const val kLinkArg           = "--lien"													// argument d'un lien bridgeplus: (bouton d'une notification)
private const val kAppIdKey  = "Software\\Classes\\AppUserModelId\\$kAppId"				// inscription de l'identifiant
private const val kSchemeKey = "Software\\Classes\\bridgeplus"							// inscription du protocole bridgeplus:

/*
	Script PowerShell gardé ouvert : chaque ligne reçue est une notification
	(XML en base64), affichée sous le nom et le logo de BridgePlus. Garder
	le processus évite de relancer PowerShell (une demi-seconde) à chaque
	notification.
*/
private val kToastScript = """
${'$'}ErrorActionPreference = 'SilentlyContinue'
[void][Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]
[void][Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime]
${'$'}n = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$kAppId')
while ((${'$'}l = [Console]::In.ReadLine()) -ne ${'$'}null) {
	try {
		${'$'}x = New-Object Windows.Data.Xml.Dom.XmlDocument
		${'$'}x.LoadXml([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(${'$'}l)))
		${'$'}n.Show([Windows.UI.Notifications.ToastNotification]::new(${'$'}x))
	} catch {}
}
"""

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
	l'utilisateur), respectent « Ne pas déranger », et leurs boutons lancent
	un lien « bridgeplus:… » qui ne porte qu'un identifiant tiré au hasard
	(jamais un chemin) : l'appli ouverte vérifie l'identifiant avant d'agir,
	si bien qu'une page web ne peut rien ouvrir par ce lien. Les boutons
	n'existent que pour l'appli installée (chemin de BridgePlus.exe connu).
	Hors Windows ou en cas d'échec, show rend faux : l'appelant se rabat sur
	la notification de la zone de notification.
*/
object WinToast
{
	private var fProcess : Process? = null													// PowerShell gardé ouvert
	private var fInput   : BufferedWriter? = null											// lignes vers PowerShell
	private var fReady   = false																// inscriptions faites

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
		inTitle   : titre
		inBody    : texte
		inLaunch  : lien du clic sur la notification ; null sans
		inActions : boutons (deux au plus)
	*/
	@Synchronized
	fun show(
		inTitle   : String,
		inBody    : String,
		inLaunch  : String?           = null,
		inActions : List<ToastAction> = emptyList()
	): Boolean
	{
		if (!fReady) return false
		val vLinks   = actionable																// boutons utilisables
		val vLaunch  = if (vLinks && inLaunch != null) " activationType=\"protocol\" launch=\"${xml(inLaunch)}\"" else ""	// clic sur la notification
		val vActions = if (vLinks && inActions.isNotEmpty())
			"<actions>" + inActions.take(2).joinToString("") { "<action content=\"${xml(it.label)}\" activationType=\"protocol\" arguments=\"${xml(it.link)}\"/>" } + "</actions>"
			else ""																				// boutons
		val vXml     = "<toast$vLaunch><visual><binding template=\"ToastGeneric\"><text>${xml(inTitle)}</text><text>${xml(inBody)}</text></binding></visual>$vActions</toast>"	// notification
		return runCatching {
			val vInput = input() ?: return false													// lignes vers PowerShell
			vInput.write(Base64.getEncoder().encodeToString(vXml.toByteArray(Charsets.UTF_8)))
			vInput.newLine()
			vInput.flush()
			true
		}.getOrElse { fProcess = null; fInput = null; false }
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
		Arrête PowerShell (fermeture de l'appli).
	*/
	@Synchronized
	fun stop()
	{
		runCatching { fInput?.close() }
		fProcess?.destroy()
		fProcess = null
		fInput   = null
	}

	/*
		Flux vers PowerShell, lancé à la première notification (ou relancé
		s'il s'est arrêté) ; null s'il ne peut pas démarrer.
	*/
	private fun input(): BufferedWriter?
	{
		if (fProcess?.isAlive == true) return fInput
		val vProcess = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", kToastScript)
			.redirectErrorStream(true)
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.start()																			// PowerShell
		fProcess = vProcess
		fInput   = vProcess.outputStream.bufferedWriter(Charsets.US_ASCII)
		return fInput
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
