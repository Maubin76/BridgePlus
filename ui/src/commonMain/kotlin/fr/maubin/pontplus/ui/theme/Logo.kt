package fr.maubin.pontplus.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
	Tracé du logo sur une grille de 108 (celle des icônes adaptatives
	d'Android, zone sûre de 21 à 87) : un tablier de pont posé sur deux piles,
	l'arche ouverte dessous, évidé d'un « + ». Rempli en pair-impair. Le même
	tracé sert à l'icône Android (res/drawable) et à l'icône Windows.
*/
const val kLogoPath =
	"M22,47 H86 V53 H22 Z " +
	"M28,81 V79 A26,26 0 0 1 80,79 V81 H73 V79 A19,19 0 0 0 35,79 V81 Z " +
	"M51.5,27 H56.5 V31.5 H61 V36.5 H56.5 V41 H51.5 V36.5 H47 V31.5 H51.5 Z"

const val kLogoGrid   = 108f															// taille de la grille du tracé
const val kLogoMargin = 15f															// début du cadre du logo dans la grille (cadre de 15 à 93)

/*
	Logo de PontPlus : le pont blanc dans un carré arrondi de la couleur
	d'accent.
	inSize     : côté du logo
	inModifier : modificateur
*/
@Composable
fun PontLogo(
	inSize     : Dp       = 32.dp,
	inModifier : Modifier = Modifier
)
{
	val vColors = Pont.colors																// palette
	val vPath   = remember { logoPath() }													// tracé
	Canvas(inModifier.size(inSize)) {
		drawRoundRect(vColors.accent, cornerRadius = CornerRadius(size.minDimension * 0.24f))
		val vScale = size.minDimension / (kLogoGrid - 2 * kLogoMargin)							// cadre du logo étendu à tout le carré
		translate(-kLogoMargin * vScale, -kLogoMargin * vScale) {
			scale(vScale, vScale, pivot = Offset.Zero) {
				drawPath(vPath, if (vColors.isDark) vColors.onAccent else Color.White)
			}
		}
	}
}

/*
	Tracé du logo, prêt à dessiner (grille de 108).
*/
fun logoPath(): Path = PathParser().parsePathString(kLogoPath).toPath().apply { fillType = PathFillType.EvenOdd }
