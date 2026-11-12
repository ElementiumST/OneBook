package com.onebook.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onebook.core.domain.Book
import org.jetbrains.compose.ui.tooling.preview.Preview

data class BooksState(val loading: Boolean = false, val books: List<Book> = emptyList(), val error: String? = null)

@Composable
fun BooksContent(
    state: BooksState,
    onPlay: (Book) -> Unit,
    onAction: (Book) -> Unit,
    actionLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.loading) LinearProgressIndicator(
            Modifier.fillMaxWidth(),
        )
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (!state.loading && state.error == null && state.books.isEmpty()) Text(
            "Книг пока нет",
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.books, key = { it.id }) { book ->
                OutlinedCard(
                    Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            book.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            book.author,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onPlay(book) },
                            ) {
                                Text(
                                    "Слушать",
                                )
                            }
                            TextButton(
                                onClick = { onAction(book) },
                            ) {
                                Text(
                                    actionLabel,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun BooksContentPreview() {
    MaterialTheme {
        BooksContent(
            state = BooksState(books = listOf(Book("preview", "Мастер и Маргарита", "Михаил Булгаков"))),
            onPlay = {},
            onAction = {},
            actionLabel = "В библиотеку",
        )
    }
}
