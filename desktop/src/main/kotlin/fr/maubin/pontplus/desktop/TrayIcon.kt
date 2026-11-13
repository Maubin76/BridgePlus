package fr.maubin.pontplus.desktop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import fr.maubin.pontplus.ui.components.LinkLook
import fr.maubin.pontplus.ui.theme.kLightColors
import fr.maubin.pontplus.ui.theme.kLogoGrid
import fr.maubin.pontplus.ui.theme.kLogoMargin
import fr.maubin.pontplus.ui.theme.logoPath

private val kGrey   = Color(0xFF7D746D)											// plaque du logo quand la liaison n'est pas établie
private val kBadge  = Color(0xFFB3261E)											// pastille d'alerte

/*
	Logo dessiné en image : le pont blanc dans le carré arrondi, de la couleur
	d'accent si la liaison est établie, gris sinon. Une marque s'ajoute selon
	l'état, pour que la forme change et pas seulement la couleur : barre
	oblique hors de la maison, anneau vide pour un téléphone absent, pastille
	rouge pour une erreur, rien quand tout va bien.
	inLook : état de la liaison ; null pour le logo seul (fenêtre, paquet)
	inSize : côté de l'image (pixels)
*/
fun logoImage(
	inLook : LinkLook?,
	inSize : Int
): ImageBitmap
{
	val vImage = ImageBitmap(inSize, inSize)													// image
	val vSide  = inSize.toFloat()																// côté
	val vPath  = logoPath()																		// tracé du pont
	CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(vImage), Size(vSide, vSide)) {
		val vLit   = inLook == null || inLook == LinkLook.Connected								// logo en couleur
		drawRoundRect(if (vLit) kLightColors.accent else kGrey, cornerRadius = CornerRadius(vSide * 0.22f))
		val vScale = vSide / (kLogoGrid - 2 * kLogoMargin)											// cadre du logo sur tout le carré
		translate(-kLogoMargin * vScale, -kLogoMargin * vScale) {
			scale(vScale, vScale, pivot = Offset.Zero) { drawPath(vPath, Color.White) }
		}
		val vStroke = (vSide / 9f).coerceAtLeast(2f)												// épaisseur des marques
		when (inLook)
		{
			LinkLook.AwayFromHome -> drawLine(Color.White, Offset(vSide * 0.12f, vSide * 0.12f), Offset(vSide * 0.88f, vSide * 0.88f), vStroke, StrokeCap.Round)
			LinkLook.Absent, LinkLook.Searching, LinkLook.Off ->
			{
				val vRadius = vSide * 0.2f																// rayon de l'anneau
				drawCircle(Color.White, vRadius, Offset(vSide - vRadius - 1f, vSide - vRadius - 1f))
				drawCircle(kGrey, vRadius, Offset(vSide - vRadius - 1f, vSide - vRadius - 1f), style = Stroke(vStroke * 0.7f))
			}
			LinkLook.Refused      ->
			{
				val vRadius = vSide * 0.22f																// rayon de la pastille
				drawCircle(Color.White, vRadius + 1f, Offset(vSide - vRadius - 1f, vSide - vRadius - 1f))
				drawCircle(kBadge, vRadius, Offset(vSide - vRadius - 1f, vSide - vRadius - 1f))
			}
			else                  -> Unit
		}
	}
	return vImage
}
