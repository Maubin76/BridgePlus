package fr.maubin.pontplus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont
import fr.maubin.pontplus.ui.theme.PontColors

/*
	Vrai si les animations sont permises. Chaque plateforme le fournit selon
	le réglage du système (Android : « Supprimer les animations » ; Windows :
	« Afficher les animations ») ; sans animation, les changements sont
	instantanés.
*/
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/*
	Allure d'un état de la liaison : toujours une icône, un texte et une
	couleur, jamais la couleur seule.
	icon : icône
	tint : couleur de l'icône (selon la palette)
*/
enum class LinkLook(
	val icon : ImageVector,
	val tint : (PontColors) -> Color
)
{
	Connected(LineIcons.Linked, { it.accent }),										// relié
	Searching(LineIcons.Searching, { it.textMuted }),									// connexion en cours
	Absent(LineIcons.Unlinked, { it.textMuted }),										// appareil injoignable
	Refused(LineIcons.Alert, { it.danger }),											// refusé par l'autre appareil
	AwayFromHome(LineIcons.AwayFromHome, { it.textMuted }),							// hors du réseau de la maison
	Off(LineIcons.Off, { it.textMuted })												// liaison arrêtée
}

/*
	Pastille d'état : icône et texte courts (liste des appareils).
	inLook     : état
	inText     : texte (« Relié », « Absent »…)
	inModifier : modificateur
*/
@Composable
fun StatusPill(
	inLook     : LinkLook,
	inText     : String,
	inModifier : Modifier = Modifier
)
{
	val vColors = Pont.colors																// palette
	val vBack   = if (inLook == LinkLook.Connected) vColors.accentSoft else vColors.surfaceHigh	// fond
	Row(
		modifier              = inModifier.background(vBack, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
		verticalAlignment     = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Icon(inLook.icon, contentDescription = null, tint = inLook.tint(vColors), modifier = Modifier.size(16.dp))
		Text(inText, style = Pont.type.bodySmall.copy(color = vColors.text))
	}
}

/*
	État de la liaison en grand (haut de l'accueil) : icône dans un disque,
	titre en très grand, précision dessous. Le changement d'état se fait en
	fondu, ou d'un coup si les animations sont coupées.
	inLook     : état
	inTitle    : titre (« Relié à Bureau »)
	inDetail   : précision ; null sans
	inModifier : modificateur
*/
@Composable
fun LinkHero(
	inLook     : LinkLook,
	inTitle    : String,
	inDetail   : String?,
	inModifier : Modifier = Modifier
)
{
	val vColors  = Pont.colors																// palette
	val vAnimate = LocalAnimationsEnabled.current											// animations permises
	AnimatedContent(
		targetState    = Triple(inLook, inTitle, inDetail),
		modifier       = inModifier,
		transitionSpec = { if (vAnimate) fadeIn() togetherWith fadeOut() else fadeIn(snap()) togetherWith fadeOut(snap()) },
		label          = "état de la liaison"
	) { (vLook, vTitle, vDetail) ->
		Column(Modifier.semantics(mergeDescendants = true) { contentDescription = listOfNotNull(vTitle, vDetail).joinToString(". ") }) {
			val vRing = if (vLook == LinkLook.Connected) vColors.accentSoft else vColors.surfaceHigh	// disque de l'icône
			Icon(
				vLook.icon,
				contentDescription = null,
				tint               = vLook.tint(vColors),
				modifier           = Modifier.size(56.dp).background(vRing, CircleShape).padding(14.dp)
			)
			Spacer(Modifier.height(20.dp))
			Text(vTitle, style = Pont.type.hero)
			if (vDetail != null)
			{
				Spacer(Modifier.height(8.dp))
				Text(vDetail, style = Pont.type.body.copy(color = vColors.textMuted))
			}
		}
	}
}
