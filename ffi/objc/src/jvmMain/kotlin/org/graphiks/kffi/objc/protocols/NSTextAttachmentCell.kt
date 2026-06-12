package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM interface for Objective-C protocol: NSTextAttachmentCell
 * Inherits protocols: NSObject
 */
interface NSTextAttachmentCell : NSObject {
    fun drawWithFrame_inView(cellFrame: NSRect, controlView: MemorySegment)
    
    fun wantsToTrackMouse(): BOOL
    
    fun highlight_withFrame_inView(flag: BOOL, cellFrame: NSRect, controlView: MemorySegment)
    
    fun trackMouse_inRect_ofView_untilMouseUp(theEvent: MemorySegment, cellFrame: NSRect, controlView: MemorySegment, flag: BOOL): BOOL
    
    fun cellSize(): NSSize
    
    fun cellBaselineOffset(): NSPoint
    
    fun drawWithFrame_inView_characterIndex(cellFrame: NSRect, controlView: MemorySegment, charIndex: NSUInteger)
    
    fun drawWithFrame_inView_characterIndex_layoutManager(cellFrame: NSRect, controlView: MemorySegment, charIndex: NSUInteger, layoutManager: MemorySegment)
    
    fun wantsToTrackMouseForEvent_inRect_ofView_atCharacterIndex(theEvent: MemorySegment, cellFrame: NSRect, controlView: MemorySegment, charIndex: NSUInteger): BOOL
    
    fun trackMouse_inRect_ofView_atCharacterIndex_untilMouseUp(theEvent: MemorySegment, cellFrame: NSRect, controlView: MemorySegment, charIndex: NSUInteger, flag: BOOL): BOOL
    
    fun cellFrameForTextContainer_proposedLineFragment_glyphPosition_characterIndex(textContainer: MemorySegment, lineFrag: NSRect, position: NSPoint, charIndex: NSUInteger): NSRect
    
    fun attachment(): MemorySegment
    
    fun setAttachment(attachment: MemorySegment)
    
    // @property attachment
}

