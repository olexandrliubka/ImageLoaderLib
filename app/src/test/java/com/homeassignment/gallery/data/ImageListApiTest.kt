package com.homeassignment.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageListApiTest {

    @Test
    fun `parses ids and urls`() {
        val json = """
            [
              { "id": 0, "imageUrl": "https://example.com/0.jpg" },
              { "id": 7, "imageUrl": "https://example.com/7.jpg" }
            ]
        """.trimIndent()

        val items = ImageListApi.parseImageList(json)

        assertEquals(
            listOf(ImageItem(0, "https://example.com/0.jpg"), ImageItem(7, "https://example.com/7.jpg")),
            items,
        )
    }
}
