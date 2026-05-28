/**
 * Type aliases pour les types Obj-C/Foundation manquants dans le fichier généré par kextract.
 *
 * Ces types sont des alias standard de l'API Objective-C qui ne sont pas inclus
 * dans le fichier généré lorsque le filtrage par --include-objc-class est utilisé.
 *
 * Généré pour kextract v0.0.0-test6 — remplacer par des types complets si nécessaire.
 *
 * TODO: Quand kextract génèrera ces types automatiquement, supprimer ce fichier.
 */
package io.ygdrasil.koreos.appkit.bindings

import java.lang.foreign.MemorySegment

// ── Primitives C / CoreFoundation ────────────────────────────────────────────

/** ObjC BOOL (signed char, 0=NO 1=YES) */
typealias BOOL = Byte

/** NSInteger (64-bit signed integer on 64-bit platforms) */
typealias NSInteger = Long

/** NSUInteger (64-bit unsigned integer on 64-bit platforms) */
typealias NSUInteger = Long

/** CGFloat (Double on 64-bit platforms) */
typealias CGFloat = Double

/** NSTimeInterval (time in seconds, Double precision) */
typealias NSTimeInterval = Double

// ── Structs passés comme MemorySegment via FFM ────────────────────────────────

/** NSPoint / CGPoint — struct {x: CGFloat, y: CGFloat} passé via MemorySegment */
typealias NSPoint = MemorySegment

/** NSSize / CGSize — struct {width: CGFloat, height: CGFloat} passé via MemorySegment */
typealias NSSize = MemorySegment

/** NSRect / CGRect — struct {origin: NSPoint, size: NSSize} passé via MemorySegment */
typealias NSRect = MemorySegment

// ── NSString typealiases ─────────────────────────────────────────────────────

/** NSWindowFrameAutosaveName (NSString *) */
typealias NSWindowFrameAutosaveName = MemorySegment

/** NSWindowPersistableFrameDescriptor (NSString *) */
typealias NSWindowPersistableFrameDescriptor = MemorySegment

/** NSWindowTabbingIdentifier (NSString *) */
typealias NSWindowTabbingIdentifier = MemorySegment

/** NSPasteboardType (NSString *) */
typealias NSPasteboardType = MemorySegment

// ── Integer typealiases ───────────────────────────────────────────────────────

/** NSWindowLevel (NSInteger) */
typealias NSWindowLevel = NSInteger

/** NSModalResponse (NSInteger) */
typealias NSModalResponse = NSInteger

/** NSToolTipTag (NSInteger) */
typealias NSToolTipTag = NSInteger

// ── NSObject protocol ────────────────────────────────────────────────────────

/**
 * Marqueur interface pour les wrappers d'objets Obj-C.
 * Tous les wrappers de classes AppKit doivent implémenter ce interface
 * en exposant leur [ptr] (MemorySegment vers l'objet Obj-C natif).
 */
interface NSObject {
    val ptr: MemorySegment
}
