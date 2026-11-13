package fr.maubin.bridgeplus.desktop

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import java.awt.Rectangle

private const val kNearest = 2														// MONITOR_DEFAULTTONEAREST

/*
	Fenêtre active au moment du raccourci (reprise de KeePlus) : son handle,
	pour lui rendre la main une fois l'élément copié, et l'écran qui la
	contient, pour y faire descendre la carte.
	hwnd   : fenêtre
	screen : zone utile de son écran, en pixels
	pid    : processus de la fenêtre (0 si inconnu)
*/
class ActiveWindow(
	val hwnd   : WinDef.HWND?,
	val screen : Rectangle?,
	val pid    : Int = 0
)
{
	/*
		Vrai si la fenêtre appartient à BridgePlus lui-même (carte déjà
		ouverte, fenêtre principale).
	*/
	val isOwn: Boolean get() = pid != 0 && pid.toLong() == ProcessHandle.current().pid()

	/*
		Rend la main à cette fenêtre (après la fermeture de la carte) : on peut
		coller aussitôt.
	*/
	fun restore()
	{
		hwnd?.let { runCatching { User32.INSTANCE.SetForegroundWindow(it) } }
	}

	companion object
	{
		/*
			Fenêtre active en ce moment ; vide hors Windows.
		*/
		fun current(): ActiveWindow
		{
			if (!WinBridge.available) return ActiveWindow(null, null)
			val vUser = User32.INSTANCE																// fonctions des fenêtres
			val vHwnd = vUser.GetForegroundWindow()													// fenêtre active
			val vPid  = IntByReference()																// processus
			if (vHwnd != null) vUser.GetWindowThreadProcessId(vHwnd, vPid)
			return ActiveWindow(vHwnd, screenOf(vHwnd), vPid.value)
		}

		/*
			Zone utile de l'écran qui contient une fenêtre (sans la barre des tâches).
			inHwnd : fenêtre
		*/
		private fun screenOf(inHwnd: WinDef.HWND?): Rectangle?
		{
			if (inHwnd == null) return null
			return runCatching {
				val vInfo = WinUser.MONITORINFO()														// écran
				User32.INSTANCE.GetMonitorInfo(User32.INSTANCE.MonitorFromWindow(inHwnd, kNearest), vInfo)
				vInfo.rcWork.let { Rectangle(it.left, it.top, it.right - it.left, it.bottom - it.top) }
			}.getOrNull()
		}
	}
}
