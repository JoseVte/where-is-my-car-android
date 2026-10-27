# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

"Where Is My Car" is an Android application that helps users save and track parking locations using maps. The app uses OSMDroid for map display and follows the single-activity architecture with Navigation Components.

**Package**: `io.josrom.whereismycar`
**Min SDK**: 24 (Android 7.0)
**Target SDK**: 36

## Build Commands

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

# Run JVM unit tests of the mobile flavor (includes the API client and mappers)
./gradlew :app:testMobileDebugUnitTest

# Run specific unit test
./gradlew test --tests io.josrom.whereismycar.ExampleUnitTest

# Run specific instrumented test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.josrom.whereismycar.ExampleInstrumentedTest
```

## Architecture

### Navigation Structure
The app uses a single-activity architecture with Android Navigation Component:
- **MainActivity**: Host activity with a toolbar and FAB
- **LocationListFragment**: Main screen with map view (OSMDroid MapView) and RecyclerView for saved locations
- **SecondFragment**: Secondary screen (currently minimal implementation)
- Navigation graph defined in `app/src/main/res/navigation/nav_graph.xml`

### Key Components

**Data Model**:
- `SavedLocation`: Data class representing a saved parking location (id, latitude, longitude, date)

**UI Components**:
- `LocationListFragment`: Main screen containing both the map and list of saved locations
  - Integrates OSMDroid map with MAPNIK tile source
  - Requires OSMDroid configuration initialization before view inflation
  - Contains RecyclerView with LocationAdapter for displaying saved locations
- `LocationAdapter`: RecyclerView adapter for displaying saved location items using ViewBinding
- View Binding is enabled project-wide (see `app/build.gradle.kts`)

**Map Integration**:
- OSMDroid is configured in `LocationListFragment.onCreateView()` BEFORE layout inflation
- Map lifecycle methods (`onResume()`, `onPause()`) are properly handled in LocationListFragment
- Requires INTERNET, ACCESS_NETWORK_STATE, ACCESS_FINE_LOCATION, and WRITE_EXTERNAL_STORAGE permissions (see AndroidManifest.xml)

### Dependencies
- **Map Libraries**:
  - `osmdroid-android` for OpenStreetMap rendering
  - MapLibre Android SDK (12.3.1) - not yet integrated in code
- **Architecture Components**: Navigation Component, ViewBinding
- **UI**: Material Design Components, ConstraintLayout

### Sync with the Where Is My Car API
- SQLite (`DBHelper`, version 3) is the source of truth, so the app works offline. `SyncManager` syncs it with the REST API (Spring Boot project in `~/Projects/Personal/whereismycar`). Firebase Realtime Database is no longer used.
- Every synced row stores its API id in `remote_id` (`Category.remoteId`, `SavedLocation.remoteId`). Local ids and API ids are different, never mix them. Read `remote_id` before deleting a local row, the delete call to the API needs it.
- Full sync (after Google sign in and on resume): `GET /api/me` (creates the API user), then categories, then locations. It downloads what is missing locally, links local categories to API categories with the same label (the API does not allow duplicate labels), and uploads local changes. Local wins on conflicts.
- Single inserts, updates and deletes are pushed right away when auto sync is on. All API calls run on `Dispatchers.IO`, one at a time (`Mutex`). Failures are logged and retried on the next full sync.
- Network code lives in the `api` package: `WhereIsMyCarApi` (Retrofit), `ApiClient` (adds the Firebase ID token and `Accept-Language`, refreshes the token once on 401) and `ApiMappers` (local and API models, dates and the API limits).
- Dates: SQLite keeps `yyyy-MM-dd HH:mm:ss` in the device time zone, the API uses ISO 8601 UTC. Colors: the color picker returns `#AARRGGBB`, the API only accepts `#RRGGBB`. Always convert through `ApiMappers`.
- API URL: `BuildConfig.API_BASE_URL`. Debug builds use `api.base.url` from `local.properties` (default `http://10.0.2.2:8080/`, the host machine as seen from the emulator). Release builds use `api.base.url.release` (default `https://api-where-is-my-car.josrom.io/`).
- Every location has a category (the API rejects locations without one). `SavedLocationRepository.insertLocation` falls back to `CategoryRepository.getOrCreateDefaultCategory()` ("Others"), the edit screen requires a category, and deleting a category deletes its locations (locally and in the API). There is no "Uncategorized" anymore.
- Known limitation: deletions made while offline are not queued, so the next full sync downloads those items again from the API.

## Important Implementation Notes

1. **OSMDroid Initialization**: Must call `Configuration.getInstance().load()` in `onCreateView()` BEFORE inflating layouts that contain MapView, otherwise the map will not render correctly.

2. **Map Lifecycle**: LocationListFragment properly manages map lifecycle with `onResume()` and `onPause()` callbacks forwarded to the MapView.

3. **ViewBinding**: All fragments and activities use ViewBinding. Binding instances are nullable in fragments and must be cleaned up in `onDestroyView()`.

4. **Database migrations**: `DBHelper.onUpgrade` migrates step by step (`if (oldVersion < N)`) and must keep user data. Never drop tables on upgrade.

5. **Java Version**: Project uses Java 11 for source and target compatibility.

6. **Gradle Version Catalog**: Dependencies are managed via `libs` version catalog (see `gradle/libs.versions.toml` if it exists).

## Test Structure

- Unit tests: `app/src/test/java/io/josrom/whereismycar/`
- Instrumented tests: `app/src/androidTest/java/io/josrom/whereismycar/`