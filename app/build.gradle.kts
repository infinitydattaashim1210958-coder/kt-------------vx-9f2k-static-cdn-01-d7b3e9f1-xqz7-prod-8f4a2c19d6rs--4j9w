import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    namespace = "com.kyronix.swadhyaa"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kyronix.swadhyaa"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProps.getProperty("gemini_api_key", "")}\"")
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
        }
        create("prod") {
            dimension = "env"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    }
    buildFeatures {
    buildConfig = true
    }
}

dependencies {
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation("org.json:json:20240303")
}
