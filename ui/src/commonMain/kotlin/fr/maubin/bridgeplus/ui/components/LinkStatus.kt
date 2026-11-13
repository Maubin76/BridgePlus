package fr.maubin.bridgeplus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType

private val kPill = RoundedCornerShape(percent = 50)								// forme en pilule

/*
	Vrai si les animations sont permises. Chaque plateforme le fournit selon
	le réglage du système (Android : « Supprimer les animations » ; Windows :
	« Effets d'animation ») ; sans animation, seuls restent des changements
	instantanés.
*/
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/*
	Allure d'un état de la liaison : toujours une icône et un texte, jamais
	une couleur seule (le monochrome n'en a d'ailleurs presque pas).
	icon : icône
	tint : couleur de l'icône
*/
enum class LinkLook(
	val icon : ImageVector,
	val tint : Color
)
{
	Connected(LineIcons.Linked, BridgeColors.text),										// relié
	Searching(LineIcons.Searching, BridgeColors.textMuted),								// connexion en cours
	Absent(LineIcons.Unlinked, BridgeColors.textMuted),									// appareil injoignable
	Refused(LineIcons.Alert, BridgeColors.danger),										// refusé par l'autre appareil
	AwayFromHome(LineIcons.AwayFromHome, BridgeColors.textMuted),						// hors du réseau de la maison
	Off(LineIcons.Off, BridgeColors.textMuted)											// liaison arrêtée
}

/*
	Pastille d'état (liste des appareils) : relié, fond clair et texte
	sombre comme une pastille active de KeePlus ; sinon, contour fin.
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
	val vLit = inLook == LinkLook.Connected													// pastille allumée
	Row(
		modifier              = inModifier
			.clip(kPill)
			.background(if (vLit) BridgeColors.text else Color.Transparent)
			.border(1.dp, if (vLit) BridgeColors.text else BridgeColors.rule, kPill)
			.padding(horizontal = 10.dp, vertical = 4.dp),
		verticalAlignment     = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp)
	) {
		Icon(inLook.icon, contentDescription = null, tint = if (vLit) BridgeColors.void else inLook.tint, modifier = Modifier.size(14.dp))
		BasicText(inText, style = bridgeType().bodySmall.copy(color = if (vLit) BridgeColors.void else BridgeColors.text))
	}
}

/*
	État de la liaison en grand (accueil) : icône dans un disque, puis titre
	très grand et fin en deux tons, comme les titres de KeePlus (le contexte
	en gris, le mot fort en blanc), et une précision dessous. Le changement
	d'état se fait en fondu, ou d'un coup si les animations sont coupées.
	inLook     : état
	inMuted    : début du titre, en gris (« Relié à ») ; vide sans
	inStrong   : fin du titre, en blanc (« Bureau. »)
	inDetail   : précision ; null sans
	inModifier : modificateur
*/
@Composable
fun LinkHero(
	inLook     : LinkLook,
	inMuted    : String,
	inStrong   : String,
	inDetail   : String?,
	inModifier : Modifier = Modifier
)
{
	val vAnimate = LocalAnimationsEnabled.current											// animations permises
	val vType    = bridgeType()																// styles de texte
	AnimatedContent(
		targetState    = listOf(inLook.name, inMuted, inStrong, inDetail.orEmpty()),
		modifier       = inModifier,
		transitionSpec = { if (vAnimate) fadeIn(tween(220)) togetherWith fadeOut(tween(220)) else fadeIn(snap()) togetherWith fadeOut(snap()) },
		label          = "état de la liaison"
	) { vState ->
		val vLook = LinkLook.valueOf(vState[0])														// état affiché
		Column(
			Modifier.semantics(mergeDescendants = true) { contentDescription = listOf(vState[1] + vState[2], vState[3]).filter { it.isNotBlank() }.joinToString(". ") },
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			Icon(
				vLook.icon,
				contentDescription = null,
				tint               = vLook.tint,
				modifier           = Modifier.size(52.dp).clip(CircleShape).background(BridgeColors.surfaceHigh).border(1.dp, BridgeColors.rule, CircleShape).padding(14.dp)
			)
			BasicText(
				buildAnnotatedString {
					withStyle(SpanStyle(color = BridgeColors.textMuted)) { append(vState[1]) }
					append(vState[2])
				},
				style = vType.hero
			)
			if (vState[3].isNotEmpty()) BasicText(vState[3], style = vType.bodySmall)
		}
	}
}
