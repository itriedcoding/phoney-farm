plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

group = "com.phonefarm"
version = "1.2.1"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

compose.desktop {
    application {
        mainClass = "com.phonefarm.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            packageName = "PhoneFarmDesktop"
            packageVersion = "1.2.1"
            description = "Phone Farm desktop agent"
            vendor = "Phone Farm"
            copyright = "MIT"
            windows {
                menuGroup = "Phone Farm"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8f2d5f0e-2f8d-4b8f-9f1d-2c7f9c5a1b30"
            }
        }
    }
}
