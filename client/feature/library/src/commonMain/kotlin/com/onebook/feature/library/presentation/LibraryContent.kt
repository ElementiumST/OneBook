package com.onebook.feature.library.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onebook.core.ui.BooksContent
import com.onebook.core.ui.BooksState
import com.onebook.core.domain.Book
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun LibraryContent(
    viewModel: LibraryViewModel,
    onPlay: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(
        viewModel,
    ) { viewModel.refresh() }
    Column(
        modifier.padding(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Библиотека",
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(
                onClick = viewModel::refresh,
            ) {
                Text(
                    "Обновить",
                )
            }
        }
        BooksContent(
            state,
            onPlay,
            viewModel::remove,
            "Удалить",
            Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun LibraryContentPreview() {
    MaterialTheme {
        LibraryContent(
            viewModel = object : LibraryViewModel {
                override val state = MutableStateFlow<BooksState>(BooksState(books = listOf(Book("preview", "Мастер и Маргарита", "Михаил Булгаков"))))
                override val message = MutableStateFlow<String?>(null)
                override fun clearMessage() {}
                override fun refresh() {}
                override fun add(book: Book) {}
                override fun remove(book: Book) {}
            },
            onPlay = {},
        )
    }
}
