package fr.maubin.pontplus.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset

/*
	Pas de capteur d'inclinaison sur PC : la parallaxe suit la souris.
*/
@Composable
actual fun rememberTilt(): State<Offset>? = null
