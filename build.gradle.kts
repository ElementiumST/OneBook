plugins {
    alias(libs.plugins.sqldelight) apply false
    base
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(files("client", "server", "shared"))
    disableDefaultRuleSets = true
}
dependencies {
    detektPlugins(project(":detekt-rules"))
}
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    include("**/src/**/*.kt")
    exclude("**/build/**")
}
tasks.named("check") {
    dependsOn("detekt", ":detekt-rules:test")
}
allprojects {
    group = "com.onebook"
    version = "0.1.0"
}
