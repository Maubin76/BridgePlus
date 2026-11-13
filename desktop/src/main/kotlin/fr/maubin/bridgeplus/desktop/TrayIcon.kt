package fr.maubin.bridgeplus.desktop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import fr.maubin.bridgeplus.ui.components.LinkLook
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.drawLogo

private val kVeil  = Color(0x8C0B0B0C)												// voile sur le logo quand la liaison n'est pas établie
private val kInk   = Color(0xFF0B0B0C)												// encre des marques

/*
	Icône de la zone de notification : le logo argent de BridgePlus, net quand
	la liaison est établie, voilé sinon. Une marque s'ajoute selon l'état,
	pour que la forme change et pas seulement la teinte : barre oblique hors
	de la maison, anneau pour un téléphone absent, pastille rouge pour une
	erreur, rien quand tout va bien.
	inLook : état de la liaison
	inSize : côté de l'image (pixels)
*/
fun trayImage(
	inLook : LinkLook,
	inSize : Int
): ImageBitmap
{
	val vImage = ImageBitmap(inSize, inSize)													// image
	val vSide  = inSize.toFloat()																// côté
	CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(vImage), Size(vSide, vSide)) {
		drawLogo(vSide, inMaterial = inSize >= 48)
		if (inLook != LinkLook.Connected) drawRoundRect(kVeil, cornerRadius = CornerRadius(vSide * 0.26f))
		val vStroke = (vSide / 9f).coerceAtLeast(2f)												// épaisseur des marques
		when (inLook)
		{
			LinkLook.AwayFromHome -> drawLine(BridgeColors.text, Offset(vSide * 0.14f, vSide * 0.14f), Offset(vSide * 0.86f, vSide * 0.86f), vStroke, StrokeCap.Round)
			LinkLook.Absent, LinkLook.Searching, LinkLook.Off ->
			{
				val vRadius = vSide * 0.2f																// rayon de l'anneau
				val vCenter = Offset(vSide - vRadius - 1f, vSide - vRadius - 1f)						// centre de l'anneau
				drawCircle(kInk, vRadius, vCenter)
				drawCircle(BridgeColors.text, vRadius - vStroke * 0.5f, vCenter, style = Stroke(vStroke * 0.7f))
			}
			LinkLook.Refused      ->
			{
				val vRadius = vSide * 0.22f																// rayon de la pastille
				val vCenter = Offset(vSide - vRadius - 1f, vSide - vRadius - 1f)						// centre de la pastille
				drawCircle(kInk, vRadius + 1f, vCenter)
				drawCircle(BridgeColors.danger, vRadius, vCenter)
			}
			LinkLook.Connected    -> Unit
		}
	}
	return vImage
}
