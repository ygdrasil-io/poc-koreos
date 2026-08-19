# Task 7 — Android API 29 diagnostic (phases 1–2)

Scope: facts, reproduction, pattern comparison, and one hypothesis only. No source, AVD, workflow, or dependency change was applied. The repository commit observed during the final state check was `1c14405bf94d00c0a7637122354a8f95c0b34248`; the working tree also contains the pre-existing Gradle-generated untracked `kotlin-js-store/wasm/` directory.

## Reproduction

Command run:

```text
rtk env ANDROID_SERIAL=emulator-5554 ./gradlew :samples:hello-triangle-android-capture:connectedDebugAndroidTest --no-daemon --stacktrace --console=plain
```

Result: reproducible failure (exit 1) on the only selected device, `Kadre_API_29(AVD) - 10`:

```text
org.graphiks.kadre.samples.android.CaptureTest > capturesTriangle[...] FAILED
java.lang.IllegalStateException: Failed to acquire Adapter
at ...HelloTriangleCaptureKt.captureTriangle(HelloTriangleCapture.kt:81)
```

The earlier all-device run produced the complementary pattern: this API 29 device failed, while `Medium_Phone(AVD) - 16` passed the same `capturesTriangle` test. The failure happens at `instance.requestAdapter(wgpuSurface)` after creating the WGPU Vulkan instance and Android surface, before device creation, shader compilation, texture allocation, or rendering.

Relevant API 29 logcat lines from the reproduced execution show Vulkan enumeration followed by a null-adapter path:

```text
D goldfish_vulkan: on_vkEnumerateDeviceExtensionProperties: host has ext semaphore? win32 0 posix 0
W com.sun.jna.Native: JNA: Callback ... WGPURequestAdapterCallback ... threw the following exception
E TestRunner: java.lang.IllegalStateException: Failed to acquire Adapter
```

No WGPU native diagnostic identifying a repository code failure was emitted in the collected logcat.

## Device and Vulkan comparison

Both targets are online arm64 emulators. `adb shell pm list features` advertises Vulkan on both; that advertised feature is insufficient here because the physical-device listing differs.

| Fact | Failing `emulator-5554` | Passing `emulator-5556` |
| --- | --- | --- |
| AVD / OS | `Kadre_API_29`; Android 10, SDK 29 | `Medium_Phone`; Android 16, SDK 36 |
| System image | `android-29/google_apis/arm64-v8a` | `android-36/google_apis_playstore/arm64-v8a` |
| AVD GPU configuration | `hw.gpu.enabled=no`, `hw.gpu.mode=auto` | `hw.gpu.enabled=yes`, `hw.gpu.mode=auto` |
| Emulator graphics properties | `ro.hardware.egl=emulation`, `ro.kernel.qemu.gles=1`, `ro.boot.hardware.vulkan=ranchu` | `ro.hardware.egl=emulation`; modern image does not expose the older `ro.kernel.qemu.*` GPU properties |
| Advertised Vulkan API | `4198400` (Vulkan 1.1) | `4206592` (Vulkan 1.3) |
| `adb shell cmd gpu vkjson` physical-device data | `devices: [ {} ]`; no `deviceName`, `driverName`, or `driverInfo` | `deviceName: llvmpipe (LLVM 21.1.4, 128 bits)`; `driverName: llvmpipe`; Mesa 26.0.0-devel |
| Test result | `requestAdapter` returns null | Full capture test passes |

The decisive contrast is that the API 29 AVD is persisted with GPU disabled and exposes no describable Vulkan physical device to `cmd gpu vkjson`; the passing AVD enables GPU and exposes the software Vulkan device used by the test. The API 29 image still advertises a Vulkan feature, explaining why instance creation/enumeration can begin without yielding an adapter suitable for WGPU.

## Repository/history/configuration analysis

The capture sample explicitly forces the Vulkan backend:

```kotlin
val instance = WGPU.createInstance(WGPUInstanceBackend.Vulkan)
val adapter = instance.requestAdapter(wgpuSurface)
```

It declares `io.ygdrasil:wgpu4k-toolkit:0.1.1`; neither this sample nor its WGPU configuration has a diff in `9bdc7f1e..HEAD`. The branch changes in `kadre-android` are Android host-test wiring and an `AndroidLoopStateTest`, not the capture sample or WGPU adapter setup.

