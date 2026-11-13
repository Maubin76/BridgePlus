// Module commun : liaison chiffrée entre le téléphone et le PC (trames, sessions,
// découverte, appairage), appareils appairés, réglages. Kotlin pur sur la JVM,
// sans Android ni interface : partagé par l'appli Android et l'appli PC.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.serialization)
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_17)
	}
}

dependencies {
	api(libs.kotlinx.coroutines.core)
	api(libs.kotlinx.serialization.json)													// messages entre appareils, fichiers des appareils

	testImplementation(libs.junit)
}
