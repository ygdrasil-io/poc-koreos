/**
 * Kotlin/JVM wrapper for Objective-C class: NSSound
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding, NSPasteboardReading, NSPasteboardWriting
 */
open class NSSound(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSound") }
        
        fun soundNamed(name: NSSoundName): MemorySegment {
            val sel = ObjCRuntime.sel("soundNamed:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        fun canInitWithPasteboard(pasteboard: MemorySegment): BOOL {
            val sel = ObjCRuntime.sel("canInitWithPasteboard:")
            return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, _class, sel, pasteboard) as BOOL
        }
        
        /** @return NSArray<NSString *> * */
        fun soundUnfilteredTypes(): MemorySegment {
            val sel = ObjCRuntime.sel("soundUnfilteredTypes")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun initWithContentsOfURL_byReference(url: MemorySegment, byRef: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfURL:byReference:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, byRef) as MemorySegment
    }
    
    fun initWithContentsOfFile_byReference(path: MemorySegment, byRef: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfFile:byReference:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, path, byRef) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithContentsOfFile_byReference(path: String, byRef: BOOL): MemorySegment = initWithContentsOfFile_byReference(ObjCRuntime.newNSString(Arena.global(), path), byRef)
    
    fun initWithData(`data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`) as MemorySegment
    }
    
    fun setName(string: NSSoundName): BOOL {
        val sel = ObjCRuntime.sel("setName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, string) as BOOL
    }
    
    fun initWithPasteboard(pasteboard: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithPasteboard:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, pasteboard) as MemorySegment
    }
    
    fun writeToPasteboard(pasteboard: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("writeToPasteboard:")
        ObjCRuntime.msgSend(null, ptr, sel, pasteboard)
    }
    
    fun play(): BOOL {
        val sel = ObjCRuntime.sel("play")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun pause(): BOOL {
        val sel = ObjCRuntime.sel("pause")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun resume(): BOOL {
        val sel = ObjCRuntime.sel("resume")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun stop(): BOOL {
        val sel = ObjCRuntime.sel("stop")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun setChannelMapping(channelMapping: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setChannelMapping:")
        ObjCRuntime.msgSend(null, ptr, sel, channelMapping)
    }
    
    fun channelMapping(): MemorySegment {
        val sel = ObjCRuntime.sel("channelMapping")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property name
    fun name(): NSSoundName {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSoundName
    }
    
    // @property soundUnfilteredTypes
    /** @return NSArray<NSString *> * */
    fun soundUnfilteredTypes(): MemorySegment {
        val sel = ObjCRuntime.sel("soundUnfilteredTypes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property playing
    fun isPlaying(): BOOL {
        val sel = ObjCRuntime.sel("isPlaying")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property delegate
    /** @return id<NSSoundDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property duration
    fun duration(): NSTimeInterval {
        val sel = ObjCRuntime.sel("duration")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    
    // @property volume
    fun volume(): Float {
        val sel = ObjCRuntime.sel("volume")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_FLOAT, ptr, sel) as Float
    }
    fun setVolume(value: Float) {
        val sel = ObjCRuntime.sel("setVolume:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property currentTime
    fun currentTime(): NSTimeInterval {
        val sel = ObjCRuntime.sel("currentTime")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setCurrentTime(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setCurrentTime:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property loops
    fun loops(): BOOL {
        val sel = ObjCRuntime.sel("loops")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setLoops(value: BOOL) {
        val sel = ObjCRuntime.sel("setLoops:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property playbackDeviceIdentifier
    fun playbackDeviceIdentifier(): NSSoundPlaybackDeviceIdentifier {
        val sel = ObjCRuntime.sel("playbackDeviceIdentifier")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSSoundPlaybackDeviceIdentifier
    }
    fun setPlaybackDeviceIdentifier(value: NSSoundPlaybackDeviceIdentifier) {
        val sel = ObjCRuntime.sel("setPlaybackDeviceIdentifier:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSDeprecated on NSSound ─────────────────────────────────────────

// Class method: +[NSSound soundUnfilteredFileTypes]
fun NSSound_soundUnfilteredFileTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("soundUnfilteredFileTypes")
    val cls = ObjCRuntime.getClass("NSSound")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

// Class method: +[NSSound soundUnfilteredPasteboardTypes]
fun NSSound_soundUnfilteredPasteboardTypes(): MemorySegment {
    val sel = ObjCRuntime.sel("soundUnfilteredPasteboardTypes")
    val cls = ObjCRuntime.getClass("NSSound")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel) as MemorySegment
}

