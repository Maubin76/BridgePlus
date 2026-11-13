package fr.maubin.bridgeplus.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference

private const val kDarkMode     = 20														// DWMWA_USE_IMMERSIVE_DARK_MODE : boutons et icônes clairs
private const val kBorderColor  = 34														// DWMWA_BORDER_COLOR : liseré de la fenêtre
private const val kCaptionColor = 35														// DWMWA_CAPTION_COLOR : fond de la barre de titre
private const val kTextColor    = 36														// DWMWA_TEXT_COLOR : titre

/*
	Accès à la composition des fenêtres de Windows (dwmapi.dll).
*/
private interface Dwm : Library
{
	/*
		Règle un attribut de la fenêtre.
		hwnd      : fenêtre
		attribute : attribut (DWMWA_*)
		value     : valeur
		size      : taille de la valeur, en octets
		Retour : 0 si appliqué
	*/
	fun DwmSetWindowAttribute(
		hwnd      : WinDef.HWND,
		attribute : Int,
		value     : Pointer,
		size      : Int
	): Int
}

/*
	Barre de titre native aux couleurs du thème de l'appli (Windows 11) :
	fond, titre, liseré et mode sombre des boutons. Ailleurs (autre système,
	Windows 10 sans ces attributs), rien ne change : la barre reste celle du
	système.
*/
object TitleBar
{
	private val fDwm: Dwm? by lazy {
		if (System.getProperty("os.name").orEmpty().startsWith("Windows")) runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() else null
	}																						// composition des fenêtres ; null hors Windows

	/*
		Colore la barre de titre d'une fenêtre déjà affichée.
		inWindow  : fenêtre
		inCaption : fond de la barre
		inText    : couleur du titre
		inDark    : thème sombre (boutons clairs)
	*/
	fun apply(
		inWindow  : java.awt.Window,
		inCaption : Color,
		inText    : Color,
		inDark    : Boolean
	)
	{
		val vDwm = fDwm ?: return
		if (!inWindow.isDisplayable) return
		runCatching {
			val vHwnd = WinDef.HWND(Native.getComponentPointer(inWindow))				// fenêtre native
			/*
				Règle un attribut entier.
				inAttribute : attribut
				inValue     : valeur
			*/
			fun set(inAttribute: Int, inValue: Int) = IntByReference(inValue).let { vDwm.DwmSetWindowAttribute(vHwnd, inAttribute, it.pointer, 4) }
			set(kDarkMode, if (inDark) 1 else 0)
			set(kCaptionColor, colorRef(inCaption))
			set(kBorderColor, colorRef(inCaption))
			set(kTextColor, colorRef(inText))
		}
	}

	/*
		Couleur au format de Windows (0x00BBGGRR).
		inColor : couleur
	*/
	internal fun colorRef(inColor: Color): Int
	{
		val vArgb = inColor.toArgb()															// 0xAARRGGBB
		return ((vArgb and 0xFF) shl 16) or (vArgb and 0xFF00) or ((vArgb shr 16) and 0xFF)
	}
}
