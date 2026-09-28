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
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                list.add(
                    GalleryMedia(
                        id = cursor.getLong(idColumn),
                        name = cursor.getString(nameColumn),
                        path = cursor.getString(pathColumn),
                        mimeType = cursor.getString(mimeColumn),
                        dateModified = cursor.getLong(dateColumn)
                    )
                )
            }
        }
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
