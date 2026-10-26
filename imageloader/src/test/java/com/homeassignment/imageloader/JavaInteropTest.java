package com.homeassignment.imageloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.TimeUnit;

/** Exercises the public API from Java to keep it Java-friendly. */
@RunWith(RobolectricTestRunner.class)
public class JavaInteropTest {

    @Test
    public void publicApiIsUsableFromJava() {
        Context context = ApplicationProvider.getApplicationContext();
        ImageLoader loader = new ImageLoader.Builder(context)
                .cacheTtl(4, TimeUnit.HOURS)
                .memoryCacheMaxBytes(8 * 1024 * 1024)
                .diskCacheMaxBytes(50L * 1024 * 1024)
                .build();
        ImageLoader.setDefault(loader);

        ImageView imageView = new ImageView(context);
        Disposable request = ImageLoader.get(context)
                .load("https://example.com/a.png")
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(imageView);
        request.dispose();
        loader.cancel(imageView);
        loader.invalidateAsync("https://example.com/a.png");
        loader.clearCacheAsync(() -> { });

        assertTrue(request.isDisposed());
        assertEquals(4L * 60 * 60 * 1000, ImageLoader.DEFAULT_CACHE_TTL_MILLIS);
    }
}
