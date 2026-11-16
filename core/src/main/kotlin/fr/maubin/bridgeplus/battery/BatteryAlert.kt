package fr.maubin.bridgeplus.battery

const val kLowBattery    = 20														// seuil de l'alerte (%)
const val kRearmBattery  = 25														// niveau qui réarme l'alerte (%)

/*
	Alerte de batterie faible du téléphone (PC) : une seule fois par
	décharge, quand le niveau passe sous 20 % sans charger ; réarmée quand
	le téléphone charge ou repasse au-dessus de 25 %.
*/
class BatteryAlert
{
	private var fArmed = true														// alerte possible

	/*
		Nouveau niveau ; rend vrai s'il faut alerter maintenant.
		inLevel    : niveau (%)
		inCharging : en charge
	*/
	@Synchronized
	fun update(
		inLevel    : Int,
		inCharging : Boolean
	): Boolean
	{
		if (inCharging || inLevel >= kRearmBattery) { fArmed = true; return false }
		if (inLevel < kLowBattery && fArmed) { fArmed = false; return true }
		return false
	}
}
