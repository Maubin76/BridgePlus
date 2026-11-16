package fr.maubin.bridgeplus.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference

private const val kClientAreaAnimation = 0x1042									// SPI_GETCLIENTAREAANIMATION : « Afficher les animations dans Windows »
private const val kPersonalize         = "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"	// clé du thème de Windows
private const val kSystemLight         = "SystemUsesLightTheme"							// 1 : barre des tâches claire

/*
	Accès aux réglages du système (user32.dll).
*/
private interface User32Prefs : Library
{
	/*
		Lit un réglage du système.
		uiAction : réglage (SPI_*)
		uiParam  : paramètre
		pvParam  : valeur lue
		fWinIni  : options d'écriture (0 en lecture)
		Retour : vrai si lu
	*/
	fun SystemParametersInfoW(
		uiAction : Int,
		uiParam  : Int,
		pvParam  : IntByReference,
		fWinIni  : Int
	): Boolean
}

/*
	Réglages du système utiles à l'interface.
*/
object SystemPrefs
{
	private val fUser: User32Prefs? by lazy {
		if (System.getProperty("os.name").orEmpty().startsWith("Windows")) runCatching { Native.load("user32", User32Prefs::class.java) }.getOrNull() else null
	}																					// user32 ; null hors Windows

	/*
		Vrai si Windows affiche les animations (Paramètres › Accessibilité ›
		Effets visuels) ; vrai si le réglage est illisible.
	*/
	fun animationsEnabled(): Boolean
	{
		val vUser  = fUser ?: return true
		val vValue = IntByReference(1)														// valeur lue
		return runCatching { if (vUser.SystemParametersInfoW(kClientAreaAnimation, 0, vValue, 0)) vValue.value != 0 else true }.getOrDefault(true)
	}

	/*
		Vrai si la barre des tâches est claire (Paramètres › Personnalisation ›
		Couleurs › « Mode Windows » clair) ; faux si sombre, ou illisible.
	*/
	fun lightTaskbar(): Boolean
	{
		if (fUser == null) return false
		return runCatching { Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, kPersonalize, kSystemLight) == 1 }.getOrDefault(false)
	}
}
