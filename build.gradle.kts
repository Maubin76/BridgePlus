// Fichier de build racine : déclare les plugins communs, appliqués dans les modules,
// et calcule la version commune aux deux applis.
plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.android.kmp.library) apply false
	alias(libs.plugins.compose.multiplatform) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.kotlin.serialization) apply false
}

// Version dérivée du nombre de commits (comme SportPlus) : elle avance à chaque commit,
// sans intervention, et un APK installé renvoie au commit exact qui l'a produit. Hors
// dépôt git, la construction continue avec 0, qui se voit aussitôt dans le nom du fichier.
val kCommitCount: Int = runCatching {
	providers.exec {
		commandLine("git", "rev-list", "--count", "HEAD")
	}.standardOutput.asText.get().trim().toInt()
}.getOrDefault(0)																	// nombre de commits

extra["bridgeVersion"]     = "1.$kCommitCount"											// version affichée (Android et PC)
extra["bridgeVersionCode"] = kCommitCount.coerceAtLeast(1)								// numéro interne Android

// Zéro avertissement : tout avertissement du compilateur Kotlin fait échouer la construction.
subprojects {
	tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
		compilerOptions.allWarningsAsErrors.set(true)
	}
}
