package fr.maubin.pontplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.theme.Pont

/*
	Titre de section d'un écran de travail.
	inText     : titre
	inModifier : modificateur
*/
@Composable
fun SectionTitle(
	inText     : String,
	inModifier : Modifier = Modifier
)
{
	Text(inText, style = Pont.type.headline, modifier = inModifier.padding(top = 8.dp, bottom = 12.dp))
}

/*
	Carte d'un écran de travail : fond surface, coins arrondis, marge
	intérieure.
	inModifier : modificateur
	inContent  : contenu
*/
@Composable
fun PontCard(
	inModifier : Modifier = Modifier,
	inContent  : @Composable () -> Unit
)
{
	Column(inModifier.fillMaxWidth().background(Pont.colors.surface, RoundedCornerShape(20.dp)).padding(18.dp)) {
		inContent()
	}
}

/*
	Encadré d'explication : icône et texte, avec une action facultative.
	inIcon     : icône
	inText     : explication
	inAction   : intitulé de l'action ; null sans
	inOnAction : action
	inModifier : modificateur
*/
@Composable
fun InfoCard(
	inIcon     : ImageVector,
	inText     : String,
	inAction   : String?    = null,
	inOnAction : () -> Unit = {},
	inModifier : Modifier   = Modifier
)
{
	val vColors = Pont.colors																// palette
	Row(
		modifier          = inModifier.fillMaxWidth().background(vColors.surfaceHigh, RoundedCornerShape(16.dp)).padding(16.dp),
		verticalAlignment = Alignment.Top
	) {
		Icon(inIcon, contentDescription = null, tint = vColors.textMuted, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			Text(inText, style = Pont.type.body)
			if (inAction != null)
			{
				Spacer(Modifier.height(6.dp))
				QuietButton(inAction, inOnAction, Modifier.padding(start = 0.dp))
			}
		}
	}
}

/*
	Ligne d'un appareil appairé : icône, nom, pastille d'état, dernière
	connexion, et les actions renommer et oublier.
	inIcon     : icône de l'appareil (téléphone, ordinateur)
	inName     : nom affiché
	inLook     : état de la liaison
	inStatus   : texte de l'état (« Relié », « Absent »…)
	inLastSeen : dernière connexion en clair ; null si jamais
	inOnRename : renommer
	inOnForget : oublier
*/
@Composable
fun DeviceRow(
	inIcon     : ImageVector,
	inName     : String,
	inLook     : LinkLook,
	inStatus   : String,
	inLastSeen : String?,
	inOnRename : () -> Unit,
	inOnForget : () -> Unit
)
{
	val vColors = Pont.colors																// palette
	Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
		Icon(inIcon, contentDescription = null, tint = vColors.text, modifier = Modifier.size(44.dp).background(vColors.surfaceHigh, RoundedCornerShape(12.dp)).padding(11.dp))
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Text(inName, style = Pont.type.title)
			Row(verticalAlignment = Alignment.CenterVertically) {
				StatusPill(inLook, inStatus)
				if (inLastSeen != null && inLook != LinkLook.Connected)
				{
					Spacer(Modifier.width(8.dp))
					Text("vu $inLastSeen", style = Pont.type.bodySmall)
				}
			}
		}
		IconButton(onClick = inOnRename) { Icon(LineIcons.Edit, contentDescription = "Renommer $inName", tint = vColors.textMuted) }
		IconButton(onClick = inOnForget) { Icon(LineIcons.Trash, contentDescription = "Oublier $inName", tint = vColors.textMuted) }
	}
}

/*
	Dialogue de renommage d'un appareil.
	inCurrent   : nom actuel
	inOriginal  : nom que l'appareil se donne (proposé si le champ est vidé)
	inOnRename  : nouveau nom (vide : reprendre le sien)
	inOnDismiss : fermeture sans changement
*/
@Composable
fun RenameDialog(
	inCurrent   : String,
	inOriginal  : String,
	inOnRename  : (String) -> Unit,
	inOnDismiss : () -> Unit
)
{
	var vText by remember { mutableStateOf(inCurrent) }										// nom saisi
	AlertDialog(
		onDismissRequest = inOnDismiss,
		title            = { Text("Renommer", style = Pont.type.headline) },
		text             = {
			OutlinedTextField(
				value           = vText,
				onValueChange   = { vText = it.take(60) },
				singleLine      = true,
				placeholder     = { Text(inOriginal) },
				supportingText  = { Text("Laissez vide pour reprendre « $inOriginal ».") },
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
				keyboardActions = KeyboardActions(onDone = { inOnRename(vText) })
			)
		},
		confirmButton    = { QuietButton("Renommer", { inOnRename(vText) }) },
		dismissButton    = { QuietButton("Annuler", inOnDismiss) }
	)
}

/*
	Confirmation avant d'oublier un appareil : sa clé est détruite.
	inName      : nom de l'appareil
	inOnForget  : oubli confirmé
	inOnDismiss : annulation
*/
@Composable
fun ForgetDialog(
	inName      : String,
	inOnForget  : () -> Unit,
	inOnDismiss : () -> Unit
)
{
	AlertDialog(
		onDismissRequest = inOnDismiss,
		title            = { Text("Oublier $inName ?", style = Pont.type.headline) },
		text             = { Text("La clé de cette paire sera détruite des deux côtés : $inName ne pourra plus se connecter. Pour le relier de nouveau, il faudra l'appairer avec un nouveau QR code.", style = Pont.type.body) },
		confirmButton    = { QuietButton("Oublier cet appareil", inOnForget, inDanger = true) },
		dismissButton    = { QuietButton("Annuler", inOnDismiss) }
	)
}
