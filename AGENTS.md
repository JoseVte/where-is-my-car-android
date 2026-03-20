# AGENTS.md

Guide for AI coding agents working on "Where Is My Car" - an Android parking location tracking app.

## Project Structure & Key Decisions

**Flavor-based Architecture**: Project uses Android Build Flavors (`mobile`, `wear`) in a single module. Wear OS (Watch) implementation is planned but not yet coded. Agents modifying core features should ensure cross-flavor compatibility:
- Base code: `app/src/main/java/io/josrom/whereismycar/`
- Flavor-specific: `app/src/mobile/`, `app/src/wear/` (watch code currently minimal in `presentation/WatchActivity.kt`)

**Navigation Graph Architecture**: Single-activity pattern with Fragment-based navigation. All screen transitions flow through `MainActivity` → `NavHostFragment` → target `Fragment`. Navigation graph: `app/src/main/res/navigation/nav_graph.xml`. Start destination is `ExploreFragment`. When adding screens, always:
1. Define Fragment class extending `androidx.fragment.app.Fragment`
2. Create layout XML with ViewBinding (`FragmentXxxxBinding`)
3. Register in navigation graph with action definitions
4. Handle lifecycle properly (cleanup in `onDestroyView()`)

**Database Layer**: Custom SQLiteOpenHelper (`DBHelper`) with direct SQL + `Cursor` (no Room/ORM). 
- Models as data classes with companion objects defining column names: `SavedLocation.kt`, `Category.kt`
- Repository pattern: `SavedLocationRepository`, `CategoryRepository` - each contains CRUD logic
- Key: Always use `DBHelper.readableDatabase` for queries, `writableDatabase` for mutations
- Migration strategy: `onUpgrade()` currently drops and recreates tables (acceptable for alpha)

## Critical Implementation Patterns

### OSMDroid Map Initialization (MUST DO FIRST)
Map rendering is fragile. Before ANY layout with `MapView` inflates:
```kotlin
// In Fragment.onCreateView() - FIRST LINE
override fun onCreateView(...): View {
    Configuration.getInstance().load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
    return FragmentLocationDetailsBinding.inflate(...).root  // NOW safe to inflate MapView
}

// Lifecycle management - REQUIRED
override fun onResume() { super.onResume(); binding.map.onResume() }
override fun onPause() { super.onPause(); binding.map.onPause() }
```
Failure to initialize causes blank/unresponsive maps. See `ExploreFragment.onCreateView()` and `LocationDetailsFragment.onCreateView()` for correct patterns.

### ViewBinding Lifecycle
All Activities/Fragments use ViewBinding (enabled project-wide in `build.gradle.kts`):
```kotlin
private var _binding: FragmentXxxxBinding? = null
private val binding get() = _binding!!  // Will crash if accessed after onDestroyView()

override fun onCreateView(...): View {
    _binding = FragmentXxxxBinding.inflate(inflater, container, false)
    return binding.root
}

override fun onDestroyView() {
    super.onDestroyView()
    _binding = null  // MANDATORY - prevents memory leaks
}
```

### Data Access Pattern
Repository methods construct SQL queries manually with selection parts. Example from `SavedLocationRepository`:
```kotlin
fun getAllLocations(limit: Int? = null, searchQuery: String? = null, categoryIds: List<Int>? = null): MutableList<SavedLocation> {
    val selectionParts = mutableListOf<String>()
    val selectionArgsList = mutableListOf<String>()
    
    if (!searchQuery.isNullOrBlank()) {
        selectionParts.add("(${SavedLocation.COLUMN_DISPLAY_NAME} LIKE ? OR ${SavedLocation.COLUMN_ADDRESS} LIKE ?)")
        selectionArgsList.add("%$searchQuery%")
        selectionArgsList.add("%$searchQuery%")
    }
    // ... build query, execute
}
```
Maintain this pattern for consistency. Use `hashMapOf<String, String>()` for JSON storage (e.g., `addressData`).

## Cross-Cutting Features

### Firebase Sync Integration
`SyncManager` handles Google Sign-In + Firebase Realtime Database sync for location data.
- Preference key: `sync_enabled` (SharedPreferences)
- Only syncs if user signed in + preference enabled
- Called from: `MainActivity.onResume()` via `refreshSyncPreference()`
- **Important**: Sync is async; refresh UI post-delayed (see line 47 in MainActivity for pattern)

