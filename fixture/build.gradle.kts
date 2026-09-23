plugins { alias(libs.plugins.android.application) }
android {
    namespace = "dev.notiq.fixture"
    compileSdk = 36
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    defaultConfig { applicationId = "dev.notiq.fixture"; minSdk = 29; targetSdk = 36; versionCode = 1; versionName = "1.0" }
}
