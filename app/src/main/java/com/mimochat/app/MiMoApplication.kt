package com.mimochat.app

import android.app.Application
import com.mimochat.app.data.AppDatabase
import com.mimochat.app.data.ChatRepository
import com.mimochat.app.data.SettingsRepository

class MiMoApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val chatRepository by lazy { ChatRepository(database, settingsRepository) }
}
