plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":client:core:domain"))
    implementation(project(":client:core:common"))
    implementation(project(":client:feature:catalog"))
    implementation(project(":client:feature:search"))
    implementation(project(":client:feature:library"))
    implementation(project(":client:feature:player"))
    implementation(compose.desktop.currentOs)
    implementation(compose.components.uiToolingPreview)
    implementation(compose.material3)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.koin.core)
    implementation(libs.decompose)
    implementation(libs.decompose.compose)
    implementation(libs.ktor.client.cio)
    implementation(libs.coroutines.swing)
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").contains("aarch64")
    val platform = when {
        os.contains("win") -> "win"
        os.contains("mac") -> if (arm) "mac-aarch64" else "mac"
        else -> if (arm) "linux-aarch64" else "linux"
    }
    listOf("base", "graphics", "media").forEach {
        implementation("org.openjfx:javafx-$it:21.0.7:$platform")
    }
}
compose.desktop {
    application { mainClass = "com.onebook.desktop.MainKt" }
}
