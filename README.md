# Image Loader – Android home assignment

[![CI](https://github.com/olexandrliubka/ImageLoaderLib/actions/workflows/ci.yml/badge.svg)](https://github.com/olexandrliubka/ImageLoaderLib/actions/workflows/ci.yml)

A small image downloading and caching library (`:imageloader`) written without Glide, Coil,
Picasso, OkHttp or any other image/network library, plus a sample app (`:app`) that shows the
images from
[`image_list.json`](https://zipoapps-storage-test.nyc3.digitaloceanspaces.com/image_list.json).

| Module | What it is |
| --- | --- |
| `imageloader` | Android library: download → disk cache → decode/downsample → memory cache → `ImageView`. Kotlin Coroutines throughout, Java-friendly API. |
| `app` | Android Views + MVVM + Coroutines sample: grid of images with their `id`, and a **Clear cache** button. |

Built with AGP 9.4.1, Gradle 9.8.2, Kotlin 2.2 (AGP built-in Kotlin), compileSdk/targetSdk 36, minSdk 24.

## Using the library

**Kotlin**

```kotlin
imageView.load(url) {
    placeholder(R.drawable.image_placeholder)
    error(R.drawable.image_error)          // optional
}
```

**Java**

```java
ImageLoader.get(context)
        .load(url)
        .placeholder(R.drawable.image_placeholder)
        .error(R.drawable.image_error)
        .into(imageView);
```

The three required inputs are the **URL**, the **placeholder** (resource id or `Drawable`), and the
**target view** (`ImageView`). `into()` returns a `Disposable` that can cancel the request.

### Cache invalidation

| Kotlin (suspend) | Java (callback on the main thread) |
| --- | --- |
| `loader.clearCache()` | `loader.clearCacheAsync(() -> …)` |
| `loader.invalidate(url)` | `loader.invalidateAsync(url, () -> …)` |

Entries also **expire 4 hours after download** (`ImageLoader.DEFAULT_CACHE_TTL_MILLIS`).

### Configuration

```kotlin
val loader = ImageLoader.Builder(context)
    .cacheTtl(4, TimeUnit.HOURS)          // or cacheTtl(4.hours) in Kotlin
    .memoryCacheMaxBytes(32 * 1024 * 1024)
    .diskCacheMaxBytes(100L * 1024 * 1024)
    .maxParallelDownloads(4)
    .fetcher { url, destination -> /* custom download, e.g. with auth headers */ }
    .build()
ImageLoader.setDefault(loader)
```

There is also a view-less `suspend fun loadBitmap(url, width, height): Bitmap` that goes through
the same caches.

## How the library works

```
into(imageView)
  │  cancel the request previously bound to this view (RecyclerView rebinding)
  ├─ memory cache hit for (url, view size)? → set bitmap synchronously, no placeholder flash
  ├─ show placeholder, wait for the view to be laid out to learn its size
  ├─ Dispatchers.IO: disk cache hit (and younger than TTL)?
  │    else ──▶ download: deduplicated per URL, at most 4 at once, cancelled if nobody waits
  ├─ Dispatchers.Default: decode with inSampleSize for the view size → memory cache
  └─ Main: setImageBitmap, or on failure the error drawable (or keep the placeholder)
```

- **Memory cache** – `LruCache` of decoded bitmaps, sized by bytes (default: 1/8 of the heap). Keyed
  by URL + target size, so a thumbnail and a full-size bitmap don't overwrite each other.
- **Disk cache** – original bytes stored under `cacheDir/image_loader`, file name = SHA-256 of the
  URL. Downloads are written to a temporary file and atomically renamed, so a crash or cancelled
  download never leaves a corrupt cache entry. The file's last-modified time is the download time;
  oldest files are evicted when the cache exceeds its size limit (default 100 MB).
- **TTL** – both layers expire 4 hours after the *download*: a bitmap decoded from a 3-hour-old file
  only lives one more hour in memory. A timestamp in the future (device clock moved back) counts as
  expired.
- **Downsampling** – the source images are 1920×1080 JPEGs; decoding them at full size into a
  phone-width list would waste ~8 MB per bitmap. The decoder picks the largest power-of-two
  `inSampleSize` that still covers the view.
- **Request deduplication** – concurrent requests for the same URL share one download. The download
  runs in the loader's own scope and counts its waiters: a cancelled caller (a recycled list item)
  doesn't abort a download other callers still need, but once the last waiter leaves – e.g. a fast
  fling past the item – the download is cancelled instead of wasting bandwidth.
- **Bounded parallelism** – a semaphore allows at most 4 downloads at once (configurable with
  `maxParallelDownloads`), so a fling doesn't open dozens of connections and the images on screen
  aren't starved by ones already scrolled away.
- **Threading** – file and network IO on `Dispatchers.IO`; decoding is CPU-bound, so it runs on
  `Dispatchers.Default`.
- **View lifecycle** – each view gets a small request manager (stored as a view tag): binding a new
  URL cancels the old request; a request still running when the view is detached is cancelled and
  automatically restarted on re-attach, so nothing leaks past an Activity's lifetime.
- **Cancellation** – structured concurrency; the download loop checks `ensureActive()`, so clearing
  the cache aborts in-flight downloads.

## Sample app architecture (MVVM)

```
MainActivity ──observes──▶ ImageListViewModel ──▶ ImageRepository ──▶ ImageListApi (HttpURLConnection + org.json)
     │  StateFlow<ImageListUiState>, events Flow       └──▶ ImageCacheInvalidator ──▶ ImageLoader.clearCache()
     └─ RecyclerView + ImageAdapter ── imageView.load(url) { placeholder(…) }
```

- `ImageListViewModel` exposes a `StateFlow<ImageListUiState>` (loading / images / error / clearing)
  and a one-off `CacheCleared` event, collected with `repeatOnLifecycle(STARTED)`.
- Tapping **Clear cache** clears both cache layers, then the Activity rebinds the list, so every
  image is downloaded again (you'll see the placeholders).
- Dependencies are wired by hand in `AppContainer` – small enough not to need Hilt.
- Material 3 with dynamic color, edge-to-edge, 1/2/3 grid columns depending on width.

## Building and testing

```bash
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest       # 30 JVM/Robolectric tests
```

Tests cover the disk cache (TTL, eviction, atomic writes), the memory cache, `inSampleSize` maths,
the loader end to end (cache hits, 4-hour expiry, `clearCache`/`invalidate`, download
deduplication, the parallel download limit, cancelling downloads nobody waits for,
placeholder → downsampled bitmap in a real `ImageView`, error drawable), Java interop, the JSON
parser and the ViewModel.

GitHub Actions ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) builds the app and runs all
tests on every push and pull request; the debug APK is attached to each run.

## Notes

- Five URLs in the provided `image_list.json` currently return **HTTP 403** from the server
  (ids 4, 13, 24, 37 and 49).
  The app shows the error drawable for them – that's the expected behavior.
- Possible next steps: `ImageDecoder` on API 28+, `Bitmap` reuse via `inBitmap`, HTTP cache
  validation (ETag / `If-Modified-Since`) instead of a fixed TTL, and a Compose wrapper.
