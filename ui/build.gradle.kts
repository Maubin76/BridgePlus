// Interface commune aux applis Android et PC : thème (couleurs, polices), logo
// et composants de la direction artistique (voir DESIGN.md). Compose
// Multiplatform, compilé pour Android et pour la JVM du PC ; s'appuie sur le
// module :core.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.kmp.library)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.compose.multiplatform)
}

kotlin {
	android {
		namespace  = "fr.maubin.pontplus.ui"
		compileSdk = 37
		minSdk     = 36
		androidResources {
			enable = true															// ressources Compose (polices) empaquetées dans l'APK
		}
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}
	jvm("desktop") {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
		}
	}

	sourceSets {
		commonMain.dependencies {
			api(project(":core"))
			api(compose.runtime)
			api(compose.foundation)
			api(compose.ui)
			api(libs.compose.desktop.material3)
			implementation(compose.components.resources)
		}
	}
}

compose.resources {
	publicResClass = true
	packageOfResClass = "fr.maubin.pontplus.ui.resources"
}
