package com.onebook.feature.catalog.presentation

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
fun CatalogContent(
    viewModel: CatalogViewModel,
    onPlay: (Book) -> Unit,
    onAdd: (Book) -> Unit,
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
                "Каталог",
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
            onAdd,
            "В библиотеку",
            Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun CatalogContentPreview() {
    MaterialTheme {
        CatalogContent(
            viewModel = object : CatalogViewModel {
                override val state = MutableStateFlow<BooksState>(BooksState(books = listOf(Book("preview", "Мастер и Маргарита", "Михаил Булгаков"))))
                override fun refresh() {}
            },
            onPlay = {},
            onAdd = {},
        )
    }
}
