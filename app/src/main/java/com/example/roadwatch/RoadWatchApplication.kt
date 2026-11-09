package com.example.roadwatch

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Keep the app and Maps SDK in English without changing the phone's language. */
internal fun Context.englishContext(): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(Locale.ENGLISH))
    configuration.setLayoutDirection(Locale.ENGLISH)
    return createConfigurationContext(configuration)
}

class RoadWatchApplication : Application() {
    override fun attachBaseContext(base: Context) {
        Locale.setDefault(Locale.ENGLISH)
        super.attachBaseContext(base.englishContext())
    }
}
