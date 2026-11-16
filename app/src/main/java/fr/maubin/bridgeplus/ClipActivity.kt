package fr.maubin.bridgeplus

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.maubin.bridgeplus.link.isWebLink
import fr.maubin.bridgeplus.peers.PairedPc
import fr.maubin.bridgeplus.ui.theme.BridgePlusTheme

/*
	Activité transparente des gestes explicites, qui ne montre rien d'autre
	qu'un court message (et, s'il faut choisir le PC, un petit dialogue) :
	- « Envoyer au PC » de la tuile : lit le presse-papiers et l'envoie ;
	- « Envoyer au PC » du menu de partage : ouvre un lien dans le navigateur
	  du PC, envoie un autre texte (presse-papiers du PC), ou un
	  ou plusieurs fichiers (photos, vidéos, documents), portés ensuite par
	  le service ;
	- « Copier » de la notification d'un texte reçu : le place dans le
	  presse-papiers.
	Android ne laisse lire le presse-papiers qu'à l'appli au premier plan :
	l'activité attend d'avoir le focus, lit, envoie (sur un fil à part),
	puis se ferme.
*/
class ClipActivity : ComponentActivity()
{
	private var fDone = false																// geste déjà traité

	override fun onWindowFocusChanged(inFocus: Boolean)
	{
		super.onWindowFocusChanged(inFocus)
		if (!inFocus || fDone) return
		fDone = true
		val vUris = sharedUris()																// fichiers partagés
		when
		{
			intent?.action == kActionCopy -> done(copyWaiting())
			vUris.isNotEmpty()            -> sendShared(vUris)
			intent?.action == Intent.ACTION_SEND && isWebLink(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()) -> bridgeApp.sendLink(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), ::done)
			intent?.action == Intent.ACTION_SEND -> bridgeApp.sendClip(intent.getStringExtra(Intent.EXTRA_TEXT), false, true, ::done)
			else                          -> readClipboard(this).let { bridgeApp.sendClip(it.text, it.sensitive, true, ::done) }
		}
	}

	/*
		Adresses des fichiers partagés (un ou plusieurs) ; vide pour un texte.
	*/
	private fun sharedUris(): List<Uri> =
		when (intent?.action)
		{
			Intent.ACTION_SEND          -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
			Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
			else                        -> emptyList()
		}

	/*
		Envoie les fichiers partagés : ouverts tout de suite (l'autorisation
		de lecture cesse avec l'activité), puis au seul PC relié, ou à celui
		choisi s'il y en a plusieurs.
		inUris : adresses des fichiers
	*/
	private fun sendShared(inUris: List<Uri>)
	{
		val vPcs = bridgeApp.connectedPcs()														// PC reliés
		if (vPcs.isEmpty()) return done("Aucun PC relié : les fichiers ne sont pas partis.")
		Thread({
			val vSources = openSources(this, inUris)												// fichiers ouverts
			runOnUiThread {
				when
				{
					vSources.isEmpty() -> done("Impossible de lire ces fichiers.")
					vPcs.size == 1     -> send(vPcs.single(), vSources)
					else               -> setContent {
						BridgePlusTheme {
							PcChoiceDialog(vPcs, vSources.size, { vPc -> send(vPc, vSources) }, { vSources.forEach { runCatching { it.open().close() } }; done(null) })
						}
					}
				}
			}
		}, "BridgePlus-partage").start()
	}

	/*
		Confie les fichiers au moteur des transferts, puis ferme l'activité.
		inPc      : PC destinataire
		inSources : fichiers ouverts
	*/
	private fun send(
		inPc      : PairedPc,
		inSources : List<OpenedSource>
	)
	{
		bridgeApp.sendFiles(inPc, inSources)
		done("Envoi à ${inPc.label} : ${if (inSources.size == 1) inSources.single().name else "${inSources.size} fichiers"}")
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
