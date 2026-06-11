/**
 * Kotlin/JVM wrapper for Objective-C class: NSHTTPCookieStorage
 * Superclass: NSObject
 */
open class NSHTTPCookieStorage(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSHTTPCookieStorage") }
        
        fun sharedCookieStorageForGroupContainerIdentifier(identifier: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sharedCookieStorageForGroupContainerIdentifier:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identifier) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun sharedCookieStorageForGroupContainerIdentifier(identifier: String): MemorySegment = sharedCookieStorageForGroupContainerIdentifier(ObjCRuntime.newNSString(Arena.global(), identifier))
        
        fun sharedHTTPCookieStorage(): MemorySegment {
            val sel = ObjCRuntime.sel("sharedHTTPCookieStorage")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun setCookie(cookie: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setCookie:")
        ObjCRuntime.msgSend(null, ptr, sel, cookie)
    }
    
    fun deleteCookie(cookie: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("deleteCookie:")
        ObjCRuntime.msgSend(null, ptr, sel, cookie)
    }
    
    fun removeCookiesSinceDate(date: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeCookiesSinceDate:")
        ObjCRuntime.msgSend(null, ptr, sel, date)
    }
    
    /** @return NSArray<NSHTTPCookie *> * */
    fun cookiesForURL(URL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("cookiesForURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URL) as MemorySegment
    }
    
    fun setCookies_forURL_mainDocumentURL(cookies: MemorySegment, URL: MemorySegment, mainDocumentURL: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("setCookies:forURL:mainDocumentURL:")
        ObjCRuntime.msgSend(null, ptr, sel, cookies, URL, mainDocumentURL)
    }
    
    /** @return NSArray<NSHTTPCookie *> * */
    fun sortedCookiesUsingDescriptors(sortOrder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("sortedCookiesUsingDescriptors:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, sortOrder) as MemorySegment
    }
    
    // @property sharedHTTPCookieStorage
    fun sharedHTTPCookieStorage(): MemorySegment {
        val sel = ObjCRuntime.sel("sharedHTTPCookieStorage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cookies
    /** @return NSArray<NSHTTPCookie *> * */
    fun cookies(): MemorySegment {
        val sel = ObjCRuntime.sel("cookies")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property cookieAcceptPolicy
    fun cookieAcceptPolicy(): NSHTTPCookieAcceptPolicy {
        val sel = ObjCRuntime.sel("cookieAcceptPolicy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSHTTPCookieAcceptPolicy
    }
    fun setCookieAcceptPolicy(value: NSHTTPCookieAcceptPolicy) {
        val sel = ObjCRuntime.sel("setCookieAcceptPolicy:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

// ── Category: NSURLSessionTaskAdditions on NSHTTPCookieStorage ─────────────────────────────────────────

fun NSHTTPCookieStorage.storeCookies_forTask(cookies: MemorySegment, task: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("storeCookies:forTask:")
    ObjCRuntime.msgSend(null, ptr, sel, cookies, task)
}

fun NSHTTPCookieStorage.getCookiesForTask_completionHandler(task: MemorySegment, completionHandler: MemorySegment): Unit {
    val sel = ObjCRuntime.sel("getCookiesForTask:completionHandler:")
    ObjCRuntime.msgSend(null, ptr, sel, task, completionHandler)
}

