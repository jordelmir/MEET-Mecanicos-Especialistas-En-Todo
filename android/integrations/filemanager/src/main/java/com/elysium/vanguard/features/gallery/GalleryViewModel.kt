package com.elysium.vanguard.features.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: GalleryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("elysium_gallery_prefs", Context.MODE_PRIVATE)

    private val _mediaFiles = MutableStateFlow<List<GalleryMedia>>(emptyList())
    val mediaFiles: StateFlow<List<GalleryMedia>> = _mediaFiles.asStateFlow()

    private val _albums = MutableStateFlow<List<GalleryAlbum>>(emptyList())
    val albums: StateFlow<List<GalleryAlbum>> = _albums.asStateFlow()

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<GalleryAlbum?>(null)
    val selectedAlbum: StateFlow<GalleryAlbum?> = _selectedAlbum.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _events = MutableSharedFlow<GalleryEvent>()
    val events: SharedFlow<GalleryEvent> = _events.asSharedFlow()

    init {
        loadFavorites()
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getMediaFiles().collect { files ->
                _mediaFiles.value = files.map { it.copy(isFavorite = _favorites.value.contains(it.id)) }
                _albums.value = discoverAlbums(files)
                _isLoading.value = false
            }
        }
    }

    private fun discoverAlbums(files: List<GalleryMedia>): List<GalleryAlbum> {
        return files.groupBy { File(it.path).parentFile?.name ?: "Unknown" }
            .map { (name, mediaList) ->
                GalleryAlbum(name, mediaList)
            }.sortedBy { it.name }
    }

    private fun loadFavorites() {
        val favJson = prefs.getString("favorites", "[]")
        val type = object : TypeToken<Set<Long>>() {}.type
        val favSet: Set<Long> = gson.fromJson(favJson, type) ?: emptySet()
        _favorites.value = favSet
    }

    fun toggleFavorite(mediaId: Long) {
        val currentFavs = _favorites.value.toMutableSet()
        if (currentFavs.contains(mediaId)) {
            currentFavs.remove(mediaId)
        } else {
            currentFavs.add(mediaId)
        }
        _favorites.value = currentFavs
        prefs.edit().putString("favorites", gson.toJson(currentFavs)).apply()

        // Update current media list
        _mediaFiles.value = _mediaFiles.value.map {
            if (it.id == mediaId) it.copy(isFavorite = currentFavs.contains(mediaId)) else it
        }
    }

    fun openMedia(media: GalleryMedia) {
        viewModelScope.launch {
            _events.emit(GalleryEvent.OpenMedia(media))
        }
    }

    fun shareMedia(media: GalleryMedia) {
        viewModelScope.launch {
            _events.emit(GalleryEvent.ShareMedia(media))
        }
    }

    fun setAlbum(album: GalleryAlbum?) {
        _selectedAlbum.value = album
    }

    fun editMedia(media: GalleryMedia) {
        viewModelScope.launch {
            _events.emit(GalleryEvent.EditMedia(media))
        }
    }

    fun setWallpaper(media: GalleryMedia) {
        viewModelScope.launch {
            _events.emit(GalleryEvent.SetWallpaper(media))
        }
    }

    fun deleteMedia(media: GalleryMedia) {
        viewModelScope.launch {
            if (repository.deleteMedia(media)) {
                loadMedia() // Refresh list
            }
        }
    }
}

sealed class GalleryEvent {
    data class OpenMedia(val media: GalleryMedia) : GalleryEvent()
    data class ShareMedia(val media: GalleryMedia) : GalleryEvent()
    data class EditMedia(val media: GalleryMedia) : GalleryEvent()
    data class SetWallpaper(val media: GalleryMedia) : GalleryEvent()
}

data class GalleryAlbum(
    val name: String,
    val media: List<GalleryMedia>
)
