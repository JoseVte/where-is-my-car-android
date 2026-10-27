package io.josrom.whereismycar

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

class WhereIsMyCarApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // App Check must be installed before any Firebase call, so Firebase Auth requests carry its token.
        // The provider depends on the build type, see AppCheckProviders in src/debug and src/release.
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(AppCheckProviders.factory())
    }
}
