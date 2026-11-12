plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}
kotlin {
    androidTarget()
    jvm("desktop")
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)


        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)

        }
    }
}
android {
    namespace = "com.onebook.core.domain"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
