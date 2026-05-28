/**
 * Re-export des types publics de koreos-core vers le package io.ygdrasil.koreos.
 *
 * Les consommateurs de la façade koreos importent uniquement depuis
 * `io.ygdrasil.koreos.*` — ils n'ont pas besoin de connaître koreos-core.
 *
 * GRA-129 : re-exports via typealias commonMain.
 */
package io.ygdrasil.koreos

// ── Interfaces fondamentales ──────────────────────────────────────────────────

/** @see io.ygdrasil.koreos.core.ApplicationHandler */
typealias ApplicationHandler = io.ygdrasil.koreos.core.ApplicationHandler

/** @see io.ygdrasil.koreos.core.ActiveEventLoop */
typealias ActiveEventLoop = io.ygdrasil.koreos.core.ActiveEventLoop

/** @see io.ygdrasil.koreos.core.EventLoopProxy */
typealias EventLoopProxy = io.ygdrasil.koreos.core.EventLoopProxy

/** @see io.ygdrasil.koreos.core.Window */
typealias Window = io.ygdrasil.koreos.core.Window

// ── Configuration ─────────────────────────────────────────────────────────────

/** @see io.ygdrasil.koreos.core.WindowAttributes */
typealias WindowAttributes = io.ygdrasil.koreos.core.WindowAttributes

// ── Identifiants ──────────────────────────────────────────────────────────────

/** @see io.ygdrasil.koreos.core.WindowId */
typealias WindowId = io.ygdrasil.koreos.core.WindowId

/** @see io.ygdrasil.koreos.core.DeviceId */
typealias DeviceId = io.ygdrasil.koreos.core.DeviceId

// ── Contrôle de flux ──────────────────────────────────────────────────────────

/** @see io.ygdrasil.koreos.core.ControlFlow */
typealias ControlFlow = io.ygdrasil.koreos.core.ControlFlow

/** @see io.ygdrasil.koreos.core.StartCause */
typealias StartCause = io.ygdrasil.koreos.core.StartCause

// ── Événements ────────────────────────────────────────────────────────────────

/** @see io.ygdrasil.koreos.core.WindowEvent */
typealias WindowEvent = io.ygdrasil.koreos.core.WindowEvent

/** @see io.ygdrasil.koreos.core.DeviceEvent */
typealias DeviceEvent = io.ygdrasil.koreos.core.DeviceEvent

// ── Handles natifs ────────────────────────────────────────────────────────────

/** @see io.ygdrasil.koreos.core.RawWindowHandle */
typealias RawWindowHandle = io.ygdrasil.koreos.core.RawWindowHandle

/** @see io.ygdrasil.koreos.core.RawDisplayHandle */
typealias RawDisplayHandle = io.ygdrasil.koreos.core.RawDisplayHandle

// ── Types DPI ─────────────────────────────────────────────────────────────────

/** @see io.ygdrasil.koreos.core.PhysicalSize */
typealias PhysicalSize<T> = io.ygdrasil.koreos.core.PhysicalSize<T>

/** @see io.ygdrasil.koreos.core.LogicalSize */
typealias LogicalSize<T> = io.ygdrasil.koreos.core.LogicalSize<T>

/** @see io.ygdrasil.koreos.core.PhysicalPosition */
typealias PhysicalPosition<T> = io.ygdrasil.koreos.core.PhysicalPosition<T>

/** @see io.ygdrasil.koreos.core.LogicalPosition */
typealias LogicalPosition<T> = io.ygdrasil.koreos.core.LogicalPosition<T>
