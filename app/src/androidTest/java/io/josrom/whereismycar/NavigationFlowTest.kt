package io.josrom.whereismycar

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Mark onboarding as completed
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("onboarding_completed", true).commit()
    }

    @Test
    fun testBottomNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Starts at Explore (Check if MapView is displayed)
            onView(withId(R.id.map)).check(matches(isDisplayed()))

            // 2. Navigate to History (Using ID of the bottom nav item)
            onView(allOf(withId(R.id.location_history_fragment), isDescendantOfA(withId(R.id.bottom_navigation)))).perform(click())
            Thread.sleep(1000)
            
            // Check toolbar title specifically to avoid ambiguity with bottom nav labels
            onView(allOf(withId(R.id.tv_toolbar_title), withText(R.string.history))).check(matches(isDisplayed()))
            onView(withId(R.id.recycler_view_locations)).check(matches(isDisplayed()))

            // 3. Navigate to Settings (Using ID of the bottom nav item)
            onView(allOf(withId(R.id.settings_fragment), isDescendantOfA(withId(R.id.bottom_navigation)))).perform(click())
            Thread.sleep(1000)
            
            onView(allOf(withId(R.id.tv_toolbar_title), withText(R.string.settings))).check(matches(isDisplayed()))
            onView(withId(R.id.settings_container)).check(matches(isDisplayed()))

            // 4. Back to Explore using the center button
            onView(withId(R.id.bg_nav_center_view)).perform(click())
            Thread.sleep(1000)
            onView(withId(R.id.map)).check(matches(isDisplayed()))
        }
    }
}
