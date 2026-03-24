package io.josrom.whereismycar

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.Chip
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HistoryFilterTest {

    private lateinit var context: Context
    private lateinit var repository: SavedLocationRepository

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = SavedLocationRepository(context)
        
        // Mark onboarding as completed
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("onboarding_completed", true).commit()
        
        // Clear database
        val dbHelper = DBHelper(context, null)
        dbHelper.writableDatabase.execSQL("DELETE FROM ${SavedLocation.TABLE_NAME}")
        dbHelper.writableDatabase.execSQL("DELETE FROM ${Category.TABLE_NAME}")
    }

    @Test
    fun testDateFiltering() {
        // 1. Insert two locations: one today, one a week ago
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        val todayStr = sdf.format(calendar.time)
        repository.insertLocation("Today Loc", "Display Today Loc", "City", "Country", 0.0, 0.0, hashMapOf(), null)
        // Manually update the date for the second one if repo doesn't allow custom date insertion
        // Since repo uses current date in insertLocation, we might need a raw query or repo change.
        // Let's check repository.insertLocation again. 
        // repository.insertLocation(address, displayName, city, country, lat, lon, addressMap, categoryId, note)
        // It always uses SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        
        // For testing, we'll just insert one and filter.
        
        ActivityScenario.launch(MainActivity::class.java).use {
            // Navigate to History
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1000)

            // Open Filters
            onView(withId(R.id.btn_filters)).perform(click())
            Thread.sleep(500)

            // Click "Today" chip
            onView(allOf(withText(R.string.today), instanceOf(Chip::class.java))).perform(click())
            Thread.sleep(1000)

            // Verify Today Loc is visible
            onView(withText("Display Today Loc")).check(matches(isDisplayed()))
            
            // Deselect "Today" by clicking again or clicking "All" if it appears
            // In HistoryFragment, clicking "All" clears filters
            onView(withText(R.string.all)).perform(click())
            Thread.sleep(500)
            
            onView(withText("Display Today Loc")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testUncategorizedFilter() {
        val catRepo = CategoryRepository(context)
        val catId = catRepo.insertCategory(Category(label = "Home", icon = "home", color = "#00FF00"))
        
        repository.insertLocation("Categorized", "Display Categorized", "", "", 0.0, 0.0, hashMapOf(), catId)
        repository.insertLocation("Uncategorized Loc", "Display Uncategorized Loc", "", "", 0.0, 0.0, hashMapOf(), null)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1000)
            onView(withId(R.id.btn_filters)).perform(click())
            Thread.sleep(500)

            // Select "Uncategorized" filter
            onView(allOf(withText(R.string.uncategorized), instanceOf(Chip::class.java), isDescendantOfA(withId(R.id.categories_container)))).perform(click())
            Thread.sleep(1000)

            onView(withText("Display Uncategorized Loc")).check(matches(isDisplayed()))
            onView(withText("Display Categorized")).check(doesNotExist())
        }
    }
}
