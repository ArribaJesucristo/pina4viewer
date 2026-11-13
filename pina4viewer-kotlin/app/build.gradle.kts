plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bone.android.a4v.oficial"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bone.android.a4v.oficial"
        minSdk = 21
        targetSdk = 34
        versionCode = 2050
        versionName = "8.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = System.getenv("KEYSTORE_FILE")
        ?: (project.findProperty("KEYSTORE_FILE") as? String)
    val releaseKeystoreFile = releaseKeystorePath?.let { file(it) }?.takeIf { it.exists() }
    val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
        ?: (project.findProperty("KEYSTORE_PASSWORD") as? String)
    val releaseKeyAlias = System.getenv("KEY_ALIAS")
        ?: (project.findProperty("KEY_ALIAS") as? String)
    val releaseKeyPassword = System.getenv("KEY_PASSWORD")
        ?: (project.findProperty("KEY_PASSWORD") as? String)
    val hasReleaseSigning = releaseKeystoreFile != null &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Coroutines & Lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Network & Parsing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
}

