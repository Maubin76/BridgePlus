package fr.maubin.bridgeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.maubin.bridgeplus.ui.icons.LineIcons
import fr.maubin.bridgeplus.ui.theme.BridgeColors
import fr.maubin.bridgeplus.ui.theme.bridgeType

/*
	Ligne d'un appareil appairé : icône dans un carré gris (comme les
	monogrammes de KeePlus), nom, pastille d'état et dernière connexion, puis
	les actions renommer et oublier. Pas de séparateur : seul l'espacement
	sépare les lignes.
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
	val vType = bridgeType()																	// styles de texte
	Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BridgeColors.surfaceHigh), contentAlignment = Alignment.Center) {
			Icon(inIcon, contentDescription = null, tint = BridgeColors.text, modifier = Modifier.size(22.dp))
		}
		Spacer(Modifier.width(14.dp))
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			OneLine(inName, vType.title)
			Row(verticalAlignment = Alignment.CenterVertically) {
				StatusPill(inLook, inStatus)
				if (inLastSeen != null && inLook != LinkLook.Connected)
				{
					Spacer(Modifier.width(8.dp))
					OneLine("vu $inLastSeen", vType.bodySmall)
				}
			}
		}
		IconAction(LineIcons.Edit, "Renommer $inName", inOnRename)
		IconAction(LineIcons.Trash, "Oublier $inName", inOnForget)
	}
}

/*
	Dialogue de BridgePlus, celui de KeePlus : carte gris foncé arrondie,
	titre, texte, puis deux boutons fantômes côte à côte.
	inTitle     : titre
	inText      : explication
	inConfirm   : intitulé du bouton de confirmation
	inOnDismiss : annulation
	inOnConfirm : confirmation
	inExtra     : contenu entre le texte et les boutons (champ) ; rien par défaut
*/
@Composable
fun ConfirmDialog(
	inTitle     : String,
	inText      : String,
	inConfirm   : String,
	inOnDismiss : () -> Unit,
	inOnConfirm : () -> Unit,
	inExtra     : @Composable () -> Unit = {}
)
{
	Dialog(onDismissRequest = inOnDismiss) {
		Column(
			Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(BridgeColors.surfaceHigh).padding(20.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp)
		) {
			BasicText(inTitle, style = bridgeType().headline)
			BasicText(inText, style = bridgeType().bodySmall)
			inExtra()
			Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				GhostButton("Annuler", inOnDismiss, Modifier.weight(1f))
				GhostButton(inConfirm, inOnConfirm, Modifier.weight(1f))
			}
		}
	}
}

/*
	Dialogue de renommage d'un appareil : champ en pilule sur fond surface.
	inCurrent   : nom actuel
	inOriginal  : nom que l'appareil se donne (repris si le champ est vidé)
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
	ConfirmDialog(
		inTitle     = "Renommer",
		inText      = "Laissez vide pour reprendre « $inOriginal ».",
		inConfirm   = "Renommer",
		inOnDismiss = inOnDismiss,
		inOnConfirm = { inOnRename(vText) }
	) {
		BasicTextField(
			value           = vText,
			onValueChange   = { vText = it.take(60) },
			singleLine      = true,
			textStyle       = bridgeType().body,
			cursorBrush     = SolidColor(BridgeColors.text),
			keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
			keyboardActions = KeyboardActions(onDone = { inOnRename(vText) }),
			decorationBox   = { vInner ->
				Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(BridgeColors.surface).padding(horizontal = 16.dp, vertical = 14.dp)) {
					if (vText.isEmpty()) BasicText(inOriginal, style = bridgeType().body.copy(color = BridgeColors.textFaint))
					vInner()
				}
			}
		)
	}
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
	ConfirmDialog(
		inTitle     = "Oublier « $inName » ?",
		inText      = "La clé de cette paire sera détruite des deux côtés : $inName ne pourra plus se connecter. Pour le relier de nouveau, il faudra l'appairer avec un nouveau QR code.",
		inConfirm   = "Oublier",
		inOnDismiss = inOnDismiss,
		inOnConfirm = inOnForget
	)
}

/*
	Encadré d'explication : icône et une phrase, avec une action discrète
	facultative (autorisations, pare-feu).
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
	Row(
		modifier          = inModifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BridgeColors.surface).padding(16.dp),
		verticalAlignment = Alignment.Top
	) {
		Icon(inIcon, contentDescription = null, tint = BridgeColors.textMuted, modifier = Modifier.size(20.dp))
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			BasicText(inText, style = bridgeType().bodySmall.copy(color = BridgeColors.text))
			if (inAction != null)
			{
				Spacer(Modifier.height(4.dp))
				TextLink(inAction, inOnAction, Modifier.padding(start = 0.dp))
			}
		}
	}
}
