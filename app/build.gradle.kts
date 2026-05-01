import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun loadProps(path: String): Properties {
    val p = Properties()
    val f = rootProject.file(path)
    if (f.exists()) {
        FileInputStream(f).use { p.load(it) }
    }
    return p
}

val envProps = loadProps("env.properties")
val keystoreProps = loadProps("keystore.properties")

fun env(name: String): String =
    envProps.getProperty(name)
        ?: System.getenv(name)
        ?: error("Missing env property: $name")

fun envOpt(name: String): String? =
    envProps.getProperty(name)
        ?: System.getenv(name)

fun ks(name: String): String =
    keystoreProps.getProperty(name)
        ?: System.getenv(name)
        ?: error("Missing keystore property: $name")

android {
    namespace = "com.imkolganov.datagate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.imkolganov.datagate"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.6"

        val githubRepo = (project.findProperty("github.repo") as String?)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "IMKolganov/DataGateAndroid"
        buildConfigField("String", "GITHUB_RELEASES_REPO", "\"$githubRepo\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("arm64-v8a")
        }
    }

    // ---------- Signing (for ANY release variant: devRelease + prodRelease) ----------
    signingConfigs {
        create("release") {
            storeFile = file(ks("storeFile"))
            storePassword = ks("storePassword")
            keyAlias = ks("keyAlias")
            keyPassword = ks("keyPassword")
        }
    }

    buildTypes {
        debug {
            // default debug signing
        }

        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // ---------- Flavors ----------
    flavorDimensions += "env"

    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"

            resValue("string", "app_name", "DataGate Dev")

            buildConfigField("String", "BACKEND_BASE_URL", "\"${env("DEV_BACKEND_URL")}\"")
            buildConfigField("String", "WEB_CLIENT_ID", "\"${env("DEV_WEB_CLIENT_ID")}\"")
            buildConfigField("String", "CRASH_REPORT_TOKEN", "\"${envOpt("DEV_CRASH_REPORT_TOKEN") ?: ""}\"")
        }

        create("prod") {
            dimension = "env"

            resValue("string", "app_name", "DataGate")

            buildConfigField("String", "BACKEND_BASE_URL", "\"${env("PROD_BACKEND_URL")}\"")
            buildConfigField("String", "WEB_CLIENT_ID", "\"${env("PROD_WEB_CLIENT_ID")}\"")
            buildConfigField("String", "CRASH_REPORT_TOKEN", "\"${envOpt("PROD_CRASH_REPORT_TOKEN") ?: ""}\"")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.credential.manager.core)
    implementation(libs.androidx.credential.manager.play)
    implementation(libs.googleid)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.material)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.okhttp.mockwebserver)
}
