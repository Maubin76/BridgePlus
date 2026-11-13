package fr.maubin.bridgeplus.desktop

import java.awt.event.KeyEvent

private const val kModAlt     = 0x0001												// MOD_ALT
private const val kModControl = 0x0002												// MOD_CONTROL
private const val kModShift   = 0x0004												// MOD_SHIFT
private const val kModWin     = 0x0008												// MOD_WIN

const val kDefaultHotkey = "Ctrl+Alt+V"											// raccourci par défaut de la carte de l'historique

/*
	Raccourci clavier global : modificateurs et touche, au format texte des
	réglages (« Ctrl+Shift+Space »).
	ctrl, alt, shift, win : modificateurs
	key                   : touche (code AWT, identique au code virtuel de
	                        Windows pour les lettres, chiffres, F1-F12, Espace)
*/
data class Hotkey(
	val ctrl  : Boolean,
	val alt   : Boolean,
	val shift : Boolean,
	val win   : Boolean,
	val key   : Int
)
{
	/*
		Modificateurs au format de Windows.
	*/
	val modifiers: Int
		get() = (if (ctrl) kModControl else 0) or (if (alt) kModAlt else 0) or (if (shift) kModShift else 0) or (if (win) kModWin else 0)

	/*
		Vrai si le raccourci peut servir : une touche et au moins un
		modificateur autre que Maj (sinon il gênerait la frappe). Ctrl+Alt
		seuls valent AltGr sur un clavier AZERTY : ils ne sont permis qu'avec
		une lettre qui ne tape rien avec AltGr (pas E, qui donne « € »), ni
		avec un chiffre (« @ », « # »…) ou une touche F.
	*/
	val usable: Boolean
		get() = key != 0 && (ctrl || alt || win) && (!(ctrl && alt && !shift && !win) || (key in KeyEvent.VK_A..KeyEvent.VK_Z && key != KeyEvent.VK_E))

	/*
		Texte des réglages.
	*/
	fun spec(): String = (listOfNotNull("Ctrl".takeIf { ctrl }, "Alt".takeIf { alt }, "Shift".takeIf { shift }, "Win".takeIf { win }) + keyName(key)).joinToString("+")

	/*
		Texte affiché : « Ctrl + Maj + Espace ».
	*/
	fun label(): String =
		(listOfNotNull("Ctrl".takeIf { ctrl }, "Alt".takeIf { alt }, "Maj".takeIf { shift }, "Win".takeIf { win }) + keyLabel(key)).joinToString(" + ")

	companion object
	{
		/*
			Raccourci lu dans les réglages ; le raccourci par défaut s'il est
			illisible.
			inSpec : texte des réglages
		*/
		fun parse(inSpec: String): Hotkey
		{
			val vParts = inSpec.split('+').map { it.trim() }.filter { it.isNotEmpty() }		// morceaux
			val vKey   = vParts.lastOrNull()?.let(::keyCode) ?: 0								// touche
			val vHotkey = Hotkey(
				ctrl  = vParts.any { it.equals("Ctrl", true) },
				alt   = vParts.any { it.equals("Alt", true) },
				shift = vParts.any { it.equals("Shift", true) },
				win   = vParts.any { it.equals("Win", true) },
				key   = vKey
			)																				// raccourci lu
			return if (vHotkey.usable) vHotkey else parse(kDefaultHotkey)
		}

		/*
			Code d'une touche nommée ; null si inconnue.
			inName : nom (lettre, chiffre, F1-F12, Space)
		*/
		private fun keyCode(inName: String): Int? = when
		{
			inName.equals("Space", true)                       -> KeyEvent.VK_SPACE
			inName.length == 1 && inName[0].isLetterOrDigit()  -> inName.uppercase()[0].code
			inName.matches(Regex("(?i)F([1-9]|1[0-2])"))       -> KeyEvent.VK_F1 + inName.substring(1).toInt() - 1
			else                                               -> null
		}

		/*
			Nom d'une touche dans les réglages.
			inKey : code
		*/
		fun keyName(inKey: Int): String = when (inKey)
		{
			KeyEvent.VK_SPACE                 -> "Space"
			in KeyEvent.VK_F1..KeyEvent.VK_F12 -> "F${inKey - KeyEvent.VK_F1 + 1}"
			else                              -> inKey.toChar().toString()
		}

		/*
			Nom affiché d'une touche.
			inKey : code
		*/
		private fun keyLabel(inKey: Int): String = if (inKey == KeyEvent.VK_SPACE) "Espace" else keyName(inKey)

		/*
			Vrai si la touche peut finir un raccourci (lettre, chiffre, F1-F12, Espace).
			inKey : code
		*/
		fun isKey(inKey: Int): Boolean =
			inKey == KeyEvent.VK_SPACE || inKey in KeyEvent.VK_A..KeyEvent.VK_Z || inKey in KeyEvent.VK_0..KeyEvent.VK_9 || inKey in KeyEvent.VK_F1..KeyEvent.VK_F12
	}
}
