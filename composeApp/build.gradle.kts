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

        val androidMain by getting {
            dependencies {
                implementation("org.maplibre.compose:maplibre-compose-runtime-vulkan-android:0.20.0")
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
