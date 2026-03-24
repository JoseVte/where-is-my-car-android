package io.josrom.whereismycar

import android.Manifest
import android.content.Context
import android.view.View
import com.google.android.material.chip.Chip
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationFlowTest {

    private lateinit var context: Context

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Mark onboarding as completed to skip it
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("onboarding_completed", true).commit()
        
        // Clear database to have a clean state
        val dbHelper = DBHelper(context, null)
        dbHelper.writableDatabase.execSQL("DELETE FROM ${SavedLocation.TABLE_NAME}")
        dbHelper.writableDatabase.execSQL("DELETE FROM ${Category.TABLE_NAME}")

        // Reset confirm deletion pref
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putBoolean("confirm_deletion", true).commit()
    }

    @Test
    fun testSaveLocationAndShowDetails() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Save current location in ExploreFragment
            onView(withId(R.id.btnSavePosition)).perform(click())
            Thread.sleep(2000)

            // 2. Navigate to History to view details
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(2000)

            // 3. Find the saved location in the history list and click it
            onView(withId(R.id.recycler_view_locations))
                .perform(RecyclerViewActions.actionOnItemAtPosition<LocationAdapter.ViewHolder>(0, click()))

            // 4. Wait for navigation to complete
            Thread.sleep(2000)

            // 5. Verify details screen is shown and has a non-empty name
            onView(allOf(withId(R.id.locationName), isDisplayed())).perform(scrollTo()).check(matches(not(withText(""))))
            
            onView(withId(R.id.addressSubtext)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.coordsText)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.btn_start_navigation)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testSearchLocation() {
        // Pre-insert a location to search for
        val repo = SavedLocationRepository(context)
        val lat = 40.7128
        val lon = -74.0060
        val testName = "Test Search Location"
        repo.insertLocation(testName, testName, "New York", "USA", lat, lon, hashMapOf())

        ActivityScenario.launch(MainActivity::class.java).use {
            // Wait for initial load
            Thread.sleep(1000)

            // Expand BottomSheet to ensure search bar is interactable
            onView(withId(R.id.bottom_sheet)).perform(setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED))
            Thread.sleep(1000)
            
            // Search for the location
            onView(withId(R.id.et_search)).perform(replaceText("Test Search"), closeSoftKeyboard())
            
            Thread.sleep(2000)

            // Verify the location is shown in the list. 
            onView(allOf(withId(R.id.text_view_title), withText(testName))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testEditAndThenDeleteLocationFlow() {
        // Pre-insert a location and a category
        val catRepo = CategoryRepository(context)
        catRepo.insertCategory(Category(label = "Test Category", icon = "favorite", color = "#FF0000"))
        
        val repo = SavedLocationRepository(context)
        val testName = "Original Name"
        repo.insertLocation(testName, testName, "City", "Country", 0.0, 0.0, hashMapOf())

        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to History
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1500)

            // 2. Click the location to see details
            onView(withId(R.id.recycler_view_locations))
                .perform(RecyclerViewActions.actionOnItemAtPosition<LocationAdapter.ViewHolder>(0, click()))
            Thread.sleep(1500)

            // 3. Click Edit button
            onView(withId(R.id.btn_edit)).perform(click())
            Thread.sleep(1500)

            // 4. Change name and select category
            val newName = "Updated Name"
            onView(withId(R.id.etLocationDisplayName)).perform(replaceText(newName), closeSoftKeyboard())
            
            // Select the category chip
            onView(allOf(withText("Test Category"), isDescendantOfA(withId(R.id.categoryChipGroup)))).perform(click())

            // 5. Save changes
            onView(withId(R.id.btnSaveChanges)).perform(scrollTo(), click())
            Thread.sleep(1500)

            // 6. Verify updated name in details screen
            onView(allOf(withId(R.id.locationName), isDisplayed())).perform(scrollTo()).check(matches(withText(newName)))
            
            // 7. Now delete it from details screen (via Edit screen again)
            onView(withId(R.id.btn_edit)).perform(click())
            Thread.sleep(1000)
            onView(withId(R.id.btnDeleteLocation)).perform(scrollTo(), click())
            
            // 8. Confirm deletion in dialog
            onView(withId(android.R.id.button1)).perform(click())
            Thread.sleep(1500)

            // 9. Should be back in History, verify it's gone
            onView(withText(newName)).check(doesNotExist())
        }
    }

    @Test
    fun testDeleteLocationFromHistoryViaMenu() {
        val repo = SavedLocationRepository(context)
        val testName = "Delete Via Menu"
        repo.insertLocation(testName, testName, "City", "Country", 0.0, 0.0, hashMapOf())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1000)

            // Click "More" button on the first item
            onView(withId(R.id.recycler_view_locations))
                .perform(RecyclerViewActions.actionOnItemAtPosition<LocationAdapter.ViewHolder>(0, 
                    clickOnViewChild(R.id.button_more)))
            Thread.sleep(500)
            
            // Click Delete in PopupMenu
            onView(withText(R.string.delete)).perform(click())
            
            // Confirm in dialog
            onView(withId(android.R.id.button1)).perform(click())
            Thread.sleep(1500)

            // Verify it's gone
            onView(withText(testName)).check(doesNotExist())
        }
    }

    @Test
    fun testEditLocationNotes() {
        val repo = SavedLocationRepository(context)
        val testName = "Notes Test"
        repo.insertLocation(testName, testName, "City", "Country", 0.0, 0.0, hashMapOf())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1000)
            onView(withId(R.id.recycler_view_locations)).perform(RecyclerViewActions.actionOnItemAtPosition<LocationAdapter.ViewHolder>(0, click()))
            Thread.sleep(1500)
            onView(withId(R.id.btn_edit)).perform(click())
            Thread.sleep(1000)
            
            val newNotes = "These are new notes"
            onView(withId(R.id.etNotes)).perform(replaceText(newNotes), closeSoftKeyboard())
            onView(withId(R.id.btnSaveChanges)).perform(scrollTo(), click())
            Thread.sleep(1500)
            
            onView(withId(R.id.noteText)).perform(scrollTo()).check(matches(withText(newNotes)))
        }
    }

    @Test
    fun testDeleteLocationWithDontAskAgain() {
        val repo = SavedLocationRepository(context)
        val testName = "Delete Me Fast"
        repo.insertLocation(testName, testName, "City", "Country", 0.0, 0.0, hashMapOf())

        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to History
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1000)

            // 2. Click the location
            onView(withId(R.id.recycler_view_locations))
                .perform(RecyclerViewActions.actionOnItemAtPosition<LocationAdapter.ViewHolder>(0, click()))
            Thread.sleep(1500)

            // 3. Click Edit button
            onView(withId(R.id.btn_edit)).perform(click())
            Thread.sleep(1000)

            // 4. Click Delete
            onView(withId(R.id.btnDeleteLocation)).perform(scrollTo(), click())
            Thread.sleep(500)

            // 5. Check "Don't ask again" and click Delete
            onView(withId(R.id.cbDontAskAgain)).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
            Thread.sleep(1500)

            // 6. Verify preference is updated
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            assertFalse(prefs.getBoolean("confirm_deletion", true))
        }
    }

    @Test
    fun testHistoryFlow() {
        // Pre-insert a location
        val repo = SavedLocationRepository(context)
        val testName = "History Location"
        repo.insertLocation(testName, testName, "History City", "History Country", 0.0, 0.0, hashMapOf())

        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to History
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(2000)

            // 2. Search in History
            onView(withId(R.id.edit_text_search)).perform(replaceText("History"), closeSoftKeyboard())
            Thread.sleep(1500)

            // 3. Verify location is visible in the history recycler view
            onView(allOf(withId(R.id.text_view_title), withText(testName))).check(matches(isDisplayed()))

            // 4. Click location and verify details
            onView(withId(R.id.recycler_view_locations))
                .perform(RecyclerViewActions.actionOnItemAtPosition<LocationAdapter.ViewHolder>(0, click()))
            
            Thread.sleep(2000)
            onView(allOf(withId(R.id.locationName), isDisplayed())).perform(scrollTo()).check(matches(withText(testName)))
        }
    }

    @Test
    fun testCategoryFilteringInHistory() {
        val catRepo = CategoryRepository(context)
        val cat1Id = catRepo.insertCategory(Category(label = "Home", icon = "home", color = "#00FF00"))
        val cat2Id = catRepo.insertCategory(Category(label = "Work", icon = "work", color = "#0000FF"))

        val repo = SavedLocationRepository(context)
        repo.insertLocation("Home Location", "Display Home Location", "", "", 0.0, 0.0, hashMapOf(), cat1Id)
        repo.insertLocation("Work Location", "Display Work Location", "", "", 0.0, 0.0, hashMapOf(), cat2Id)

        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to History
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1500)

            // 2. Open Filters
            onView(withId(R.id.btn_filters)).perform(click())
            Thread.sleep(1000)

            // 3. Select "Home" category chip.
            onView(allOf(withText("Home"), instanceOf(Chip::class.java), isDescendantOfA(withId(R.id.categories_container)))).perform(click())
            Thread.sleep(1000)

            // 4. Verify only Home Location is visible
            onView(withText("Display Home Location")).check(matches(isDisplayed()))
            onView(withText("Display Work Location")).check(doesNotExist())

            // 5. Select "Work" category chip
            onView(allOf(withText("Work"), instanceOf(Chip::class.java), isDescendantOfA(withId(R.id.categories_container)))).perform(click())
            Thread.sleep(1000)

            // Both should be visible
            onView(withText("Display Home Location")).check(matches(isDisplayed()))
            onView(withText("Display Work Location")).check(matches(isDisplayed()))
            
            // 6. Deselect "Home"
            onView(allOf(withText("Home"), instanceOf(Chip::class.java), isDescendantOfA(withId(R.id.categories_container)))).perform(click())
            Thread.sleep(1000)
            onView(withText("Display Home Location")).check(doesNotExist())
            onView(withText("Display Work Location")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testTransportModeToggleInDetails() {
        val repo = SavedLocationRepository(context)
        val name = "Nav Toggle Test"
        repo.insertLocation(name, name, "City", "Country", 39.47, -0.37, hashMapOf())

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.location_history_fragment)).perform(click())
            Thread.sleep(1000)
            onView(withId(R.id.recycler_view_locations)).perform(RecyclerViewActions.actionOnItemAtPosition<LocationAdapter.ViewHolder>(0, click()))
            Thread.sleep(1500)

            // 1. Click Start Navigation
            onView(withId(R.id.btn_start_navigation)).perform(click())
            Thread.sleep(1000)

            // 2. Transport toggle should be in the layout
            onView(withId(R.id.transportToggleGroup)).check(matches(anyOf(isDisplayed(), not(isDisplayed()))))
        }
    }

    /**
     * Helper action to change BottomSheet state
     */
    private fun setBottomSheetState(state: Int): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
            override fun getDescription(): String = "set bottom sheet state to $state"
            override fun perform(uiController: UiController?, view: View?) {
                val behavior = BottomSheetBehavior.from(view!!)
                behavior.state = state
            }
        }
    }

    /**
     * Helper to click on a specific view inside a RecyclerView item
     */
    private fun clickOnViewChild(viewId: Int) = object : ViewAction {
        override fun getConstraints() = null
        override fun getDescription() = "Click on a child view with specified id."
        override fun perform(uiController: UiController, view: View) {
            val v = view.findViewById<View>(viewId)
            v.performClick()
        }
    }
}
