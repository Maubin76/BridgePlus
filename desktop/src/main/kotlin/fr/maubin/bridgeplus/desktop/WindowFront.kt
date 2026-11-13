package fr.maubin.bridgeplus.desktop

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/*
	Mise au premier plan de la fenêtre au lancement. Windows refuse
	d'ordinaire qu'un programme prenne le premier plan s'il ne l'a pas déjà
	(la fenêtre s'ouvre alors derrière, ou ne fait que clignoter dans la
	barre des tâches) : on se rattache un instant au fil de la fenêtre active
	pour obtenir ce droit. Ailleurs, ou si Windows refuse quand même, la
	fenêtre passe devant par les moyens d'AWT (toujours au-dessus le temps
	d'un instant, puis à l'avant).
*/
object WindowFront
{
	private val fWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")	// Windows

	/*
		Amène une fenêtre déjà affichée au premier plan, avec le focus.
		inWindow : fenêtre
	*/
	fun bring(inWindow: java.awt.Window)
	{
		if (!inWindow.isDisplayable) return
		if (fWindows) runCatching { foreground(WinDef.HWND(Native.getComponentPointer(inWindow))) }
		val vOnTop = inWindow.isAlwaysOnTop													// réglage d'origine
		runCatching {
			inWindow.isAlwaysOnTop = true
			inWindow.toFront()
			inWindow.requestFocus()
		}
		inWindow.isAlwaysOnTop = vOnTop
	}

	/*
		Donne le premier plan à une fenêtre native : rattache un instant le fil
		de l'appli à celui de la fenêtre active, puis montre, élève et active
		la fenêtre.
		inHwnd : fenêtre native
	*/
	private fun foreground(inHwnd: WinDef.HWND)
	{
		val vUser    = User32.INSTANCE																// fonctions des fenêtres
		val vActive  = vUser.GetForegroundWindow()													// fenêtre active (explorateur, terminal…)
		val vOther   = if (vActive != null) vUser.GetWindowThreadProcessId(vActive, null) else 0	// son fil
		val vOwn     = Kernel32.INSTANCE.GetCurrentThreadId()										// fil de l'appli
		val vAttach  = vOther != 0 && vOther != vOwn												// rattachement utile
		if (vAttach) vUser.AttachThreadInput(WinDef.DWORD(vOwn.toLong()), WinDef.DWORD(vOther.toLong()), true)
		try
		{
			vUser.ShowWindow(inHwnd, WinUser.SW_SHOW)
			vUser.BringWindowToTop(inHwnd)
			vUser.SetForegroundWindow(inHwnd)
			vUser.SetFocus(inHwnd)
		}
		finally
		{
			if (vAttach) vUser.AttachThreadInput(WinDef.DWORD(vOwn.toLong()), WinDef.DWORD(vOther.toLong()), false)
		}
	}
}
