plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}
kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            api(project(":client:core:domain"))
            api(project(":client:core:data"))
            implementation(project(":client:feature:catalog"))
            implementation(project(":client:feature:search"))
            implementation(project(":client:feature:library"))
            implementation(project(":client:feature:player"))
            api(libs.ktor.client.core)

            implementation(libs.serialization.json)
            api(libs.decompose)
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)

        }
    }
}
android {
    namespace = "com.onebook.core.common"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
