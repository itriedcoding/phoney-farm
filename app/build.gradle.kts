plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystore = rootProject.file("signing/phonefarm.jks")

android {
    namespace = "com.phonefarm.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.phonefarm.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.2.1"

        resourceConfigurations += listOf("en")
        buildConfigField("String", "REPOSITORY", "\"itriedcoding/phoney-farm\"")
        buildConfigField("String", "RELEASES_API", "\"https://api.github.com/repos/itriedcoding/phoney-farm/releases\"")
        buildConfigField("String", "RELEASES_PAGE", "\"https://github.com/itriedcoding/phoney-farm/releases\"")
    }

    signingConfigs {
        if (releaseKeystore.exists()) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PHONEFARM_STORE_PASSWORD") ?: "phonefarm-release"
                keyAlias = System.getenv("PHONEFARM_KEY_ALIAS") ?: "phonefarm"
                keyPassword = System.getenv("PHONEFARM_KEY_PASSWORD") ?: "phonefarm-release"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-service:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
