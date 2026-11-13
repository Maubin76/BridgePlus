package fr.maubin.bridgeplus

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/*
	Activité transparente des gestes explicites, qui ne montre rien d'autre
	qu'un court message :
	- « Envoyer au PC » de la tuile : lit le presse-papiers et l'envoie ;
	- « Envoyer au PC » du menu de partage : envoie le texte partagé ;
	- « Copier » de la notification d'un texte reçu : le place dans le
	  presse-papiers.
	Android ne laisse lire le presse-papiers qu'à l'appli au premier plan :
	l'activité attend d'avoir le focus, lit, envoie (sur un fil à part),
	puis se ferme.
*/
class ClipActivity : Activity()
{
	private var fDone = false																// geste déjà traité

	override fun onWindowFocusChanged(inFocus: Boolean)
	{
		super.onWindowFocusChanged(inFocus)
		if (!inFocus || fDone) return
		fDone = true
		when (intent?.action)
		{
			kActionCopy        -> done(copyWaiting())
			Intent.ACTION_SEND -> bridgeApp.sendClip(intent.getStringExtra(Intent.EXTRA_TEXT), false, true, ::done)
			else               -> readClipboard(this).let { bridgeApp.sendClip(it.text, it.sensitive, true, ::done) }
		}
	}

	/*
		Montre le message du geste, puis ferme l'activité.
		inMessage : message ; null pour aucun
	*/
	private fun done(inMessage: String?)
	{
		inMessage?.let { Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show() }
		finish()
	}

	@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")											// convention « in » des paramètres
	override fun onCreate(inState: Bundle?)
	{
		super.onCreate(inState)
		overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
	}

	override fun finish()
	{
		super.finish()
		overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
	}

	/*
		Place dans le presse-papiers le texte reçu qui attendait ; rend le
		message à montrer.
	*/
	private fun copyWaiting(): String
	{
		cancelReceived(this)
		val vText = bridgeApp.takeWaiting() ?: return "Ce texte n'est plus disponible."		// texte en attente
		return if (writeClipboard(this, vText)) "Copié" else "Copie impossible."
	}

	companion object
	{
		const val kActionSend = "fr.maubin.bridgeplus.action.ENVOYER_PRESSE_PAPIERS"		// envoyer le presse-papiers (tuile)
		const val kActionCopy = "fr.maubin.bridgeplus.action.COPIER_TEXTE_RECU"				// copier le texte reçu (notification)

		/*
			Ouverture de l'activité pour envoyer le presse-papiers.
			inContext : contexte
		*/
		fun sendIntent(inContext: Context): PendingIntent =
			PendingIntent.getActivity(
				inContext, 0,
				Intent(inContext, ClipActivity::class.java).setAction(kActionSend).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
				PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
			)
	}
}

/*
	Tuile de réglages rapides « Envoyer au PC » : un appui envoie le
	presse-papiers au PC (par l'activité transparente, seule autorisée à le
	lire). La tuile dit à quel PC le téléphone est relié.
*/
class ClipTileService : TileService()
{
	override fun onStartListening()
	{
		super.onStartListening()
		val vTile      = qsTile ?: return														// tuile
		val vConnected = bridgeLink.state.value.connected.map { it.label }						// PC reliés
		vTile.state    = if (vConnected.isEmpty()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
		vTile.subtitle = if (vConnected.isEmpty()) "Aucun PC relié" else vConnected.joinToString(", ")
		vTile.updateTile()
	}

	override fun onClick()
	{
		super.onClick()
		startActivityAndCollapse(ClipActivity.sendIntent(this))
	}
}
