@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Read version properties from gradle.properties
val versionProps = Properties()
val versionPropsFile = rootProject.file("gradle.properties")
if (versionPropsFile.exists()) {
    try {
        FileInputStream(versionPropsFile).use { fis ->
            versionProps.load(fis)
        }
    } catch (e: Exception) {
        project.logger.warn("Could not load gradle.properties for versioning: ${e.message}")
    }
} else {
    project.logger.warn("gradle.properties not found for versioning. Using default versions.")
}

val appVersionCode = versionProps.getProperty("ksuwebui.versionCode", "1").toInt()
val appVersionName: String? = versionProps.getProperty("ksuwebui.versionName", "1.0")?.replace("\"", "")

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists() && keystorePropertiesFile.isFile) {
    try {
        FileInputStream(keystorePropertiesFile).use { fis ->
            keystoreProperties.load(fis)
        }
    } catch (e: Exception) {
        project.logger.warn("Could not load keystore.properties: ${e.message}")
    }
} else {
    project.logger.info("keystore.properties not found at ${keystorePropertiesFile.path}. Release builds may use debug signing.")
}

android {
    namespace = "io.github.a13e300.ksuwebui"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.a13e300.ksuwebui"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        val keyAlias = keystoreProperties.getProperty("keyAlias")
        val keyPassword = keystoreProperties.getProperty("keyPassword")
        val storeFileProp = keystoreProperties.getProperty("storeFile")
        val storePassword = keystoreProperties.getProperty("storePassword")

        if (!keyAlias.isNullOrEmpty() && !keyPassword.isNullOrEmpty() && !storeFileProp.isNullOrEmpty() && !storePassword.isNullOrEmpty()) {
            create("release") {
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                this.storeFile = project.file(storeFileProp)
                this.storePassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                project.logger.warn("Release signing configuration not found. Release build will use debug signing.")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    tasks.withType<PackageAndroidArtifact>().configureEach {
        doFirst {
            appMetadata.asFile.orNull?.writeText("")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes.add("**/*")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)
}