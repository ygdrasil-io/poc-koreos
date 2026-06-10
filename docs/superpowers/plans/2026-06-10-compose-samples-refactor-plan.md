# Compose Samples Refactor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure Compose samples into `samples/compose/` with a shared infrastructure module and responsive showcase per platform.

**Architecture:** Extract the duplicated Compose integration layer (ComposeSceneHost, renderers, event bridge) and feature demos from `samples/hello-compose/` into `samples/compose/shared/`. Create four thin platform modules (`desktop/`, `android/`, `ios/`, `web/`) that each instantiate a responsive showcase UI. `simulation-demo` is not modified.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Kadre, Gradle, KMP source sets (commonMain, jvmMain, androidMain, iosMain, jsMain, wasmJsMain)

---

### File Structure (all relative to repo root)

**Created:**
- `samples/compose/shared/build.gradle.kts`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/FeatureCategory.kt`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/ShowcaseFeature.kt`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.kt`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/ShowcaseApp.kt`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/KeyTestDemo.kt`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/CaptureDemo.kt`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/CoroutinesDemo.kt`
- `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/NativeFfiDemo.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/ComposeSceneHost.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/ComposeWindowRenderer.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/GlComposeRenderer.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/MetalComposeRenderer.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/WindowEventBridge.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/NativeFfi.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/Win32WglContext.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/X11GlxContext.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/WaylandEglContext.kt`
- `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/PlatformContext.jvm.kt`
- `samples/compose/shared/src/androidMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.android.kt`
- `samples/compose/shared/src/iosMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.ios.kt`
- `samples/compose/shared/src/jsMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.js.kt`
- `samples/compose/shared/src/wasmJsMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.wasmJs.kt`
- `samples/compose/desktop/build.gradle.kts`
- `samples/compose/desktop/src/main/kotlin/org/graphiks/kadre/samples/compose/desktop/Main.kt`
- `samples/compose/android/build.gradle.kts`
- `samples/compose/android/src/main/AndroidManifest.xml`
- `samples/compose/android/src/main/kotlin/org/graphiks/kadre/samples/compose/android/MainActivity.kt`
- `samples/compose/android/src/main/res/values/strings.xml`
- `samples/compose/ios/build.gradle.kts`
- `samples/compose/ios/src/iosMain/kotlin/org/graphiks/kadre/samples/compose/ios/MainViewController.kt`
- `samples/compose/web/build.gradle.kts`
- `samples/compose/web/src/jsMain/kotlin/org/graphiks/kadre/samples/compose/web/Main.kt`
- `samples/compose/web/src/wasmJsMain/kotlin/org/graphiks/kadre/samples/compose/web/Main.kt`
- `samples/compose/web/src/jsMain/resources/index.html`
- `samples/compose/web/src/wasmJsMain/resources/index.html`

**Modified:**
- `settings.gradle.kts` — remove `:samples:hello-compose`, add compose modules

**Deleted:**
- `samples/hello-compose/` (entire directory)

---

### Task 1: Create shared module build file

**Files:**
- Create: `samples/compose/shared/build.gradle.kts`

- [ ] **Create `samples/compose/shared/build.gradle.kts`**

```kotlin
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()
    androidTarget { publishLibraryVariants("release") }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) { browser() }
    wasmJs { browser() }
    jvmToolchain(25)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        androidMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(libs.androidx.activity.compose)
            }
        }
        jvmMain {
            dependencies {
                implementation(project(":kadre"))
                implementation(project(":kadre-coroutines"))
                implementation(project(":kadre-win32"))
                implementation(project(":kadre-x11"))
                implementation(project(":kadre-wayland"))
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        iosMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        jsMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        wasmJsMain {
            dependsOn(jsMain)
        }
    }
}
```

- [ ] **Create directory structure**

Run: `mkdir -p samples/compose/shared/src/{commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features,jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra,androidMain/kotlin/org/graphiks/kadre/samples/compose/showcase,iosMain/kotlin/org/graphiks/kadre/samples/compose/showcase,jsMain/kotlin/org/graphiks/kadre/samples/compose/showcase,wasmJsMain/kotlin/org/graphiks/kadre/samples/compose/showcase}`

---

### Task 2: Create showcase UI infrastructure (commonMain)

**Files:**
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/FeatureCategory.kt`
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/ShowcaseFeature.kt`
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.kt`

