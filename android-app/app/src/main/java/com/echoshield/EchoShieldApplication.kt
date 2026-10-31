package com.echoshield

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Custom application class initializing core security frameworks and Firebase services.
 */
class EchoShieldApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}
