package io.josrom.whereismycar

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Mark onboarding as completed
        val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("onboarding_completed", true).commit()
        
        // Clear categories and start with a clean state
        val dbHelper = DBHelper(context, null)
        dbHelper.writableDatabase.execSQL("DELETE FROM ${Category.TABLE_NAME}")
        
        // Ensure initial preference states
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("confirm_deletion", true)
            .putString("theme_selection", "system")
            .putString("language_selection", "system")
            .commit()
    }

    @Test
    fun testAddCategoryFlow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to Settings
            onView(withId(R.id.settings_fragment)).perform(click())
            Thread.sleep(1000)

            // 2. Click Manage Categories explicitly on the child view
            onView(withId(androidx.preference.R.id.recycler_view))
                .perform(RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withId(R.id.item_manage_categories)),
                    clickOnViewChild(R.id.item_manage_categories)
                ))
            Thread.sleep(1000)

            // 3. Click Add New Category
            onView(withId(R.id.btn_add_category)).perform(click())
            Thread.sleep(1000)

            // 4. Fill in category name
            val testCategoryName = "Work Test"
            onView(withId(R.id.category_name)).perform(replaceText(testCategoryName), closeSoftKeyboard())
            
            // 5. Select an icon by position (scrolling if needed)
            onView(withId(R.id.rv_icons))
                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(2, click()))

            // 6. Save the category
            onView(withId(R.id.btn_save_category)).perform(click())
            Thread.sleep(1500)

            // 7. Verify the category is visible in the list
            onView(allOf(withId(R.id.category_name), withText(testCategoryName))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testEditCategoryFlow() {
        // Pre-insert a category
        val repo = CategoryRepository(context)
        val originalName = "Original Cat"
        repo.insertCategory(Category(label = originalName, icon = "@drawable/favorite", color = "#FF0000"))

        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to Settings -> Manage Categories
            onView(withId(R.id.settings_fragment)).perform(click())
            Thread.sleep(1000)
            
            onView(withId(androidx.preference.R.id.recycler_view))
                .perform(RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withId(R.id.item_manage_categories)),
                    clickOnViewChild(R.id.item_manage_categories)
                ))
            Thread.sleep(1000)

            // 2. Click Edit on the category
            onView(withId(R.id.rv_categories))
                .perform(RecyclerViewActions.actionOnItem<ManageCategoriesAdapter.ViewHolder>(
                    hasDescendant(withText(originalName)),
                    clickOnViewChild(R.id.edit)
                ))
            Thread.sleep(1000)

            // 3. Update name
            val updatedName = "Updated Cat"
            onView(withId(R.id.category_name)).perform(replaceText(updatedName), closeSoftKeyboard())
            onView(withId(R.id.btn_save_category)).perform(click())
            Thread.sleep(1500)

            // 4. Verify update
            onView(allOf(withId(R.id.category_name), withText(updatedName))).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testDeleteCategoryFlow() {
        // Pre-insert a category
        val repo = CategoryRepository(context)
        val testName = "To Delete"
        repo.insertCategory(Category(label = testName, icon = "@drawable/ic_delete", color = "#FF0000"))

        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to Settings -> Manage Categories
            onView(withId(R.id.settings_fragment)).perform(click())
            Thread.sleep(1000)
            onView(withId(androidx.preference.R.id.recycler_view)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                    hasDescendant(withId(R.id.item_manage_categories)),
                    clickOnViewChild(R.id.item_manage_categories)
                )
            )
            Thread.sleep(1000)

            // 2. Click Delete on the category
            onView(withId(R.id.rv_categories)).perform(
                RecyclerViewActions.actionOnItem<ManageCategoriesAdapter.ViewHolder>(
                    hasDescendant(withText(testName)),
                    clickOnViewChild(R.id.delete)
                )
            )
            Thread.sleep(1000)

            // 3. Confirm deletion in dialog
            onView(withText(R.string.delete)).perform(click())
            Thread.sleep(1000)

            // 4. Verify it's gone from the list
            onView(withText(testName)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        }
    }

    @Test
    fun testToggleConfirmDeletion() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to Settings
            onView(withId(R.id.settings_fragment)).perform(click())
            Thread.sleep(1000)

            // 2. Find the switch and toggle it
            onView(withId(androidx.preference.R.id.recycler_view))
                .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withId(R.id.switch_delete_confirm))
                ))
            
            // It starts as true, so click to make it false
            onView(withId(R.id.switch_delete_confirm)).perform(click())
            Thread.sleep(500)

            // 3. Verify SharedPreferences
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            assertFalse(prefs.getBoolean("confirm_deletion", true))

            // 4. Toggle back
            onView(withId(R.id.switch_delete_confirm)).perform(click())
            Thread.sleep(500)
            assertTrue(prefs.getBoolean("confirm_deletion", false))
        }
    }

    @Test
    fun testThemeSelection() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to Settings
            onView(withId(R.id.settings_fragment)).perform(click())
            Thread.sleep(1000)

            // 2. Find Theme Toggle and click Dark
            onView(withId(androidx.preference.R.id.recycler_view))
                .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withId(R.id.btn_theme_dark))
                ))
            
            onView(withId(R.id.btn_theme_dark)).perform(click())
            Thread.sleep(500)

            // 3. Verify SharedPreferences
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            assertTrue(prefs.getString("theme_selection", "system") == "dark")

            // 4. Click Light
            onView(withId(R.id.btn_theme_light)).perform(click())
            Thread.sleep(500)
            assertTrue(prefs.getString("theme_selection", "dark") == "light")
        }
    }

    @Test
    fun testLanguageSelection() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // 1. Navigate to Settings
            onView(withId(R.id.settings_fragment)).perform(click())
            Thread.sleep(1000)

            // 2. Find Language Toggle and click Spanish
            onView(withId(androidx.preference.R.id.recycler_view))
                .perform(RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withId(R.id.btn_lang_es))
                ))
            
            onView(withId(R.id.btn_lang_es)).perform(click())
            Thread.sleep(500)

            // 3. Verify SharedPreferences
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            assertTrue(prefs.getString("language_selection", "system") == "es")

            // 4. Click English
            onView(withId(R.id.btn_lang_en)).perform(click())
            Thread.sleep(500)
            assertTrue(prefs.getString("language_selection", "es") == "en")
        }
    }

    /**
     * Helper to click on a specific view inside a RecyclerView item
     */
    private fun clickOnViewChild(viewId: Int) = object : androidx.test.espresso.ViewAction {
        override fun getConstraints() = null
        override fun getDescription() = "Click on a child view with specified id."
        override fun perform(uiController: androidx.test.espresso.UiController, view: android.view.View) {
            val v = view.findViewById<android.view.View>(viewId)
            v.performClick()
        }
    }
}
