import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("cc.star0.wear.lib.miwearhaptics")
}

val releaseSigningEnvironment = listOf(
    "ANDROID_KEYSTORE_FILE",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }

val hasReleaseSigning = releaseSigningEnvironment.values.any { it != null }

if (hasReleaseSigning) {
    val missingVariables = releaseSigningEnvironment.filterValues { it.isNullOrBlank() }.keys
    require(missingVariables.isEmpty()) {
        "Missing release signing environment variables: ${missingVariables.joinToString()}"
    }
}

android {
    namespace = "cc.star0.wear.pomodoro"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cc.star0.wear.pomodoro"
        minSdk = 25
        targetSdk = 37
        versionCode = 5
        versionName = "0.3.2"
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseSigningEnvironment["ANDROID_KEYSTORE_FILE"]))
                storePassword = releaseSigningEnvironment["ANDROID_KEYSTORE_PASSWORD"]
                keyAlias = releaseSigningEnvironment["ANDROID_KEY_ALIAS"]
                keyPassword = releaseSigningEnvironment["ANDROID_KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "DebugProbesKt.bin"
        }
    }
}

androidComponents {
    val apkProjectName = rootProject.name
    onVariants(selector().withBuildType("release")) { variant ->
        // Keep in sync with res/xml/locales_config.xml; default resources remain available.
        variant.androidResources.localeFilters.addAll("en", "zh")
        // Prefer a smaller download; Android extracts these libraries when installing.
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
        variant.packaging.jniLibs.useLegacyPackagingFromBundle.set(true)
        // Version markers are tooling metadata; built-ins are only read by kotlin-reflect,
        // which is not a runtime dependency. Keep service registrations and license notices.
        variant.packaging.resources.excludes.addAll("META-INF/*.version", "**/*.kotlin_builtins")
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier ?: "universal"
            output.outputFileName.set(
                output.versionName.map { version -> "$apkProjectName-$version-$abi.apk" },
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.wear.compose.navigation3)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
}
