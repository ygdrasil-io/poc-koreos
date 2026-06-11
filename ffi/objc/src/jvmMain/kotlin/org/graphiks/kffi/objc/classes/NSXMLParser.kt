/**
 * Kotlin/JVM wrapper for Objective-C class: NSXMLParser
 * Superclass: NSObject
 */
open class NSXMLParser(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSXMLParser") }
        
    }
    
    fun initWithContentsOfURL(url: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithContentsOfURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url) as MemorySegment
    }
    
    fun initWithData(`data`: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithData:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `data`) as MemorySegment
    }
    
    fun initWithStream(stream: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithStream:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, stream) as MemorySegment
    }
    
    fun parse(): BOOL {
        val sel = ObjCRuntime.sel("parse")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    fun abortParsing(): Unit {
        val sel = ObjCRuntime.sel("abortParsing")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property delegate
    /** @return id<NSXMLParserDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldProcessNamespaces
    fun shouldProcessNamespaces(): BOOL {
        val sel = ObjCRuntime.sel("shouldProcessNamespaces")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShouldProcessNamespaces(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldProcessNamespaces:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property shouldReportNamespacePrefixes
    fun shouldReportNamespacePrefixes(): BOOL {
        val sel = ObjCRuntime.sel("shouldReportNamespacePrefixes")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShouldReportNamespacePrefixes(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldReportNamespacePrefixes:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property externalEntityResolvingPolicy
    fun externalEntityResolvingPolicy(): NSXMLParserExternalEntityResolvingPolicy {
        val sel = ObjCRuntime.sel("externalEntityResolvingPolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSXMLParserExternalEntityResolvingPolicy
    }
    fun setExternalEntityResolvingPolicy(value: NSXMLParserExternalEntityResolvingPolicy) {
        val sel = ObjCRuntime.sel("setExternalEntityResolvingPolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property allowedExternalEntityURLs
    /** @return NSSet<NSURL *> * */
    fun allowedExternalEntityURLs(): MemorySegment {
        val sel = ObjCRuntime.sel("allowedExternalEntityURLs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setAllowedExternalEntityURLs(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setAllowedExternalEntityURLs:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property parserError
    fun parserError(): MemorySegment {
        val sel = ObjCRuntime.sel("parserError")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property shouldResolveExternalEntities
    fun shouldResolveExternalEntities(): BOOL {
        val sel = ObjCRuntime.sel("shouldResolveExternalEntities")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setShouldResolveExternalEntities(value: BOOL) {
        val sel = ObjCRuntime.sel("setShouldResolveExternalEntities:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

// ── Category: NSXMLParserLocatorAdditions on NSXMLParser ─────────────────────────────────────────

fun NSXMLParser.publicID(): MemorySegment {
    val sel = ObjCRuntime.sel("publicID")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSXMLParser.systemID(): MemorySegment {
    val sel = ObjCRuntime.sel("systemID")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSXMLParser.lineNumber(): NSInteger {
    val sel = ObjCRuntime.sel("lineNumber")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

fun NSXMLParser.columnNumber(): NSInteger {
    val sel = ObjCRuntime.sel("columnNumber")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

// @property publicID
fun NSXMLParser.publicID(): MemorySegment {
    val sel = ObjCRuntime.sel("publicID")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property systemID
fun NSXMLParser.systemID(): MemorySegment {
    val sel = ObjCRuntime.sel("systemID")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property lineNumber
fun NSXMLParser.lineNumber(): NSInteger {
    val sel = ObjCRuntime.sel("lineNumber")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

// @property columnNumber
fun NSXMLParser.columnNumber(): NSInteger {
    val sel = ObjCRuntime.sel("columnNumber")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
}

