plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}
kotlin { jvmToolchain(21) }
android {
    namespace = "com.onebook.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.onebook.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug { buildConfigField("String", "API_URL", "\"http://10.0.2.2:8080\"") }
        release { buildConfigField("String", "API_URL", "\"https://localhost\"") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation(project(":client:core:domain"))
    implementation(project(":client:core:common"))
    implementation(project(":client:feature:catalog"))
    implementation(project(":client:feature:search"))
    implementation(project(":client:feature:library"))
    implementation(project(":client:feature:player"))
    implementation(compose.components.uiToolingPreview)
    implementation(compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.koin.core)
    implementation(libs.decompose)
    implementation(libs.decompose.compose)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.media3.exoplayer)
}
