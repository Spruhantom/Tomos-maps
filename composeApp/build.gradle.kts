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
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.maplibre.compose:maplibre-compose:0.20.0")
                implementation("org.maplibre.compose:location:0.20.0")
                implementation("io.ktor:ktor-client-core:3.3.3")
                implementation("io.ktor:ktor-client-cio:3.3.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:3.3.3")
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("org.maplibre.compose:maplibre-compose-runtime-vulkan-android:0.20.0")
                // Optional Android-only online Google Maps provider; offline maps remain MapLibre.
                implementation("com.google.maps.android:maps-compose:8.4.0")
                // Android API 26–32 do not consistently provide JCA Ed25519.
                implementation("org.bouncycastle:bcprov-jdk18on:1.80")
                // Ferrostar core with a TomOS-owned offline Valhalla custom route provider.
                implementation("com.stadiamaps.ferrostar:core:0.59.0")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
                // Prebuilt Android ABIs plus the JNI wrapper for on-device Valhalla routing.
                implementation("io.github.rallista:valhalla-mobile:0.6.4")
                implementation("io.github.rallista:valhalla-models:0.5.2")
                implementation("io.github.rallista:valhalla-models-config:0.5.2")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.maplibre.compose:maplibre-compose-runtime-vulkan-linux-x64:0.20.0")
                implementation("org.maplibre.compose:location-runtime-linux:0.20.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "uk.co.tomosmaps.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
    }
}
