package fr.maubin.bridgeplus.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.W32APIOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val kHwndMessage       = -3											// HWND_MESSAGE : fenêtre de messages seulement
private const val kWmClipboardUpdate = 0x031D										// WM_CLIPBOARDUPDATE : le presse-papiers a changé
private const val kWmApp             = 0x8000										// WM_APP : messages de BridgePlus
private const val kMsgWrite          = kWmApp + 1									// écrire les textes en attente
private const val kMsgRegister       = kWmApp + 2									// changer le raccourci global
private const val kWmHotkey          = 0x0312										// WM_HOTKEY : raccourci global appuyé
private const val kHotkeyId          = 0x4250										// identifiant du raccourci global
private const val kModNoRepeat       = 0x4000										// MOD_NOREPEAT : pas de répétition en maintenant les touches
private const val kCfUnicodeText     = 13											// CF_UNICODETEXT
private const val kGmemMoveable      = 0x0002										// GMEM_MOVEABLE

/*
	Fonctions du presse-papiers de Windows (user32.dll).
*/
private interface ClipboardApi : Library
{
	/*
		Ouvre le presse-papiers pour une fenêtre.
		hwnd : fenêtre
	*/
	fun OpenClipboard(hwnd: WinDef.HWND?): Boolean

	/*
		Ferme le presse-papiers.
	*/
	fun CloseClipboard(): Boolean

	/*
		Vide le presse-papiers ; la fenêtre qui l'a ouvert en devient propriétaire.
	*/
	fun EmptyClipboard(): Boolean

	/*
		Dépose une donnée.
		format : format
		data   : mémoire globale
	*/
	fun SetClipboardData(
		format : Int,
		data   : Pointer?
	): Pointer?

	/*
		Donnée d'un format ; null s'il est absent.
		format : format
	*/
	fun GetClipboardData(format: Int): Pointer?

	/*
		Vrai si un format est présent, sans demander la donnée (aucun rendu
		différé n'est déclenché).
		format : format
	*/
	fun IsClipboardFormatAvailable(format: Int): Boolean

	/*
		Enregistre un format nommé.
		name : nom du format
	*/
	fun RegisterClipboardFormat(name: String): Int

	/*
		Fenêtre propriétaire actuelle du presse-papiers.
	*/
	fun GetClipboardOwner(): WinDef.HWND?

	/*
		Abonne une fenêtre aux changements du presse-papiers.
		hwnd : fenêtre
	*/
	fun AddClipboardFormatListener(hwnd: WinDef.HWND): Boolean
}

/*
	Mémoire globale de Windows (kernel32.dll), pour les données du presse-papiers.
*/
private interface GlobalMemory : Library
{
	/*
		Réserve un bloc de mémoire.
		flags : options
		bytes : taille
	*/
	fun GlobalAlloc(
		flags : Int,
		bytes : Long
	): Pointer?

	/*
		Rend l'adresse d'un bloc.
		mem : bloc
	*/
	fun GlobalLock(mem: Pointer): Pointer?

	/*
		Libère l'adresse d'un bloc.
		mem : bloc
	*/
	fun GlobalUnlock(mem: Pointer): Boolean

	/*
		Taille d'un bloc.
		mem : bloc
	*/
	fun GlobalSize(mem: Pointer): Long
}

