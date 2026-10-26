package com.homeassignment.imageloader

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.annotation.MainThread
import com.homeassignment.imageloader.internal.BitmapDecoder
import com.homeassignment.imageloader.internal.Clock
import com.homeassignment.imageloader.internal.DiskCache
import com.homeassignment.imageloader.internal.HttpUrlFetcher
import com.homeassignment.imageloader.internal.ImageRequest
import com.homeassignment.imageloader.internal.MemoryCache
import com.homeassignment.imageloader.internal.MemoryKey
import com.homeassignment.imageloader.internal.TargetSize
import com.homeassignment.imageloader.internal.awaitSize
import com.homeassignment.imageloader.internal.knownSizeOrNull
import com.homeassignment.imageloader.internal.requestManager
import com.homeassignment.imageloader.internal.requestManagerOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Downloads, caches and displays remote images.
 *
 * Images are cached in two layers:
 * - an in-memory LRU of decoded bitmaps, keyed by URL and target size;
 * - a disk cache of the original downloaded bytes.
 *
 * Every cached entry is valid for [Builder.cacheTtl] (4 hours by default) counted from the moment
 * the image was downloaded. Caches can be invalidated manually with [invalidate] and [clearCache]
 * (or their `*Async` counterparts for Java callers).
 *
 * Kotlin:
 * ```
 * imageView.load(url) { placeholder(R.drawable.placeholder) }
 * ```
 * Java:
 * ```
 * ImageLoader.get(context).load(url).placeholder(R.drawable.placeholder).into(imageView);
 * ```
 */
