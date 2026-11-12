package com.onebook.server.infrastructure.providers.akniga

import com.onebook.server.domain.provider.BookMetadata
import com.onebook.server.domain.provider.ProviderBook
import java.net.URI

data class AknigaCatalogEntry(val externalId: String, val bookUrl: String, val metadata: BookMetadata?) {
    fun bookOrNull(): ProviderBook? = metadata?.let { ProviderBook(externalId, bookUrl, it) }
}

data class AknigaCatalogPage(val entries: List<AknigaCatalogEntry>, val nextPageUrl: String?)

class AknigaParseException(message: String) : IllegalArgumentException(message)

internal const val AKNIGA_ROOT = "https://akniga.org/"

/** Only page URLs from the portal may become request targets; images are never fetched here. */
internal fun portalUrl(value: String, base: String = AKNIGA_ROOT): String {
    val uri = try {
        URI(base).resolve(value).normalize()
    } catch (_: IllegalArgumentException) {
        throw AknigaParseException("Invalid Akniga URL: $value")
    }
    if (uri.scheme != "https" || uri.host != "akniga.org" || uri.port !in listOf(-1, 443) || uri.userInfo != null) {
        throw AknigaParseException("Expected an HTTPS URL on akniga.org: $value")
    }
    return URI(uri.scheme, null, uri.host, -1, uri.path, null, null).toASCIIString()
}

internal fun catalogPageNumber(url: String): Int = when (val path = URI(url).path) {
    "/", "/index/" -> 1
    else -> Regex("/index/page([1-9][0-9]*)/").matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()
        ?: throw AknigaParseException("Unexpected catalog URL: $url")
}
