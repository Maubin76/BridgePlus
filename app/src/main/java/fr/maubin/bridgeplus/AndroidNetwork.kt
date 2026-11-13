package fr.maubin.bridgeplus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import fr.maubin.bridgeplus.link.NetworkId
import fr.maubin.bridgeplus.link.isHomeNetwork

/*
	Réseau du téléphone, tel que la liaison le voit.
	onWifi     : relié à un Wi-Fi
	wifi       : Wi-Fi reconnu par son nom ; null sans Wi-Fi ou sans autorisation de localisation
	permission : autorisation de localisation accordée (lecture du nom du Wi-Fi)
*/
data class PhoneNetwork(
	val onWifi     : Boolean,
	val wifi       : NetworkId?,
	val permission : Boolean
)

/*
	Vrai si l'autorisation de localisation, nécessaire pour lire le nom du
	Wi-Fi, est accordée.
	inContext : contexte
*/
fun hasWifiNamePermission(inContext: Context): Boolean =
	inContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/*
	Vrai si la localisation est permise « toujours » : le nom du Wi-Fi se lit
	alors aussi depuis le service, appli fermée.
	inContext : contexte
*/
fun hasBackgroundWifiName(inContext: Context): Boolean =
	hasWifiNamePermission(inContext) && inContext.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

/*
	Vrai si l'appli est exclue de l'optimisation de la batterie : condition
	pour qu'Android la laisse relancer la liaison au retour à la maison.
	inContext : contexte
*/
fun isBatteryUnrestricted(inContext: Context): Boolean =
	inContext.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(inContext.packageName) == true

/*
	Vrai si les notifications de l'appli sont permises.
	inContext : contexte
*/
fun hasNotificationPermission(inContext: Context): Boolean =
	inContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/*
	Réseau courant du téléphone : Wi-Fi ou non, nom du Wi-Fi si l'autorisation
	est accordée (seul le nom est lu, jamais une position).
	inContext : contexte
*/
fun currentPhoneNetwork(inContext: Context): PhoneNetwork
{
	val vManager = inContext.getSystemService(ConnectivityManager::class.java)			// connexions
	val vActive  = vManager?.getNetworkCapabilities(vManager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true	// Wi-Fi par défaut
	val vAllowed = hasWifiNamePermission(inContext)										// autorisation
	@Suppress("DEPRECATION")
	val vSsid    = if (vAllowed) inContext.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid else null	// nom du Wi-Fi (même si les données mobiles restent le réseau par défaut)
	val vWifi    = NetworkId.wifi(vSsid)															// Wi-Fi reconnu
	return PhoneNetwork(vActive || vWifi != null, vWifi, vAllowed)
}

/*
	Vrai si le téléphone est sur un des réseaux de la maison.
	inContext : contexte
	inHome    : réseaux de la maison
*/
fun isOnHome(
	inContext : Context,
	inHome    : List<NetworkId>
): Boolean = currentPhoneNetwork(inContext).wifi?.let { isHomeNetwork(listOf(it), inHome) } == true

/*
	Nom du téléphone : celui donné dans les réglages d'Android, sinon le modèle.
	inContext : contexte
*/
fun phoneName(inContext: Context): String =
	Settings.Global.getString(inContext.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
