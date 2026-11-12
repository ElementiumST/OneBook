package com.onebook.server.akniga

internal fun fixture(name: String): String = checkNotNull(object {}.javaClass.getResource("/akniga/$name")) {
    "Missing Akniga fixture: $name"
}.readText(Charsets.UTF_8)

internal const val SERIES_URL = "https://akniga.org/tarasov-roman-artefakt"
internal const val AUTHORS_URL = "https://akniga.org/strugackiy-arkadiy-strugackiy-boris-bufet-mezhplanetnikov"

internal fun fixtureForPath(path: String): String = fixture(when (path) {
    "/" -> "catalog-page1.html"
    "/index/page2/" -> "catalog-page2.html"
    "/tarasov-roman-artefakt" -> "book-series.html"
    "/strugackiy-arkadiy-strugackiy-boris-bufet-mezhplanetnikov" -> "book-authors.html"
    else -> error("Unexpected fixture request: $path")
})
