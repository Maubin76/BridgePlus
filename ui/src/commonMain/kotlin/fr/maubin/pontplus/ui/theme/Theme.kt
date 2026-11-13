package fr.maubin.pontplus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.maubin.pontplus.ui.resources.Res
import fr.maubin.pontplus.ui.resources.satoshi_bold
import fr.maubin.pontplus.ui.resources.satoshi_light
import fr.maubin.pontplus.ui.resources.satoshi_medium
import fr.maubin.pontplus.ui.resources.satoshi_regular
import org.jetbrains.compose.resources.Font

/*
	Couleurs de PontPlus (voir DESIGN.md) : fonds neutres légèrement chauds,
	un seul accent, l'orange international du Golden Gate, et deux couleurs
	d'état. Une palette claire et une sombre, choisies selon le système.
	ground      : fond des écrans
	surface     : cartes, champs
	surfaceHigh : pastilles, survol, cartes en relief
	rule        : contours, séparateurs
	text        : texte principal
	textMuted   : texte secondaire (au moins 4.5:1 sur ground, surface et surfaceHigh)
	textFaint   : indications non essentielles
	accent      : accent (boutons principaux, liaison établie, logo)
	onAccent    : texte sur l'accent
	accentSoft  : fond discret teinté d'accent (pastille de l'état relié)
	success     : réussite
	danger      : erreur, suppression
	isDark      : vrai pour la palette sombre
*/
@Immutable
data class PontColors(
	val ground      : Color,
	val surface     : Color,
	val surfaceHigh : Color,
	val rule        : Color,
	val text        : Color,
	val textMuted   : Color,
	val textFaint   : Color,
	val accent      : Color,
	val onAccent    : Color,
	val accentSoft  : Color,
	val success     : Color,
	val danger      : Color,
	val isDark      : Boolean
)

// Palette claire.
val kLightColors = PontColors(
	ground      = Color(0xFFFAF8F6),
	surface     = Color(0xFFFFFFFF),
	surfaceHigh = Color(0xFFF2EEEA),
	rule        = Color(0xFFE6E0DA),
	text        = Color(0xFF1C1816),
	textMuted   = Color(0xFF5F5751),
	textFaint   = Color(0xFF7D746D),
	accent      = Color(0xFFB8401F),
	onAccent    = Color(0xFFFFFFFF),
	accentSoft  = Color(0xFFF8E4DB),
	success     = Color(0xFF2D7A57),
	danger      = Color(0xFFB3261E),
	isDark      = false
)

// Palette sombre.
val kDarkColors = PontColors(
	ground      = Color(0xFF121010),
	surface     = Color(0xFF1C1917),
	surfaceHigh = Color(0xFF262220),
	rule        = Color(0xFF36312E),
	text        = Color(0xFFF3EFEC),
	textMuted   = Color(0xFFADA49E),
	textFaint   = Color(0xFF8C847E),
	accent      = Color(0xFFFF8A5C),
	onAccent    = Color(0xFF2A0E04),
	accentSoft  = Color(0xFF3B2219),
	success     = Color(0xFF6FC59A),
	danger      = Color(0xFFF2827A),
	isDark      = true
)

/*
	Styles de texte de PontPlus (voir DESIGN.md) ; les noms Material 3 servent
	de correspondance pour les composants standard.
	hero      : très grand état de la liaison
	display   : grand titre d'écran
	headline  : titre de section
	title     : titre d'élément (appareil, carte)
	body      : texte courant
	bodySmall : texte secondaire
	label     : boutons, pastilles
*/
@Immutable
data class PontType(
	val hero      : TextStyle,
	val display   : TextStyle,
	val headline  : TextStyle,
	val title     : TextStyle,
	val body      : TextStyle,
	val bodySmall : TextStyle,
	val label     : TextStyle
)

private val LocalPontColors = staticCompositionLocalOf { kLightColors }			// palette en cours
private val LocalPontType   = staticCompositionLocalOf<PontType?> { null }		// styles de texte en cours

/*
	Accès au thème de PontPlus depuis les composants.
*/
object Pont
{
	// Palette en cours.
	val colors: PontColors
		@Composable @ReadOnlyComposable get() = LocalPontColors.current

	// Styles de texte en cours.
	val type: PontType
		@Composable @ReadOnlyComposable get() = LocalPontType.current ?: error("PontPlusTheme absent")
}

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
	Styles de texte, construits avec la police chargée et la palette.
	inColors : palette
*/
@Composable
fun pontType(inColors: PontColors): PontType
{
	val vSans = satoshi()																	// Satoshi
	return PontType(
		hero      = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.025).em, color = inColors.text),
		display   = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em, color = inColors.text),
		headline  = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em, color = inColors.text),
		title     = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = inColors.text),
		body      = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = inColors.text),
		bodySmall = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.005.em, color = inColors.textMuted),
		label     = TextStyle(fontFamily = vSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.01.em, color = inColors.text)
	)
}

/*
	Thème de PontPlus, clair ou sombre selon le système : palette et styles de
	la direction artistique, et Material 3 accordé pour les composants
	standard (champs, menus, dialogues).
	inDark    : palette sombre
	inContent : contenu
*/
@Composable
fun PontPlusTheme(
	inDark    : Boolean = isSystemInDarkTheme(),
	inContent : @Composable () -> Unit
)
{
	val vColors = if (inDark) kDarkColors else kLightColors								// palette
	val vType   = pontType(vColors)														// styles de texte
	val vScheme = if (inDark) darkColorScheme(
		primary              = vColors.accent,
		onPrimary            = vColors.onAccent,
		secondary            = vColors.textMuted,
		background           = vColors.ground,
		onBackground         = vColors.text,
		surface              = vColors.surface,
		onSurface            = vColors.text,
		surfaceVariant       = vColors.surfaceHigh,
		onSurfaceVariant     = vColors.textMuted,
		surfaceContainer     = vColors.surface,
		surfaceContainerHigh = vColors.surfaceHigh,
		outline              = vColors.textMuted,
		outlineVariant       = vColors.rule,
		error                = vColors.danger
	) else lightColorScheme(
		primary              = vColors.accent,
		onPrimary            = vColors.onAccent,
		secondary            = vColors.textMuted,
		background           = vColors.ground,
		onBackground         = vColors.text,
		surface              = vColors.surface,
		onSurface            = vColors.text,
		surfaceVariant       = vColors.surfaceHigh,
		onSurfaceVariant     = vColors.textMuted,
		surfaceContainer     = vColors.surface,
		surfaceContainerHigh = vColors.surfaceHigh,
		outline              = vColors.textMuted,
		outlineVariant       = vColors.rule,
		error                = vColors.danger
	)																						// couleurs Material 3
	val vMaterial = Typography(
		displayLarge   = vType.hero,
		displayMedium  = vType.display,
		headlineMedium = vType.headline,
		titleLarge     = vType.headline,
		titleMedium    = vType.title,
		bodyLarge      = vType.body,
		bodyMedium     = vType.body,
		bodySmall      = vType.bodySmall,
		labelLarge     = vType.label
	)																						// polices Material 3
	CompositionLocalProvider(LocalPontColors provides vColors, LocalPontType provides vType) {
		MaterialTheme(colorScheme = vScheme, typography = vMaterial, content = inContent)
	}
}
