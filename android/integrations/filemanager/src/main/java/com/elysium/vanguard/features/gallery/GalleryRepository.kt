package com.elysium.vanguard.features.gallery

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
class GalleryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getMediaFiles(): Flow<List<GalleryMedia>> = flow {
        val mediaList = mutableListOf<GalleryMedia>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )

        // Query Images
        queryMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, mediaList)
        // Query Videos
        queryMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, mediaList)

        emit(mediaList.sortedByDescending { it.dateModified })
    }.flowOn(Dispatchers.IO)

    private fun queryMediaStore(
        uri: android.net.Uri,
        projection: Array<String>,
        list: MutableList<GalleryMedia>
    ) {
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    try {
                        val name = if (nameColumn >= 0) cursor.getString(nameColumn) ?: "Untitled" else "Untitled"
                        val path = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                        val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn) ?: "image/*" else "image/*"
                        val date = if (dateColumn >= 0) cursor.getLong(dateColumn) else 0L
                        val id = if (idColumn >= 0) cursor.getLong(idColumn) else 0L

                        if (path.isNotEmpty()) {
                            list.add(
                                GalleryMedia(
                                    id = id,
                                    name = name,
                                    path = path,
                                    mimeType = mime,
                                    dateModified = date
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    fun deleteMedia(media: GalleryMedia): Boolean {
        return try {
            val contentUri = if (media.mimeType.startsWith("video")) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = android.content.ContentUris.withAppendedId(contentUri, media.id)
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Unit
            false
        }
    }
}

data class GalleryMedia(
    val id: Long,
    val name: String,
    val path: String,
    val mimeType: String,
    val dateModified: Long,
    val isFavorite: Boolean = false
)
