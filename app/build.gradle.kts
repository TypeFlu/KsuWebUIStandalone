@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

fun String.execute(execOperations: ExecOperations, workingDir: File): String {
    val output = ByteArrayOutputStream()
    val errorOutput = ByteArrayOutputStream()
    val result = execOperations.exec {
        commandLine = this@execute.split("\\s".toRegex())
        this.workingDir = workingDir
        standardOutput = output
        this.errorOutput = errorOutput
        isIgnoreExitValue = true
    }

    return if (result.exitValue == 0) {
        output.toByteArray().toString(Charsets.UTF_8).trim()
    } else {
        project.logger.warn(
            "Command '${this@execute}' failed in $workingDir " +
                    "with exit code ${result.exitValue}. " +
                    "Error: ${errorOutput.toByteArray().toString(Charsets.UTF_8).trim()}"
        )
        "1.1"
    }
}

val gitCommitCount: Int = try {
    val execOps = project.objects.newInstance(ExecOperations::class.java)
    "git rev-list HEAD --count".execute(execOps, project.rootDir)
        .toIntOrNull() ?: 0
} catch (e: Exception) {
    project.logger.warn("Failed to get git commit count: ${e.message}. Defaulting to 1.1.")
    1
}

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
        versionCode = gitCommitCount
        versionName = "1.0"
        setProperty("archivesBaseName", "$applicationId-$versionName-$versionCode")
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

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)
}
