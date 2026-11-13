package fr.maubin.bridgeplus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.maubin.bridgeplus.ui.resources.Res
import fr.maubin.bridgeplus.ui.resources.satoshi_bold
import fr.maubin.bridgeplus.ui.resources.satoshi_light
import fr.maubin.bridgeplus.ui.resources.satoshi_medium
import fr.maubin.bridgeplus.ui.resources.satoshi_regular
import org.jetbrains.compose.resources.Font

/*
	Couleurs de BridgePlus, celles de KeePlus (voir DESIGN.md) : monochrome
	noir et argent, deux couleurs d'état.
*/
@Immutable
object BridgeColors
{
	val void           = Color(0xFF050505)											// bas de la brume, fond le plus profond
	val ground         = Color(0xFF0B0B0C)											// fond des écrans de travail
	val mistTop        = Color(0xFF9A9A9C)											// haut de la brume
	val mistMid        = Color(0xFF3A3A3C)											// milieu de la brume
	val surface        = Color(0xFF141415)											// cartes, champs
	val surfaceHigh    = Color(0xFF1C1C1E)											// dialogues, icônes d'appareil
	val surfaceHighest = Color(0xFF262628)											// pastille active, survol
	val rule           = Color(0xFF2A2A2C)											// contours, séparateurs
	val gridLine       = Color(0x0FFFFFFF)											// trait de la grille
	val gridLineLit    = Color(0x2EFFFFFF)											// trait de la grille illuminée
	val emblem         = Color(0xFF2E2E30)											// grand pont flou du décor
	val text           = Color(0xFFF5F5F5)											// texte principal
	val textMuted      = Color(0xFF9C9C9E)											// texte secondaire, mot gris des titres
	val textFaint      = Color(0xFF6A6A6C)											// indications non essentielles
	val silverLight    = Color(0xFFF2F2F2)											// haut du bouton argent
	val silverDark     = Color(0xFFB8B8BA)											// bas du bouton argent
	val silverInk      = Color(0xFF111111)											// texte sur l'argent
	val danger         = Color(0xFFD9645F)											// erreur, refus, oubli
	val success        = Color(0xFF6FB592)											// appairage réussi
}

/*
	Styles de texte de BridgePlus, ceux de KeePlus (voir DESIGN.md) ; les noms
	Material 3 servent de correspondance pour les composants standard.
	hero      : très grand titre fin des écrans clés (état de la liaison)
	display   : grand titre
	headline  : titre d'écran de travail, de dialogue
	title     : titre d'élément (appareil, carte)
	body      : texte courant
	bodySmall : texte secondaire
	label     : boutons, pastilles
*/
@Immutable
data class BridgeType(
	val hero      : TextStyle,
	val display   : TextStyle,
	val headline  : TextStyle,
	val title     : TextStyle,
	val body      : TextStyle,
	val bodySmall : TextStyle,
	val label     : TextStyle
)

/*
	Police Satoshi, dans les graisses utilisées.
*/
@Composable
fun satoshi(): FontFamily = FontFamily(
	Font(Res.font.satoshi_light, FontWeight.Light),
	Font(Res.font.satoshi_regular, FontWeight.Normal),
	Font(Res.font.satoshi_medium, FontWeight.Medium),
	Font(Res.font.satoshi_bold, FontWeight.Bold)
)

/*
	Styles de texte, construits avec la police chargée.
*/
@Composable
fun bridgeType(): BridgeType
{
	val vSans = satoshi()																	// Satoshi
	return BridgeType(
		hero      = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.025).em, color = BridgeColors.text),
		display   = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em, color = BridgeColors.text),
		headline  = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.01).em, color = BridgeColors.text),
		title     = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, color = BridgeColors.text),
		body      = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = BridgeColors.text),
		bodySmall = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.005.em, color = BridgeColors.textMuted),
		label     = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em, color = BridgeColors.text)
	)
}

/*
	Thème de BridgePlus, sombre uniquement comme KeePlus : Material 3 aux
	couleurs et polices de la direction artistique, pour les composants
	standard (champs, menus).
	inContent : contenu
*/
@Composable
fun BridgePlusTheme(inContent: @Composable () -> Unit)
{
	val vType   = bridgeType()																// styles de BridgePlus
	val vColors = darkColorScheme(
		primary              = BridgeColors.text,
		onPrimary            = BridgeColors.void,
		secondary            = BridgeColors.textMuted,
		onSecondary          = BridgeColors.void,
		background           = BridgeColors.ground,
		onBackground         = BridgeColors.text,
		surface              = BridgeColors.surface,
		onSurface            = BridgeColors.text,
		surfaceVariant       = BridgeColors.surfaceHigh,
		onSurfaceVariant     = BridgeColors.textMuted,
		surfaceContainer     = BridgeColors.surface,
		surfaceContainerHigh = BridgeColors.surfaceHigh,
		outline              = BridgeColors.textMuted,
		outlineVariant       = BridgeColors.rule,
		error                = BridgeColors.danger,
		onError              = BridgeColors.void
	)																						// couleurs Material 3
	val vMaterial = Typography(
		displayLarge   = vType.hero,
		displayMedium  = vType.display,
		headlineMedium = vType.headline,
		titleMedium    = vType.title,
		bodyLarge      = vType.body,
		bodyMedium     = vType.body,
		bodySmall      = vType.bodySmall,
		labelLarge     = vType.label
	)																						// polices Material 3
	MaterialTheme(colorScheme = vColors, typography = vMaterial, content = inContent)
}
