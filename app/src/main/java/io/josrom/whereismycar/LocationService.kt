package io.josrom.whereismycar

import android.app.Service
import android.content.Intent
import android.os.IBinder

class LocationService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Your location tracking logic goes here
        return START_STICKY
    }
}
