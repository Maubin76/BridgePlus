package fr.maubin.pontplus.ui.screens

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import fr.maubin.pontplus.ui.components.pontTexture
import fr.maubin.pontplus.ui.components.mistTop
import fr.maubin.pontplus.ui.components.IconAction
import fr.maubin.pontplus.ui.components.FilterPill
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.ui.components.LocalAnimationsEnabled
import fr.maubin.pontplus.ui.components.PontBackdrop
import fr.maubin.pontplus.ui.components.trackPointer
import fr.maubin.pontplus.ui.icons.LineIcons
import fr.maubin.pontplus.ui.platform.rememberTilt
import fr.maubin.pontplus.ui.theme.PontBrand
import fr.maubin.pontplus.ui.theme.PontColors
import fr.maubin.pontplus.ui.theme.pontType

/*
	Gabarit des écrans clés (accueil du téléphone), celui de KeePlus : décor
	complet avec parallaxe (inclinaison du téléphone ou souris), marque en
	haut à gauche et actions en haut à droite, contenu calé en bas, sur
	440 dp au plus.
	inGlow    : avancée de la vague de la grille (voir PontBackdrop)
	inFade    : dissipation du décor (voir PontBackdrop)
	inOnBack  : retour (flèche à gauche de la marque) ; null pour aucun
	inActions : actions en haut à droite (icônes) ; aucune par défaut
	inContent : contenu, en colonne
*/
@Composable
fun KeyScreen(
	inGlow    : Float           = -1f,
	inFade    : Float           = 0f,
	inOnBack  : (() -> Unit)?   = null,
	inActions : @Composable () -> Unit = {},
	inContent : @Composable ColumnScope.() -> Unit
)
{
	val vAnimated = LocalAnimationsEnabled.current														// effets marqués permis
	val vTilt     = rememberTilt()																// inclinaison (téléphone)
	val vPointer  = remember { mutableStateOf(Offset.Zero) }								// souris (PC)
	val vTarget   = when
	{
		!vAnimated    -> Offset.Zero
		vTilt != null -> vTilt.value
		else          -> vPointer.value
	}																						// décalage voulu
	val vParallax by animateOffsetAsState(vTarget, tween(if (vTilt != null) 60 else 400))	// décalage adouci
	PontBackdrop(
		inModifier = if (vTilt == null) Modifier.trackPointer(vPointer) else Modifier,
		inParallax = vParallax,
		inGlow     = inGlow,
		inFade     = inFade
	) {
		BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
			Column(
				Modifier
					.widthIn(max = 440.dp)
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.heightIn(min = maxHeight)
					.padding(horizontal = 24.dp, vertical = 20.dp),
				verticalArrangement = Arrangement.SpaceBetween
			) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					if (inOnBack != null)
					{
						Icon(
							LineIcons.Back, "Retour",
							Modifier
								.size(40.dp)
								.clip(CircleShape)
								.clickable(role = Role.Button, onClick = inOnBack)
								.padding(8.dp),
							tint = PontColors.text
						)
						Spacer(Modifier.size(8.dp))
					}
					PontBrand()
					Spacer(Modifier.weight(1f))
					inActions()
				}
				Column(Modifier.fillMaxWidth().padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = inContent)
			}
		}
	}
}

/*
	Titre des écrans clés, très grand et fin : la première partie en blanc,
	le mot de contexte en gris, le mot fort en blanc (« Vos mots de passe,
	bien gardés. »).
	inLead   : début, en blanc (peut contenir un retour à la ligne)
	inMuted  : mot de contexte, en gris
	inStrong : mot fort, en blanc
*/
@Composable
fun HeroTitle(
	inLead   : String,
	inMuted  : String,
	inStrong : String
)
{
	BasicText(
		buildAnnotatedString {
			append(inLead)
			withStyle(SpanStyle(color = PontColors.textMuted)) { append(inMuted) }
			append(inStrong)
		},
		style = pontType().hero
	)
}

/*
	Message d'erreur : icône et texte en rouge sourd.
	inText : message ; rien n'est affiché s'il est null
*/
@Composable
fun ErrorLine(inText: String?)
{
	if (inText == null) return
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
		Icon(LineIcons.Alert, null, Modifier.size(18.dp), tint = PontColors.danger)
		BasicText(inText, style = pontType().bodySmall.copy(color = PontColors.danger))
	}
}

/*
	Gabarit des écrans de travail (appareils, réglages), celui de KeePlus :
	fond uni avec un léger voile de brume en haut, grille fine et grain ;
	flèche de retour et titre ; contenu défilant sur 640 dp au plus.
	inTitle   : titre de l'écran
	inOnBack  : retour ; null pour aucun (page de la fenêtre du PC)
	inContent : contenu, en colonne
*/
@Composable
fun WorkScreen(
	inTitle   : String,
	inOnBack  : (() -> Unit)?,
	inContent : @Composable ColumnScope.() -> Unit
)
{
	val vType = pontType()																	// styles de texte
	Box(Modifier.fillMaxSize().background(mistTop()).pontTexture(), contentAlignment = Alignment.TopCenter) {
		Column(
			Modifier
				.fillMaxSize()
				.safeDrawingPadding()
				.verticalScroll(rememberScrollState())
				.widthIn(max = 640.dp)
				.padding(horizontal = 20.dp, vertical = 12.dp),
			verticalArrangement = Arrangement.spacedBy(20.dp)
		) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (inOnBack != null) IconAction(LineIcons.Back, "Retour", inOnBack, inTint = PontColors.text)
				BasicText(inTitle, Modifier.weight(1f).padding(start = if (inOnBack != null) 4.dp else 0.dp, top = 8.dp, bottom = 8.dp), style = vType.headline)
			}
			inContent()
		}
	}
}

/*
	Section des réglages et des listes, celle de KeePlus : titre discret,
	puis une carte (fond surface, contour fin, coins arrondis).
	inTitle   : titre
	inContent : contenu de la section
*/
@Composable
fun SettingsSection(
	inTitle   : String,
	inContent : @Composable ColumnScope.() -> Unit
)
{
	Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
		BasicText(inTitle, Modifier.padding(start = 4.dp), style = pontType().bodySmall)
		Column(
			Modifier
				.fillMaxWidth()
				.clip(RoundedCornerShape(20.dp))
				.background(PontColors.surface)
				.border(1.dp, PontColors.rule, RoundedCornerShape(20.dp))
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			content             = inContent
		)
	}
}

/*
	Trait fin entre deux réglages d'une même section.
*/
@Composable
fun SettingsDivider()
{
	Box(Modifier.fillMaxWidth().height(1.dp).background(PontColors.rule))
}

/*
	Réglage oui ou non : libellé, explication, et deux pastilles
	« Activé » / « Désactivé » (pas d'interrupteur : les pastilles de
	KeePlus).
	inLabel    : libellé
	inHelp     : explication ; null sans
	inOn       : réglage actif
	inOnChange : nouveau choix
*/
@Composable
fun OnOffSetting(
	inLabel    : String,
	inHelp     : String?,
	inOn       : Boolean,
	inOnChange : (Boolean) -> Unit
)
{
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		BasicText(inLabel, style = pontType().body)
		inHelp?.let { BasicText(it, style = pontType().bodySmall) }
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			FilterPill("Activé", inOn) { inOnChange(true) }
			FilterPill("Désactivé", !inOn) { inOnChange(false) }
		}
	}
}
