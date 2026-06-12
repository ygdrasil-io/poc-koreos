package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSSound
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding, NSPasteboardReading, NSPasteboardWriting
 */
open class NSSound(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSound") }
        
        open fun soundNamed(name: NSSoundName): MemorySegment {
            val sel = ObjCRuntime.sel("soundNamed:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        open fun canInitWithPasteboard(pasteboard: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canInitWithPasteboard:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, pasteboard) as BOOL
        }
        
        /** @return NSArray<NSString *> * */
        open fun soundUnfilteredTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("soundUnfilteredTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    open fun initWithContentsOfURL_byReference(url: MemorySegment, byRef: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfURL:byReference:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, byRef) as MemorySegment
    }
    
    open fun initWithContentsOfFile_byReference(path: MemorySegment, byRef: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfFile:byReference:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, byRef) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun initWithContentsOfFile_byReference(path: String, byRef: BOOL): MemorySegment = initWithContentsOfFile_byReference(ObjCRuntime.newNSString(Arena.global(), path), byRef)
    
    open fun initWithData(`data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`) as MemorySegment
    }
    
    open fun setName(string: NSSoundName): BOOL {
        val sel = ObjCRuntime.sel("setName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string) as BOOL
    }
    
    open fun initWithPasteboard(pasteboard: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPasteboard:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pasteboard) as MemorySegment
    }
    
    open fun writeToPasteboard(pasteboard: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("writeToPasteboard:")
        ObjCRuntime.msgSend(null, ptr, sel, pasteboard)
    }
    
    open fun play(): BOOL {
        val sel = ObjCRuntime.sel("play")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun pause(): BOOL {
        val sel = ObjCRuntime.sel("pause")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun resume(): BOOL {
        val sel = ObjCRuntime.sel("resume")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun stop(): BOOL {
        val sel = ObjCRuntime.sel("stop")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    open fun setChannelMapping(channelMapping: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setChannelMapping:")
        ObjCRuntime.msgSend(null, ptr, sel, channelMapping)
    }
    
    open fun channelMapping(): MemorySegment {
        val sel = ObjCRuntime.sel("channelMapping")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property name
    open fun name(): NSSoundName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSoundName
    }
    
    // @property soundUnfilteredTypes
    /** @return NSArray<NSString *> * */
    }
    
    // @property playing
    open fun isPlaying(): BOOL {
        val sel = ObjCRuntime.sel("isPlaying")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property delegate
    /** @return id<NSSoundDelegate> */
    open fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property duration
    open fun duration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
    // @property volume
    open fun volume(): Float {
        val sel = ObjCRuntime.sel("volume")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    open fun setVolume(value: Float) {
        val sel = ObjCRuntime.sel("setVolume:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentTime
    open fun currentTime(): NSTimeInterval {
        val sel = ObjCRuntime.sel("currentTime")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    open fun setCurrentTime(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setCurrentTime:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property loops
    open fun loops(): BOOL {
        val sel = ObjCRuntime.sel("loops")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    open fun setLoops(value: BOOL) {
        val sel = ObjCRuntime.sel("setLoops:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property playbackDeviceIdentifier
    open fun playbackDeviceIdentifier(): NSSoundPlaybackDeviceIdentifier {
        val sel = ObjCRuntime.sel("playbackDeviceIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSoundPlaybackDeviceIdentifier
    }
    open fun setPlaybackDeviceIdentifier(value: NSSoundPlaybackDeviceIdentifier) {
        val sel = ObjCRuntime.sel("setPlaybackDeviceIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSSound ─────────────────────────────────────────

// Class<*> method: +[NSSound soundUnfilteredFileTypes]
fun NSSound_soundUnfilteredFileTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("soundUnfilteredFileTypes")
    val cls = ObjCRuntime.getClass("NSSound")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class<*> method: +[NSSound soundUnfilteredPasteboardTypes]
fun NSSound_soundUnfilteredPasteboardTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("soundUnfilteredPasteboardTypes")
    val cls = ObjCRuntime.getClass("NSSound")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