public class ImageLoader internal constructor(
    private val memoryCache: MemoryCache,
    private val diskCache: DiskCache,
    private val fetcher: Fetcher,
    private val decoder: BitmapDecoder,
    private val ioDispatcher: CoroutineDispatcher,
    private val decodeDispatcher: CoroutineDispatcher,
    maxParallelDownloads: Int,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Downloads in progress, keyed by URL, so concurrent requests for one URL share one download. */
    private val inFlight = HashMap<String, Download>()

    /** Caps concurrent network requests, so a fast scroll doesn't open dozens of connections. */
    private val downloadPermits = Semaphore(maxParallelDownloads)

    /** Starts building a request for [url]. Finish it with [RequestBuilder.into]. */
    public fun load(url: String?): RequestBuilder = RequestBuilder(this, url)

    /** Cancels the request currently bound to [view], if any. */
    @MainThread
    public fun cancel(view: ImageView) {
        view.requestManagerOrNull()?.clear()
    }

    /**
     * Loads the bitmap for [url] without a target view, going through the same caches as [load].
     *
     * The image is downsampled to be at least [width] x [height]; pass [ORIGINAL_SIZE] (the default)
     * to decode at full resolution.
     */
    @Throws(IOException::class)
    public suspend fun loadBitmap(
        url: String,
        width: Int = ORIGINAL_SIZE,
        height: Int = ORIGINAL_SIZE,
    ): Bitmap = loadBitmap(url, TargetSize(width, height))

    /** Removes [url] from the memory and disk caches; the next request downloads it again. */
    public suspend fun invalidate(url: String) {
        withContext(ioDispatcher) {
            synchronized(inFlight) { inFlight.remove(url) }?.deferred?.cancel()
            memoryCache.remove(url)
            diskCache.remove(url)
        }
    }

    /** Clears the memory and disk caches; every image is downloaded again on its next request. */
    public suspend fun clearCache() {
        withContext(ioDispatcher) {
            val downloads = synchronized(inFlight) { inFlight.values.toList().also { inFlight.clear() } }
            downloads.forEach { it.deferred.cancel() }
            memoryCache.clear()
            diskCache.clear()
        }
    }

    /** Callback-based [invalidate] for Java callers. [onComplete] runs on the main thread. */
    @JvmOverloads
    public fun invalidateAsync(url: String, onComplete: Runnable? = null) {
        scope.launch {
            invalidate(url)
            onComplete?.run()
        }
    }

    /** Callback-based [clearCache] for Java callers. [onComplete] runs on the main thread. */
    @JvmOverloads
    public fun clearCacheAsync(onComplete: Runnable? = null) {
        scope.launch {
            clearCache()
            onComplete?.run()
        }
    }

    @MainThread
    internal fun enqueue(request: ImageRequest): Disposable {
        check(Looper.myLooper() == Looper.getMainLooper()) { "into() must be called on the main thread" }
        val job = scope.launch(start = CoroutineStart.LAZY) { execute(request) }
        val disposable = request.target.requestManager().setRequest(job) { enqueue(request) }
        // Main.immediate runs the job synchronously up to its first suspension point, so memory
        // cache hits are displayed within this call, without a placeholder frame.
        job.start()
        return disposable
    }

    private suspend fun execute(request: ImageRequest) {
        val view = request.target
        val url = request.url
        if (url.isNullOrBlank()) {
            view.setImageDrawable(request.error?.resolve(view.context) ?: request.placeholder?.resolve(view.context))
            return
        }

        val knownSize = view.knownSizeOrNull()
        if (knownSize != null) {
            memoryCache[MemoryKey(url, knownSize)]?.let {
                view.setImageBitmap(it)
                return
            }
        }

        view.setImageDrawable(request.placeholder?.resolve(view.context))
        try {
            val bitmap = loadBitmap(url, knownSize ?: view.awaitSize())
            view.setImageBitmap(bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $url", e)
            request.error?.let { view.setImageDrawable(it.resolve(view.context)) }
        }
    }

    private suspend fun loadBitmap(url: String, size: TargetSize): Bitmap {
        val key = MemoryKey(url, size)
        memoryCache[key]?.let { return it }
        val file = withContext(ioDispatcher) { diskCache[url] } ?: download(url)
        // Decoding is CPU-bound, so it runs on the Default pool rather than the IO pool.
        val bitmap = withContext(decodeDispatcher) { decoder.decode(file, size) } ?: run {
            withContext(ioDispatcher) { diskCache.remove(url) }
            throw IOException("Unable to decode image: $url")
        }
        // The memory entry expires together with the download it was decoded from.
        memoryCache.put(key, bitmap, createdAtMillis = file.lastModified())
        return bitmap
    }

    private suspend fun download(url: String): File {
        val download = synchronized(inFlight) {
            inFlight.getOrPut(url) {
                // Runs in the loader scope rather than the caller's, so it outlives any single caller;
                // it is cancelled only once no caller is waiting for it any more.
                val deferred = scope.async(ioDispatcher, start = CoroutineStart.LAZY) {
                    try {
                        diskCache[url] ?: downloadPermits.withPermit {
                            diskCache.put(url) { fetcher.fetch(url, it) }
                        }
                    } finally {
                        synchronized(inFlight) {
                            if (inFlight[url]?.deferred === coroutineContext.job) inFlight.remove(url)
                        }
                    }
                }
                Download(deferred)
            }.also { it.waiters++ }
        }
        download.deferred.start()
        try {
            return download.deferred.await()
        } finally {
            synchronized(inFlight) {
                download.waiters--
                // E.g. every list item showing this image was scrolled away: stop downloading it.
                if (download.waiters == 0 && !download.deferred.isCompleted) {
                    download.deferred.cancel()
                    if (inFlight[url] === download) inFlight.remove(url)
                }
            }
        }
    }

    /** A shared download and the number of callers awaiting it; guarded by the `inFlight` lock. */
    private class Download(val deferred: Deferred<File>) {
        var waiters = 0
    }

    /** Configures and creates an [ImageLoader]. */
    public class Builder(context: Context) {
        private val appContext = context.applicationContext
        private var cacheTtlMillis = DEFAULT_CACHE_TTL_MILLIS
        private var memoryCacheMaxBytes = defaultMemoryCacheBytes()
        private var diskCacheMaxBytes = DEFAULT_DISK_CACHE_BYTES
        private var diskCacheDirectory: File? = null
        private var fetcher: Fetcher? = null
        private var maxParallelDownloads = DEFAULT_MAX_PARALLEL_DOWNLOADS
        internal var clock: Clock = Clock.System
        internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        internal var decodeDispatcher: CoroutineDispatcher = Dispatchers.Default

        /** How long a downloaded image stays valid in the caches. Defaults to 4 hours. */
        public fun cacheTtl(duration: Long, unit: TimeUnit): Builder = apply {
            require(duration > 0) { "TTL must be positive" }
            cacheTtlMillis = unit.toMillis(duration)
        }

        /** How long a downloaded image stays valid in the caches. Defaults to 4 hours. */
        @JvmSynthetic
        public fun cacheTtl(duration: Duration): Builder = cacheTtl(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)

        /** Maximum size of decoded bitmaps kept in memory. Defaults to 1/8 of the app's heap. */
        public fun memoryCacheMaxBytes(bytes: Int): Builder = apply {
            require(bytes > 0) { "Memory cache size must be positive" }
            memoryCacheMaxBytes = bytes
        }

        /** Maximum size of the disk cache. Defaults to 100 MB. */
        public fun diskCacheMaxBytes(bytes: Long): Builder = apply {
            require(bytes > 0) { "Disk cache size must be positive" }
            diskCacheMaxBytes = bytes
        }

        /** Directory for the disk cache. Defaults to `cacheDir/image_loader`. */
        public fun diskCacheDirectory(directory: File): Builder = apply { diskCacheDirectory = directory }

        /** Replaces the default [java.net.HttpURLConnection] based downloader. */
        public fun fetcher(fetcher: Fetcher): Builder = apply { this.fetcher = fetcher }

        /** Maximum number of images downloaded at the same time. Defaults to 4. */
        public fun maxParallelDownloads(count: Int): Builder = apply {
            require(count > 0) { "Parallel downloads must be positive" }
            maxParallelDownloads = count
        }

        public fun build(): ImageLoader = ImageLoader(
            memoryCache = MemoryCache(memoryCacheMaxBytes, cacheTtlMillis, clock),
            diskCache = DiskCache(
                directory = diskCacheDirectory ?: File(appContext.cacheDir, DEFAULT_DISK_CACHE_DIR),
                maxSizeBytes = diskCacheMaxBytes,
                ttlMillis = cacheTtlMillis,
                clock = clock,
            ),
            fetcher = fetcher ?: HttpUrlFetcher(),
            decoder = BitmapDecoder(),
            ioDispatcher = ioDispatcher,
            decodeDispatcher = decodeDispatcher,
            maxParallelDownloads = maxParallelDownloads,
        )

        private fun defaultMemoryCacheBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    public companion object {
        /** Default cache validity: 4 hours. */
        public const val DEFAULT_CACHE_TTL_MILLIS: Long = 4 * 60 * 60 * 1000L

        /** Pass as width/height to [loadBitmap] to decode without downsampling. */
        public const val ORIGINAL_SIZE: Int = 0

        private const val DEFAULT_DISK_CACHE_BYTES = 100L * 1024 * 1024
        private const val DEFAULT_MAX_PARALLEL_DOWNLOADS = 4
        private const val DEFAULT_DISK_CACHE_DIR = "image_loader"
        private const val TAG = "ImageLoader"

        @Volatile
        private var default: ImageLoader? = null

        /** Returns the shared loader, creating one with default settings on first use. */
        @JvmStatic
        public fun get(context: Context): ImageLoader =
            default ?: synchronized(this) {
                default ?: Builder(context).build().also { default = it }
            }

        /** Replaces the shared loader returned by [get], e.g. with a custom-configured one. */
        @JvmStatic
        public fun setDefault(loader: ImageLoader) {
            synchronized(this) { default = loader }
        }
    }
}
