package com.homeassignment.imageloader

import java.io.File
import java.io.IOException

/** Downloads image bytes. Replace the default implementation with [ImageLoader.Builder.fetcher]. */
public fun interface Fetcher {
    /**
     * Downloads [url] and writes the response body to [destination].
     * Called on a background dispatcher; must throw if the download fails.
     */
    @Throws(IOException::class)
    public suspend fun fetch(url: String, destination: File)
}

/** Thrown when the server responds with a non-2xx status code. */
public class HttpException(public val code: Int, url: String) : IOException("HTTP $code for $url")
