/**
 * Kotlin/JVM wrapper for Objective-C class: NSSharingService
 * Superclass: NSObject
 */
open class NSSharingService(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSSharingService") }
        
        /** @return NSArray<NSSharingService *> * */
        fun sharingServicesForItems(items: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("sharingServicesForItems:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, items) as MemorySegment
        }
        
        fun sharingServiceNamed(serviceName: NSSharingServiceName): MemorySegment {
            val sel = ObjCRuntime.sel("sharingServiceNamed:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, serviceName) as MemorySegment
        }
        
    }
    
    fun initWithTitle_image_alternateImage_handler(title: MemorySegment, image: MemorySegment, alternateImage: MemorySegment, block: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithTitle:image:alternateImage:handler:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, title, image, alternateImage, block) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun initWithTitle_image_alternateImage_handler(title: String, image: MemorySegment, alternateImage: MemorySegment, block: MemorySegment): MemorySegment = initWithTitle_image_alternateImage_handler(ObjCRuntime.newNSString(Arena.global(), title), image, alternateImage, block)
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun canPerformWithItems(items: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("canPerformWithItems:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, items) as BOOL
    }
    
    fun performWithItems(items: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("performWithItems:")
        ObjCRuntime.msgSend(null, ptr, sel, items)
    }
    
    // @property delegate
    /** @return id<NSSharingServiceDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property title
    fun title(): MemorySegment {
        val sel = ObjCRuntime.sel("title")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun titleAsString(): String = ObjCRuntime.toJavaString(title())
    
    // @property image
    fun image(): MemorySegment {
        val sel = ObjCRuntime.sel("image")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property alternateImage
    fun alternateImage(): MemorySegment {
        val sel = ObjCRuntime.sel("alternateImage")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property menuItemTitle
    fun menuItemTitle(): MemorySegment {
        val sel = ObjCRuntime.sel("menuItemTitle")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setMenuItemTitle(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setMenuItemTitle:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun menuItemTitleAsString(): String = ObjCRuntime.toJavaString(menuItemTitle())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setMenuItemTitle(value: String) = setMenuItemTitle(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property recipients
    /** @return NSArray<NSString *> * */
    fun recipients(): MemorySegment {
        val sel = ObjCRuntime.sel("recipients")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRecipients(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRecipients:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property subject
    fun subject(): MemorySegment {
        val sel = ObjCRuntime.sel("subject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setSubject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setSubject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun subjectAsString(): String = ObjCRuntime.toJavaString(subject())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    fun setSubject(value: String) = setSubject(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property messageBody
    fun messageBody(): MemorySegment {
        val sel = ObjCRuntime.sel("messageBody")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun messageBodyAsString(): String = ObjCRuntime.toJavaString(messageBody())
    
    // @property permanentLink
    fun permanentLink(): MemorySegment {
        val sel = ObjCRuntime.sel("permanentLink")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property accountName
    fun accountName(): MemorySegment {
        val sel = ObjCRuntime.sel("accountName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun accountNameAsString(): String = ObjCRuntime.toJavaString(accountName())
    
    // @property attachmentFileURLs
    /** @return NSArray<NSURL *> * */
    fun attachmentFileURLs(): MemorySegment {
        val sel = ObjCRuntime.sel("attachmentFileURLs")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
}

