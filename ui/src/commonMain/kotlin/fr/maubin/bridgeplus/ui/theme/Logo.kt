package fr.maubin.bridgeplus.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/*
	Logo de BridgePlus, dans la matière de l'appli : une tuile noire où la
	brume grise descend du haut sur une grille fine (le décor des écrans
	clés), et, au trait arrondi comme les icônes, le téléphone en gris, deux
	points de liaison, et l'écran du PC en blanc (deux tons, comme les
	titres). Le trait s'épaissit aux petites tailles pour rester visible.
	La tuile est décrite dans un carré de 100 unités ; l'emblème, dans son
	propre carré de 100 unités (repère de l'emblème), posé dans la tuile.
*/

private const val kTileRadius = 23.2f														// arrondi de la tuile (unités)
private const val kMarkLeft   = 11.6f														// position du repère de l'emblème dans la tuile (unités)
private const val kMarkTop    = 9.3f
private const val kMarkScale  = .768f														// côté du repère de l'emblème / côté de la tuile
private const val kGridStep   = 10.7f														// pas de la grille (unités)

const val kPhoneData   = "M15,27 H23 A5,5 0 0 1 28,32 V66 A5,5 0 0 1 23,71 H15 A5,5 0 0 1 10,66 V32 A5,5 0 0 1 15,27 Z"	// téléphone (repère de l'emblème)
const val kScreenData  = "M54,27 H86 A4,4 0 0 1 90,31 V57 A4,4 0 0 1 86,61 H54 A4,4 0 0 1 50,57 V31 A4,4 0 0 1 54,27 Z M63,73 H77 M70,61 V73"	// écran du PC et son pied
val kLinkDots          = listOf(Offset(35.5f, 49f), Offset(42.5f, 49f))						// points de la liaison (repère de l'emblème)
const val kDotRadius   = 2.8f																// rayon maximal d'un point (repère de l'emblème)

private val kMistTop   = Color(0xFF6E6E71)													// brume, en haut de la tuile
private val kMistMid   = Color(0xFF2E2E30)													// brume, vers 40 %

/*
	Épaisseur du trait de l'emblème selon la taille du logo (taille optique),
	dans le repère de l'emblème : fin en grand, plus épais en petit pour
	garder au moins un pixel.
	inSide : côté du logo (pixels)
*/
fun markStroke(inSide: Float): Float = when
{
	inSide >= 96f -> 5f
	inSide >= 48f -> 6.5f
	inSide >= 24f -> 8.5f
	else          -> 10.5f
}

/*
	Nombre au format des tracés SVG.
	inValue : nombre
*/
private fun n(inValue: Double): String = ((inValue * 100).roundToLong() / 100.0).toString()

/*
	Tracé SVG du pont du décor dans un repère carré : tablier sur toute la
	largeur, arche dessous (bande de la largeur du repère, ouverte en bas).
	Rempli en pair-impair (les parties ne se chevauchent pas).
	inX, inY : coin du repère
	inS      : côté du repère
*/
fun bridgeData(
	inX : Double,
	inY : Double,
	inS : Double
): String
{
	fun p(inRx: Double, inRy: Double) = "${n(inX + inRx * inS)},${n(inY + inRy * inS)}"
	val vOuter = n(.40625 * inS)																// rayon extérieur de l'arche
	val vInner = n(.296875 * inS)															// rayon intérieur de l'arche
	return "M${p(0.0, .390625)} L${p(1.0, .390625)} L${p(1.0, .484375)} L${p(0.0, .484375)} Z " +
		"M${p(.09375, .921875)} L${p(.09375, .890625)} A$vOuter,$vOuter 0 0 1 ${p(.90625, .890625)} L${p(.90625, .921875)} " +
		"L${p(.796875, .921875)} L${p(.796875, .890625)} A$vInner,$vInner 0 0 0 ${p(.203125, .890625)} L${p(.203125, .921875)} Z"
}

/*
	Tracé Compose d'un tracé SVG.
	inData : tracé SVG
*/
private fun path(inData: String): Path = PathParser().parsePathString(inData).toPath()

/*
	Pont du décor, dans un carré de côté inSize : sert au grand décor flou
	des écrans clés.
	inSize   : côté du carré, en pixels
	inOrigin : coin haut gauche du carré
*/
fun bridgePath(
	inSize   : Float,
	inOrigin : Offset = Offset.Zero
): Path = path(bridgeData(inOrigin.x.toDouble(), inOrigin.y.toDouble(), inSize.toDouble())).apply { fillType = PathFillType.EvenOdd }

