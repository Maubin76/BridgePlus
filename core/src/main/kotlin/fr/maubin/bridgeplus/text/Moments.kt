package fr.maubin.bridgeplus.text

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val kHour = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)				// heure (14:02)
private val kDay  = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)				// jour (3 nov.)

/*
	Moment passé en français court, pour les listes : « à l'instant »,
	« il y a 5 min », « il y a 2 h », « hier à 14:02 », « le 3 nov. ».
	inThen : moment (ms)
	inNow  : maintenant (ms)
	inZone : fuseau d'affichage
*/
fun relativeTime(
	inThen : Long,
	inNow  : Long   = System.currentTimeMillis(),
	inZone : ZoneId = ZoneId.systemDefault()
): String
{
	val vSeconds = (inNow - inThen) / 1000													// écart (s)
	val vThen    = Instant.ofEpochMilli(inThen).atZone(inZone)								// moment, daté
	val vDays    = ChronoUnit.DAYS.between(vThen.toLocalDate(), Instant.ofEpochMilli(inNow).atZone(inZone).toLocalDate())	// jours de calendrier d'écart
	return when
	{
		vSeconds < 60      -> "à l'instant"
		vSeconds < 3_600   -> "il y a ${vSeconds / 60} min"
		vDays == 0L        -> "il y a ${vSeconds / 3_600} h"
		vDays == 1L        -> "hier à ${kHour.format(vThen)}"
		else               -> "le ${kDay.format(vThen)}"
	}
}
