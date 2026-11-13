package fr.maubin.bridgeplus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
	Encadré d'explication : icône et une phrase, avec une ou deux actions
	discrètes facultatives (autorisations, pare-feu).
	inIcon      : icône
	inText      : explication
	inAction    : intitulé de l'action ; null sans
	inOnAction  : action
	inModifier  : modificateur
	inAction2   : intitulé d'une seconde action ; null sans
	inOnAction2 : seconde action
*/
@Composable
fun InfoCard(
	inIcon      : ImageVector,
	inText      : String,
	inAction    : String?    = null,
	inOnAction  : () -> Unit = {},
	inModifier  : Modifier   = Modifier,
	inAction2   : String?    = null,
	inOnAction2 : () -> Unit = {}
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
				Row {
					TextLink(inAction, inOnAction, Modifier.padding(start = 0.dp))
					if (inAction2 != null) TextLink(inAction2, inOnAction2)
				}
			}
		}
	}
}

/*
	Ligne d'un échange du presse-papiers : sens (icône et texte pour les
	lecteurs d'écran), texte sur une ligne, appareil et moment.
	inOutgoing : vrai pour un texte envoyé, faux pour un texte reçu
	inText     : texte échangé
	inPeers    : appareils concernés
	inWhen     : moment en clair (« il y a 2 min »)
*/
@Composable
fun ClipRow(
	inOutgoing : Boolean,
	inText     : String,
	inPeers    : String,
	inWhen     : String
)
{
	val vType = bridgeType()																	// styles de texte
	Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(BridgeColors.surfaceHigh), contentAlignment = Alignment.Center) {
			Icon(if (inOutgoing) LineIcons.Sent else LineIcons.Received, contentDescription = if (inOutgoing) "Envoyé" else "Reçu", tint = BridgeColors.textMuted, modifier = Modifier.size(18.dp))
		}
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			OneLine(inText.replace('\n', ' ').trim(), vType.body)
			OneLine("${if (inOutgoing) "Vers" else "De"} $inPeers · $inWhen", vType.bodySmall)
		}
	}
}

/*
	Ligne de l'historique : sens, texte sur deux lignes au plus, appareil et
	moment ; épingle et croix à droite. Toucher (ou cliquer) la ligne copie
	l'élément. La ligne choisie au clavier (carte du PC) est surlignée.
	inOutgoing : vrai pour un envoi
	inText     : aperçu du texte
	inPeers    : appareils concernés
	inWhen     : moment en clair
	inPinned   : élément épinglé
	inSelected : ligne choisie au clavier
	inOnCopy   : copier l'élément
	inOnPin    : épingler ou désépingler
	inOnDelete : supprimer
*/
@Composable
fun HistoryRow(
	inOutgoing : Boolean,
	inText     : String,
	inPeers    : String,
	inWhen     : String,
	inPinned   : Boolean,
	inSelected : Boolean,
	inOnCopy   : () -> Unit,
	inOnPin    : () -> Unit,
	inOnDelete : () -> Unit
)
{
	val vType = bridgeType()																	// styles de texte
	Row(
		Modifier
			.fillMaxWidth()
			.clip(RoundedCornerShape(14.dp))
			.background(if (inSelected) BridgeColors.surfaceHigh else Color.Transparent)
			.clickable(role = Role.Button, onClickLabel = "Copier", onClick = inOnCopy)
			.padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(if (inOutgoing) LineIcons.Sent else LineIcons.Received, contentDescription = if (inOutgoing) "Envoyé" else "Reçu", tint = BridgeColors.textFaint, modifier = Modifier.size(18.dp))
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f)) {
			BasicText(inText.replace('\n', ' ').trim(), style = vType.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
			OneLine("${if (inOutgoing) "Vers" else "De"} $inPeers · $inWhen", vType.bodySmall)
		}
		IconAction(LineIcons.Pin, if (inPinned) "Désépingler" else "Épingler", inOnPin, inTint = if (inPinned) BridgeColors.text else BridgeColors.textFaint)
		IconAction(LineIcons.Close, "Supprimer", inOnDelete)
	}
}

/*
	Confirmation avant d'effacer tout l'historique, épingles comprises.
	inOnClear   : effacement confirmé
	inOnDismiss : annulation
*/
@Composable
fun ClearHistoryDialog(
	inOnClear   : () -> Unit,
	inOnDismiss : () -> Unit
)
{
	ConfirmDialog(
		inTitle     = "Tout effacer ?",
		inText      = "Tout l'historique de cet appareil sera effacé, éléments épinglés compris. L'autre appareil garde le sien.",
		inConfirm   = "Tout effacer",
		inOnDismiss = inOnDismiss,
		inOnConfirm = inOnClear
	)
}

/*
	Barre d'avancement fine, à la manière de KeePlus : piste `rule`, partie
	faite en `text` ; lue par les lecteurs d'écran comme une progression.
	inProgress : avancement, de 0 à 1
	inModifier : modificateur
*/
@Composable
fun ProgressLine(
	inProgress : Float,
	inModifier : Modifier = Modifier
)
{
	Box(
		inModifier
			.fillMaxWidth()
			.height(4.dp)
			.clip(RoundedCornerShape(2.dp))
			.background(BridgeColors.rule)
			.semantics { progressBarRangeInfo = ProgressBarRangeInfo(inProgress, 0f..1f) }
	) {
		Box(Modifier.fillMaxWidth(inProgress.coerceIn(0f, 1f)).height(4.dp).background(BridgeColors.text))
	}
}

/*
	Ligne d'un transfert : sens, nom, détail (appareil, avancement, raison
	d'un échec), barre d'avancement pendant le transfert, et les actions
	(annuler, ouvrir…).
	inOutgoing : vrai pour un envoi
	inName     : nom du fichier
	inDetail   : détail en clair
	inProgress : avancement de 0 à 1 ; null quand le transfert est terminé
	inFailed   : échec (détail en rouge sourd)
	inActions  : actions à droite
*/
@Composable
fun TransferRow(
	inOutgoing : Boolean,
	inName     : String,
	inDetail   : String,
	inProgress : Float?,
	inFailed   : Boolean,
	inActions  : @Composable () -> Unit
)
{
	val vType = bridgeType()																	// styles de texte
	Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(BridgeColors.surfaceHigh), contentAlignment = Alignment.Center) {
			Icon(if (inOutgoing) LineIcons.Sent else LineIcons.Received, contentDescription = if (inOutgoing) "Envoi" else "Réception", tint = BridgeColors.textMuted, modifier = Modifier.size(18.dp))
		}
		Spacer(Modifier.width(12.dp))
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
			OneLine(inName, vType.body)
			OneLine(inDetail, vType.bodySmall.copy(color = if (inFailed) BridgeColors.danger else BridgeColors.textMuted))
			if (inProgress != null) ProgressLine(inProgress)
		}
		inActions()
	}
}
