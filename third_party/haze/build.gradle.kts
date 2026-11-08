plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.drewhamilton.poko")
}

android {
    namespace = "dev.chrisbanes.haze"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.compose.ui:ui:1.8.0")
            implementation("org.jetbrains.compose.foundation:foundation:1.8.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity:1.10.1")
            implementation("androidx.core:core-ktx:1.15.0")
            implementation("androidx.tracing:tracing-ktx:1.3.0")
        }
        all { languageSettings.optIn("dev.chrisbanes.haze.ExperimentalHazeApi") }
    }
}

poko { pokoAnnotation.set("dev/chrisbanes/haze/Poko") }
