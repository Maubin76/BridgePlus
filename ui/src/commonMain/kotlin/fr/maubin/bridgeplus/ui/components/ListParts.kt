package fr.maubin.bridgeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import fr.maubin.bridgeplus.ui.icons.LineIcons
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
	Barre de recherche : pilule, loupe à gauche, croix pour vider.
	inState       : texte cherché
	inModifier    : modificateur
	inPlaceholder : indication
*/
@Composable
fun SearchField(
	inState       : TextFieldState,
	inModifier    : Modifier = Modifier,
	inPlaceholder : String   = "Rechercher"
)
{
	BasicTextField(
		state       = inState,
		modifier    = inModifier.fillMaxWidth(),
		textStyle   = bridgeType().body,
		cursorBrush = SolidColor(BridgeColors.text),
		lineLimits  = TextFieldLineLimits.SingleLine,
		decorator   = { vInner ->
			Row(
				Modifier.fillMaxWidth().height(48.dp).clip(kPill).background(BridgeColors.surfaceHigh).padding(start = 14.dp, end = 4.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(LineIcons.Search, null, Modifier.size(20.dp), tint = BridgeColors.textMuted)
				Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
					if (inState.text.isEmpty()) BasicText(inPlaceholder, style = bridgeType().body.copy(color = BridgeColors.textFaint))
					vInner()
				}
				if (inState.text.isNotEmpty()) IconAction(LineIcons.Close, "Effacer la recherche", { inState.clearText() })
			}
		}
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
