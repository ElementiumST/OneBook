package com.onebook.server.infrastructure.providers.akniga

import java.nio.file.Path
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    require(args.size == 1) { "Usage: AknigaExportMainKt <output-directory>" }
    createAknigaHttpClient().use { client ->
        val exporter = AknigaCatalogExporter(AknigaProvider(client), Path.of(args.single())) { progress ->
            println("${progress.updatedAt} status=${progress.status} pages=${progress.pagesCompleted} " +
                "books=${progress.booksExported} failed=${progress.failedBooks} next=${progress.nextPageUrl}")
            System.out.flush()
        }
        val result = exporter.export()
        check(result.status == "COMPLETED") { "Export finished with ${result.failedBooks} unresolved records; see failures.jsonl" }
    }
}
