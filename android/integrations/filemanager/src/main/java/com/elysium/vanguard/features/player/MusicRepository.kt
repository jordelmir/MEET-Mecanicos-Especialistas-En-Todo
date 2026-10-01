package com.elysium.vanguard.features.player

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getMusicFiles(): Flow<List<MusicTrack>> = flow {
        val musicList = mutableListOf<MusicTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = null // Include all audio types (AMR, OGG, MIDI, etc.)

        context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                musicList.add(
                    MusicTrack(
                        id = cursor.getLong(idColumn),
                        name = cursor.getString(nameColumn),
                        path = cursor.getString(pathColumn),
                        mimeType = cursor.getString(mimeColumn),
                        album = cursor.getString(albumColumn),
                        artist = cursor.getString(artistColumn),
                        duration = cursor.getLong(durationColumn),
                        dateModified = cursor.getLong(dateColumn)
                    )
                )
            }
        }
        emit(musicList.sortedByDescending { it.dateModified })
    }.flowOn(Dispatchers.IO)
}

data class MusicTrack(
    val id: Long,
    val name: String,
    val path: String,
    val mimeType: String,
    val album: String?,
    val artist: String?,
    val duration: Long,
    val dateModified: Long,
    val isFavorite: Boolean = false
)
