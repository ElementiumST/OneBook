package com.onebook.server.infrastructure.providers.akniga

import com.onebook.server.domain.provider.BookMetadata
import com.onebook.server.domain.provider.ProviderBook
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Parses only public metadata; no scripts, player endpoints or audio downloads are executed. */
class AknigaHtmlParser {
    fun parseCatalog(html: String, pageUrl: String = AKNIGA_ROOT): AknigaCatalogPage {
        val url = portalUrl(pageUrl)
        val currentPage = catalogPageNumber(url)
        val document = Jsoup.parse(html, url)
        val cards = document.select(".content__main__articles--pageBooks").filterNot { card ->
            // The portal interleaves editorial collections with book cards using the same base class.
            card.hasClass("collection-item-container") && !card.hasAttr("data-bid") &&
                card.selectFirst("a.content__article-main-link[href]")?.let {
                    portalUrl(it.attr("href"), url).startsWith(AKNIGA_ROOT + "collection/")
                } == true
        }
        if (cards.isEmpty()) fail(url, "No catalog cards found; refusing an unverified empty catalog")
        val navigation = document.selectFirst(".page__nav") ?: fail(url, "Catalog pagination is missing")
        val entries = cards.map { card ->
            val id = bookId(card, url)
            val link = card.selectFirst("a.content__article-main-link[href]") ?: fail(url, "Book $id has no link")
            val bookUrl = portalUrl(link.attr("href"), url)
            val authors = names(card, ".additional-info a[href*=/author/]")
            val narrators = names(card, ".additional-info a[href*=/performer/]")
            val title = headingTitle(card.selectFirst("h2.caption__article-main")?.text().orEmpty())
            val abbreviated = card.select(".additional-info .link__action--author").any {
                Regex("и\\s+др\\.", RegexOption.IGNORE_CASE).containsMatchIn(it.text())
            }
            val metadata = if (title == null || authors.isBlank() || narrators.isBlank() || abbreviated) null else {
                BookMetadata(
                    title = title,
                    author = authors,
                    narrator = narrators,
                    coverUrl = imageUrl(card.selectFirst(".article--cover img"), "src"),
                    series = series(card.selectFirst(".book-series a[href]")),
                    durationSeconds = duration(card.selectFirst(".link__action--label--time")?.text(), bookUrl),
                )
            }
            AknigaCatalogEntry(id, bookUrl, metadata)
        }
        if (entries.map { it.externalId }.distinct().size != entries.size) fail(url, "Repeated book ID on one page")
        val nextLinks = buildList {
            navigation.select("a.page__nav--next[href]").forEach { add(portalUrl(it.attr("href"), url)) }
            document.select(".js-loadmore-button[data-url]").forEach { add(portalUrl(it.attr("data-url"), url)) }
        }.distinct()
        if (nextLinks.size > 1) fail(url, "Conflicting next page URLs")
        val next = nextLinks.singleOrNull()
        if (next != null && catalogPageNumber(next) != currentPage + 1) fail(url, "Next page is not sequential: $next")
        val laterPagesExist = navigation.select("a[href]").any {
            val href = portalUrl(it.attr("href"), url)
            catalogPageNumber(href) > currentPage
        }
        if (next == null && laterPagesExist) fail(url, "Next page link is missing before the end of the catalog")
        return AknigaCatalogPage(entries, next)
    }

    fun parseBook(html: String, pageUrl: String): ProviderBook {
        val url = portalUrl(pageUrl)
        val document = Jsoup.parse(html, url)
        val article = document.select("article.book--page[data-bid]").singleOrNull()
            ?: fail(url, "Expected exactly one book article")
        val id = bookId(article, url)
        val canonical = document.selectFirst("link[rel=canonical][href]")?.attr("href")
            ?.let { portalUrl(it, url) } ?: url
        val title = article.selectFirst(".caption__article-title")?.text()?.takeIf { it.isNotBlank() }
            ?: headingTitle(article.selectFirst("h1.caption__article-main")?.text().orEmpty())
            ?: fail(url, "Book title is missing")
        val authors = names(article, "a[rel=author]").ifBlank { fail(url, "Book author is missing") }
        val narrators = names(article, "a[rel=performer]").ifBlank { fail(url, "Book narrator is missing") }
        val cover = document.selectFirst("meta[property=og:image][content]")
        return ProviderBook(
            externalId = id,
            bookUrl = canonical,
            metadata = BookMetadata(
                title = title,
                author = authors,
                narrator = narrators,
                coverUrl = imageUrl(cover, "content") ?: imageUrl(article.selectFirst(".book--header img"), "src"),
                series = series(article.selectFirst("a.link__series[href]")),
                durationSeconds = duration(article.selectFirst(".book-duration-$id")?.text()
                    ?: article.selectFirst(".caption__article-duration")?.text(), url),
            ),
        )
    }

    private fun bookId(element: Element, url: String): String = element.attr("data-bid").takeIf {
        it.matches(Regex("[1-9][0-9]*"))
    } ?: fail(url, "Stable numeric data-bid is missing")

    private fun names(element: Element, selector: String): String = element.select(selector)
        .map { it.text().trim() }.filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }.sortedBy { it.lowercase(Locale.ROOT) }.joinToString(", ")

    private fun headingTitle(heading: String): String? = Regex("^.+?\\s+[–—]\\s+(.+)$")
        .matchEntire(heading)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun series(link: Element?): String? = link?.text()?.replace(Regex("\\s*\\([0-9]+(?:[.,][0-9]+)?\\)$"), "")
        ?.trim()?.takeIf { it.isNotBlank() }

    private fun imageUrl(element: Element?, attribute: String): String? = element?.absUrl(attribute)
        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

    private fun duration(value: String?, url: String): Long? {
        val text = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val units = Regex("([0-9]+)\\s*(час(?:а|ов)?|минут(?:а|ы)?|секунд(?:а|ы)?)", RegexOption.IGNORE_CASE)
        if (text.replace(units, "").isNotBlank() || !units.containsMatchIn(text)) fail(url, "Unrecognized duration: $text")
        return try {
            units.findAll(text).fold(0L) { total, match ->
                val multiplier = when {
                    match.groupValues[2].startsWith("час", ignoreCase = true) -> 3600L
                    match.groupValues[2].startsWith("минут", ignoreCase = true) -> 60L
                    else -> 1L
                }
                Math.addExact(total, Math.multiplyExact(match.groupValues[1].toLong(), multiplier))
            }
        } catch (_: ArithmeticException) {
            fail(url, "Duration is too large: $text")
        } catch (_: NumberFormatException) {
            fail(url, "Duration is too large: $text")
        }
    }

    private fun fail(url: String, message: String): Nothing = throw AknigaParseException("$url: $message")
}