The closure workflow's intended environment is separately configured: `reactivecircus/android-emulator-runner@v2` on Ubuntu, API 34 x86_64, with `-gpu swiftshader_indirect`. It is not equivalent to the local API 29 arm64 AVD with `hw.gpu.enabled=no`.

## Single hypothesis

The failure is an emulator graphics-configuration incompatibility, not a code defect introduced by this branch: the persisted API 29 AVD disables GPU acceleration, so the explicitly Vulkan-only WGPU path cannot obtain an adapter. This is supported by the repeatable device-specific failure, the pass on a GPU-enabled AVD with the identical APK/test, the absence of branch changes to the capture/WGPU path, and the empty API 29 `vkjson` physical-device record.

## Minimal proposed test (not run)

Run the unchanged `connectedDebugAndroidTest` once against a clean API 29 Google APIs arm64 AVD started with an explicitly enabled SwiftShader Vulkan renderer (for example, the same AVD launched with `-no-snapshot -gpu swiftshader_indirect`), and inspect `cmd gpu vkjson` before the test. This changes one variable—the Vulkan-capable emulator graphics configuration—while preserving API 29, ABI, APK, and test. A non-empty physical-device record plus a passing adapter acquisition would confirm the hypothesis; a remaining null adapter would instead isolate an API 29/WGPU compatibility issue for further investigation.

## Phase 3 — ephemeral SwiftShader test

The failed API 29 instance was shut down and relaunched without editing its AVD configuration:

```text
rtk /Users/chaos/Library/Android/sdk/emulator/emulator -avd Kadre_API_29 -no-snapshot -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -port 5554
```

The emulator (37.1.11.0) accepted the override and reported these host-side facts during startup:

```text
emuglConfig_init: vulkan_mode_selected:swiftshader gles_mode_selected:swangle
Selecting Vulkan device: SwiftShader Device (LLVM 10.0.0), Version: 1.3.0
```

Before the test, the Android guest was boot-complete (`sys.boot_completed=1`) and remained SDK 29 / `Kadre_API_29`. Its Vulkan query was still:

```text
rtk adb -s emulator-5554 shell cmd gpu vkjson
devices: [ {} ]
```

It still did not report `deviceName`, `driverName`, or `driverInfo`. The exact unchanged instrumentation command then ran:

```text
rtk env ANDROID_SERIAL=emulator-5554 ./gradlew :samples:hello-triangle-android-capture:connectedDebugAndroidTest --no-daemon --stacktrace --console=plain
```

Result: exit 1; the same sole `capturesTriangle` test failed at `HelloTriangleCapture.kt:81` with `Failed to acquire Adapter`. The emulator log did confirm that WGPU created and immediately destroyed Vulkan instances, but no adapter was yielded:

```text
Created VkInstance ... application:'wgpu' engine:'wgpu-hal'
Destroyed VkInstance ... application:'wgpu' engine:'wgpu-hal'
```

The temporary emulator was then stopped with `rtk adb -s emulator-5554 emu kill`. A fresh read of `/Users/chaos/.android/avd/Kadre_API_29.avd/config.ini` still reports `hw.gpu.enabled=no`, `hw.gpu.mode=auto`, and the original API 29 system image, confirming no durable AVD reconfiguration.

### Phase 3 outcome: hypothesis **infirmed**

The initial hypothesis attributed the null adapter to the persisted `hw.gpu.enabled=no` setting. The explicit, accepted SwiftShader override did not create a describable guest Vulkan physical device and did not change the test result, so that setting alone is not the cause. The evidence remains consistent with an API 29 arm64 guest Vulkan/WGPU incompatibility, but this phase neither demonstrates a branch-introduced code defect nor establishes a more specific root cause.

## Sélection de device — nouveau cycle phases 1–3

### Faits

L’état ADB a été vérifié avec les deux émulateurs connectés et boot-complete avant le test ciblé :

```text
emulator-5554  device  Kadre_API_29  SDK 29
emulator-5556  device  Medium_Phone  SDK 36
```

Le device compatible (`emulator-5556`) décrit un adapter Vulkan logiciel (`deviceName: llvmpipe`, `driverName: llvmpipe`, Mesa 26.0.0-devel). L’API 29 a été démarré éphémèrement en parallèle, afin que son exclusion ne soit pas seulement due à son absence.

### Hypothèse unique

