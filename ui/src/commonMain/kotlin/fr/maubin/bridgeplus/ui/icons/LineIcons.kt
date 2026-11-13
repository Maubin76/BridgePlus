package fr.maubin.bridgeplus.ui.icons

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
	Jeu d'icônes au trait fin de KeePlus, repris tel quel, et complété des
	icônes de la liaison : géométrie simple, trait de 1,6, aucun aplat, pour
	s'accorder à Satoshi.
*/
object LineIcons
{
	// Afficher : œil ouvert.
	val Eye: ImageVector = lineIcon(
		"Afficher",
		"M2.5 12 C5 7.5 8.5 5.5 12 5.5 C15.5 5.5 19 7.5 21.5 12 C19 16.5 15.5 18.5 12 18.5 C8.5 18.5 5 16.5 2.5 12 Z",
		"M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0"
	)

	// Masquer : œil barré.
	val EyeOff: ImageVector = lineIcon(
		"Masquer",
		"M4 4 L20 20",
		"M9.9 5.8 C10.6 5.6 11.3 5.5 12 5.5 C15.5 5.5 19 7.5 21.5 12 C20.8 13.3 19.9 14.4 19 15.3",
		"M16.3 17.3 C15 18.1 13.5 18.5 12 18.5 C8.5 18.5 5 16.5 2.5 12 C3.4 10.3 4.6 8.9 5.9 7.9",
		"M10 10.2 A3 3 0 0 0 13.8 14"
	)

	// Verrou fermé.
	val Lock: ImageVector = lineIcon(
		"Verrouiller",
		"M6.5 10.5 H17.5 A1.5 1.5 0 0 1 19 12 V19 A1.5 1.5 0 0 1 17.5 20.5 H6.5 A1.5 1.5 0 0 1 5 19 V12 A1.5 1.5 0 0 1 6.5 10.5 Z",
		"M8 10.5 V7.5 A4 4 0 0 1 16 7.5 V10.5",
		"M12 14.5 V16.5"
	)

	// Fichier : feuille au coin plié.
	val File: ImageVector = lineIcon(
		"Fichier",
		"M13.5 3.5 H7 A1.5 1.5 0 0 0 5.5 5 V19 A1.5 1.5 0 0 0 7 20.5 H17 A1.5 1.5 0 0 0 18.5 19 V8.5 Z",
		"M13.5 3.5 V8.5 H18.5"
	)

	// Retour : flèche vers la gauche.
	val Back: ImageVector = lineIcon(
		"Retour",
		"M19 12 H5",
		"M11 6 L5 12 L11 18"
	)

	// Rechercher : loupe.
	val Search: ImageVector = lineIcon(
		"Rechercher",
		"M4.5 10.5 a6 6 0 1 0 12 0 a6 6 0 1 0 -12 0",
		"M15 15 L20 20"
	)

	// Copier : deux feuilles décalées.
	val Copy: ImageVector = lineIcon(
		"Copier",
		"M9.5 8.5 H18 A1.5 1.5 0 0 1 19.5 10 V18.5 A1.5 1.5 0 0 1 18 20 H9.5 A1.5 1.5 0 0 1 8 18.5 V10 A1.5 1.5 0 0 1 9.5 8.5 Z",
		"M16 8.5 V6 A1.5 1.5 0 0 0 14.5 4.5 H6 A1.5 1.5 0 0 0 4.5 6 V14.5 A1.5 1.5 0 0 0 6 16 H8"
	)

	// Fermer : croix.
	val Close: ImageVector = lineIcon(
		"Fermer",
		"M6 6 L18 18",
		"M18 6 L6 18"
	)

	// Ouvrir le site : flèche sortant d'un cadre.
	val External: ImageVector = lineIcon(
		"Ouvrir",
		"M13.5 4.5 H19.5 V10.5",
		"M19.5 4.5 L11 13",
		"M17.5 13.5 V18 A1.5 1.5 0 0 1 16 19.5 H6 A1.5 1.5 0 0 1 4.5 18 V8 A1.5 1.5 0 0 1 6 6.5 H10.5"
	)

	// Tout : quatre cases.
	val All: ImageVector = lineIcon(
		"Tout",
		"M5 4.5 H10 V9.5 H5 Z",
		"M14 4.5 H19 V9.5 H14 Z",
		"M5 14.5 H10 V19.5 H5 Z",
		"M14 14.5 H19 V19.5 H14 Z"
	)

