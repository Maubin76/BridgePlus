package fr.maubin.bridgeplus.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/*
	Modules d'un QR code : vrai pour un module noir ; correction d'erreurs
	moyenne, sans marge (la marge blanche est dessinée autour).
	inText : contenu
*/
fun qrModules(inText: String): Array<BooleanArray>
{
	val vMatrix = QRCodeWriter().encode(inText, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))	// modules
	return Array(vMatrix.height) { inY -> BooleanArray(vMatrix.width) { inX -> vMatrix[inX, inY] } }
}

/*
	QR code noir sur blanc, quel que soit le thème (lisible par l'appareil
	photo), avec sa marge blanche.
	inText        : contenu
	inSize        : côté
	inDescription : description pour les lecteurs d'écran
*/
@Composable
fun QrCode(
	inText        : String,
	inSize        : Dp,
	inDescription : String
)
{
	val vModules = remember(inText) { qrModules(inText) }								// modules
	Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(18.dp).semantics { contentDescription = inDescription }) {
		Canvas(Modifier.size(inSize)) {
			val vCell = size.width / vModules.size											// côté d'un module
			vModules.forEachIndexed { inY, inRow ->
				inRow.forEachIndexed { inX, inDark ->
					if (inDark) drawRect(Color.Black, Offset(inX * vCell, inY * vCell), Size(vCell + 0.5f, vCell + 0.5f))
				}
			}
		}
	}
}