La gate locale devient fiable si le seam `ANDROID_SERIAL` isole explicitement un AVD Vulkan-compatible : Gradle ne doit alors exécuter l’instrumentation que sur ce serial, comme la CI où le runner crée un seul émulateur compatible.

### Test

Avec les deux AVDs connectés, le script intégral a été exécuté exactement ainsi :

```text
rtk env ANDROID_SERIAL=emulator-5556 scripts/android-emulator-test.sh
```

Résultat : exit 0. La sortie du script mentionne seulement :

```text
Starting 1 tests on Medium_Phone(AVD) - 16
Finished 1 tests on Medium_Phone(AVD) - 16
BUILD SUCCESSFUL in 25s
JUnit evidence: tests=1 skipped=0 failures=0 errors=0
PNG evidence: .../Medium_Phone(AVD) - 16/hello-triangle-android.png 800x600 target=android-triangle colors=47436 non-background=60000
```

Le répertoire JUnit ne contient que `TEST-Medium_Phone(AVD) - 16-_samples_hello-triangle-android-capture-.xml`, et le seul PNG validé est sous `.../connected/Medium_Phone(AVD) - 16/`. Aucune exécution API 29 n’apparaît dans cette invocation, bien qu’il ait été connecté avant le lancement.

### Conclusion : hypothèse **confirmée**

`ANDROID_SERIAL=emulator-5556` isole effectivement l’AVD compatible dans le script complet, y compris les validateurs JUnit et PNG, et exclut l’API 29 incompatible lorsqu’il est simultanément connecté. L’échec initial de la gate locale est donc causé par la sélection implicite de tous les devices connectés, et non par l’APK sur l’AVD compatible. L’API 29 temporaire a été arrêté après le test; aucun changement de code ni de configuration persistante n’a été appliqué ou proposé dans ce cycle.

## TDD — cycle rouge de sélection Android

Avant toute modification de `scripts/android-emulator-test.sh`, le nouveau test d’intégration `scripts/test-android-device-selection.sh` a exécuté le script réel depuis un dépôt temporaire. Il fournissait deux faux devices online : `emulator-29` dont `adb shell cmd gpu vkjson` renvoie `{"devices":[{}]}`, et `emulator-36` dont la sortie décrit un physical device Vulkan. Le faux Gradle écrit le `ANDROID_SERIAL` effectivement reçu, puis produit un JUnit et un PNG valides afin que seule la sélection soit observée.

Commande rouge :

```text
rtk bash scripts/test-android-device-selection.sh
```

Résultat (exit 1) :

```text
FAIL: unique Vulkan selection did not export emulator-36 to Gradle
```

Cause confirmée avant l’implémentation : le script existant appelle directement `./gradlew` sans aucun préflight `adb`; Gradle a donc reçu `ANDROID_SERIAL` vide. Le test couvre ensuite, pour le cycle vert, les erreurs zéro/ambiguë avant Gradle et l’honneur puis la précondition d’un `ANDROID_SERIAL` explicite, sans fallback automatique.

## TDD — cycle vert et vérification réelle

L’implémentation minimale ajoute un préflight avant Gradle. Il interroge `adb devices`, inspecte chaque device online avec `adb -s <serial> shell cmd gpu vkjson`, et accepte uniquement un JSON contenant un objet `devices` avec un `properties.deviceName` non vide. Cette règle rejette explicitement `devices: [{}]`. Sans serial explicite, le script exige exactement un candidat; avec `ANDROID_SERIAL`, il valide ce serial sans le remplacer, puis échoue avant Gradle s’il ne décrit pas un physical device.

Commande verte :

```text
rtk bash scripts/test-android-device-selection.sh
```

Résultat : exit 0 — `PASS: Android device selection is explicit, unique, and preconditioned`.

Vérification réelle automatique, avec API 29 (`devices: [{}]`) et API 36 (`deviceName: llvmpipe`) simultanément online :

```text
rtk env -u ANDROID_SERIAL scripts/android-emulator-test.sh
```

Résultat : exit 0. Gradle a exécuté seulement `Medium_Phone(AVD) - 16`; le validateur a confirmé un JUnit 1/1 sans erreur ni échec et le PNG `android-triangle` 800×600 (47,436 couleurs, 60,000 pixels non-background). L’API 29 n’a pas atteint Gradle. Les échecs de Gradle/rendu restent propagés par le script : le préflight ne possède aucun chemin de fallback après le lancement du test.
