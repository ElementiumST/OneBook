pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "OneBook"
include(":detekt-rules")
include(":shared")
include(":server", ":client:core:mobile", ":client:core:desktop")
include(":client:core:domain", ":client:core:data", ":client:core:common")
include(":client:feature:catalog", ":client:feature:search", ":client:feature:library", ":client:feature:player")

include(":client:core:ui")