- [ ] **Create `FeatureCategory.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

enum class FeatureCategory(val displayName: String) {
    Keyboard("Keyboard & Input"),
    Capture("Capture & Rendering"),
    Concurrency("Coroutines"),
    Platform("Platform & FFI"),
}
```

- [ ] **Create `ShowcaseFeature.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import androidx.compose.runtime.Composable

interface ShowcaseFeature {
    val id: String
    val title: String
    val description: String
    val category: FeatureCategory
    val devOnly: Boolean
    @Composable fun Content(platformContext: PlatformContext)
}
```

- [ ] **Create `PlatformContext.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

expect class PlatformContext {
    fun isFeatureSupported(category: FeatureCategory): Boolean
}
```

---

### Task 3: Create ShowcaseApp with responsive UI (commonMain)

**Files:**
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/ShowcaseApp.kt`

- [ ] **Create `ShowcaseApp.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.compose.showcase.features.CaptureDemo
import org.graphiks.kadre.samples.compose.showcase.features.CoroutinesDemo
import org.graphiks.kadre.samples.compose.showcase.features.KeyTestDemo
import org.graphiks.kadre.samples.compose.showcase.features.NativeFfiDemo

private val allFeatures: List<ShowcaseFeature> = listOf(
    KeyTestDemo,
    CoroutinesDemo,
    NativeFfiDemo,
    CaptureDemo,
)

private val userFeatures = allFeatures.filter { !it.devOnly }
private val featuresByCategory = userFeatures.groupBy { it.category }

@Composable
fun ShowcaseApp(platformContext: PlatformContext) {
    MaterialTheme {
        var selectedCategory by remember { mutableStateOf(featuresByCategory.keys.first()) }
        var selectedFeature by remember { mutableStateOf<ShowcaseFeature?>(null) }
        val isWide = WindowWidthSizeClass.calculateFromCurrent() == WindowWidthSizeClass.Expanded

        if (isWide) {
            DesktopLayout(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                selectedFeature = selectedFeature,
                onFeatureSelected = { selectedFeature = it },
                platformContext = platformContext,
            )
        } else {
            MobileLayout(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                selectedFeature = selectedFeature,
                onFeatureSelected = { selectedFeature = it },
                platformContext = platformContext,
            )
        }
    }
}

