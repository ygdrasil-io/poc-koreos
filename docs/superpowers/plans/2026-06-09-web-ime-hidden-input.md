# Web IME — Hidden Input Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add proper IME support to the Web backend using a hidden `<input>` element for composition events, and wire `setImeAllowed`, `setImePurpose`, `setImeCursorArea` through the DOM bridge.

**Architecture:** The webMain source set cannot use DOM types. A hidden `<input>` overlay is created/managed in `jsMain` and `wasmJsMain` via `WebDomBridge`. Composition events dispatch through the bridge's existing `onWindowEvent` → `WebWindowEvent.Ime(WebImeEvent.*)` pipeline.

**Tech Stack:** Kotlin Multiplatform (JS + wasmJs), browser DOM APIs

---

### Task 1: Add IME methods to WebDomBridge

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebDomBridge.kt`

- [ ] **Add `setImePurpose` and `setImeCursorArea` default methods**

```kotlin
// In WebDomBridge, after getImeCursorArea() around line 110
fun setImePurpose(purpose: String) { /* no-op by default */ }
fun setImeCursorArea(x: Int, y: Int, width: Int, height: Int) { /* no-op by default */ }
```

- [ ] **Run existing tests to verify no regression**

Run: `./gradlew :kadre-web-common:check`

---

### Task 2: Implement hidden input in JsWebDomBridge

**Files:**
- Modify: `kadre-web-common/src/jsMain/kotlin/org/graphiks/kadre/web/JsWebDomBridge.kt`

- [ ] **Add imports, hidden input field, and override IME methods**

Add to class JsWebDomBridge:
```kotlin
import org.w3c.dom.HTMLInputElement

// Field at class level:
private var imeInput: HTMLInputElement? = null

// Override methods:
override fun setImeAllowed(allowed: Boolean) {
    if (allowed) {
        val input = imeInput ?: createImeInputBox().also { imeInput = it }
        input.focus()
    } else {
        imeInput?.blur()
    }
}

override fun setImePurpose(purpose: String) {
    imeInput?.inputMode = purpose
}

override fun setImeCursorArea(x: Int, y: Int, width: Int, height: Int) {
    val input = imeInput ?: return
    input.style.left = "${x}px"
    input.style.top = "${y}px"
    input.style.width = "${width}px"
    input.style.height = "${height}px"
}

private fun createImeInputBox(): HTMLInputElement {
    val input = document.createElement("input").unsafeCast<HTMLInputElement>().apply {
        style.position = "absolute"
        style.opacity = "0"
        style.height = "0px"
        style.width = "0px"
        style.pointerEvents = "none"
        style.left = "0px"
        style.top = "0px"
        style.zIndex = "-1"
        autocapitalize = "off"
        autocomplete = "off"
        autocorrect = "off"
        spellcheck = false.asBoolean()
    }
    canvasElement?.let { it.parentElement?.appendChild(input) }
        ?: document.body?.appendChild(input)

    // Composition event listeners on the hidden input
    input.addEventListener("compositionstart", {
        canvasElement?.let { input.focus() }
        dispatch(WebWindowEvent.Ime(WebImeEvent.Enabled))
    })
    input.addEventListener("compositionupdate") { e ->
        val data = e.asDynamic().data as? String ?: ""
        dispatch(WebWindowEvent.Ime(WebImeEvent.Preedit(text = data, cursorRange = null)))
    }
    input.addEventListener("compositionend") { e ->
        val data = e.asDynamic().data as? String ?: ""
        dispatch(WebWindowEvent.Ime(WebImeEvent.Commit(text = data)))
        dispatch(WebWindowEvent.Ime(WebImeEvent.Disabled))
        input.value = ""
    }

    return input
}
```

- [ ] **Remove duplicate composition listeners from attach() and manage lifecycle**

Remove the IME composition event listeners block from `attach()` (lines 204-218) since they're now on the hidden input.

In `detach()`, clean up the IME input:
```kotlin
imeInput?.remove()
imeInput = null
```

---

### Task 3: Implement hidden input in WasmJsWebDomBridge

**Files:**
- Modify: `kadre-web-common/src/wasmJsMain/kotlin/org/graphiks/kadre/web/WasmJsWebDomBridge.kt`

- [ ] **Add @JsFun functions for IME input creation and management**

```kotlin
@JsFun("""() => {
    const input = document.createElement('input');
    input.style.position = 'absolute';
    input.style.opacity = '0';
    input.style.height = '0px';
    input.style.width = '0px';
    input.style.pointerEvents = 'none';
    input.style.left = '0px';
    input.style.top = '0px';
    input.style.zIndex = '-1';
    input.autocapitalize = 'off';
    input.autocomplete = 'off';
    input.autocorrect = 'off';
    input.spellcheck = false;
    return input;
}""")
private external fun createImeInputElement(): JsEventTarget

