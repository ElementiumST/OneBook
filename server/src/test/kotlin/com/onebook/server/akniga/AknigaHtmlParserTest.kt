package com.onebook.server.akniga

import com.onebook.server.infrastructure.providers.akniga.AknigaHtmlParser
import com.onebook.server.infrastructure.providers.akniga.AknigaParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AknigaHtmlParserTest {
    private val parser = AknigaHtmlParser()

    @Test fun `book metadata comes from the primary article and retains the numeric book id`() {
        val book = parser.parseBook(fixture("book-series.html"), SERIES_URL)
        assertEquals("113752", book.externalId)
        assertEquals(SERIES_URL, book.bookUrl)
        assertEquals("Артефакт", book.metadata.title)
        assertEquals("Тарасов Роман", book.metadata.author)
        assertEquals("CHUGA", book.metadata.narrator)
        assertEquals("Аномальные территории", book.metadata.series)
        assertEquals(27_720L, book.metadata.durationSeconds)
        assertEquals("https://akniga.org/uploads/artefact-large.jpg", book.metadata.coverUrl)
    }

    @Test fun `authors are complete distinct and ordered consistently`() {
        val book = parser.parseBook(fixture("book-authors.html"), AUTHORS_URL)
        assertEquals("Стругацкий Аркадий, Стругацкий Борис", book.metadata.author)
        assertEquals("Мещеряков Артём", book.metadata.narrator)
        assertEquals(1800L, book.metadata.durationSeconds)
        assertEquals("https://akniga.org/uploads/buffet.jpg", book.metadata.coverUrl)
        assertNull(book.metadata.series)
    }

    @Test fun `catalog reads metadata directly and requests details for abbreviated authors`() {
        val page = parser.parseCatalog(fixture("catalog-page1.html"))
        assertEquals("https://akniga.org/index/page2/", page.nextPageUrl)
        assertEquals(listOf("113752", "113645"), page.entries.map { it.externalId })
        assertEquals("Артефакт", page.entries[0].metadata?.title)
        assertEquals("Аномальные территории", page.entries[0].metadata?.series)
        assertEquals("https://akniga.org/uploads/artefact.jpg", page.entries[0].metadata?.coverUrl)
        assertNull(page.entries[1].metadata)
    }

    @Test fun `last page ends the traversal and does not require a series`() {
        val page = parser.parseCatalog(fixture("catalog-page2.html"), "https://akniga.org/index/page2/")
        assertNull(page.nextPageUrl)
        val book = page.entries.single().bookOrNull()!!
        assertEquals("Комната Наоми", book.metadata.title)
        assertEquals(23_820L, book.metadata.durationSeconds)
        assertNull(book.metadata.series)
    }

    @Test fun `unknown optional values stay null`() {
        val html = fixture("book-authors.html").replace("30 минут", "")
            .replace("<img src=\"/uploads/buffet.jpg\">", "")
        val metadata = parser.parseBook(html, AUTHORS_URL).metadata
        assertNull(metadata.durationSeconds)
        assertNull(metadata.coverUrl)
    }

    @Test fun `missing required metadata fails rather than inventing author or narrator`() {
        val html = fixture("book-authors.html").replace("rel=\"performer\"", "rel=\"other\"")
        assertFailsWith<AknigaParseException> { parser.parseBook(html, AUTHORS_URL) }
    }

    @Test fun `duration supports seconds and rejects unrecognized formats`() {
        val html = fixture("book-authors.html")
        assertEquals(3662L, parser.parseBook(html.replace("30 минут", "1 час 1 минута 2 секунды"), AUTHORS_URL).metadata.durationSeconds)
        assertFailsWith<AknigaParseException> { parser.parseBook(html.replace("30 минут", "завтра"), AUTHORS_URL) }
        assertFailsWith<AknigaParseException> {
            parser.parseBook(html.replace("30 минут", "99999999999999999999999 часов"), AUTHORS_URL)
        }
    }

    @Test fun `challenge page cannot be mistaken for an empty catalog or a book`() {
        assertFailsWith<AknigaParseException> { parser.parseCatalog(fixture("challenge.html")) }
        assertFailsWith<AknigaParseException> { parser.parseBook(fixture("challenge.html"), SERIES_URL) }
    }

    @Test fun `missing next link before the last page fails the import`() {
        val html = fixture("catalog-page1.html").replace("page__nav--next", "removed-next")
            .replace("js-loadmore-button", "removed-loadmore")
        assertFailsWith<AknigaParseException> { parser.parseCatalog(html) }
        assertFailsWith<AknigaParseException> { parser.parseCatalog(html.replace("page__nav", "removed-pagination")) }
    }

    @Test fun `foreign urls and pagination loops are rejected`() {
        assertFailsWith<AknigaParseException> {
            parser.parseCatalog(fixture("catalog-page1.html").replace("/index/page2/", "https://other.example/index/page2/"))
        }
        assertFailsWith<AknigaParseException> {
            parser.parseCatalog(fixture("catalog-page1.html").replace("/index/page2/", "/index/"))
        }
        assertFailsWith<AknigaParseException> {
            parser.parseBook(fixture("book-series.html").replace(SERIES_URL, "https://other.example/book"), SERIES_URL)
        }
    }

    @Test fun `book id cannot be derived from an unstable url or a topic id`() {
        assertFailsWith<AknigaParseException> {
            parser.parseBook(fixture("book-series.html").replace("data-bid=\"113752\"", ""), SERIES_URL)
        }
    }

    @Test fun `editorial collections are not books but an unknown card without a book id still fails`() {
        val html = fixture("catalog-page1.html").replace("</body>", fixture("collection-card.html") + "</body>")
        assertEquals(2, parser.parseCatalog(html).entries.size)
        assertFailsWith<AknigaParseException> {
            parser.parseCatalog(html.replace("data-bid=\"113752\"", ""))
        }
    }
}
