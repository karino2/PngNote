package io.github.karino2.pngnote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PngNote : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}