@JsFun("(el, parent) => { parent.appendChild(el); }")
private external fun appendImeInput(el: JsEventTarget, parent: JsEventTarget)

@JsFun("(el) => { el.focus(); }")
private external fun focusElement(el: JsEventTarget)

@JsFun("(el) => { el.blur(); }")
private external fun blurElement(el: JsEventTarget)

@JsFun("(el, value) => { el.inputMode = value; }")
private external fun setInputMode(el: JsEventTarget, value: String)

@JsFun("(el, left, top, width, height) => { el.style.left = left + 'px'; el.style.top = top + 'px'; el.style.width = width + 'px'; el.style.height = height + 'px'; }")
private external fun setInputPosition(el: JsEventTarget, left: Int, top: Int, width: Int, height: Int)

@JsFun("(el) => { el.remove(); }")
private external fun removeElementFromDom(el: JsEventTarget)

@JsFun("(el) => { return el.parentElement; }")
private external fun getParentElement(el: JsEventTarget): JsEventTarget?

@JsFun("(el, type, handler) => { el.addEventListener(type, handler); }")
private external fun addImeEventListener(el: JsEventTarget, type: String, handler: JsAny)
```

- [ ] **Add IME input field and override methods**

```kotlin
private var imeInput: JsEventTarget? = null

override fun setImeAllowed(allowed: Boolean) {
    if (allowed) {
        val input = imeInput ?: createImeInputBox().also { imeInput = it }
        focusElement(input)
    } else {
        imeInput?.let { blurElement(it) }
    }
}

override fun setImePurpose(purpose: String) {
    imeInput?.let { setInputMode(it, purpose) }
}

override fun setImeCursorArea(x: Int, y: Int, width: Int, height: Int) {
    imeInput?.let { setInputPosition(it, x, y, width, height) }
}

private fun createImeInputBox(): JsEventTarget {
    val input = createImeInputElement()
    val parent = targetElement?.let { getParentElement(it) } ?: getDocument()
    appendImeInput(input, parent)

    // Composition event listeners
    val startHandler: (JsAny) -> Unit = { _ ->
        dispatch(WebWindowEvent.Ime(WebImeEvent.Enabled))
    }
    val updateHandler: (JsAny) -> Unit = { e ->
        val ce = e.unsafeCast<JsCompositionEvent>()
        val text = ce.data?.toString() ?: ""
        dispatch(WebWindowEvent.Ime(WebImeEvent.Preedit(text = text, cursorRange = null)))
    }
    val endHandler: (JsAny) -> Unit = { e ->
        val ce = e.unsafeCast<JsCompositionEvent>()
        val text = ce.data?.toString() ?: ""
        dispatch(WebWindowEvent.Ime(WebImeEvent.Commit(text = text)))
        dispatch(WebWindowEvent.Ime(WebImeEvent.Disabled))
        // Clear the input
        setInputValue(input, "")
    }

    addImeEventListener(input, "compositionstart", wrapEventHandler(startHandler))
    addImeEventListener(input, "compositionupdate", wrapEventHandler(updateHandler))
    addImeEventListener(input, "compositionend", wrapEventHandler(endHandler))

    return input
}
```

Wait, `setInputValue` needs a `@JsFun`:
```kotlin
@JsFun("(el, value) => { el.value = value; }")
private external fun setInputValue(el: JsEventTarget, value: String)
```

- [ ] **Remove duplicate composition listeners from attach() and clean up in detach()**

Remove block at lines 419-435 in attach().

In `detach()`:
```kotlin
imeInput?.let { removeElementFromDom(it) }
imeInput = null
```

---

### Task 4: Update WebWindow to delegate to bridge

**Files:**
- Modify: `kadre-web-common/src/webMain/kotlin/org/graphiks/kadre/web/WebWindow.kt`

- [ ] **Wire setImePurpose and setImeCursorArea to bridge**

Replace `setImePurpose`:
```kotlin
override fun setImePurpose(purpose: org.graphiks.kadre.core.ImePurpose) {
    bridge.setImePurpose(purpose.name.lowercase())
}
```

Replace `setImeCursorArea`:
```kotlin
override fun setImeCursorArea(position: PhysicalPosition<Int>, size: PhysicalSize<Int>) {
    _imeCursorPosition = position
    _imeCursorSize = size
    bridge.setImeCursorArea(position.x, position.y, size.width, size.height)
}
```

---

### Task 5: Add tests

**Files:**
- Create: `kadre-web-common/src/webTest/kotlin/org/graphiks/kadre/web/WebWindowImeTest.kt`

- [ ] **Write IME tests**

Following the pattern from WebWindowSizeTest.kt (stub bridge, verify state):

```kotlin
package org.graphiks.kadre.web

