package fr.maubin.pontplus.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.ui.theme.Pont

private val kPill = RoundedCornerShape(999.dp)										// forme des boutons

/*
	Bouton principal : pilule pleine, couleur d'accent. Un seul par écran.
	inText     : intitulé
	inOnClick  : action
	inModifier : modificateur
	inIcon     : icône devant l'intitulé ; null sans
	inEnabled  : bouton actif
*/
@Composable
fun PrimaryButton(
	inText     : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier       = Modifier,
	inIcon     : ImageVector?   = null,
	inEnabled  : Boolean        = true
)
{
	val vColors = Pont.colors																// palette
	Button(
		onClick        = inOnClick,
		modifier       = inModifier,
		enabled        = inEnabled,
		shape          = kPill,
		contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
		colors         = ButtonDefaults.buttonColors(containerColor = vColors.accent, contentColor = vColors.onAccent)
	) {
		ButtonContent(inText, inIcon)
	}
}

/*
	Bouton secondaire : pilule à contour.
	inText     : intitulé
	inOnClick  : action
	inModifier : modificateur
	inIcon     : icône devant l'intitulé ; null sans
	inEnabled  : bouton actif
*/
@Composable
fun SecondaryButton(
	inText     : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier       = Modifier,
	inIcon     : ImageVector?   = null,
	inEnabled  : Boolean        = true
)
{
	val vColors = Pont.colors																// palette
	OutlinedButton(
		onClick        = inOnClick,
		modifier       = inModifier,
		enabled        = inEnabled,
		shape          = kPill,
		border         = BorderStroke(1.dp, vColors.rule),
		contentPadding = PaddingValues(horizontal = 20.dp, vertical = 11.dp),
		colors         = ButtonDefaults.outlinedButtonColors(contentColor = vColors.text)
	) {
		ButtonContent(inText, inIcon)
	}
}

/*
	Bouton discret : texte seul (actions d'une ligne, dialogues).
	inText     : intitulé
	inOnClick  : action
	inModifier : modificateur
	inIcon     : icône devant l'intitulé ; null sans
	inDanger   : action destructrice (texte en couleur de danger)
*/
@Composable
fun QuietButton(
	inText     : String,
	inOnClick  : () -> Unit,
	inModifier : Modifier     = Modifier,
	inIcon     : ImageVector? = null,
	inDanger   : Boolean      = false
)
{
	val vColors = Pont.colors																// palette
	TextButton(
		onClick  = inOnClick,
		modifier = inModifier,
		shape    = kPill,
		colors   = ButtonDefaults.textButtonColors(contentColor = if (inDanger) vColors.danger else vColors.text)
	) {
		ButtonContent(inText, inIcon)
	}
}

/*
	Contenu d'un bouton : icône facultative et intitulé.
	inText : intitulé
	inIcon : icône ; null sans
*/
@Composable
private fun ButtonContent(
	inText : String,
	inIcon : ImageVector?
)
{
	Row(verticalAlignment = Alignment.CenterVertically) {
		if (inIcon != null)
		{
			Icon(inIcon, contentDescription = null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
		}
		Text(inText, style = Pont.type.label.copy(color = Color.Unspecified))
	}
}
