package fr.maubin.bridgeplus.ui.components

import androidx.compose.foundation.Canvas as CanvasBox
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgePath
import kotlin.math.hypot
import kotlin.random.Random

private val kGridStep     = 48.dp															// pas de la grille
private val kEmblemBlur   = 32.dp															// flou de l'emblème
private val kParallax     = 12.dp															// amplitude de la parallaxe
private const val kGrain  = 0.05f															// opacité du grain
private const val kRing   = 0.10f															// largeur de la vague (part du rayon)

/*
	Décor des écrans clés (voir DESIGN.md), celui de KeePlus : brume grise qui
	tombe sur une grille fine, grand pont flou décentré à droite, grain léger.
	inModifier : modificateur
	inParallax : décalage de l'emblème, de -1 à 1 sur chaque axe
	inGlow     : avancée de la vague qui illumine la grille depuis le centre,
	             de 0 à 1 ; négatif : pas de vague
	inFade     : dissipation du décor vers le fond uni, de 0 (décor complet) à 1
	inContent  : contenu posé sur le décor
*/
@Composable
fun BridgeBackdrop(
	inModifier : Modifier = Modifier,
	inParallax : Offset   = Offset.Zero,
	inGlow     : Float    = -1f,
	inFade     : Float    = 0f,
	inContent  : @Composable BoxScope.() -> Unit
)
{
	val vGrain = remember { noiseTile() }													// grain, tiré une fois
	val vShown = 1f - inFade.coerceIn(0f, 1f)												// part visible du décor
	Box(inModifier.fillMaxSize().background(BridgeColors.ground)) {
		// Brume.
		CanvasBox(Modifier.fillMaxSize().graphicsLayer { alpha = vShown }) {
			drawRect(Brush.verticalGradient(0f to BridgeColors.mistTop, 0.42f to BridgeColors.mistMid, 0.72f to BridgeColors.void, 1f to BridgeColors.void))
		}
		// Emblème flou, décentré à droite, déplacé par la parallaxe.
		CanvasBox(
			Modifier
				.fillMaxSize()
				.graphicsLayer {
					translationX = inParallax.x * kParallax.toPx()
					translationY = inParallax.y * kParallax.toPx()
					alpha        = vShown
				}
				.blur(kEmblemBlur, BlurredEdgeTreatment.Unbounded)
		) {
			val vSide = minOf(size.width * 1.6f, size.height * 1.25f)						// côté du carré de l'emblème
			drawPath(bridgePath(vSide, Offset(size.width - vSide * 0.44f, size.height * 0.02f)), BridgeColors.emblem)	// 44 % visible, à droite
		}
		// Grille, estompée vers le bas, et vague lumineuse.
		CanvasBox(Modifier.fillMaxSize().graphicsLayer { alpha = vShown }) {
			val vStep  = kGridStep.toPx()															// pas en pixels
			val vBase  = Brush.verticalGradient(0f to BridgeColors.gridLine, 0.55f to BridgeColors.gridLine, 0.9f to Color.Transparent)	// traits de base
			val vGlow  = if (inGlow < 0f) null else glowBrush(inGlow, center, hypot(size.width, size.height) / 2f)	// vague
			var vX     = (size.width % vStep) / 2f													// premier trait vertical
			while (vX < size.width)
			{
				drawLine(vBase, Offset(vX, 0f), Offset(vX, size.height), 1f)
				if (vGlow != null) drawLine(vGlow, Offset(vX, 0f), Offset(vX, size.height), 1f)
				vX += vStep
			}
			var vY = vStep / 2f																	// premier trait horizontal
			while (vY < size.height)
			{
				drawLine(vBase, Offset(0f, vY), Offset(size.width, vY), 1f)
				if (vGlow != null) drawLine(vGlow, Offset(0f, vY), Offset(size.width, vY), 1f)
				vY += vStep
			}
		}
		// Grain.
		CanvasBox(Modifier.fillMaxSize()) {
			drawRect(ShaderBrush(ImageShader(vGrain, TileMode.Repeated, TileMode.Repeated)), alpha = kGrain * vShown)
		}
		inContent()
	}
}

/*
	Vrai à l'intérieur d'un panneau qui porte déjà la texture des écrans de
	travail (voir bridgeTexture).
*/
val LocalTextured = staticCompositionLocalOf { false }

