package com.homeassignment.imageloader.internal

import com.homeassignment.imageloader.Fetcher
import com.homeassignment.imageloader.HttpException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Default [Fetcher] built on [HttpURLConnection]; expects to be called on an IO dispatcher. */
internal class HttpUrlFetcher(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : Fetcher {

    override suspend fun fetch(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = true
            // Caching is handled by the loader itself.
            connection.useCaches = false

            val code = connection.responseCode
            if (code !in 200..299) throw HttpException(code, url)

            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        // Lets cache invalidation abort a download that is no longer needed.
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
