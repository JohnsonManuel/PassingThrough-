package com.passingthrough.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import org.osmdroid.config.Configuration
import java.io.File

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Http.init(cacheDir)
        Notifications.createChannels(this)
        // OpenStreetMap's tile servers require a real User-Agent; tiles are cached for offline use.
        Configuration.getInstance().apply {
            userAgentValue = Http.USER_AGENT
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
    }

    /** Wikimedia rejects image requests without a descriptive User-Agent. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", Http.USER_AGENT)
                                .build(),
                        )
                    }
                    .build()
            }
            .crossfade(true)
            .build()
}
