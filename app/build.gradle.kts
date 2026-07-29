plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // Add the Google services Gradle plugin; Plugins de Firebase.
    //id("com.android.application"); Quedarse con Alias, es mas moderno
    id("com.google.gms.google-services")
}

android {
    namespace = "com.utch.rentafacil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.utch.rentafacil"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {

    // Import the Firebase BoM; Firebase BoM importaciones.
    implementation(platform(libs.firebase.bom))

    // Dependencia de Firebase Authentication
    implementation (libs.firebase.auth)
    //implementation ("com.google.firebase:firebase-auth")

    //Dependencias de Firebase Firestore (se necesita para el Storage) (fotos, videos, etc.)
    implementation (libs.google.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)

    //Dependencia para descargar fotos de Firebase Storage
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Dependencia de Firebase Cloud Functions (sin el -ktx)
    implementation("com.google.firebase:firebase-functions")

// Dependencia del SDK de Stripe para la pasarela de pagos
    implementation("com.stripe:stripe-android:23.11.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    //implementation(libs.firebase.auth.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}