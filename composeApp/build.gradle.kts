plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        namespace = "uk.co.tomosmaps.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    jvmToolchain(25)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.maplibre.compose:maplibre-compose:0.20.0")
            implementation("org.maplibre.compose:location:0.20.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        androidMain.dependencies {
            runtimeOnly("org.maplibre.compose:maplibre-compose-runtime-vulkan-android:0.20.0")
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            runtimeOnly("org.maplibre.compose:maplibre-compose-runtime-vulkan-linux-x64:0.20.0")
            runtimeOnly("org.maplibre.compose:location-runtime-linux:0.20.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "uk.co.tomosmaps.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
    }
}
