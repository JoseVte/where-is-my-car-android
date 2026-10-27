package io.josrom.whereismycar

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds (emulator included) cannot pass Play Integrity. The debug provider prints a token in logcat
 * ("Enter this debug secret into the allow list..."): add it in Firebase Console, App Check, Apps,
 * Manage debug tokens. Never ship a debug token in a release build.
 */
object AppCheckProviders {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
