package com.homeassignment.gallery

import android.app.Application
import com.google.android.material.color.DynamicColors

class GalleryApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
