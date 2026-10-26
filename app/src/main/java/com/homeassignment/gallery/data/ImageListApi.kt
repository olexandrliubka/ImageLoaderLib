package com.homeassignment.gallery.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads the JSON list of images. */
class ImageListApi(
    private val url: String = IMAGE_LIST_URL,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun fetchImages(): List<ImageItem> = withContext(ioDispatcher) {
        parseImageList(get(url))
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val IMAGE_LIST_URL = "https://zipoapps-storage-test.nyc3.digitaloceanspaces.com/image_list.json"
        private const val TIMEOUT_MILLIS = 15_000

        /** Parses `[{"id": 0, "imageUrl": "https://..."}, ...]`. */
        fun parseImageList(json: String): List<ImageItem> {
            val array = JSONArray(json)
            return List(array.length()) { index ->
                val item = array.getJSONObject(index)
                ImageItem(id = item.getInt("id"), url = item.getString("imageUrl"))
            }
        }
    }
}