/*
	Fond de la tuile sur toute la zone de dessin : brume grise en haut, noir
	à partir des deux tiers, grille fine estompée vers le bas (fond de
	l'icône adaptative Android, tuile du logo).
	inUnit : pixels par unité de la tuile ; par défaut, la zone est une
	         tuile de 100 unités
	inGrid : grille dessinée (invisible et brouillonne en très petit)
*/
fun DrawScope.drawLogoGround(
	inUnit : Float   = minOf(size.width, size.height) / 100f,
	inGrid : Boolean = true
)
{
	drawRect(Brush.verticalGradient(0f to kMistTop, .42f to kMistMid, .72f to BridgeColors.void, 1f to BridgeColors.void))
	if (!inGrid) return
	val vStep  = kGridStep * inUnit																// pas de la grille
	val vLine  = (.45f * inUnit).coerceAtLeast(.5f)												// épaisseur des traits
	val vFade  = Brush.verticalGradient(0f to BridgeColors.gridLine, .55f to BridgeColors.gridLine, .9f to Color.Transparent)	// traits estompés vers le bas
	var vAt    = vStep																			// position du trait
	while (vAt < maxOf(size.width, size.height))
	{
		drawLine(vFade, Offset(vAt, 0f), Offset(vAt, size.height), vLine)
		drawLine(vFade, Offset(0f, vAt), Offset(size.width, vAt), vLine)
		vAt += vStep
	}
}

/*
	Emblème du logo (téléphone, liaison, écran du PC), au trait, dans le
	repère d'un logo de inSide pixels décalé de inOffset.
	inSide   : côté du logo (pixels)
	inOffset : position du coin du logo
	inMuted  : couleur du téléphone et de la liaison
	inText   : couleur de l'écran du PC
	inWidth  : épaisseur du trait (repère de l'emblème) ; par défaut, selon
	           la taille
*/
fun DrawScope.drawLogoMark(
	inSide   : Float,
	inOffset : Offset = Offset.Zero,
	inMuted  : Color  = BridgeColors.textMuted,
	inText   : Color  = BridgeColors.text,
	inWidth  : Float  = markStroke(inSide)
)
{
	val vK      = inSide / 100f																	// pixels par unité de la tuile
	val vStroke = Stroke(inWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)				// trait
	withTransform({
		translate(inOffset.x + kMarkLeft * vK, inOffset.y + kMarkTop * vK)
		scale(vK * kMarkScale, vK * kMarkScale, Offset.Zero)
	}) {
		drawPath(path(kPhoneData), inMuted, style = vStroke)
		kLinkDots.forEach { drawCircle(inMuted, minOf(inWidth / 2f, kDotRadius), it) }
		drawPath(path(kScreenData), inText, style = vStroke)
	}
}

/*
	Logo complet dans un carré de côté inSide : tuile (brume, grille,
	liseré) et emblème.
	inSide   : côté (pixels)
	inDetail : grille dessinée ; faux en très petit, où elle brouillerait
	           l'emblème
*/
fun DrawScope.drawLogo(
	inSide   : Float,
	inDetail : Boolean = true
)
{
	val vK    = inSide / 100f																	// pixels par unité
	val vTile = Path().apply { addRoundRect(RoundRect(0f, 0f, inSide, inSide, CornerRadius(kTileRadius * vK))) }	// tuile
	clipPath(vTile) { drawLogoGround(vK, inDetail) }
	val vEdge = (.9f * vK).coerceAtLeast(1f)													// épaisseur du liseré
	drawRoundRect(Color.White.copy(alpha = .14f), Offset(vEdge / 2f, vEdge / 2f), Size(inSide - vEdge, inSide - vEdge), CornerRadius(kTileRadius * vK - vEdge / 2f), style = Stroke(vEdge))
	drawLogoMark(inSide)
}

/*
	Logo en image (icônes de fenêtre et de la zone de notification, fichiers
	d'icônes).
	inSize   : côté (pixels)
	inDetail : grille dessinée ; faux pour le très petit
*/
fun logoImage(
	inSize   : Int,
	inDetail : Boolean = inSize >= 48
): ImageBitmap
{
	val vImage = ImageBitmap(inSize, inSize)													// image
	CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(vImage), Size(inSize.toFloat(), inSize.toFloat())) { drawLogo(inSize.toFloat(), inDetail) }
	return vImage
}

/*
	Logo de BridgePlus : grille à partir de 40 dp, sans en dessous (elle y
	serait invisible et brouillerait l'emblème). Le trait suit la taille en
	pixels.
	inModifier : modificateur
	inSize     : côté du logo
*/
@Composable
fun BridgeLogo(
	inModifier : Modifier = Modifier,
	inSize     : Dp       = 28.dp
)
{
	Canvas(inModifier.size(inSize)) { drawLogo(size.minDimension, inSize >= 40.dp) }
}

/*
	Logo suivi du nom « BridgePlus », en haut à gauche des écrans clés et de
	la fenêtre du PC. D'une seule couleur : en deux tons, « Bridge » se
	perdrait dans la brume claire du haut des écrans clés.
	inModifier : modificateur
*/
@Composable
fun BridgeBrand(inModifier: Modifier = Modifier)
{
	Row(inModifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
		BridgeLogo()
		BasicText("BridgePlus", style = bridgeType().title)
	}
}
