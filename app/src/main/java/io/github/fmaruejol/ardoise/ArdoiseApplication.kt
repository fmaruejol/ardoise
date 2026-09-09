package io.github.fmaruejol.ardoise

import android.app.Application
import io.github.fmaruejol.ardoise.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ArdoiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@ArdoiseApplication)
            modules(appModules)
        }
    }
}
