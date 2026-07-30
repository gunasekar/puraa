import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing is read from a gitignored keystore.properties at the repo
// root (see README). Absent it, debug builds still work; release stays
// unsigned until the file is present.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.isNotEmpty()

// The git tag is the single source of truth for the version — there are no
// hardcoded numbers to drift out of sync. `versionName` is the tag with its
// leading `v` stripped (v0.2.0 → 0.2.0); a build off an untagged commit gets a
// descriptive suffix (0.2.0-3-gabc123). `versionCode` is the commit count, so
// it increases monotonically. Release ritual: `git tag vX.Y.Z && git push --tags`.
fun git(vararg args: String): String? =
    runCatching {
        providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }

val gitVersionName: String = (git("describe", "--tags", "--dirty", "--always") ?: "0.0.0").removePrefix("v")
val gitVersionCode: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

android {
    namespace = "com.puraa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.puraa"
        minSdk = 26
        targetSdk = 35
        versionCode = gitVersionCode
        versionName = gitVersionName

        // Where the self-updater looks for `update.json` (see
        // com.puraa.update.Updater). `releases/latest/download/<asset>` is a
        // permanent redirect to that asset on the newest release, so there is
        // no GitHub API call, no rate limit, and no API shape to track.
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://github.com/gunasekar/puraa/releases/latest/download/update.json\"",
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            // A debug build is `com.puraa.debug` signed with the debug key, so
            // a release APK could never install over it. Don't even check.
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        named("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
}

// Room writes a JSON schema per version here so migrations can be tracked
// and tested. Commit the generated app/schemas/ files.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
}
