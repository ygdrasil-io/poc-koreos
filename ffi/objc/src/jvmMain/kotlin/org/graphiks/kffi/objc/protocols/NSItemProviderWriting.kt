package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSItemProviderWriting
 * Inherits protocols: NSObject
 */
interface NSItemProviderWriting : NSObject {
    // @optional
    fun itemProviderVisibilityForRepresentationWithTypeIdentifier(typeIdentifier: MemorySegment): NSItemProviderRepresentationVisibility =
        throw UnsupportedOperationException("Optional ObjC method 'itemProviderVisibilityForRepresentationWithTypeIdentifier:' not implemented")
    
    // @optional
    fun loadDataWithTypeIdentifier_forItemProviderCompletionHandler(typeIdentifier: MemorySegment, completionHandler: MemorySegment): MemorySegment
    
    /** @return NSArray<NSString *> * */
    fun writableTypeIdentifiersForItemProvider(): MemorySegment
    
    /** @return NSArray<NSString *> * */
    // @optional
    // @property writableTypeIdentifiersForItemProvider
    /** @return NSArray<NSString *> * */
    // @property writableTypeIdentifiersForItemProvider
    /** @return NSArray<NSString *> * */