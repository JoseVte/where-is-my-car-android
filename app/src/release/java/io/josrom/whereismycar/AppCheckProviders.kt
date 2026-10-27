package io.josrom.whereismycar

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release builds prove they are the genuine app installed from Google Play. */
object AppCheckProviders {
    fun factory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
}
