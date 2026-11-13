package fr.maubin.pontplus.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import fr.maubin.pontplus.ui.theme.kLogoPath
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

private val kIcoSizes = listOf(16, 24, 32, 48, 64, 128, 256)								// tailles du fichier .ico

/*
	Image encodée en PNG.
	inImage : image
*/
private fun png(inImage: ImageBitmap): ByteArray = Image.makeFromBitmap(inImage.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes

/*
	Fichier .ico (Windows) : une image PNG par taille, en petit-boutiste.
	inImages : images PNG par taille
*/
private fun ico(inImages: Map<Int, ByteArray>): ByteArray
{
	val vOut    = ByteArrayOutputStream()													// fichier
	fun u16(inValue: Int) { vOut.write(inValue and 0xFF); vOut.write(inValue shr 8 and 0xFF) }
	fun u32(inValue: Int) { u16(inValue and 0xFFFF); u16(inValue ushr 16) }
	u16(0); u16(1); u16(inImages.size)
	var vOffset = 6 + 16 * inImages.size													// début des images
	for ((vSize, vPng) in inImages)
	{
		vOut.write(if (vSize >= 256) 0 else vSize); vOut.write(if (vSize >= 256) 0 else vSize)
		vOut.write(0); vOut.write(0); u16(1); u16(32); u32(vPng.size); u32(vOffset)
		vOffset += vPng.size
	}
	inImages.values.forEach { vOut.write(it) }
	return vOut.toByteArray()
}

/*
	Icône vectorielle Android (grille de 108) du tracé du logo.
	inColor   : couleur du tracé (#AARRGGBB)
	inComment : commentaire en tête du fichier
*/
private fun vector(
	inColor   : String,
	inComment : String
): String = """<?xml version="1.0" encoding="utf-8"?>
<!-- $inComment -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
	android:width="108dp"
	android:height="108dp"
	android:viewportWidth="108"
	android:viewportHeight="108">
	<path
		android:fillColor="$inColor"
		android:fillType="evenOdd"
		android:pathData="$kLogoPath" />
</vector>
"""

/*
	Génère les fichiers du logo à partir de son tracé (module :ui) : image de
	référence, icône Windows, premier plan et version monochrome de l'icône
	adaptative Android. À relancer après toute retouche du logo ; le résultat
	est versionné.
*/
class LogoAssetsTest
{
	/*
		Écrit tous les fichiers du logo.
	*/
	@Test
	fun generateLogoAssets()
	{
		val vRoot = File("..")																	// racine du projet
		File(vRoot, "doc/logo").mkdirs()
		File(vRoot, "doc/logo/pontplus-logo.png").writeBytes(png(logoImage(null, 1024)))
		File(vRoot, "desktop/icons").mkdirs()
		File(vRoot, "desktop/icons/pontplus.ico").writeBytes(ico(kIcoSizes.associateWith { png(logoImage(null, it)) }))
		val vDrawables = File(vRoot, "app/src/main/res/drawable").apply { mkdirs() }			// dessins Android
		File(vDrawables, "ic_launcher_foreground.xml").writeText(vector("#FFFFFFFF", "Généré par LogoAssetsTest : pont blanc évidé du « + », premier plan de l'icône."))
		File(vDrawables, "ic_launcher_monochrome.xml").writeText(vector("#FF000000", "Généré par LogoAssetsTest : pont évidé du « + », pour les icônes thématiques."))
	}
}
