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

## Important Implementation Notes

1. **OSMDroid Initialization**: Must call `Configuration.getInstance().load()` in `onCreateView()` BEFORE inflating layouts that contain MapView, otherwise the map will not render correctly.

2. **Map Lifecycle**: LocationListFragment properly manages map lifecycle with `onResume()` and `onPause()` callbacks forwarded to the MapView.

3. **ViewBinding**: All fragments and activities use ViewBinding. Binding instances are nullable in fragments and must be cleaned up in `onDestroyView()`.

4. **Java Version**: Project uses Java 11 for source and target compatibility.

5. **Gradle Version Catalog**: Dependencies are managed via `libs` version catalog (see `gradle/libs.versions.toml` if it exists).

## Test Structure

- Unit tests: `app/src/test/java/io/josrom/whereismycar/`
- Instrumented tests: `app/src/androidTest/java/io/josrom/whereismycar/`