@Composable
private fun DesktopLayout(
    selectedCategory: FeatureCategory,
    onCategorySelected: (FeatureCategory) -> Unit,
    selectedFeature: ShowcaseFeature?,
    onFeatureSelected: (ShowcaseFeature?) -> Unit,
    platformContext: PlatformContext,
) {
    ModalNavigationDrawer(
        drawerState = remember { DrawerValue.Closed },
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Compose Showcase",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                featuresByCategory.forEach { (category, _) ->
                    NavigationDrawerItem(
                        label = { Text(category.displayName) },
                        selected = category == selectedCategory,
                        onClick = { onCategorySelected(category); onFeatureSelected(null) },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedCategory.displayName) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            },
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(featuresByCategory[selectedCategory].orEmpty()) { feature ->
                    if (platformContext.isFeatureSupported(feature.category)) {
                        FeatureCard(
                            feature = feature,
                            onClick = { onFeatureSelected(feature) },
                            platformContext = platformContext,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileLayout(
    selectedCategory: FeatureCategory,
    onCategorySelected: (FeatureCategory) -> Unit,
    selectedFeature: ShowcaseFeature?,
    onFeatureSelected: (ShowcaseFeature?) -> Unit,
    platformContext: PlatformContext,
) {
    if (selectedFeature != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedFeature.title) },
                    navigationIcon = {
                        Text("←", modifier = Modifier.padding(16.dp).clickable { onFeatureSelected(null) })
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                selectedFeature.Content(platformContext)
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Compose Showcase") })
            },
            bottomBar = {
                NavigationBar {
                    featuresByCategory.keys.forEach { category ->
                        NavigationBarItem(
                            selected = category == selectedCategory,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category.displayName) },
                            icon = {},
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(featuresByCategory[selectedCategory].orEmpty()) { feature ->
                    if (platformContext.isFeatureSupported(feature.category)) {
                        FeatureCard(
                            feature = feature,
                            onClick = { onFeatureSelected(feature) },
                            platformContext = platformContext,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    feature: ShowcaseFeature,
    onClick: () -> Unit,
    platformContext: PlatformContext,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleMedium)
            Text(feature.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

Note: the import `androidx.compose.material3.windowsizeclass.WindowWidthSizeClass` may need `implementation(compose.material3)` but it's actually in `compose.material3` since Compose 1.7+. Verify during implementation.

---

### Task 4: Port feature composables to shared/commonMain

**Files:**
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/KeyTestDemo.kt`
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/CaptureDemo.kt`
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/CoroutinesDemo.kt`
- Create: `samples/compose/shared/src/commonMain/kotlin/org/graphiks/kadre/samples/compose/showcase/features/NativeFfiDemo.kt`

- [ ] **Create `KeyTestDemo.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.compose.showcase.FeatureCategory
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseFeature

object KeyTestDemo : ShowcaseFeature {
    override val id = "keytest"
    override val title = "Keyboard Test"
    override val description = "Headless keyboard input forwarding self-test"
    override val category = FeatureCategory.Keyboard
    override val devOnly = false

    @Composable
    override fun Content(platformContext: PlatformContext) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Text("Keyboard Input Forwarding")
            Text("Demonstrates AWT KeyEvent → Compose key event conversion.")
        }
    }
}
```

- [ ] **Create `CaptureDemo.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.compose.showcase.FeatureCategory
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseFeature

object CaptureDemo : ShowcaseFeature {
    override val id = "capture"
    override val title = "Offscreen Capture"
    override val description = "Raster and windowed GPU capture for headless CI verification"
    override val category = FeatureCategory.Capture
    override val devOnly = true

    @Composable
    override fun Content(platformContext: PlatformContext) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Text("Offscreen Capture & CI")
            Text("Internal feature — used for headless rendering verification.")
        }
    }
}
```

- [ ] **Create `CoroutinesDemo.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.compose.showcase.FeatureCategory
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseFeature

object CoroutinesDemo : ShowcaseFeature {
    override val id = "coroutines"
    override val title = "Coroutines Demo"
    override val description = "Kadre event loop driven by coroutines with delay()"
    override val category = FeatureCategory.Concurrency
    override val devOnly = false

    @Composable
    override fun Content(platformContext: PlatformContext) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Text("Coroutine-driven Kadre Loop")
            Text("Demonstrates EventLoopDispatcher, delay(), and structured shutdown.")
        }
    }
}
```

- [ ] **Create `NativeFfiDemo.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.graphiks.kadre.samples.compose.showcase.FeatureCategory
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseFeature

object NativeFfiDemo : ShowcaseFeature {
    override val id = "native-ffi"
    override val title = "Native FFI"
    override val description = "Panama FFM helpers for GL context backends"
    override val category = FeatureCategory.Platform
    override val devOnly = false

    @Composable
    override fun Content(platformContext: PlatformContext) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Text("Panama FFM Native Helpers")
            Text("Symbol lookup, method handle creation, and pointer utilities used by GL contexts.")
        }
    }
}
```

---

### Task 5: Port desktop infrastructure to shared/jvmMain

**Files:**
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/ComposeSceneHost.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/ComposeWindowRenderer.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/GlComposeRenderer.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/MetalComposeRenderer.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/WindowEventBridge.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/NativeFfi.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/Win32WglContext.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/X11GlxContext.kt`
- Create: `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/WaylandEglContext.kt`

- [ ] **Copy infra files from hello-compose to shared/jvmMain, updating package names**

For each file in `samples/hello-compose/src/main/kotlin/org/graphiks/kadre/samples/hellocompose/`:
- `ComposeSceneHost.kt`, `ComposeWindowRenderer.kt`, `GlComposeRenderer.kt`, `MetalComposeRenderer.kt`, `WindowEventBridge.kt`, `NativeFfi.kt`, `Win32WglContext.kt`, `X11GlxContext.kt`, `WaylandEglContext.kt`

Copy to `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/` and replace:
- `package org.graphiks.kadre.samples.hellocompose` with `package org.graphiks.kadre.samples.compose.infra`

No code changes needed for the files themselves. However, change the visibility of `applyWindowEvent` in `WindowEventBridge.kt` from `internal` to `public` because it's called from the desktop module (different Gradle module). The line to change:

```kotlin
// before:
internal fun ComposeWindowRenderer.applyWindowEvent(...)
// after:
fun ComposeWindowRenderer.applyWindowEvent(...)
```

- [ ] **Create shared/jvmMain PlatformContext**

Create `samples/compose/shared/src/jvmMain/kotlin/org/graphiks/kadre/samples/compose/infra/PlatformContext.jvm.kt`:

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

actual class PlatformContext {
    actual fun isFeatureSupported(category: FeatureCategory): Boolean = true
}
```

---

### Task 6: Create platform PlatformContext actuals

**Files:**
- Create: `samples/compose/shared/src/androidMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.android.kt`
- Create: `samples/compose/shared/src/iosMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.ios.kt`
- Create: `samples/compose/shared/src/jsMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.js.kt`
- Create: `samples/compose/shared/src/wasmJsMain/kotlin/org/graphiks/kadre/samples/compose/showcase/PlatformContext.wasmJs.kt`

- [ ] **Create `PlatformContext.android.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

actual class PlatformContext {
    actual fun isFeatureSupported(category: FeatureCategory): Boolean = true
}
```

- [ ] **Create `PlatformContext.ios.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

actual class PlatformContext {
    actual fun isFeatureSupported(category: FeatureCategory): Boolean = true
}
```

- [ ] **Create `PlatformContext.js.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

actual class PlatformContext {
    actual fun isFeatureSupported(category: FeatureCategory): Boolean = true
}
```

- [ ] **Create `PlatformContext.wasmJs.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.showcase

import org.graphiks.kadre.samples.compose.showcase.FeatureCategory

actual class PlatformContext {
    actual fun isFeatureSupported(category: FeatureCategory): Boolean = true
}
```

---

### Task 7: Create desktop module

**Files:**
- Create: `samples/compose/desktop/build.gradle.kts`
- Create: `samples/compose/desktop/src/main/kotlin/org/graphiks/kadre/samples/compose/desktop/Main.kt`

- [ ] **Create `samples/compose/desktop/build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.ui.InternalComposeUiApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}

