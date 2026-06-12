package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSURLCredential
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSURLCredential(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSURLCredential") }
        
    }
    
    // @property persistence
    open fun persistence(): NSURLCredentialPersistence {
        val sel = ObjCRuntime.sel("persistence")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSURLCredentialPersistence
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _internal: MemorySegment
}

// ── Category: NSInternetPassword on NSURLCredential ─────────────────────────────────────────

fun NSURLCredential.initWithUser_password_persistence(user: MemorySegment, password: MemorySegment, persistence: NSURLCredentialPersistence): MemorySegment {
    val sel = ObjCRuntime.sel("initWithUser:password:persistence:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, user, password, persistence) as MemorySegment
}

fun NSURLCredential.user(): MemorySegment {
    val sel = ObjCRuntime.sel("user")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURLCredential.password(): MemorySegment {
    val sel = ObjCRuntime.sel("password")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURLCredential.hasPassword(): BOOL {
    val sel = ObjCRuntime.sel("hasPassword")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// Class<*> method: +[NSURLCredential credentialWithUser:password:persistence:]
fun NSURLCredential_credentialWithUser_password_persistence(user: MemorySegment, password: MemorySegment, persistence: NSURLCredentialPersistence): MemorySegment {
    val sel = ObjCRuntime.sel("credentialWithUser:password:persistence:")
    val cls = ObjCRuntime.getClass("NSURLCredential")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, user, password, persistence) as MemorySegment
}

// @property user
    val sel = ObjCRuntime.sel("user")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property password
    val sel = ObjCRuntime.sel("password")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property hasPassword
    val sel = ObjCRuntime.sel("hasPassword")
    return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel) as BOOL
}

// ── Category: NSClientCertificate on NSURLCredential ─────────────────────────────────────────

fun NSURLCredential.initWithIdentity_certificates_persistence(identity: MemorySegment, certArray: MemorySegment, persistence: NSURLCredentialPersistence): MemorySegment {
    val sel = ObjCRuntime.sel("initWithIdentity:certificates:persistence:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, identity, certArray, persistence) as MemorySegment
}

fun NSURLCredential.identity(): MemorySegment {
    val sel = ObjCRuntime.sel("identity")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

fun NSURLCredential.certificates(): MemorySegment {
    val sel = ObjCRuntime.sel("certificates")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// Class<*> method: +[NSURLCredential credentialWithIdentity:certificates:persistence:]
fun NSURLCredential_credentialWithIdentity_certificates_persistence(identity: MemorySegment, certArray: MemorySegment, persistence: NSURLCredentialPersistence): MemorySegment {
    val sel = ObjCRuntime.sel("credentialWithIdentity:certificates:persistence:")
    val cls = ObjCRuntime.getClass("NSURLCredential")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, identity, certArray, persistence) as MemorySegment
}

// @property identity
    val sel = ObjCRuntime.sel("identity")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// @property certificates
    val sel = ObjCRuntime.sel("certificates")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
}

// ── Category: NSServerTrust on NSURLCredential ─────────────────────────────────────────

fun NSURLCredential.initWithTrust(trust: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("initWithTrust:")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, trust) as MemorySegment
}

// Class<*> method: +[NSURLCredential credentialForTrust:]
fun NSURLCredential_credentialForTrust(trust: MemorySegment): MemorySegment {
    val sel = ObjCRuntime.sel("credentialForTrust:")
    val cls = ObjCRuntime.getClass("NSURLCredential")
    return ObjCRuntime.msgSend(ValueLayout.ADDRESS, cls, sel, trust) as MemorySegment
}

