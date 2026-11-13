package fr.maubin.pontplus.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.ui.theme.PontColors
import fr.maubin.pontplus.ui.theme.pontType

private val kPill          = RoundedCornerShape(percent = 50)							// forme en pilule
private val kButtonHeight  = 56.dp															// hauteur des boutons

/*
	Échelle des boutons (hauteur, marges, texte) de la partie d'écran qui la
	fournit : 1 par défaut ; 0,8 dans les écrans de travail du téléphone
	(écran étroit, boutons plus compacts).
*/
val LocalButtonScale = staticCompositionLocalOf { 1f }

/*
	Style de texte d'un bouton à l'échelle donnée.
	inStyle : style de base
	inScale : échelle
*/
internal fun scaled(
	inStyle : TextStyle,
	inScale : Float
): TextStyle =
	if (inScale == 1f) inStyle else inStyle.copy(fontSize = inStyle.fontSize * inScale, lineHeight = inStyle.lineHeight * inScale)
private const val kShinePeriod = 6000														// période du reflet (ms)
private const val kShinePass   = 1400														// durée du passage du reflet (ms)

/*
	Bouton principal, un seul par écran : pilule argentée, liseré clair en
	haut, reflet qui la traverse lentement toutes les 6 s.
	inLabel    : texte du bouton
	inOnClick  : action
	inModifier : modificateur
	inEnabled  : bouton actif ; inactif, il est atténué
*/
@Composable
fun SilverButton(
	inLabel    : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier = Modifier,
	inEnabled  : Boolean  = true
)
{
	val vSource  = remember { MutableInteractionSource() }									// appuis
	val vPressed by vSource.collectIsPressedAsState()										// bouton enfoncé
	val vScale   by animateFloatAsState(if (vPressed) 0.98f else 1f)						// léger enfoncement
	val vShine   = if (LocalAnimationsEnabled.current) shineProgress() else -1f						// position du reflet
	val vSize    = LocalButtonScale.current													// échelle des boutons
	Box(
		inModifier
			.fillMaxWidth()
			.height(kButtonHeight * vSize)
			.graphicsLayer { scaleX = vScale; scaleY = vScale; alpha = if (inEnabled) 1f else 0.55f }
			.clip(kPill)
			.drawBehind {
				drawRect(Brush.verticalGradient(listOf(PontColors.silverLight, PontColors.silverDark)))
				drawLine(Color.White.copy(alpha = 0.9f), Offset(size.height / 2f, 0.5f), Offset(size.width - size.height / 2f, 0.5f), 1f)
				if (vShine in 0f..1f)
				{
					val vBand = size.width * 0.35f														// largeur du reflet
					val vX    = -vBand + vShine * (size.width + 2 * vBand)							// position du reflet
					drawRect(Brush.linearGradient(
						0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent,
						start = Offset(vX - vBand / 2f, 0f), end = Offset(vX + vBand / 2f, size.height)
					))
				}
			}
			.clickable(vSource, null, inEnabled, role = Role.Button, onClick = inOnClick),
		contentAlignment = Alignment.Center
	) {
		BasicText(inLabel, style = scaled(pontType().label, vSize).copy(color = PontColors.silverInk))
	}
}

/*
	Bouton secondaire : pilule au contour fin, texte clair.
	inLabel    : texte du bouton
	inOnClick  : action
	inModifier : modificateur
	inEnabled  : bouton actif
*/
@Composable
fun GhostButton(
	inLabel    : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier = Modifier,
	inEnabled  : Boolean  = true
)
{
	val vSize = LocalButtonScale.current														// échelle des boutons
	Box(
		inModifier
			.fillMaxWidth()
			.height(kButtonHeight * vSize)
			.graphicsLayer { alpha = if (inEnabled) 1f else 0.55f }
			.clip(kPill)
			.border(1.dp, PontColors.rule, kPill)
			.clickable(enabled = inEnabled, role = Role.Button, onClick = inOnClick),
		contentAlignment = Alignment.Center
	) {
		BasicText(inLabel, style = scaled(pontType().label, vSize))
	}
}

/*
	Bouton texte discret (lien), pour les actions secondaires.
	inLabel    : texte
	inOnClick  : action
	inModifier : modificateur
	inDanger   : action destructrice (texte en rouge sourd)
*/
@Composable
fun TextLink(
	inLabel    : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier = Modifier,
	inDanger   : Boolean  = false
)
{
	val vSize = LocalButtonScale.current														// échelle des boutons
	BasicText(
		inLabel,
		inModifier.clip(kPill).clickable(role = Role.Button, onClick = inOnClick).padding(horizontal = 12.dp * vSize, vertical = 10.dp * vSize),
		style = scaled(pontType().label, vSize).copy(color = if (inDanger) PontColors.danger else PontColors.textMuted)
	)
}

/*
	Avancée du reflet du bouton principal : de 0 à 1 pendant son passage,
	-1 le reste du temps.
*/
@Composable
private fun shineProgress(): Float
{
	val vTransition = rememberInfiniteTransition()											// animation sans fin
	val vValue by vTransition.animateFloat(
		initialValue  = -1f,
		targetValue   = -1f,
		animationSpec = infiniteRepeatable(
			keyframes {
				durationMillis = kShinePeriod
				-1f at 0
				0f at kShinePeriod - kShinePass using LinearEasing
				1f at kShinePeriod
			},
			RepeatMode.Restart
		)
	)																						// position du reflet
	return vValue
}
