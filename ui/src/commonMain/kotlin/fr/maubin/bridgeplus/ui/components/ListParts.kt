package fr.maubin.bridgeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType

private val kPill = RoundedCornerShape(percent = 50)									// forme en pilule
/*
	Bouton icône rond, zone de toucher de 44 dp.
	inIcon        : icône
	inDescription : description pour l'accessibilité
	inOnClick     : action
	inModifier    : modificateur
	inTint        : couleur de l'icône
*/
@Composable
fun IconAction(
	inIcon        : ImageVector,
	inDescription : String,
	inOnClick     : () -> Unit,
	inModifier    : Modifier = Modifier,
	inTint        : Color    = BridgeColors.textMuted
)
{
	val vSize = LocalButtonScale.current														// échelle des boutons
	Icon(
		inIcon, inDescription,
		inModifier
			.size(44.dp * vSize)
			.clip(CircleShape)
			.clickable(role = Role.Button, onClick = inOnClick)
			.padding(11.dp * vSize),
		tint = inTint
	)
}

/*
	Pastille de choix (réglages, réseaux) : active, fond clair et texte sombre.
	inLabel    : texte
	inSelected : pastille active
	inOnClick  : action
*/
@Composable
fun FilterPill(
	inLabel    : String,
	inSelected : Boolean,
	inOnClick  : () -> Unit
)
{
	val vSize  = LocalButtonScale.current														// échelle des boutons
	val vStyle = scaled(bridgeType().label, vSize)													// style du texte
	BasicText(
		inLabel,
		Modifier
			.clip(kPill)
			.background(if (inSelected) BridgeColors.text else Color.Transparent)
			.border(1.dp, if (inSelected) BridgeColors.text else BridgeColors.rule, kPill)
			.clickable(role = Role.Tab, onClick = inOnClick)
			.padding(horizontal = 16.dp * vSize, vertical = 8.dp * vSize),
		style = vStyle.copy(color = if (inSelected) BridgeColors.void else BridgeColors.text)
	)
}

/*
	Texte sur une ligne, coupé par des points de suspension.
	inText     : texte
	inStyle    : style
	inModifier : modificateur
*/
@Composable
fun OneLine(
	inText     : String,
	inStyle    : TextStyle,
	inModifier : Modifier = Modifier
)
{
	BasicText(inText, inModifier, style = inStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
