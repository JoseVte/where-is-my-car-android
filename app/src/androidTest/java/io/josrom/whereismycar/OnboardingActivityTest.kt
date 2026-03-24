package io.josrom.whereismycar

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingActivityTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear preferences before each test to ensure onboarding starts
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().clear().commit()
    }

    @Test
    fun testOnboardingFlow() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { _ ->
            // Check first page
            onView(withText(R.string.onboarding_title_1)).check(matches(isDisplayed()))
            onView(withId(R.id.btnNext)).check(matches(withText(R.string.next)))

            // Click Next to second page
            onView(withId(R.id.btnNext)).perform(click())
            onView(withText(R.string.onboarding_title_2)).check(matches(isDisplayed()))

            // Click Next to third page
            onView(withId(R.id.btnNext)).perform(click())
            onView(withText(R.string.onboarding_title_3)).check(matches(isDisplayed()))
            onView(withId(R.id.btnNext)).check(matches(withText(R.string.get_started)))

            // Click Get Started
            onView(withId(R.id.btnNext)).perform(click())

            // Verify shared preference is set
            val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            assertTrue(sharedPref.getBoolean("onboarding_completed", false))
        }
    }

    @Test
    fun testOnboardingSkip() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { _ ->
            // Click Skip on the first page
            onView(withId(R.id.onboarding_skip)).perform(click())

            // Verify shared preference is set
            val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            assertTrue(sharedPref.getBoolean("onboarding_completed", false))
        }
    }
}