/*
	Texture des écrans de travail (appareils, réglages) : la
	grille fine et le grain du décor des écrans clés, sans brume ni emblème,
	posés sur le fond et sous le contenu. La grille est calée sur la fenêtre :
	ses traits restent alignés d'un panneau à l'autre. Elle s'estompe vers
	le bas, comme sur l'accueil.
	À placer juste après le fond (background) du panneau. Sans effet dans
	un panneau déjà texturé (LocalTextured) : la texture n'est jamais
	dessinée deux fois.
*/
@Composable
fun Modifier.bridgeTexture(): Modifier
{
	if (LocalTextured.current) return this
	val vGrain  = remember { noiseTile() }													// grain, tiré une fois
	var vOrigin by remember { mutableStateOf(Offset.Zero) }									// position du panneau dans la fenêtre
	return this
		.onGloballyPositioned { vOrigin = it.positionInWindow() }
		.drawBehind {
			val vStep = kGridStep.toPx()															// pas en pixels
			val vLine = Brush.verticalGradient(0f to BridgeColors.gridLine, 0.55f to BridgeColors.gridLine, 0.9f to Color.Transparent)	// traits
			var vX    = mod(-vOrigin.x, vStep)														// premier trait vertical, calé sur la fenêtre
			while (vX < size.width)
			{
				drawLine(vLine, Offset(vX, 0f), Offset(vX, size.height), 1f)
				vX += vStep
			}
			var vY    = mod(vStep / 2f - vOrigin.y, vStep)											// premier trait horizontal
			while (vY < size.height)
			{
				drawLine(vLine, Offset(0f, vY), Offset(size.width, vY), 1f)
				vY += vStep
			}
			drawRect(ShaderBrush(ImageShader(vGrain, TileMode.Repeated, TileMode.Repeated)), alpha = kGrain)
		}
}

/*
	Reste positif d'une division (décalage de la grille).
	inValue : valeur
	inStep  : pas
*/
private fun mod(
	inValue : Float,
	inStep  : Float
): Float = ((inValue % inStep) + inStep) % inStep

/*
	Anneau lumineux d'une vague partie du centre.
	inProgress : avancée, de 0 (centre) à 1 (coins)
	inCenter   : centre de la vague
	inRadius   : rayon atteint à la fin
*/
private fun glowBrush(
	inProgress : Float,
	inCenter   : Offset,
	inRadius   : Float
): Brush
{
	val vFront = inProgress.coerceIn(0f, 1f)													// front de la vague
	val vIn    = (vFront - kRing).coerceAtLeast(0f)											// début de l'anneau
	val vOut   = (vFront + kRing / 3f).coerceAtMost(1f)										// fin de l'anneau
	val vLit   = BridgeColors.gridLineLit.copy(alpha = BridgeColors.gridLineLit.alpha * (1f - vFront * 0.6f))	// éclat, qui faiblit en s'éloignant
	val vStops = buildList {
		if (vIn > 0f) add(0f to Color.Transparent)
		add(vIn to Color.Transparent)
		add(((vIn + vOut) / 2f).coerceAtLeast(vIn + 0.0001f) to vLit)
		add(vOut.coerceAtLeast(vIn + 0.0002f) to Color.Transparent)
		if (vOut < 1f) add(1f to Color.Transparent)
	}																						// arrêts du dégradé
	return Brush.radialGradient(*vStops.toTypedArray(), center = inCenter, radius = inRadius)
}

/*
	Petite tuile de bruit gris (128 × 128) pour le grain, répétée sur l'écran.
*/
private fun noiseTile(): ImageBitmap
{
	val vSize   = 128																			// côté de la tuile
	val vImage  = ImageBitmap(vSize, vSize)													// tuile
	val vCanvas = Canvas(vImage)								// dessin dans la tuile
	val vRandom = Random(7)																	// tirage stable
	val vLevels = List(6) { mutableListOf<Offset>() }										// points par niveau de gris
	for (vY in 0 until vSize) for (vX in 0 until vSize) vLevels[vRandom.nextInt(vLevels.size)] += Offset(vX + 0.5f, vY + 0.5f)
	vLevels.forEachIndexed { vIndex, vPoints ->
		val vPaint = Paint().apply { color = Color.White.copy(alpha = vIndex / (vLevels.size - 1f)); strokeWidth = 1f }	// gris du niveau
		vCanvas.drawPoints(PointMode.Points, vPoints, vPaint)
	}
	return vImage
}

/*
	Suit la souris (PC) : position relative au centre, de -1 à 1, pour la
	parallaxe là où il n'y a pas de capteur d'inclinaison.
	inState : position mise à jour
*/
fun Modifier.trackPointer(inState: MutableState<Offset>): Modifier =
	pointerInput(inState) {
		awaitPointerEventScope {
			while (true)
			{
				val vEvent = awaitPointerEvent()													// événement de la souris
				if (vEvent.type != PointerEventType.Move) continue
				val vAt = vEvent.changes.first().position										// position de la souris
				inState.value = Offset(
					((vAt.x / size.width) * 2f - 1f).coerceIn(-1f, 1f),
					((vAt.y / size.height) * 2f - 1f).coerceIn(-1f, 1f)
				)
			}
		}
	}

/*
	Léger voile de brume en haut des écrans de travail (120 premiers dp).
*/
fun mistTop(): Brush =
	Brush.verticalGradient(0f to BridgeColors.mistMid.copy(alpha = 0.32f), 0.16f to BridgeColors.ground, 1f to BridgeColors.ground)