/*
	Pont avec Windows, sur un fil dédié qui fait tourner la boucle de
	messages d'une fenêtre invisible (comme celui de KeePlus) :
	- raccourci clavier global de la carte de l'historique (RegisterHotKey) ;
	- presse-papiers, abonné aux changements (AddClipboardFormatListener).
	À chaque copie d'une autre appli, les
	marques « sensible » sont lues **avant** le texte :
	- ExcludeClipboardContentFromMonitorProcessing présent ;
	- CanIncludeInClipboardHistory ou CanUploadToCloudClipboard à 0 ;
	- Clipboard Viewer Ignore présent (KeePass et d'autres gestionnaires).
	Un contenu sensible n'est jamais lu : KeePlus fournit ses secrets en
	rendu différé, et les lire consommerait sa séquence de collage. Les copies
	de BridgePlus lui-même (textes reçus) sont ignorées.
	Hors Windows, rien n'est disponible (available faux).
	fOnCopy   : copie d'une autre appli : texte (null sans texte) et marque
	            « sensible » ; appelé sur un fil à part
	fOnHotkey : raccourci global appuyé ; appelé sur le fil de Windows
*/
class WinBridge(
	private val fOnCopy   : (String?, Boolean) -> Unit,
	private val fOnHotkey : () -> Unit = {}
)
{
	private val fUser    = User32.INSTANCE													// fonctions des fenêtres
	private val fClip    = Native.load("user32", ClipboardApi::class.java, W32APIOptions.DEFAULT_OPTIONS)	// presse-papiers
	private val fMemory  = Native.load("kernel32", GlobalMemory::class.java)				// mémoire globale
	private val fWorker  = Executors.newSingleThreadExecutor { inTask -> Thread(inTask, "BridgePlus-copie").apply { isDaemon = true } }	// traitement des copies
	private val fPending = LinkedBlockingQueue<String>()									// textes à écrire
	private var fProc    : WinUser.WindowProc? = null										// procédure de la fenêtre (gardée en vie)
	@Volatile private var fHwnd : WinDef.HWND? = null										// fenêtre invisible
	private var fExclude      = 0																// format ExcludeClipboardContentFromMonitorProcessing
	private var fHistory      = 0																// format CanIncludeInClipboardHistory
	private var fCloud        = 0																// format CanUploadToCloudClipboard
	private var fViewerIgnore = 0																// format Clipboard Viewer Ignore
	@Volatile private var fPendingKey : Pair<Int, Int>? = null								// raccourci à enregistrer (modificateurs, touche)
	@Volatile private var fHotkeyOk   = false													// dernier enregistrement accepté par Windows
	@Volatile private var fDone       : CountDownLatch? = null								// fin de l'enregistrement en cours

	/*
		Démarre le fil de Windows et attend la fenêtre (2 s au plus).
	*/
	fun start()
	{
		val vReady = CountDownLatch(1)															// fenêtre créée
		Thread({ loop(vReady) }, "BridgePlus-Windows").apply { isDaemon = true }.start()
		vReady.await(2, TimeUnit.SECONDS)
	}

	/*
		Place un texte reçu dans le presse-papiers (sur le fil de Windows).
		Rend faux si la fenêtre n'existe pas.
		inText : texte
	*/
	fun write(inText: String): Boolean
	{
		val vHwnd = fHwnd ?: return false
		fPending.add(inText)
		fUser.PostMessage(vHwnd, kMsgWrite, WinDef.WPARAM(0), WinDef.LPARAM(0))
		return true
	}

	/*
		Enregistre le raccourci global (remplace le précédent) ; rend faux s'il
		est déjà pris par une autre appli, ou sans fenêtre.
		inModifiers : modificateurs Windows (MOD_CONTROL…)
		inKey       : touche (code virtuel)
	*/
	fun register(
		inModifiers : Int,
		inKey       : Int
	): Boolean
	{
		val vHwnd = fHwnd ?: return false
		fPendingKey = inModifiers to inKey
		fHotkeyOk   = false
		val vDone   = CountDownLatch(1)															// enregistrement fait
		fDone       = vDone
		fUser.PostMessage(vHwnd, kMsgRegister, WinDef.WPARAM(0), WinDef.LPARAM(0))
		vDone.await(2, TimeUnit.SECONDS)
		return fHotkeyOk
	}

	/*
		Boucle de messages : fenêtre invisible abonnée au presse-papiers, puis
		messages jusqu'à la fin.
		inReady : signal de la fenêtre créée
	*/
	private fun loop(inReady: CountDownLatch)
	{
		fExclude      = fClip.RegisterClipboardFormat("ExcludeClipboardContentFromMonitorProcessing")
		fHistory      = fClip.RegisterClipboardFormat("CanIncludeInClipboardHistory")
		fCloud        = fClip.RegisterClipboardFormat("CanUploadToCloudClipboard")
		fViewerIgnore = fClip.RegisterClipboardFormat("Clipboard Viewer Ignore")
		val vProc  = WinUser.WindowProc { inHwnd, inMsg, inW, inL -> handle(inHwnd, inMsg, inW, inL) }	// procédure
		fProc      = vProc
		val vClass = WinUser.WNDCLASSEX().apply {
			hInstance     = Kernel32.INSTANCE.GetModuleHandle(null)
			lpfnWndProc   = vProc
			lpszClassName = "BridgePlusPont"
		}																						// classe de la fenêtre
		fUser.RegisterClassEx(vClass)
		val vHwnd  = fUser.CreateWindowEx(0, "BridgePlusPont", "BridgePlus", 0, 0, 0, 0, 0, WinDef.HWND(Pointer.createConstant(kHwndMessage.toLong())), null, vClass.hInstance, null)	// fenêtre
		if (vHwnd != null && fClip.AddClipboardFormatListener(vHwnd)) fHwnd = vHwnd
		inReady.countDown()
		if (fHwnd == null) return
		val vMsg = WinUser.MSG()																// message reçu
		while (fUser.GetMessage(vMsg, null, 0, 0) > 0)
		{
			fUser.TranslateMessage(vMsg)
			fUser.DispatchMessage(vMsg)
		}
	}

	/*
		Messages de la fenêtre invisible.
		inHwnd : fenêtre
		inMsg  : message
		inW    : paramètre
		inL    : paramètre
	*/
	private fun handle(
		inHwnd : WinDef.HWND,
		inMsg  : Int,
		inW    : WinDef.WPARAM,
		inL    : WinDef.LPARAM
	): WinDef.LRESULT
	{
		when (inMsg)
		{
			kWmClipboardUpdate -> { readNow(inHwnd); return WinDef.LRESULT(0) }
			kMsgWrite          -> { writeNow(inHwnd); return WinDef.LRESULT(0) }
			kMsgRegister       -> { registerNow(inHwnd); return WinDef.LRESULT(0) }
			kWmHotkey          -> { if (inW.toInt() == kHotkeyId) runCatching { fOnHotkey() }; return WinDef.LRESULT(0) }
		}
		return fUser.DefWindowProc(inHwnd, inMsg, inW, inL)
	}

	/*
		Enregistre le raccourci demandé (sur le fil de Windows).
		inHwnd : fenêtre invisible
	*/
	private fun registerNow(inHwnd: WinDef.HWND)
	{
		fUser.UnregisterHotKey(inHwnd.pointer, kHotkeyId)
		val vKey  = fPendingKey																	// raccourci demandé
		fHotkeyOk = vKey != null && fUser.RegisterHotKey(inHwnd, kHotkeyId, vKey.first or kModNoRepeat, vKey.second)
		fDone?.countDown()
	}

	/*
		Lit une copie d'une autre appli : marques « sensible » d'abord, texte
		ensuite seulement s'il n'est pas sensible.
		inHwnd : fenêtre invisible
	*/
	private fun readNow(inHwnd: WinDef.HWND)
	{
		if (fClip.GetClipboardOwner()?.pointer == inHwnd.pointer) return
		if (!open(inHwnd)) return
		var vText      : String? = null															// texte copié
		var vSensitive = false																	// contenu marqué sensible
		try
		{
			vSensitive = (fExclude != 0 && fClip.IsClipboardFormatAvailable(fExclude))
				|| (fViewerIgnore != 0 && fClip.IsClipboardFormatAvailable(fViewerIgnore))
				|| dwordIsZero(fHistory)
				|| dwordIsZero(fCloud)
			if (!vSensitive && fClip.IsClipboardFormatAvailable(kCfUnicodeText))
			{
				val vData = fClip.GetClipboardData(kCfUnicodeText)									// bloc du texte
				val vPtr  = vData?.let { fMemory.GlobalLock(it) }										// adresse du texte
				if (vData != null && vPtr != null)
				{
					vText = vPtr.getWideString(0)
					fMemory.GlobalUnlock(vData)
				}
			}
		}
		finally
		{
			fClip.CloseClipboard()
		}
		val vResult = vText																		// texte lu
		fWorker.execute { runCatching { fOnCopy(vResult, vSensitive) } }
	}

	/*
		Vrai si un format nommé est présent avec la valeur 0 (« ne pas garder
		dans l'historique », « ne pas envoyer dans le cloud »).
		inFormat : format
	*/
	private fun dwordIsZero(inFormat: Int): Boolean
	{
		if (inFormat == 0 || !fClip.IsClipboardFormatAvailable(inFormat)) return false
		val vData = fClip.GetClipboardData(inFormat) ?: return false								// bloc
		if (fMemory.GlobalSize(vData) < 4) return false
		val vPtr  = fMemory.GlobalLock(vData) ?: return false										// adresse
		val vZero = vPtr.getInt(0) == 0																// valeur nulle
		fMemory.GlobalUnlock(vData)
		return vZero
	}

	/*
		Écrit le dernier texte reçu en attente (les précédents sont dépassés).
		inHwnd : fenêtre invisible
	*/
	private fun writeNow(inHwnd: WinDef.HWND)
	{
		var vText : String? = null																// dernier texte en attente
		while (true) vText = fPending.poll() ?: break
		val vLast = vText ?: return
		if (!open(inHwnd)) return
		try
		{
			fClip.EmptyClipboard()
			global((vLast + Char(0)).toByteArray(Charsets.UTF_16LE))?.let { fClip.SetClipboardData(kCfUnicodeText, it) }
		}
		finally
		{
			fClip.CloseClipboard()
		}
	}

	/*
		Ouvre le presse-papiers, en réessayant un court instant s'il est
		occupé par une autre appli ; faux s'il reste inaccessible.
		inHwnd : fenêtre invisible
	*/
	private fun open(inHwnd: WinDef.HWND): Boolean
	{
		repeat(10)
		{
			if (fClip.OpenClipboard(inHwnd)) return true
			Thread.sleep(20)
		}
		return false
	}

	/*
		Copie des octets dans un bloc de mémoire globale ; null en cas d'échec.
		inBytes : contenu
	*/
	private fun global(inBytes: ByteArray): Pointer?
	{
		val vMem = fMemory.GlobalAlloc(kGmemMoveable, inBytes.size.toLong()) ?: return null	// bloc
		val vPtr = fMemory.GlobalLock(vMem) ?: return null										// adresse
		vPtr.write(0, inBytes, 0, inBytes.size)
		fMemory.GlobalUnlock(vMem)
		return vMem
	}

	companion object
	{
		/*
			Vrai sous Windows : le pont peut fonctionner.
		*/
		val available: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")
	}
}