application {
    mainClass.set("org.graphiks.kadre.samples.compose.desktop.MainKt")
    applicationDefaultJvmArgs = buildList {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            add("-XstartOnFirstThread")
        }
        add("--enable-native-access=ALL-UNNAMED")
    }
}

dependencies {
    implementation(project(":samples:compose:shared"))
    implementation(compose.desktop.currentOs)
}
```

- [ ] **Create `samples/compose/desktop/src/main/kotlin/org/graphiks/kadre/samples/compose/desktop/Main.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.desktop

import org.graphiks.kadre.PhysicalSize
import org.graphiks.kadre.WindowAttributes
import org.graphiks.kadre.core.RawWindowHandle
import org.graphiks.kadre.core.WindowEvent
import org.graphiks.kadre.coroutines.kadreApplication
import org.graphiks.kadre.samples.compose.infra.ComposeWindowRenderer
import org.graphiks.kadre.samples.compose.infra.KeyForwarder
import org.graphiks.kadre.samples.compose.infra.applyWindowEvent
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseApp

private fun runShowcase() = kadreApplication {
    val keys = KeyForwarder()
    val win = createWindow(
        WindowAttributes("Compose Showcase", PhysicalSize(900, 700), visible = true, resizable = true),
    )
    val handle = win.window.rawWindowHandle as? RawWindowHandle ?: run { exit(); return@kadreApplication }
    val renderer = ComposeWindowRenderer.create(handle, win.window.scaleFactor, dispatcher).getOrElse {
        println("[compose-showcase] Cannot create renderer: ${it.message}")
        exit(); return@kadreApplication
    }
    val inner = win.window.innerSize
    renderer.resize(inner.width, inner.height, win.window.scaleFactor)
    renderer.setContent { ShowcaseApp(PlatformContext()) }

    var lastRenderNanos = 0L
    val frameIntervalNanos = 50_000_000L

    win.events.collect { event ->
        when (event) {
            is WindowEvent.CloseRequested -> {
                renderer.dispose(); exit()
            }
            is WindowEvent.RedrawRequested -> {
                val now = System.nanoTime()
                if (now - lastRenderNanos >= frameIntervalNanos) {
                    lastRenderNanos = now
                    renderer.applyWindowEvent(event, win.window, keys)
                }
            }
            else -> renderer.applyWindowEvent(event, win.window, keys)
        }
    }
}

fun main() {
    println("[compose-showcase] Starting Compose Showcase")
    runShowcase()
    println("[compose-showcase] Done")
}
```

- [ ] **Create directory for desktop**

Run: `mkdir -p samples/compose/desktop/src/main/kotlin/org/graphiks/kadre/samples/compose/desktop`

---

### Task 8: Create android module

**Files:**
- Create: `samples/compose/android/build.gradle.kts`
- Create: `samples/compose/android/src/main/AndroidManifest.xml`
- Create: `samples/compose/android/src/main/kotlin/org/graphiks/kadre/samples/compose/android/MainActivity.kt`
- Create: `samples/compose/android/src/main/res/values/strings.xml`

- [ ] **Create `samples/compose/android/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

