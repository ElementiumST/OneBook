package com.onebook.feature.player.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onebook.core.domain.Book
import com.onebook.core.domain.PlaybackFailure
import com.onebook.core.domain.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

private fun time(ms: Long): String {
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}

@Composable
fun PlayerContent(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val playback by viewModel.state.collectAsState()
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Плеер",
            style = MaterialTheme.typography.headlineSmall,
        )
        val current = playback.book
        if (current == null) Text(
            "Выберите книгу для прослушивания",
        )
        else {
            Text(
                current.title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                current.author,
            )
            var seeking by remember(
                current.id,
            ) { mutableStateOf<Float?>(null) }
            Slider(
                value = seeking ?: playback.positionMs.toFloat().coerceIn(0f, playback.durationMs.coerceAtLeast(1).toFloat()),
                onValueChange = { seeking = it },
                onValueChangeFinished = { seeking?.let { viewModel.seek(it.toLong()) }; seeking = null },
                valueRange = 0f..playback.durationMs.coerceAtLeast(1).toFloat(),
                enabled = playback.durationMs > 0,
            )
            Text(
                "${time(playback.positionMs)} / ${time(playback.durationMs)}",
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { viewModel.seek((playback.positionMs - 15000).coerceAtLeast(0)) },
                ) {
                    Text(
                        "−15 с",
                    )
                }
                Button(
                    onClick = viewModel::toggle,
                ) {
                    Text(
                        if (playback.playing) "Пауза" else "Слушать",
                    )
                }
                TextButton(
                    onClick = { viewModel.seek((playback.positionMs + 15000).coerceAtMost(playback.durationMs)) },
                ) {
                    Text(
                        "+15 с",
                    )
                }
            }
        }
        playback.failure?.let {
            val message = when (it) {
                PlaybackFailure.OpenBook -> "Не удалось открыть книгу. Повторите попытку."
                PlaybackFailure.PlayAudio -> "Не удалось воспроизвести аудио"
                PlaybackFailure.SavePosition -> "Не удалось сохранить позицию на сервере"
            }
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Preview
@Composable
private fun PlayerContentPreview() {
    MaterialTheme {
        PlayerContent(
            viewModel = object : PlayerViewModel {
                override val state = MutableStateFlow<PlayerState>(PlayerState(book = Book("preview", "Мастер и Маргарита", "Михаил Булгаков"), durationMs = 3600000))
                override fun open(book: Book) {}
                override fun toggle() {}
                override fun seek(positionMs: Long) {}
                override fun pauseAndSave() {}
                override suspend fun finishPlayback() {}
            },
        )
    }
}
