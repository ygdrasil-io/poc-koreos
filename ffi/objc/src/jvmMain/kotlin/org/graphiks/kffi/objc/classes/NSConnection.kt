/**
 * Kotlin/JVM wrapper for Objective-C class: NSConnection
 * Superclass: NSObject
 */
open class NSConnection(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSConnection") }
        
        /** @return NSArray<NSConnection *> * */
        fun allConnections(): MemorySegment {
            val sel = ObjCRuntime.sel("allConnections")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun defaultConnection(): MemorySegment {
            val sel = ObjCRuntime.sel("defaultConnection")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun connectionWithRegisteredName_host(name: MemorySegment, hostName: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("connectionWithRegisteredName:host:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, hostName) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun connectionWithRegisteredName_host(name: String, hostName: String): MemorySegment = connectionWithRegisteredName_host(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), hostName))
        
        fun connectionWithRegisteredName_host_usingNameServer(name: MemorySegment, hostName: MemorySegment, server: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("connectionWithRegisteredName:host:usingNameServer:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, hostName, server) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun connectionWithRegisteredName_host_usingNameServer(name: String, hostName: String, server: MemorySegment): MemorySegment = connectionWithRegisteredName_host_usingNameServer(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), hostName), server)
        
        fun rootProxyForConnectionWithRegisteredName_host(name: MemorySegment, hostName: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("rootProxyForConnectionWithRegisteredName:host:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, hostName) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun rootProxyForConnectionWithRegisteredName_host(name: String, hostName: String): MemorySegment = rootProxyForConnectionWithRegisteredName_host(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), hostName))
        
        fun rootProxyForConnectionWithRegisteredName_host_usingNameServer(name: MemorySegment, hostName: MemorySegment, server: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("rootProxyForConnectionWithRegisteredName:host:usingNameServer:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, hostName, server) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun rootProxyForConnectionWithRegisteredName_host_usingNameServer(name: String, hostName: String, server: MemorySegment): MemorySegment = rootProxyForConnectionWithRegisteredName_host_usingNameServer(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), hostName), server)
        
        fun serviceConnectionWithName_rootObject_usingNameServer(name: MemorySegment, root: MemorySegment, server: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("serviceConnectionWithName:rootObject:usingNameServer:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, root, server) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun serviceConnectionWithName_rootObject_usingNameServer(name: String, root: MemorySegment, server: MemorySegment): MemorySegment = serviceConnectionWithName_rootObject_usingNameServer(ObjCRuntime.newNSString(Arena.global(), name), root, server)
        
        fun serviceConnectionWithName_rootObject(name: MemorySegment, root: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("serviceConnectionWithName:rootObject:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, root) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun serviceConnectionWithName_rootObject(name: String, root: MemorySegment): MemorySegment = serviceConnectionWithName_rootObject(ObjCRuntime.newNSString(Arena.global(), name), root)
        
        fun connectionWithReceivePort_sendPort(receivePort: MemorySegment, sendPort: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("connectionWithReceivePort:sendPort:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, receivePort, sendPort) as MemorySegment
        }
        
        fun currentConversation(): MemorySegment {
            val sel = ObjCRuntime.sel("currentConversation")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
    }
    
    fun invalidate(): Unit {
        val sel = ObjCRuntime.sel("invalidate")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addRequestMode(rmode: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addRequestMode:")
        ObjCRuntime.msgSend(null, ptr, sel, rmode)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun addRequestMode(rmode: String): Unit = addRequestMode(ObjCRuntime.newNSString(Arena.global(), rmode))
    
    fun removeRequestMode(rmode: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeRequestMode:")
        ObjCRuntime.msgSend(null, ptr, sel, rmode)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun removeRequestMode(rmode: String): Unit = removeRequestMode(ObjCRuntime.newNSString(Arena.global(), rmode))
    
    fun registerName(name: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("registerName:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, name) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun registerName(name: String): BOOL = registerName(ObjCRuntime.newNSString(Arena.global(), name))
    
    fun registerName_withNameServer(name: MemorySegment, server: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("registerName:withNameServer:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, name, server) as BOOL
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    fun registerName_withNameServer(name: String, server: MemorySegment): BOOL = registerName_withNameServer(ObjCRuntime.newNSString(Arena.global(), name), server)
    
    fun initWithReceivePort_sendPort(receivePort: MemorySegment, sendPort: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithReceivePort:sendPort:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, receivePort, sendPort) as MemorySegment
    }
    
    fun enableMultipleThreads(): Unit {
        val sel = ObjCRuntime.sel("enableMultipleThreads")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun addRunLoop(runloop: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("addRunLoop:")
        ObjCRuntime.msgSend(null, ptr, sel, runloop)
    }
    
    fun removeRunLoop(runloop: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("removeRunLoop:")
        ObjCRuntime.msgSend(null, ptr, sel, runloop)
    }
    
    fun runInNewThread(): Unit {
        val sel = ObjCRuntime.sel("runInNewThread")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    fun dispatchWithComponents(components: MemorySegment): Unit {
        val sel = ObjCRuntime.sel("dispatchWithComponents:")
        ObjCRuntime.msgSend(null, ptr, sel, components)
    }
    
    // @property statistics
    /** @return NSDictionary<NSString *,NSNumber *> * */
    fun statistics(): MemorySegment {
        val sel = ObjCRuntime.sel("statistics")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property requestTimeout
    fun requestTimeout(): NSTimeInterval {
        val sel = ObjCRuntime.sel("requestTimeout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setRequestTimeout(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setRequestTimeout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property replyTimeout
    fun replyTimeout(): NSTimeInterval {
        val sel = ObjCRuntime.sel("replyTimeout")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_DOUBLE, ptr, sel) as NSTimeInterval
    }
    fun setReplyTimeout(value: NSTimeInterval) {
        val sel = ObjCRuntime.sel("setReplyTimeout:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property rootObject
    fun rootObject(): MemorySegment {
        val sel = ObjCRuntime.sel("rootObject")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setRootObject(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setRootObject:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property delegate
    /** @return id<NSConnectionDelegate> */
    fun delegate(): MemorySegment {
        val sel = ObjCRuntime.sel("delegate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    fun setDelegate(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setDelegate:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property independentConversationQueueing
    fun independentConversationQueueing(): BOOL {
        val sel = ObjCRuntime.sel("independentConversationQueueing")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    fun setIndependentConversationQueueing(value: BOOL) {
        val sel = ObjCRuntime.sel("setIndependentConversationQueueing:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property valid
    fun isValid(): BOOL {
        val sel = ObjCRuntime.sel("isValid")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property rootProxy
    fun rootProxy(): MemorySegment {
        val sel = ObjCRuntime.sel("rootProxy")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property requestModes
    /** @return NSArray<NSString *> * */
    fun requestModes(): MemorySegment {
        val sel = ObjCRuntime.sel("requestModes")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property sendPort
    fun sendPort(): MemorySegment {
        val sel = ObjCRuntime.sel("sendPort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property receivePort
    fun receivePort(): MemorySegment {
        val sel = ObjCRuntime.sel("receivePort")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property multipleThreadsEnabled
    fun multipleThreadsEnabled(): BOOL {
        val sel = ObjCRuntime.sel("multipleThreadsEnabled")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
    }
    
    // @property remoteObjects
    fun remoteObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("remoteObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property localObjects
    fun localObjects(): MemorySegment {
        val sel = ObjCRuntime.sel("localObjects")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: receivePort: MemorySegment
    // ivar: sendPort: MemorySegment
    // ivar: delegate: MemorySegment
    // ivar: busy: int32_t
    // ivar: localProxyCount: int32_t
    // ivar: waitCount: int32_t
    // ivar: delayedRL: MemorySegment
    // ivar: statistics: MemorySegment
    // ivar: isDead: Any
    // ivar: isValid: Any
    // ivar: wantsInvalid: Any
    // ivar: authGen: Any
    // ivar: authCheck: Any
    // ivar: _reserved1: Any
    // ivar: _reserved2: Any
    // ivar: doRequest: Any
    // ivar: isQueueing: Any
    // ivar: isMulti: Any
    // ivar: invalidateRP: Any
    // ivar: ___1: MemorySegment
    // ivar: ___2: MemorySegment
    // ivar: runLoops: MemorySegment
    // ivar: requestModes: MemorySegment
    // ivar: rootObject: MemorySegment
    // ivar: registerInfo: MemorySegment
    // ivar: replMode: MemorySegment
    // ivar: classInfoImported: MemorySegment
    // ivar: releasedProxies: MemorySegment
    // ivar: reserved: MemorySegment
}