android {
    namespace = "org.graphiks.kadre.samples.compose.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.graphiks.kadre.samples.compose.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    kotlin { jvmToolchain(25) }
}

dependencies {
    implementation(project(":samples:compose:shared"))
    implementation(libs.androidx.activity.compose)
}
```

- [ ] **Create `samples/compose/android/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="@string/app_name">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Create `samples/compose/android/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Compose Showcase</string>
</resources>
```

- [ ] **Create `samples/compose/android/src/main/kotlin/org/graphiks/kadre/samples/compose/android/MainActivity.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShowcaseApp(PlatformContext()) }
    }
}
```

---

### Task 9: Create ios module

**Files:**
- Create: `samples/compose/ios/build.gradle.kts`
- Create: `samples/compose/ios/src/iosMain/kotlin/org/graphiks/kadre/samples/compose/ios/MainViewController.kt`

- [ ] **Create `samples/compose/ios/build.gradle.kts`**

```kotlin
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvmToolchain(25)

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":samples:compose:shared"))
            }
        }
    }
}
```

- [ ] **Create `samples/compose/ios/src/iosMain/kotlin/org/graphiks/kadre/samples/compose/ios/MainViewController.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.ios

import androidx.compose.ui.window.ComposeUIViewController
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseApp

fun MainViewController() = ComposeUIViewController { ShowcaseApp(PlatformContext()) }
```

---

### Task 10: Create web module

**Files:**
- Create: `samples/compose/web/build.gradle.kts`
- Create: `samples/compose/web/src/jsMain/kotlin/org/graphiks/kadre/samples/compose/web/Main.kt`
- Create: `samples/compose/web/src/wasmJsMain/kotlin/org/graphiks/kadre/samples/compose/web/Main.kt`
- Create: `samples/compose/web/src/jsMain/resources/index.html`
- Create: `samples/compose/web/src/wasmJsMain/resources/index.html`

- [ ] **Create `samples/compose/web/build.gradle.kts`**

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js {
        browser { commonWebpackConfig { outputFileName = "compose-showcase.js" } }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "compose-showcase-wasm.js" } }
        binaries.executable()
    }
    jvmToolchain(25)

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":samples:compose:shared"))
            }
        }
    }
}
```

- [ ] **Create `samples/compose/web/src/jsMain/kotlin/org/graphiks/kadre/samples/compose/web/Main.kt`**

```kotlin
package org.graphiks.kadre.samples.compose.web

import androidx.compose.ui.window.CanvasBasedWindow
import org.graphiks.kadre.samples.compose.showcase.PlatformContext
import org.graphiks.kadre.samples.compose.showcase.ShowcaseApp

fun main() {
    CanvasBasedWindow("Compose Showcase") { ShowcaseApp(PlatformContext()) }
}
```

- [ ] **Create `samples/compose/web/src/wasmJsMain/kotlin/org/graphiks/kadre/samples/compose/web/Main.kt`**

Same content as `jsMain` version.

- [ ] **Create `samples/compose/web/src/jsMain/resources/index.html`**

```html
<!DOCTYPE html>
<html><head><title>Compose Showcase</title></head><body></body></html>
```

- [ ] **Create `samples/compose/web/src/wasmJsMain/resources/index.html`**

Same content as jsMain version.

---

### Task 11: Update settings.gradle.kts and cleanup

**Files:**
- Modify: `settings.gradle.kts` — lines 80, 91-92 (add compose modules, remove hello-compose)
- Delete: `samples/hello-compose/` entirely

- [ ] **Update `settings.gradle.kts`**

Remove line 80:
```
include(":samples:hello-compose")
```

Add after line 92:
```
include(":samples:compose:shared")
include(":samples:compose:desktop")
include(":samples:compose:android")
include(":samples:compose:ios")
include(":samples:compose:web")
```

- [ ] **Delete `samples/hello-compose/` directory**

Run: `rm -rf samples/hello-compose`

- [ ] **Verify build compiles**

Run: `./gradlew :samples:compose:shared:compileKotlinJvm :samples:compose:desktop:compileKotlin :samples:compose:shared:compileKotlinAndroid :samples:compose:shared:compileKotlinIosX64 :samples:compose:shared:compileKotlinJs :samples:compose:shared:compileKotlinWasmJs`
Expected: BUILD SUCCESSFUL
