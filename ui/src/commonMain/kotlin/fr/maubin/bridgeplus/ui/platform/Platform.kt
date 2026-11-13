package fr.maubin.bridgeplus.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset

/*
	Inclinaison de l'appareil, de -1 à 1 sur chaque axe, lissée, pour la
	parallaxe de l'emblème ; null là où il n'y a pas de capteur (PC : la
	parallaxe suit alors la souris).
*/
@Composable
expect fun rememberTilt(): State<Offset>?