	// Groupe : dossier.
	val Folder: ImageVector = lineIcon(
		"Groupe",
		"M4 7 A1.5 1.5 0 0 1 5.5 5.5 H9.5 L11.5 7.5 H18.5 A1.5 1.5 0 0 1 20 9 V17.5 A1.5 1.5 0 0 1 18.5 19 H5.5 A1.5 1.5 0 0 1 4 17.5 Z"
	)

	// Corbeille.
	val Trash: ImageVector = lineIcon(
		"Corbeille",
		"M4.5 7 H19.5",
		"M9.5 7 V5 H14.5 V7",
		"M6.5 7 L7.5 19 A1.5 1.5 0 0 0 9 20.5 H15 A1.5 1.5 0 0 0 16.5 19 L17.5 7",
		"M10.5 11 V16.5",
		"M13.5 11 V16.5"
	)

	// Nouveau tirage : flèches en cercle.
	val Refresh: ImageVector = lineIcon(
		"Nouveau tirage",
		"M19.5 12 A7.5 7.5 0 1 1 17 6.4",
		"M17.5 3 V6.8 H13.7"
	)

	// Ajouter : plus.
	val Plus: ImageVector = lineIcon(
		"Ajouter",
		"M12 5 V19",
		"M5 12 H19"
	)

	// Modifier : crayon.
	val Edit: ImageVector = lineIcon(
		"Modifier",
		"M15.5 5 L19 8.5 L9 18.5 L4.5 19.5 L5.5 15 Z",
		"M13.5 7 L17 10.5"
	)

	// Restaurer : flèche de retour en arrière.
	val Restore: ImageVector = lineIcon(
		"Restaurer",
		"M4.5 12 A7.5 7.5 0 1 0 7 6.4",
		"M6.5 3 V6.8 H10.3"
	)

	// Empreinte : arcs concentriques.
	val Fingerprint: ImageVector = lineIcon(
		"Empreinte",
		"M6.5 7.5 A7 7 0 0 1 17.5 7.5",
		"M5 12 A7 7 0 0 1 19 12 V13.5",
		"M8 18 A11 11 0 0 1 8 12 A4 4 0 0 1 16 12 V14 A8 8 0 0 1 15 18.5",
		"M12 12 V14.5 A10 10 0 0 1 10.5 20"
	)

	// Appareils : écran et téléphone.
	val Devices: ImageVector = lineIcon(
		"Appareils",
		"M3.5 6 A1.5 1.5 0 0 1 5 4.5 H15.5 A1.5 1.5 0 0 1 17 6 V8",
		"M3.5 6 V14 A1.5 1.5 0 0 0 5 15.5 H12",
		"M7 19.5 H12",
		"M15.5 10 H19.5 A1 1 0 0 1 20.5 11 V19 A1 1 0 0 1 19.5 20 H15.5 A1 1 0 0 1 14.5 19 V11 A1 1 0 0 1 15.5 10 Z",
		"M17.5 17.5 V17.6"
	)

	// Synchroniser : deux flèches opposées.
	val Sync: ImageVector = lineIcon(
		"Synchroniser",
		"M5 10 A7 7 0 0 1 18 8",
		"M18 4 V8 H14",
		"M19 14 A7 7 0 0 1 6 16",
		"M6 20 V16 H10"
	)

	// Réglages : curseurs.
	val Settings: ImageVector = lineIcon(
		"Réglages",
		"M4.5 7 H13",
		"M17 7 H19.5",
		"M15 5 V9",
		"M4.5 12 H7",
		"M11 12 H19.5",
		"M9 10 V14",
		"M4.5 17 H13",
		"M17 17 H19.5",
		"M15 15 V19"
	)

	// Attention : cercle et point d'exclamation.
	val Alert: ImageVector = lineIcon(
		"Attention",
		"M3.5 12 a8.5 8.5 0 1 0 17 0 a8.5 8.5 0 1 0 -17 0",
		"M12 7.5 V12.5",
		"M12 16 V16.2"
	)

	// --- Liaison (BridgePlus) ---

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

	// Envoyé : flèche qui monte vers l'autre appareil.
	val Sent: ImageVector = lineIcon(
		"Envoyé",
		"M12 19 V5",
		"M6.5 10.5 L12 5 L17.5 10.5"
	)

	// Reçu : flèche qui descend de l'autre appareil.
	val Received: ImageVector = lineIcon(
		"Reçu",
		"M12 5 V19",
		"M6.5 13.5 L12 19 L17.5 13.5"
	)
}
