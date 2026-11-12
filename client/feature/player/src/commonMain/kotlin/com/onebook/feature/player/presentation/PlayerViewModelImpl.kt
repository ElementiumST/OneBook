package com.onebook.feature.player.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onebook.core.domain.AudioPlayer
import com.onebook.core.domain.Book
import com.onebook.core.domain.PlayerState
import kotlinx.coroutines.launch

internal class PlayerViewModelImpl(private val player: AudioPlayer) : ViewModel(), PlayerViewModel {
    override val state = player.state

    override fun open(book: Book) { viewModelScope.launch { player.open(book) } }
    override fun toggle() { viewModelScope.launch { player.toggle() } }
    override fun seek(positionMs: Long) { viewModelScope.launch { player.seek(positionMs) } }
    override fun pauseAndSave() { viewModelScope.launch { player.pauseAndSave() } }
    override suspend fun finishPlayback() = player.pauseAndSave()
}
