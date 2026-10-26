package com.homeassignment.imageloader

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageLoaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val clock = FakeClock()

    private fun loader(fetcher: Fetcher, configure: ImageLoader.Builder.() -> Unit = {}) =
        ImageLoader.Builder(ApplicationProvider.getApplicationContext())
            .diskCacheDirectory(tempFolder.root)
            .fetcher(fetcher)
            .apply {
                clock = this@ImageLoaderTest.clock
                configure()
            }
            .build()

    @Test
    fun `cached image is not downloaded again`() = runBlocking {
        val fetcher = FakeFetcher()
        val loader = loader(fetcher)

        val first = loader.loadBitmap(URL_A)
        val second = loader.loadBitmap(URL_A)

        assertSame(first, second)
        assertEquals(1, fetcher.fetchCount(URL_A))
    }

    @Test
    fun `disk cache serves new sizes without downloading`() = runBlocking {
        val fetcher = FakeFetcher(width = 400, height = 200)
        val loader = loader(fetcher)

        val full = loader.loadBitmap(URL_A)
        val small = loader.loadBitmap(URL_A, width = 100, height = 50)

        assertEquals(400, full.width)
        assertEquals(100, small.width)
        assertEquals(1, fetcher.fetchCount(URL_A))
    }

    @Test
    fun `image is downloaded again once the 4 hour ttl has passed`() = runBlocking {
        val fetcher = FakeFetcher()
        val loader = loader(fetcher)

        loader.loadBitmap(URL_A)
        clock.time += TimeUnit.HOURS.toMillis(4) - 1
        loader.loadBitmap(URL_A)
        assertEquals(1, fetcher.fetchCount(URL_A))

        clock.time += 1
        loader.loadBitmap(URL_A)
        assertEquals(2, fetcher.fetchCount(URL_A))
    }

    @Test
    fun `custom ttl is respected`() = runBlocking {
        val fetcher = FakeFetcher()
        val loader = loader(fetcher) { cacheTtl(10, TimeUnit.MINUTES) }

        loader.loadBitmap(URL_A)
        clock.time += TimeUnit.MINUTES.toMillis(10)
        loader.loadBitmap(URL_A)

        assertEquals(2, fetcher.fetchCount(URL_A))
    }

    @Test
    fun `clearCache forces every image to be downloaded again`() = runBlocking {
        val fetcher = FakeFetcher()
        val loader = loader(fetcher)
        loader.loadBitmap(URL_A)
        loader.loadBitmap(URL_B)

        loader.clearCache()
        loader.loadBitmap(URL_A)
        loader.loadBitmap(URL_B)

        assertEquals(2, fetcher.fetchCount(URL_A))
        assertEquals(2, fetcher.fetchCount(URL_B))
    }

    @Test
    fun `invalidate only affects the given url`() = runBlocking {
        val fetcher = FakeFetcher()
        val loader = loader(fetcher)
        loader.loadBitmap(URL_A)
        loader.loadBitmap(URL_B)

        loader.invalidate(URL_A)
        loader.loadBitmap(URL_A)
        loader.loadBitmap(URL_B)

        assertEquals(2, fetcher.fetchCount(URL_A))
        assertEquals(1, fetcher.fetchCount(URL_B))
    }

    @Test
    fun `concurrent requests for one url share a single download`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val fetcher = FakeFetcher(beforeWrite = {
            started.complete(Unit)
            gate.await()
        })
        val loader = loader(fetcher)

        val requests = List(5) { async(Dispatchers.IO) { loader.loadBitmap(URL_A) } }
        started.await()
        gate.complete(Unit)
        requests.awaitAll()

        assertEquals(1, fetcher.fetchCount(URL_A))
    }

    @Test
    fun `no more than maxParallelDownloads run at once`() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val fetcher = FakeFetcher(beforeWrite = {
            maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            delay(50)
            active.decrementAndGet()
        })
        val loader = loader(fetcher) { maxParallelDownloads(2) }

        List(8) { index -> async(Dispatchers.IO) { loader.loadBitmap("https://example.com/$index.png") } }
            .awaitAll()

        assertEquals(2, maxActive.get())
    }

    @Test
    fun `download is cancelled once nobody waits for it`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val fetcher = FakeFetcher(beforeWrite = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        })
        val loader = loader(fetcher)

        val request = launch(Dispatchers.IO) { loader.loadBitmap(URL_A) }
        started.await()
        request.cancel()

        withTimeout(5_000) { cancelled.await() }
    }

    @Test
    fun `download continues while another caller still waits for it`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val fetcher = FakeFetcher(beforeWrite = {
            started.complete(Unit)
            gate.await()
        })
        val loader = loader(fetcher)

        val cancelledCaller = launch(Dispatchers.IO) { loader.loadBitmap(URL_A) }
        started.await()
        val remainingCaller = async(Dispatchers.IO) { loader.loadBitmap(URL_A) }
        // Give the second caller time to join the download before the first one leaves.
        delay(100)
        cancelledCaller.cancel()
        gate.complete(Unit)

        assertEquals(400, withTimeout(5_000) { remainingCaller.await() }.width)
        assertEquals(1, fetcher.fetchCount(URL_A))
    }

    @Test
    fun `into shows the placeholder, then the downsampled image`() {
        val fetcher = FakeFetcher(width = 400, height = 200)
        // Run everything on the main looper so the test controls it.
        val loader = loader(fetcher) {
            ioDispatcher = Dispatchers.Main.immediate
            decodeDispatcher = Dispatchers.Main.immediate
        }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val imageView = ImageView(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(imageView, ViewGroup.LayoutParams(100, 50)) })
        val placeholder = ColorDrawable()

        loader.load(URL_A).placeholder(placeholder).into(imageView)
        assertTrue(imageView.drawable is ColorDrawable)

        shadowOf(Looper.getMainLooper()).idle()
        val drawable = imageView.drawable as BitmapDrawable
        assertEquals(100, drawable.bitmap.width)
        assertEquals(50, drawable.bitmap.height)
    }

    @Test
    fun `into shows the error drawable when the download fails`() {
        val loader = loader({ _, _ -> throw HttpException(404, URL_A) }) {
            ioDispatcher = Dispatchers.Main.immediate
            decodeDispatcher = Dispatchers.Main.immediate
        }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val imageView = ImageView(activity)
        activity.setContentView(FrameLayout(activity).apply { addView(imageView, ViewGroup.LayoutParams(100, 50)) })
        val error = ColorDrawable()

        loader.load(URL_A).error(error).into(imageView)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(imageView.drawable is ColorDrawable)
    }

    private companion object {
        const val URL_A = "https://example.com/a.png"
        const val URL_B = "https://example.com/b.png"
    }
}
