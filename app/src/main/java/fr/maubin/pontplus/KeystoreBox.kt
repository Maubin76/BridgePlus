package fr.maubin.pontplus

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import fr.maubin.pontplus.store.SecretBox
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val kKeystore = "AndroidKeyStore"											// magasin de clés d'Android
private const val kAlias    = "pontplus-fichiers"										// clé des fichiers sensibles
private const val kIvBytes  = 12															// numéro à usage unique d'AES-GCM

/*
	Coffret des fichiers sensibles du téléphone (clés des paires, historique) :
	AES-256-GCM avec une clé du Keystore d'Android, qui ne quitte jamais le
	matériel sécurisé du téléphone. Sur le disque : numéro à usage unique puis
	chiffré.
*/
object KeystoreBox : SecretBox
{
	override fun seal(inPlain: ByteArray): ByteArray
	{
		val vCipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }	// chiffreur (numéro tiré par le Keystore)
		return vCipher.iv + vCipher.doFinal(inPlain)
	}

	override fun open(inSealed: ByteArray): ByteArray?
	{
		if (inSealed.size <= kIvBytes) return null
		return try
		{
			Cipher.getInstance("AES/GCM/NoPadding").run {
				init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, inSealed, 0, kIvBytes))
				doFinal(inSealed, kIvBytes, inSealed.size - kIvBytes)
			}
		}
		catch (inError: AEADBadTagException)
		{
			null
		}
	}

	/*
		Clé du Keystore ; créée à la première utilisation.
	*/
	@Synchronized
	private fun key(): SecretKey
	{
		val vStore = KeyStore.getInstance(kKeystore).apply { load(null) }						// magasin
		(vStore.getKey(kAlias, null) as? SecretKey)?.let { return it }
		return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, kKeystore).run {
			init(
				KeyGenParameterSpec.Builder(kAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
					.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
					.setKeySize(256)
					.build()
			)
			generateKey()
		}
	}
}
