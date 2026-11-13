package fr.maubin.pontplus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.maubin.pontplus.peers.PhoneLinkState
import fr.maubin.pontplus.ui.components.LinkHero
import fr.maubin.pontplus.ui.components.PontCard
import fr.maubin.pontplus.ui.components.SectionTitle
import fr.maubin.pontplus.ui.theme.Pont
import fr.maubin.pontplus.ui.theme.PontLogo

/*
	Accueil : état de la liaison en grand, autorisations qui manquent, puis
	les derniers échanges (vides tant que le presse-papiers et les fichiers
	ne circulent pas : étapes suivantes).
	inState   : état de la liaison
	inLook    : état en clair
	inResumes : retours à l'écran (relecture des autorisations)
*/
@Composable
fun HomeScreen(
	inState   : PhoneLinkState,
	inLook    : PhoneLook,
	inResumes : Int
)
{
	Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			PontLogo(28.dp)
			Spacer(Modifier.width(10.dp))
			Text("PontPlus", style = Pont.type.title)
		}
		LinkHero(inLook.look, inLook.title, inLook.detail)
		if (inState.pcs.isNotEmpty()) MissingPermissions(inResumes)
		inState.forgotten?.let { Text("$it a oublié ce téléphone : la paire est effacée des deux côtés.", style = Pont.type.body.copy(color = Pont.colors.textMuted)) }
		Column {
			SectionTitle("Derniers échanges")
			PontCard {
				Text("Rien d'échangé pour l'instant.", style = Pont.type.body)
				Text("Les textes copiés et les fichiers envoyés entre le téléphone et le PC apparaîtront ici.", style = Pont.type.bodySmall)
			}
		}
	}
}
