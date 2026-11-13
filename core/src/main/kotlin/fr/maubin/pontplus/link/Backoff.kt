package fr.maubin.pontplus.link

import kotlin.random.Random

const val kBackoffFirstMs = 1_000L													// premier délai avant de réessayer
const val kBackoffMaxMs   = 60_000L													// délai maximal entre deux essais

/*
	Délai progressif entre deux essais de connexion : 1 s, 2 s, 4 s… jusqu'à
	60 s au plus, avec une variation de ±20 % pour que deux appareils ne
	réessaient pas au même instant. Remis à zéro après une connexion réussie
	ou un changement de réseau.
	inFirstMs : premier délai (ms)
	inMaxMs   : délai maximal (ms)
	inJitter  : variation relative (0,2 pour ±20 %)
	inRandom  : aléa (fixé dans les tests)
*/
class Backoff(
	private val inFirstMs : Long   = kBackoffFirstMs,
	private val inMaxMs   : Long   = kBackoffMaxMs,
	private val inJitter  : Double = 0.2,
	private val inRandom  : Random = Random.Default
)
{
	private var fAttempt = 0															// essais ratés depuis la remise à zéro

	/*
		Délai avant le prochain essai (ms), puis passe au palier suivant.
	*/
	@Synchronized
	fun next(): Long
	{
		val vBase  = (inFirstMs shl fAttempt.coerceAtMost(30)).coerceAtMost(inMaxMs)	// délai du palier
		val vShift = if (inJitter <= 0.0) 0.0 else inRandom.nextDouble(-inJitter, inJitter)	// variation
		fAttempt++
		return (vBase * (1 + vShift)).toLong().coerceIn(1, inMaxMs)
	}

	/*
		Revient au premier délai.
	*/
	@Synchronized
	fun reset()
	{
		fAttempt = 0
	}
}
