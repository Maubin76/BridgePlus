package fr.maubin.bridgeplus.desktop

import com.sun.jna.platform.win32.Crypt32Util
import fr.maubin.bridgeplus.store.SecretBox

private const val kUiForbidden = 0x1											// CRYPTPROTECT_UI_FORBIDDEN : jamais de fenêtre
private val kEntropy = "BridgePlus".toByteArray(Charsets.UTF_8)				// sel propre à BridgePlus

/*
	Coffret des fichiers sensibles du PC par DPAPI (Windows) : la clé est
	tenue par Windows et liée au compte de l'utilisateur ; un autre compte, ou
	le fichier copié sur un autre PC, ne peut pas l'ouvrir.
*/
object DpapiBox : SecretBox
{
	override fun seal(inPlain: ByteArray): ByteArray =
		Crypt32Util.cryptProtectData(inPlain, kEntropy, kUiForbidden, "BridgePlus", null)

	override fun open(inSealed: ByteArray): ByteArray? =
		runCatching { Crypt32Util.cryptUnprotectData(inSealed, kEntropy, kUiForbidden, null) }.getOrNull()
}
