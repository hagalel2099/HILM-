package com.nossouniverso.launcher

import android.app.Application
import com.nossouniverso.launcher.data.AppRepository
import com.nossouniverso.launcher.data.IconPackManager
import com.nossouniverso.launcher.data.ShortcutStorage

class NossoUniversoApp : Application() {

    lateinit var shortcutStorage: ShortcutStorage
        private set

    lateinit var iconPackManager: IconPackManager
        private set

    lateinit var appRepository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        shortcutStorage = ShortcutStorage(this)
        iconPackManager = IconPackManager(this, shortcutStorage)
        appRepository = AppRepository(this, iconPackManager)
    }
}
