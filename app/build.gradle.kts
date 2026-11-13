import java.util.Properties

// Module application Android : Kotlin (intégré à AGP 9), Compose.
plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
}

// Clé de signature des versions publiées : lue dans le gradle.properties de
// l'utilisateur, dans local.properties (jamais versionnés) ou passée par la CI
// (-Pbridgeplus.keystore=…). Absente : la version release est signée avec la clé
// de debug, et ne peut pas mettre à jour une appli signée autrement.
val kLocal    = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) } }	// local.properties
val kSigning  = { inName: String -> (findProperty("bridgeplus.$inName") ?: kLocal.getProperty("bridgeplus.$inName"))?.toString() }	// réglage de signature
val kKeystore = kSigning("keystore")?.let { file(it) }?.takeIf { it.exists() }		// fichier de la clé
val kVersion  = rootProject.extra["bridgeVersion"].toString()							// version (nombre de commits)

android {
	namespace  = "fr.maubin.bridgeplus"
	compileSdk = 37

	defaultConfig {
		applicationId = "fr.maubin.bridgeplus"
		minSdk        = 36
		targetSdk     = 37
		versionCode   = rootProject.extra["bridgeVersionCode"] as Int
		versionName   = kVersion
	}

	signingConfigs {
		if (kKeystore != null)
		{
			create("publication") {
				storeFile     = kKeystore
				storePassword = kSigning("keyPassword")
				keyAlias      = kSigning("keyAlias")
				keyPassword   = kSigning("keyPassword")
			}
		}
	}

	buildTypes {
		// Version optimisée (R8), signée avec la clé de publication : les mises à jour
		// s'installent par-dessus sans perdre les appairages. Sans clé, clé de debug.
		release {
			isMinifyEnabled   = true
			isShrinkResources = true
			signingConfig     = signingConfigs.findByName("publication") ?: signingConfigs.getByName("debug")
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	// Zéro avertissement : une remarque de lint fait échouer la construction.
	lint {
		warningsAsErrors = true
		abortOnError     = true
	}

	buildFeatures {
		compose     = true
		buildConfig = true
	}
}

dependencies {
	implementation(project(":core"))
	implementation(project(":ui"))
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.compose.material3)
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.zxing.core)														// lecture du QR code d'appairage du PC
	implementation(libs.androidx.camera.camera2)											// appareil photo (scan du QR code)
	implementation(libs.androidx.camera.lifecycle)
	implementation(libs.androidx.camera.view)
	debugImplementation(libs.androidx.compose.ui.tooling)

	testImplementation(libs.junit)
}
