package com.onebook.feature.search.presentation

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
fun SearchContent(
    viewModel: SearchViewModel,
    onPlay: (Book) -> Unit,
    onAdd: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Поиск",
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            query,
            viewModel::search,
            label = {
                Text(
                    "Название или автор",
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isBlank()) Text(
            "Введите название книги или имя автора",
        )
        else BooksContent(
            state,
            onPlay,
            onAdd,
            "В библиотеку",
            Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun SearchContentPreview() {
    MaterialTheme {
        SearchContent(
            viewModel = object : SearchViewModel {
                override val query = MutableStateFlow<String>("Пример")
                override val state = MutableStateFlow<BooksState>(BooksState(books = listOf(Book("preview", "Мастер и Маргарита", "Михаил Булгаков"))))
                override fun search(query: String) {}
            },
            onPlay = {},
            onAdd = {},
        )
    }
}