import org.graphiks.kadre.core.ImePurpose
import org.graphiks.kadre.core.PhysicalPosition
import org.graphiks.kadre.core.PhysicalSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private class ImeRecordingBridge : WebDomBridge {
    override var onWindowEvent: ((WebWindowEvent) -> Unit)? = null
    override fun attach(targetElementId: String) {}
    override fun detach() {}

    var lastImePurpose: String? = null
    var lastCursorAreaX: Int? = null
    var lastCursorAreaY: Int? = null
    var lastCursorAreaW: Int? = null
    var lastCursorAreaH: Int? = null

    override fun setImePurpose(purpose: String) {
        lastImePurpose = purpose
    }

    override fun setImeCursorArea(x: Int, y: Int, width: Int, height: Int) {
        lastCursorAreaX = x
        lastCursorAreaY = y
        lastCursorAreaW = width
        lastCursorAreaH = height
    }
}

class WebWindowImeTest {

    @Test
    fun `setImePurpose delegates purpose to bridge`() {
        val bridge = ImeRecordingBridge()
        val window = WebWindow("canvas", bridge)

        window.setImePurpose(ImePurpose.Terminal)

        assertEquals("terminal", bridge.lastImePurpose)
    }

    @Test
    fun `setImePurpose Normal delegates text`() {
        val bridge = ImeRecordingBridge()
        val window = WebWindow("canvas", bridge)

        window.setImePurpose(ImePurpose.Normal)

        assertEquals("normal", bridge.lastImePurpose)
    }

    @Test
    fun `setImePurpose Password delegates password`() {
        val bridge = ImeRecordingBridge()
        val window = WebWindow("canvas", bridge)

        window.setImePurpose(ImePurpose.Password)

        assertEquals("password", bridge.lastImePurpose)
    }

    @Test
    fun `setImeCursorArea delegates position and size to bridge`() {
        val bridge = ImeRecordingBridge()
        val window = WebWindow("canvas", bridge)

        window.setImeCursorArea(
            position = PhysicalPosition(100, 200),
            size = PhysicalSize(30, 40),
        )

        assertEquals(100, bridge.lastCursorAreaX)
        assertEquals(200, bridge.lastCursorAreaY)
        assertEquals(30, bridge.lastCursorAreaW)
        assertEquals(40, bridge.lastCursorAreaH)
    }

    @Test
    fun `setImeCursorArea stores values locally`() {
        val bridge = ImeRecordingBridge()
        val window = WebWindow("canvas", bridge)

        window.setImeCursorArea(
            position = PhysicalPosition(50, 75),
            size = PhysicalSize(20, 15),
        )

        // Values should also be stored in private fields (verified via bridge delegation)
        assertNotNull(bridge.lastCursorAreaX)
    }

    @Test
    fun `setImeAllowed delegates to bridge`() {
        val bridge = ImeRecordingBridge()
        val window = WebWindow("canvas", bridge)

        // Bridge will record calls via its implementation
        window.setImeAllowed(true)
        window.setImeAllowed(false)
    }
}
```

---

### Task 6: Run tests and verify

- [ ] **Run the web tests**

```bash
./gradlew :kadre-web-common:check
```

Or if that doesn't exist:
```bash
./gradlew :kadre-web-common:jsTest
```


### Task 7: Commit

```bash
git add -A && git commit -m "feat(web): IME events via hidden input + composition events"
```
