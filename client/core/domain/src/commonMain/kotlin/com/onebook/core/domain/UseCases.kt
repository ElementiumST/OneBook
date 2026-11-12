package com.onebook.core.domain

class GetBooks(private val repository: BookRepository) {
    suspend operator fun invoke() = repository.books()
}

class SearchBooks(private val repository: BookRepository) {
    suspend operator fun invoke(query: String) = repository.books(query.trim())
}

class GetLibrary(private val repository: LibraryRepository) {
    suspend operator fun invoke() = repository.books()
}

class AddToLibrary(private val repository: LibraryRepository) {
    suspend operator fun invoke(book: Book) = repository.add(book.id)
}

class RemoveFromLibrary(private val repository: LibraryRepository) {
    suspend operator fun invoke(book: Book) = repository.remove(book.id)
}

class GetPlaybackSource(private val repository: BookRepository) {
    suspend operator fun invoke(book: Book) = repository.playbackSource(book.id)
}

class GetListeningPosition(private val repository: PositionRepository) {
    suspend operator fun invoke(book: Book) = repository.get(book.id).coerceAtLeast(0)
}

class SaveListeningPosition(private val repository: PositionRepository) {
    suspend operator fun invoke(book: Book, positionMs: Long) {
        require(positionMs >= 0) { "Position must not be negative" }
        repository.save(book.id, positionMs)
    }
}
