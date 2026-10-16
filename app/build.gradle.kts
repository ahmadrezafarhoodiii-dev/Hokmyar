plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "hokm.assistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "hokm.assistant.cardtracker"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "0.3-card-tracker"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
