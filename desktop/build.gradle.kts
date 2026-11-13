// Appli PC (Windows) : Compose Multiplatform pour la JVM. Reste dans la zone de
// notification et tient la liaison avec les téléphones appairés.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.compose.multiplatform)
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
	implementation(project(":core"))
	implementation(project(":ui"))
	implementation(compose.desktop.currentOs)
	implementation(libs.compose.desktop.material3)
	implementation(libs.kotlinx.coroutines.swing)
	implementation(libs.jna.platform)													// barre de titre, premier plan, DPAPI, réglage des animations (Windows)
	implementation(libs.zxing.core)														// QR code de l'appairage du téléphone

	testImplementation(libs.junit)
	testImplementation(compose.desktop.currentOs)
}

// Version commune aux deux applis (nombre de commits) ; le paquet Windows veut trois nombres (1.12 → 1.12.0).
val kVersion = rootProject.extra["bridgeVersion"].toString()							// version affichée
val kPackage = (kVersion.split('.') + listOf("0", "0", "0")).take(3).joinToString(".")	// version du paquet

// Version lue par l'appli au lancement (présentation aux téléphones) : ressource
// version.properties générée à la construction.
val generateVersion = tasks.register("generateVersion") {
	val vOut = layout.buildDirectory.dir("generated/version")						// dossier généré
	inputs.property("version", kVersion)
	outputs.dir(vOut)
	doLast {
		vOut.get().file("version.properties").asFile.apply { parentFile.mkdirs(); writeText("version=$kVersion\n") }
	}
}
sourceSets.main { resources.srcDir(generateVersion) }

// Tests sans écran, comme sur la CI (Linux) : une fenêtre AWT y serait refusée.
// Le test du vrai presse-papiers de Windows ne tourne que sur demande (il en remplace le
// contenu) : ./gradlew :desktop:test -Pbridgeplus.testPressePapiers
tasks.withType<Test>().configureEach {
	systemProperty("java.awt.headless", "true")
	systemProperty("bridgeplus.testPressePapiers", (findProperty("bridgeplus.testPressePapiers") != null).toString())
	// Captures du README (doc/images) : ./gradlew :desktop:test --tests "*PreviewRenderTest*" -Pbridgeplus.captures
	systemProperty("bridgeplus.captures", (findProperty("bridgeplus.captures") != null).toString())
}

compose.desktop {
	application {
		mainClass = "fr.maubin.bridgeplus.desktop.MainKt"
		// JDK de l'empaquetage (jpackage) : celui de Gradle par défaut (CI) ; en local, si
		// Gradle tourne sur le JDK d'Android Studio (sans jpackage) :
		// ./gradlew :desktop:packageZip -Pbridgeplus.packagingJdk=C:/Java/current
		findProperty("bridgeplus.packagingJdk")?.let { javaHome = it.toString() }

		nativeDistributions {
			targetFormats(TargetFormat.Msi)
			packageName    = "BridgePlus"
			packageVersion = kPackage
			description    = "Le téléphone et le PC, reliés par le Wi-Fi de la maison"
			vendor         = "Mathieu Aubin"
			modules("java.instrument", "jdk.unsupported", "java.xml", "jdk.localedata")

			windows {
				iconFile.set(project.file("icons/bridgeplus.ico"))
				menuGroup   = "BridgePlus"
				upgradeUuid = "5b0f8f4e-6f0e-4c39-9d7e-2f3d1c4a9b61"
			}
		}
	}
}

// Dossier portable zippé : BridgePlus.exe et sa JVM, à dézipper n'importe où, sans
// installation ni droits administrateur.
tasks.register<Zip>("packageZip") {
	group       = "distribution"
	description = "Construit le dossier portable de l'appli PC et le zippe."
	dependsOn("createDistributable")
	from(layout.buildDirectory.dir("compose/binaries/main/app"))
	archiveFileName.set("BridgePlus-$kVersion-windows.zip")
	destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
