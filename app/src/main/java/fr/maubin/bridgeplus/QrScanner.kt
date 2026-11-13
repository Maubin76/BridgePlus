package fr.maubin.bridgeplus

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import fr.maubin.bridgeplus.ui.components.GhostButton
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.BridgePlusTheme
import fr.maubin.bridgeplus.ui.theme.bridgeType
import java.util.concurrent.Executors

/*
	Lecture d'un QR code dans une image de l'appareil photo (plan de
	luminance) ; null si aucun n'y est lisible.
	inImage  : image de l'appareil photo
	inReader : lecteur
*/
private fun decodeQr(
	inImage  : ImageProxy,
	inReader : MultiFormatReader
): String?
{
	val vPlane  = inImage.planes.firstOrNull() ?: return null								// luminance
	val vBytes  = ByteArray(vPlane.buffer.remaining()).also { vPlane.buffer.get(it) }		// octets
	val vSource = PlanarYUVLuminanceSource(vBytes, vPlane.rowStride, inImage.height, 0, 0, inImage.width, inImage.height, false)	// source
	return try { inReader.decodeWithState(BinaryBitmap(HybridBinarizer(vSource))).text } catch (vError: NotFoundException) { null } finally { inReader.reset() }
}

/*
	Lecteur de QR code plein écran : aperçu de l'appareil photo (dos) ; rend
	le premier QR code lu. Demande l'autorisation de l'appareil photo ;
	refusée, explique et propose de fermer.
	inOnResult : contenu du QR code lu
	inOnClose  : fermeture sans lecture
*/
@Composable
fun QrScannerDialog(
	inOnResult : (String) -> Unit,
	inOnClose  : () -> Unit
)
{
	val vType    = bridgeType()																	// styles de texte
	val vContext = LocalContext.current														// contexte
	var vAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(vContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }	// appareil photo autorisé
	val vAsk     = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vAllowed = it }	// demande d'autorisation
	LaunchedEffect(Unit) { if (!vAllowed) vAsk.launch(Manifest.permission.CAMERA) }
	Dialog(onDismissRequest = inOnClose, properties = DialogProperties(usePlatformDefaultWidth = false)) { BridgePlusTheme {
		Box(Modifier.fillMaxSize().background(BridgeColors.void)) {
			if (vAllowed) CameraPreview(inOnResult)
			BasicText("Scanner le QR code du PC", Modifier.statusBarsPadding().padding(24.dp), style = vType.headline)
			Column(
				Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(BridgeColors.void.copy(alpha = 0.7f)).navigationBarsPadding().padding(24.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				BasicText(
					if (vAllowed) "Sur le PC : BridgePlus › Appairer un téléphone, puis visez le QR code."
					else "BridgePlus a besoin de l'appareil photo pour lire le QR code affiché par le PC.",
					style = vType.body
				)
				GhostButton("Fermer", inOnClose)
			}
		}
	} }
}

/*
	Aperçu de l'appareil photo et analyse des images ; s'arrête dès qu'un QR
	code est lu.
	inOnResult : contenu du QR code lu
*/
@Composable
private fun CameraPreview(inOnResult: (String) -> Unit)
{
	val vContext  = LocalContext.current														// contexte
	val vOwner    = LocalLifecycleOwner.current													// cycle de vie
	val vOnResult by rememberUpdatedState(inOnResult)											// rappel courant
	val vExecutor = remember { Executors.newSingleThreadExecutor() }							// fil de l'analyse
	var vDone     by remember { mutableStateOf(false) }										// QR code déjà lu
	DisposableEffect(Unit) { onDispose { vExecutor.shutdown() } }
	AndroidView(
		modifier = Modifier.fillMaxSize(),
		factory  = { vViewContext ->
			val vView   = PreviewView(vViewContext)												// aperçu
			val vFuture = ProcessCameraProvider.getInstance(vViewContext)							// appareil photo
			vFuture.addListener({
				val vProvider = vFuture.get()														// fournisseur
				val vReader   = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }	// lecteur
				val vPreview  = Preview.Builder().build().also { it.surfaceProvider = vView.surfaceProvider }	// aperçu
				val vAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { vStep ->
					vStep.setAnalyzer(vExecutor) { vImage ->
						val vText = if (vDone) null else decodeQr(vImage, vReader)					// contenu lu
						vImage.close()
						if (vText != null)
						{
							vDone = true
							ContextCompat.getMainExecutor(vContext).execute { vProvider.unbindAll(); vOnResult(vText) }
						}
					}
				}																					// analyse des images
				vProvider.unbindAll()
				vProvider.bindToLifecycle(vOwner, CameraSelector.DEFAULT_BACK_CAMERA, vPreview, vAnalysis)
			}, ContextCompat.getMainExecutor(vViewContext))
			vView
		}
	)
}
