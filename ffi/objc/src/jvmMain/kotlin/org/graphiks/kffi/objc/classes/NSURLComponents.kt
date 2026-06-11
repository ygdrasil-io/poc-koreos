/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLComponents
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSURLComponents(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLComponents") }
        
        fun componentsWithURL_resolvingAgainstBaseURL(url: MemorySegment, resolve: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("componentsWithURL:resolvingAgainstBaseURL:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, url, resolve) as MemorySegment
        }
        
        fun componentsWithString(URLString: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("componentsWithString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, URLString) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun componentsWithString(URLString: String): MemorySegment = componentsWithString(ObjCRuntime.newNSString(Arena.global(), URLString))
        
        fun componentsWithString_encodingInvalidCharacters(URLString: MemorySegment, encodingInvalidCharacters: BOOL): MemorySegment {
            val sel = ObjCRuntime.sel("componentsWithString:encodingInvalidCharacters:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, URLString, encodingInvalidCharacters) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun componentsWithString_encodingInvalidCharacters(URLString: String, encodingInvalidCharacters: BOOL): MemorySegment = componentsWithString_encodingInvalidCharacters(ObjCRuntime.newNSString(Arena.global(), URLString), encodingInvalidCharacters)
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun initWithURL_resolvingAgainstBaseURL(url: MemorySegment, resolve: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithURL:resolvingAgainstBaseURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, url, resolve) as MemorySegment
    }
    
    fun initWithString(URLString: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URLString) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString(URLString: String): MemorySegment = initWithString(ObjCRuntime.newNSString(Arena.global(), URLString))
    
    fun initWithString_encodingInvalidCharacters(URLString: MemorySegment, encodingInvalidCharacters: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("initWithString:encodingInvalidCharacters:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, URLString, encodingInvalidCharacters) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithString_encodingInvalidCharacters(URLString: String, encodingInvalidCharacters: BOOL): MemorySegment = initWithString_encodingInvalidCharacters(ObjCRuntime.newNSString(Arena.global(), URLString), encodingInvalidCharacters)
    
    fun URLRelativeToURL(baseURL: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("URLRelativeToURL:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, baseURL) as MemorySegment
    }
    
    // @property URL
    fun URL(): MemorySegment {
        val sel = ObjCRuntime.sel("URL")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property string
    fun string(): MemorySegment {
        val sel = ObjCRuntime.sel("string")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun stringAsString(): String = ObjCRuntime.toJavaString(string())
    
    // @property scheme
    fun scheme(): MemorySegment {
        val sel = ObjCRuntime.sel("scheme")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setScheme(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setScheme:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun schemeAsString(): String = ObjCRuntime.toJavaString(scheme())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setScheme(value: String) = setScheme(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property user
    fun user(): MemorySegment {
        val sel = ObjCRuntime.sel("user")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setUser(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setUser:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun userAsString(): String = ObjCRuntime.toJavaString(user())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setUser(value: String) = setUser(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property password
    fun password(): MemorySegment {
        val sel = ObjCRuntime.sel("password")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPassword(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPassword:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun passwordAsString(): String = ObjCRuntime.toJavaString(password())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPassword(value: String) = setPassword(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property host
    fun host(): MemorySegment {
        val sel = ObjCRuntime.sel("host")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setHost(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setHost:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun hostAsString(): String = ObjCRuntime.toJavaString(host())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setHost(value: String) = setHost(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property port
    fun port(): MemorySegment {
        val sel = ObjCRuntime.sel("port")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPort(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPort:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property path
    fun path(): MemorySegment {
        val sel = ObjCRuntime.sel("path")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun pathAsString(): String = ObjCRuntime.toJavaString(path())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPath(value: String) = setPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property query
    fun query(): MemorySegment {
        val sel = ObjCRuntime.sel("query")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setQuery(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setQuery:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun queryAsString(): String = ObjCRuntime.toJavaString(query())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setQuery(value: String) = setQuery(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property fragment
    fun fragment(): MemorySegment {
        val sel = ObjCRuntime.sel("fragment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setFragment(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setFragment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun fragmentAsString(): String = ObjCRuntime.toJavaString(fragment())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setFragment(value: String) = setFragment(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property percentEncodedUser
    fun percentEncodedUser(): MemorySegment {
        val sel = ObjCRuntime.sel("percentEncodedUser")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentEncodedUser(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentEncodedUser:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun percentEncodedUserAsString(): String = ObjCRuntime.toJavaString(percentEncodedUser())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPercentEncodedUser(value: String) = setPercentEncodedUser(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property percentEncodedPassword
    fun percentEncodedPassword(): MemorySegment {
        val sel = ObjCRuntime.sel("percentEncodedPassword")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentEncodedPassword(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentEncodedPassword:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun percentEncodedPasswordAsString(): String = ObjCRuntime.toJavaString(percentEncodedPassword())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPercentEncodedPassword(value: String) = setPercentEncodedPassword(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property percentEncodedHost
    fun percentEncodedHost(): MemorySegment {
        val sel = ObjCRuntime.sel("percentEncodedHost")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentEncodedHost(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentEncodedHost:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun percentEncodedHostAsString(): String = ObjCRuntime.toJavaString(percentEncodedHost())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPercentEncodedHost(value: String) = setPercentEncodedHost(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property percentEncodedPath
    fun percentEncodedPath(): MemorySegment {
        val sel = ObjCRuntime.sel("percentEncodedPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentEncodedPath(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentEncodedPath:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun percentEncodedPathAsString(): String = ObjCRuntime.toJavaString(percentEncodedPath())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPercentEncodedPath(value: String) = setPercentEncodedPath(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property percentEncodedQuery
    fun percentEncodedQuery(): MemorySegment {
        val sel = ObjCRuntime.sel("percentEncodedQuery")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentEncodedQuery(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentEncodedQuery:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun percentEncodedQueryAsString(): String = ObjCRuntime.toJavaString(percentEncodedQuery())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPercentEncodedQuery(value: String) = setPercentEncodedQuery(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property percentEncodedFragment
    fun percentEncodedFragment(): MemorySegment {
        val sel = ObjCRuntime.sel("percentEncodedFragment")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentEncodedFragment(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentEncodedFragment:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun percentEncodedFragmentAsString(): String = ObjCRuntime.toJavaString(percentEncodedFragment())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setPercentEncodedFragment(value: String) = setPercentEncodedFragment(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property encodedHost
    fun encodedHost(): MemorySegment {
        val sel = ObjCRuntime.sel("encodedHost")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setEncodedHost(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setEncodedHost:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun encodedHostAsString(): String = ObjCRuntime.toJavaString(encodedHost())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setEncodedHost(value: String) = setEncodedHost(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property rangeOfScheme
    fun rangeOfScheme(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfScheme")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property rangeOfUser
    fun rangeOfUser(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfUser")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property rangeOfPassword
    fun rangeOfPassword(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfPassword")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property rangeOfHost
    fun rangeOfHost(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfHost")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property rangeOfPort
    fun rangeOfPort(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfPort")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property rangeOfPath
    fun rangeOfPath(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfPath")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property rangeOfQuery
    fun rangeOfQuery(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfQuery")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property rangeOfFragment
    fun rangeOfFragment(): NSRange {
        val sel = ObjCRuntime.sel("rangeOfFragment")
        return ObjCRuntime.msgSendStret(MemoryLayout.structLayout(ValueLayout.JAVA_LONG.withName("location"), ValueLayout.JAVA_LONG.withName("length")).withName("_NSRange"), ptr, sel) as NSRange
    }
    
    // @property queryItems
    /** @return NSArray<NSURLQueryItem *> * */
    fun queryItems(): MemorySegment {
        val sel = ObjCRuntime.sel("queryItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setQueryItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setQueryItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property percentEncodedQueryItems
    /** @return NSArray<NSURLQueryItem *> * */
    fun percentEncodedQueryItems(): MemorySegment {
        val sel = ObjCRuntime.sel("percentEncodedQueryItems")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setPercentEncodedQueryItems(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setPercentEncodedQueryItems:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
}

