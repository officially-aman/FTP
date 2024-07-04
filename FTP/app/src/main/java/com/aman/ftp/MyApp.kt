package com.aman.ftp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        initializeFirebase(this)
    }

    private fun initializeFirebase(context: Application) {
        // Check if FirebaseApp is already initialized
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setDatabaseUrl("none")
                .setApiKey("none")
                .setApplicationId("none")
                .build()
            FirebaseApp.initializeApp(context, options)
        }

        // Enable persistence
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
