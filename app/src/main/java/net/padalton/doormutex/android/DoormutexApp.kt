package net.padalton.doormutex.android

import android.app.Application
import android.content.Context
import net.padalton.doormutex.android.data.HubRepository
import net.padalton.doormutex.android.widget.RefreshWorker

class DoormutexApp : Application() {
    lateinit var repository: HubRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = HubRepository(this)
        RefreshWorker.schedule(this)
    }
}

val Context.repository: HubRepository
    get() = (applicationContext as DoormutexApp).repository