### SharedPreferences Pattern
Core settings stored in SharedPreferences accessed via:
```kotlin
PreferenceManager.getDefaultSharedPreferences(context).apply {
    getBoolean("sync_enabled", true)  // Default to enabled
    getString("theme", "light")
    // commit/apply after writes
}
```
Settings UI: `SettingsFragment` uses `PreferenceFragmentCompat` with XML preferences.

### Permissions
App requires: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION`, `WRITE_EXTERNAL_STORAGE` (see `AndroidManifest.xml`). Handle runtime permissions for API 30+ in Fragments/Activities that need location.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug build to connected device
./gradlew installDebug

# Clean build artifacts
./gradlew clean

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run specific unit test
./gradlew test --tests io.josrom.whereismycar.ExampleUnitTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.josrom.whereismycar.ExampleInstrumentedTest

# Core builds
./gradlew assembleDebug              # Mobile debug APK
./gradlew assembleWearDebug          # Watch debug APK
./gradlew installDebug               # Install mobile to connected device

# Tests
./gradlew test                       # Unit tests (app/src/test/)
./gradlew connectedAndroidTest       # Instrumented tests (app/src/androidTest/)

# Specific test
./gradlew test --tests io.josrom.whereismycar.ExampleUnitTest
```

**Key Note**: Gradle uses version catalog (`gradle/libs.versions.toml`) for dependency management. Add/update dependencies there, then reference with `libs.*` in build files.

## File Organization for Common Tasks

| Task | Primary Files |
|------|---------------|
| Add new screen | Create `Fragment`, layout XML, update `nav_graph.xml` |
| Add onboarding page | Extend `OnboardingActivity` + create layout in `res/layout/item_onboarding_x.xml` |
| Configure cloud sync | Modify `CloudSyncSettingsFragment` + `SyncManager` |
| New database column | Modify `SavedLocation`/`Category` data class + `DBHelper.onCreate()` (migration: add to `onUpgrade()`) |
| Add map feature | Extend `LocationDetailsFragment` (see route overlay pattern) |
| Add setting | Create XML in `res/xml/` + handle in `SettingsFragment` or `AppSettingsPreference` |
| Wear OS feature | `app/src/wear/` folder (currently minimal - reference `WEAR_OS_PLAN.md`) |

## Emerging Patterns (In Progress)

- **Onboarding Flow**: `OnboardingActivity` provides initial user setup with `ViewPager2` and `TabLayout`. Uses `OnboardingAdapter` for swipeable pages with skip functionality.
- **Wear OS Integration**: Planned for `wear` flavor using Compose. See `WEAR_OS_PLAN.md` for roadmap.
- **Presentation Package**: `presentation/` folder contains Wear OS Compose themes (`WhereIsMyCarTheme`) and activities (`WatchActivity.kt`).
- **MapLibre SDK**: Version 12.3.1 is declared in `libs.versions.toml` but not yet integrated (currently using OSMDroid only).
- **Compose Integration**: Wear OS screens will use Compose; mobile still View-based.

## Conventions

- **Package**: `io.josrom.whereismycar`
- **Language**: Kotlin (Java 11 compatibility)
- **Code Style**: No specific linter (check `lint.xml` for Android Lint config)
- **Naming**: Fragment classes end with `Fragment` (e.g., `LocationDetailsFragment`), Adapters end with `Adapter`
- **Logging**: `Log.d("TAG", message)` or `Log.w()` for warnings (see `MainActivity` and `SyncManager` for examples)

## Extension Points & Boundaries

**Fragment Communication**: Use Bundle arguments + Navigation arguments (not direct Fragment references). Example:
```kotlin
arguments?.let { locationId = it.getInt("locationId") }  // Receive in Fragment
```

**Adapter Pattern**: `LocationAdapter` uses ViewBinding with item click listeners. Extend for new list-based UIs.

**Service**: `LocationService` is stubbed (`START_STICKY`) - extend here for background location tracking if needed.

---

**Last Updated**: March 17, 2026 | **Min SDK**: 28 | **Target SDK**: 36
