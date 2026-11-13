package fr.maubin.pontplus.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val kStroke = 1.6f														// épaisseur du trait, sur une grille de 24

/*
	Construit une icône au trait fin (grille 24 × 24, extrémités arrondies) à
	partir de tracés SVG. La couleur est donnée par la teinte de l'icône.
	inName  : nom de l'icône
	inPaths : tracés SVG, chacun dessiné en contour
*/
internal fun lineIcon(
	inName         : String,
	vararg inPaths : String
): ImageVector
{
	val vBuilder = ImageVector.Builder(
		name           = inName,
		defaultWidth   = 24.dp,
		defaultHeight  = 24.dp,
		viewportWidth  = 24f,
		viewportHeight = 24f
	)																					// icône en construction
	inPaths.forEach { vPath ->
		vBuilder.addPath(
			pathData        = addPathNodes(vPath),
			fill            = null,
			stroke          = SolidColor(Color.Black),
			strokeLineWidth = kStroke,
			strokeLineCap   = StrokeCap.Round,
			strokeLineJoin  = StrokeJoin.Round
		)
	}
	return vBuilder.build()
}

/*
	Jeu d'icônes au trait fin de PontPlus (même facture que KeePlus) :
	géométrie simple, trait de 1,6, aucun aplat.
*/
object LineIcons
{
	// Relié : deux maillons attachés.
	val Linked: ImageVector = lineIcon(
		"Relié",
		"M10 14 a3.5 3.5 0 0 0 5 0 l3 -3 a3.5 3.5 0 0 0 -5 -5 l-1 1",
		"M14 10 a3.5 3.5 0 0 0 -5 0 l-3 3 a3.5 3.5 0 0 0 5 5 l1 -1"
	)

	// Absent : deux maillons séparés.
	val Unlinked: ImageVector = lineIcon(
		"Absent",
		"M13 7 l1 -1 a3.5 3.5 0 0 1 5 5 l-1 1",
		"M11 17 l-1 1 a3.5 3.5 0 0 1 -5 -5 l1 -1",
		"M8 3 v2.5", "M3 8 h2.5", "M16 21 v-2.5", "M21 16 h-2.5"
	)

	// Recherche en cours : flèches qui tournent.
	val Searching: ImageVector = lineIcon(
		"Recherche",
		"M19.5 12 a7.5 7.5 0 0 1 -13 5",
		"M4.5 12 a7.5 7.5 0 0 1 13 -5",
		"M17.5 3.5 v3.5 h-3.5",
		"M6.5 20.5 v-3.5 h3.5"
	)

	// Hors de la maison : maison barrée.
	val AwayFromHome: ImageVector = lineIcon(
		"Hors de la maison",
		"M4 11 L12 4.5 L20 11",
		"M6 9.5 V19.5 H18 V9.5",
		"M3.5 3.5 L20.5 20.5"
	)

	// Arrêt : cercle barré.
	val Off: ImageVector = lineIcon(
		"Arrêté",
		"M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0",
		"M6 6 L18 18"
	)

	// Refus : cercle avec point d'exclamation.
	val Alert: ImageVector = lineIcon(
		"Alerte",
		"M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0",
		"M12 7.5 V13",
		"M12 16.4 V16.6"
	)

	// Accueil : maison.
	val Home: ImageVector = lineIcon(
		"Accueil",
		"M4 11 L12 4.5 L20 11",
		"M6 9.5 V19.5 H18 V9.5",
		"M10 19.5 V14.5 H14 V19.5"
	)

	// Appareils : téléphone devant un écran.
	val Devices: ImageVector = lineIcon(
		"Appareils",
		"M3.5 15.5 V6 a1.5 1.5 0 0 1 1.5 -1.5 H18 a1.5 1.5 0 0 1 1.5 1.5 V8",
		"M2.5 18.5 H12",
		"M14.5 10 h5 a1 1 0 0 1 1 1 v8.5 a1 1 0 0 1 -1 1 h-5 a1 1 0 0 1 -1 -1 v-8.5 a1 1 0 0 1 1 -1 Z",
		"M16.8 18 h0.4"
	)

	// Réglages : curseurs.
	val Settings: ImageVector = lineIcon(
		"Réglages",
		"M4 7 H20", "M4 12 H20", "M4 17 H20",
		"M9 5 V9", "M15 10 V14", "M7 15 V19"
	)

	// Ordinateur.
	val Computer: ImageVector = lineIcon(
		"Ordinateur",
		"M4.5 5 H19.5 a1 1 0 0 1 1 1 V15 a1 1 0 0 1 -1 1 H4.5 a1 1 0 0 1 -1 -1 V6 a1 1 0 0 1 1 -1 Z",
		"M9 19.5 H15", "M12 16 V19.5"
	)

	// Téléphone.
	val Phone: ImageVector = lineIcon(
		"Téléphone",
		"M8 3 H16 a1.5 1.5 0 0 1 1.5 1.5 V19.5 a1.5 1.5 0 0 1 -1.5 1.5 H8 a1.5 1.5 0 0 1 -1.5 -1.5 V4.5 a1.5 1.5 0 0 1 1.5 -1.5 Z",
		"M11 18 H13"
	)

	// QR code.
	val Qr: ImageVector = lineIcon(
		"QR code",
		"M4 4 H10 V10 H4 Z", "M14 4 H20 V10 H14 Z", "M4 14 H10 V20 H4 Z",
		"M14 14 H16.5", "M19.5 14 H20 V16.5", "M14 17 V20 H17", "M17 17 H20 V20"
	)

	// Renommer : crayon.
	val Edit: ImageVector = lineIcon(
		"Renommer",
		"M4 20 L4.8 16.2 L15.8 5.2 a1.8 1.8 0 0 1 2.6 0 L18.8 5.6 a1.8 1.8 0 0 1 0 2.6 L7.8 19.2 Z",
		"M14 7 L17 10"
	)

	// Oublier : corbeille.
	val Trash: ImageVector = lineIcon(
		"Oublier",
		"M4.5 7 H19.5", "M9.5 7 V4.5 H14.5 V7",
		"M6.5 7 L7.3 19.5 H16.7 L17.5 7",
		"M10.5 11 V16", "M13.5 11 V16"
	)

	// Fermer : croix.
	val Close: ImageVector = lineIcon(
		"Fermer",
		"M6 6 L18 18", "M18 6 L6 18"
	)

	// Ajouter : plus.
	val Plus: ImageVector = lineIcon(
		"Ajouter",
		"M12 5 V19", "M5 12 H19"
	)

	// Validé : coche.
	val Check: ImageVector = lineIcon(
		"Validé",
		"M5 12.5 L10 17.5 L19 7"
	)

	// Information : cercle avec « i ».
	val Info: ImageVector = lineIcon(
		"Information",
		"M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0",
		"M12 11 V16.5",
		"M12 7.6 V7.8"
	)

	// Wi-Fi.
	val Wifi: ImageVector = lineIcon(
		"Wi-Fi",
		"M3 9.5 a13 13 0 0 1 18 0",
		"M6 12.8 a8.5 8.5 0 0 1 12 0",
		"M9 16 a4.2 4.2 0 0 1 6 0",
		"M12 19.2 V19.4"
	)
}
