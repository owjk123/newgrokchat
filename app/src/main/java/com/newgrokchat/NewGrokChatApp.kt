package com.newgrokchat

import android.app.Application
import com.newgrokchat.data.local.Prefs

class NewGrokChatApp : Application() {
    
    lateinit var prefs: Prefs
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
    }
    
    companion object {
        lateinit var instance: NewGrokChatApp
            private set
    }
}
