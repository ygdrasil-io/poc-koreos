package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSInputServiceProvider
 */
interface NSInputServiceProvider {
    fun insertText_client(string: MemorySegment, sender: MemorySegment)
    
    fun doCommandBySelector_client(selector: MemorySegment, sender: MemorySegment)
    
    fun markedTextAbandoned(sender: MemorySegment)
    
    fun markedTextSelectionChanged_client(newSel: NSRange, sender: MemorySegment)
    
    fun terminate(sender: MemorySegment)
    
    fun canBeDisabled(): BOOL
    
    fun wantsToInterpretAllKeystrokes(): BOOL
    
    fun wantsToHandleMouseEvents(): BOOL
    
    fun wantsToDelayTextChangeNotifications(): BOOL
    
    fun inputClientBecomeActive(sender: MemorySegment)
    
    fun inputClientResignActive(sender: MemorySegment)
    
    fun inputClientEnabled(sender: MemorySegment)
    
    fun inputClientDisabled(sender: MemorySegment)
    
    fun activeConversationWillChange_fromOldConversation(sender: MemorySegment, oldConversation: NSInteger)
    
    fun activeConversationChanged_toNewConversation(sender: MemorySegment, newConversation: NSInteger)
    
}

