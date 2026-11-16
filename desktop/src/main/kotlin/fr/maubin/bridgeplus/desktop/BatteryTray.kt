package fr.maubin.bridgeplus.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import fr.maubin.bridgeplus.battery.kLowBattery
import fr.maubin.bridgeplus.link.LinkMessage
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Typeface

private const val kNone = "–"																// texte de l'icône sans téléphone relié

/*
	Police des chiffres de l'icône : Segoe UI gras, celle de la barre des
	tâches, nette en 16 px ; à défaut la police grasse du système.
*/
private val kDigits: Typeface? by lazy {
	runCatching { FontMgr.default.matchFamilyStyle("Segoe UI", FontStyle.BOLD) ?: FontMgr.default.matchFamilyStyle(null, FontStyle.BOLD) }.getOrNull()
}

/*
	Texte de l'info-bulle de l'icône : « Pixel 9 · 78 % · en charge », ou
	l'absence de téléphone relié.
	inPhone   : nom du téléphone ; null sans téléphone relié
	inBattery : batterie ; null si inconnue
*/
fun batteryTooltip(
	inPhone   : String?,
	inBattery : LinkMessage.Battery?
): String = when
{
	inPhone == null || inBattery == null -> "BridgePlus — aucun téléphone relié"
	inBattery.charging                   -> "$inPhone · ${inBattery.level} % · en charge"
	else                                 -> "$inPhone · ${inBattery.level} %"
}

/*
	Couleur de l'icône (chiffres et jauge) : verte en charge, rouge sous
	20 %, sinon celle du texte.
	inBattery : batterie
	inInk     : couleur du texte
*/
fun batteryColor(
	inBattery : LinkMessage.Battery,
	inInk     : Color
): Color = when
{
	inBattery.charging              -> BridgeColors.success
	inBattery.level < kLowBattery   -> BridgeColors.danger
	else                            -> inInk
}

/*
	Icône de la batterie : le pourcentage sur presque toute la hauteur, une
	jauge fine dessous, chiffres et jauge verts en charge et rouges sous
	20 % ; dessinée à la taille demandée par Windows, pour rester nette.
	Sans téléphone relié : un tiret grisé et une jauge vide.
	fBattery : batterie ; null sans téléphone relié
	fLight   : barre des tâches claire (texte sombre)
*/
class BatteryPainter(
	private val fBattery : LinkMessage.Battery?,
	private val fLight   : Boolean
) : Painter()
{
	override val intrinsicSize: Size = Size.Unspecified

	/*
		Dessine l'icône.
	*/
	override fun DrawScope.onDraw()
	{
		val vSide   = size.minDimension																// côté
		val vBase   = if (fLight) BridgeColors.ground else BridgeColors.text						// couleur du texte
		val vInk    = fBattery?.let { batteryColor(it, vBase) } ?: vBase.copy(alpha = 0.45f)		// chiffres, tiret grisé sans téléphone
		val vText   = fBattery?.level?.coerceIn(0, 100)?.toString() ?: kNone						// pourcentage
		val vHeight = (vSide * 0.12f).coerceAtLeast(2f)												// épaisseur de la jauge
		val vTop    = vSide - vHeight																// haut de la jauge, collée en bas
		val vArea   = vTop - vSide * 0.08f															// hauteur réservée aux chiffres
		val vPaint  = Paint().apply { color = vInk.toArgb(); isAntiAlias = true }					// encre
		val vProbe  = Font(kDigits, vSide)															// police à la taille du côté (mesure)
		val vCap    = vProbe.metrics.capHeight.takeIf { it > 0f } ?: (vSide * 0.7f)				// hauteur des chiffres à cette taille
		val vRoom   = if (fBattery == null) vSide * 0.5f else vSide								// largeur permise (tiret : la moitié)
		val vScale  = minOf(vArea / vCap, vRoom / vProbe.measureTextWidth(vText, vPaint))		// agrandissement : hauteur ou largeur
		val vFont   = Font(kDigits, vSide * vScale)												// police
		val vWidth  = vFont.measureTextWidth(vText, vPaint)										// largeur du texte
		drawIntoCanvas { it.skiaCanvas.drawString(vText, (size.width - vWidth) / 2f, (vArea + vCap * vScale) / 2f, vFont, vPaint) }
		val vLeft   = vSide * 0.06f																	// gauche de la jauge
		val vLength = vSide * 0.88f																	// longueur de la jauge
		drawRoundRect(vBase.copy(alpha = 0.3f), Offset(vLeft, vTop), Size(vLength, vHeight), CornerRadius(vHeight / 2f))
		fBattery?.let { vBattery ->
			val vFill = (vLength * vBattery.level.coerceIn(0, 100) / 100f).coerceAtLeast(vHeight)	// partie pleine
			drawRoundRect(vInk, Offset(vLeft, vTop), Size(vFill, vHeight), CornerRadius(vHeight / 2f))
		}
	}
}

/*
	Icône de la batterie du téléphone relié dans la zone de notification de
	la barre des tâches, à côté de celle de BridgePlus. Elle reste en place
	tant que le réglage est activé, même sans téléphone relié (tiret) :
	Windows reconnaît une icône à son numéro, donné dans l'ordre de
	création ; une icône retirée puis remise en aurait un nouveau et
	retournerait dans les icônes masquées. Composer après l'icône de
	BridgePlus, pour garder le même numéro d'un lancement à l'autre.
	inApp    : appli PC
	inOnOpen : ouvrir la fenêtre
*/
@Composable
fun ApplicationScope.BatteryTray(
	inApp    : DesktopApp,
	inOnOpen : () -> Unit
)
{
	val vSettings by inApp.settings.collectAsState()											// réglages
	val vBattery  by inApp.battery.collectAsState()												// batterie des téléphones reliés
	val vState    by inApp.link.state.collectAsState()											// téléphones appairés
	if (!vSettings.batteryIcon) return
	val vEntry = vBattery.entries.firstOrNull()													// téléphone affiché ; null sans
	val vPhone = vEntry?.let { vOne -> vState.phones.firstOrNull { it.id == vOne.key }?.label ?: "Téléphone" }	// son nom
	val vLight = remember(vEntry?.value) { SystemPrefs.lightTaskbar() }							// barre des tâches claire
	val vIcon  = remember(vEntry?.value, vLight) { BatteryPainter(vEntry?.value, vLight) }		// icône
	Tray(
		icon     = vIcon,
		tooltip  = batteryTooltip(vPhone, vEntry?.value),
		onAction = inOnOpen,
		menu     = {
			Item("Ouvrir BridgePlus", onClick = inOnOpen)
			Item("Masquer cette icône", onClick = { inApp.updateSettings { it.copy(batteryIcon = false) } })
		}
	)
}
