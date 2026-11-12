plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}
kotlin { jvmToolchain(21) }
application { mainClass.set("com.onebook.server.MainKt") }
dependencies {
    implementation(project(":shared"))
    implementation(libs.coroutines.core)
    implementation(libs.jsoup)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.json)
    implementation(libs.serialization.json)
    implementation(libs.logback)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test)
    testImplementation(libs.ktor.client.content.negotiation)
}
tasks.test { useJUnitPlatform() }
tasks.named<JavaExec>("run") { workingDir(rootProject.projectDir) }

tasks.register<Test>("aknigaTest") {
    description = "Runs the offline Akniga parser and provider tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("com.onebook.server.akniga.*") }
}

tasks.register<JavaExec>("aknigaParserLab") {
    description = "Inspects Akniga fixtures by default; use -PaknigaUrl or -PaknigaLimit for live requests."
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.onebook.server.akniga.AknigaParserLabKt")
    workingDir(rootProject.projectDir)
    systemProperty("file.encoding", "UTF-8")
    listOf("aknigaUrl", "aknigaFile", "aknigaLimit", "aknigaMode").forEach { property ->
        providers.gradleProperty(property).orNull?.let { args("--$property", it) }
    }
}
