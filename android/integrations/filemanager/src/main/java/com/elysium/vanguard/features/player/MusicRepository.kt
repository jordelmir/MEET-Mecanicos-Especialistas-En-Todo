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

        try {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val mimeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dateColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    try {
                        val name = if (nameColumn >= 0) cursor.getString(nameColumn) ?: "Unknown Track" else "Unknown Track"
                        val id = if (idColumn >= 0) cursor.getLong(idColumn) else 0L
                        val rawPath = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                        val path = if (rawPath.isNotEmpty()) rawPath else android.content.ContentUris.withAppendedId(uri, id).toString()
                        val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn) ?: "audio/*" else "audio/*"
                        val album = if (albumColumn >= 0) cursor.getString(albumColumn) else null
                        val artist = if (artistColumn >= 0) cursor.getString(artistColumn) else null
                        val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                        val date = if (dateColumn >= 0) cursor.getLong(dateColumn) else 0L

                        musicList.add(
                            MusicTrack(
                                id = id,
                                name = name,
                                path = path,
                                mimeType = mime,
                                album = album,
                                artist = artist,
                                duration = duration,
                                dateModified = date
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        // Fallback & direct storage scan for full offline discovery
        try {
            val audioExtensions = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "wma")
            val existingPaths = musicList.map { it.path }.toMutableSet()
            val candidateDirs = listOf(
                java.io.File("/sdcard/Music"),
                java.io.File("/sdcard/Download"),
                java.io.File("/sdcard/Podcasts"),
                java.io.File("/sdcard/Recordings"),
                java.io.File("/sdcard/Notifications"),
                java.io.File("/sdcard/Ringtones"),
                java.io.File(android.os.Environment.getExternalStorageDirectory(), "Music"),
                java.io.File(android.os.Environment.getExternalStorageDirectory(), "Download"),
            )

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().maxDepth(3).filter { it.isFile && it.extension.lowercase() in audioExtensions }.forEach { file ->
                        if (existingPaths.add(file.absolutePath)) {
                            musicList.add(
                                MusicTrack(
                                    id = file.absolutePath.hashCode().toLong(),
                                    name = file.nameWithoutExtension,
                                    path = file.absolutePath,
                                    mimeType = "audio/${file.extension.lowercase()}",
                                    album = file.parentFile?.name ?: "Almacenamiento Local",
                                    artist = "Dispositivo",
                                    duration = 0L,
                                    dateModified = file.lastModified() / 1000L
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

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
