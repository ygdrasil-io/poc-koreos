package org.graphiks.kffi.objc

import java.lang.invoke.*
import java.lang.foreign.*
import java.lang.foreign.MemoryLayout.PathElement.*

/**
 * Kotlin/JVM wrapper for Objective-C class: NSXMLNode
 * Superclass: NSObject
 * Protocols: NSCopying
 */
open class NSXMLNode(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSXMLNode") }
        
        open fun document(): MemorySegment {
            val sel = ObjCRuntime.sel("document")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        open fun documentWithRootElement(element: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("documentWithRootElement:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, element) as MemorySegment
        }
        
        open fun elementWithName(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("elementWithName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun elementWithName(name: String): MemorySegment = elementWithName(ObjCRuntime.newNSString(Arena.global(), name))
        
        open fun elementWithName_URI(name: MemorySegment, URI: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("elementWithName:URI:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, URI) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun elementWithName_URI(name: String, URI: String): MemorySegment = elementWithName_URI(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), URI))
        
        open fun elementWithName_stringValue(name: MemorySegment, string: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("elementWithName:stringValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, string) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun elementWithName_stringValue(name: String, string: String): MemorySegment = elementWithName_stringValue(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), string))
        
        open fun elementWithName_children_attributes(name: MemorySegment, children: MemorySegment, attributes: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("elementWithName:children:attributes:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, children, attributes) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun elementWithName_children_attributes(name: String, children: MemorySegment, attributes: MemorySegment): MemorySegment = elementWithName_children_attributes(ObjCRuntime.newNSString(Arena.global(), name), children, attributes)
        
        open fun attributeWithName_stringValue(name: MemorySegment, stringValue: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("attributeWithName:stringValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, stringValue) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun attributeWithName_stringValue(name: String, stringValue: String): MemorySegment = attributeWithName_stringValue(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), stringValue))
        
        open fun attributeWithName_URI_stringValue(name: MemorySegment, URI: MemorySegment, stringValue: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("attributeWithName:URI:stringValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, URI, stringValue) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun attributeWithName_URI_stringValue(name: String, URI: String, stringValue: String): MemorySegment = attributeWithName_URI_stringValue(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), URI), ObjCRuntime.newNSString(Arena.global(), stringValue))
        
        open fun namespaceWithName_stringValue(name: MemorySegment, stringValue: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("namespaceWithName:stringValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, stringValue) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun namespaceWithName_stringValue(name: String, stringValue: String): MemorySegment = namespaceWithName_stringValue(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), stringValue))
        
        open fun processingInstructionWithName_stringValue(name: MemorySegment, stringValue: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("processingInstructionWithName:stringValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, stringValue) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun processingInstructionWithName_stringValue(name: String, stringValue: String): MemorySegment = processingInstructionWithName_stringValue(ObjCRuntime.newNSString(Arena.global(), name), ObjCRuntime.newNSString(Arena.global(), stringValue))
        
        open fun commentWithStringValue(stringValue: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("commentWithStringValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, stringValue) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun commentWithStringValue(stringValue: String): MemorySegment = commentWithStringValue(ObjCRuntime.newNSString(Arena.global(), stringValue))
        
        open fun textWithStringValue(stringValue: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("textWithStringValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, stringValue) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun textWithStringValue(stringValue: String): MemorySegment = textWithStringValue(ObjCRuntime.newNSString(Arena.global(), stringValue))
        
        open fun DTDNodeWithXMLString(string: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("DTDNodeWithXMLString:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun DTDNodeWithXMLString(string: String): MemorySegment = DTDNodeWithXMLString(ObjCRuntime.newNSString(Arena.global(), string))
        
        open fun localNameForName(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("localNameForName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        open fun localNameForNameAsString(name: MemorySegment): String = ObjCRuntime.toJavaString(localNameForName(name))
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun localNameForName(name: String): MemorySegment = localNameForName(ObjCRuntime.newNSString(Arena.global(), name))
        
        /** Convenience overload — [String] parameters and [String] return type. */
        open fun localNameForNameAsString(name: String): String = ObjCRuntime.toJavaString(localNameForName(ObjCRuntime.newNSString(Arena.global(), name)))
        
        open fun prefixForName(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("prefixForName:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
        open fun prefixForNameAsString(name: MemorySegment): String = ObjCRuntime.toJavaString(prefixForName(name))
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun prefixForName(name: String): MemorySegment = prefixForName(ObjCRuntime.newNSString(Arena.global(), name))
        
        /** Convenience overload — [String] parameters and [String] return type. */
        open fun prefixForNameAsString(name: String): String = ObjCRuntime.toJavaString(prefixForName(ObjCRuntime.newNSString(Arena.global(), name)))
        
        open fun predefinedNamespaceForPrefix(name: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("predefinedNamespaceForPrefix:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        open fun predefinedNamespaceForPrefix(name: String): MemorySegment = predefinedNamespaceForPrefix(ObjCRuntime.newNSString(Arena.global(), name))
        
    }
    
    open fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    open fun initWithKind(kind: NSXMLNodeKind): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKind:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, kind) as MemorySegment
    }
    
    open fun initWithKind_options(kind: NSXMLNodeKind, options: NSXMLNodeOptions): MemorySegment {
        val sel = ObjCRuntime.sel("initWithKind:options:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, kind, options) as MemorySegment
    }
    
    open fun setStringValue_resolvingEntities(string: MemorySegment, resolve: BOOL): Unit {
        val sel = ObjCRuntime.sel("setStringValue:resolvingEntities:")
        ObjCRuntime.msgSend(null, ptr, sel, string, resolve)
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun setStringValue_resolvingEntities(string: String, resolve: BOOL): Unit = setStringValue_resolvingEntities(ObjCRuntime.newNSString(Arena.global(), string), resolve)
    
    open fun childAtIndex(index: NSUInteger): MemorySegment {
        val sel = ObjCRuntime.sel("childAtIndex:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, index) as MemorySegment
    }
    
    open fun detach(): Unit {
        val sel = ObjCRuntime.sel("detach")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    open fun XMLStringWithOptions(options: NSXMLNodeOptions): MemorySegment {
        val sel = ObjCRuntime.sel("XMLStringWithOptions:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, options) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun XMLStringWithOptionsAsString(options: NSXMLNodeOptions): String = ObjCRuntime.toJavaString(XMLStringWithOptions(options))
    
    open fun canonicalXMLStringPreservingComments(comments: BOOL): MemorySegment {
        val sel = ObjCRuntime.sel("canonicalXMLStringPreservingComments:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, comments) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun canonicalXMLStringPreservingCommentsAsString(comments: BOOL): String = ObjCRuntime.toJavaString(canonicalXMLStringPreservingComments(comments))
    
    /** @return NSArray<__kindof NSXMLNode *> * */
    open fun nodesForXPath_error(xpath: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("nodesForXPath:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, xpath, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun nodesForXPath_error(xpath: String, error: MemorySegment): MemorySegment = nodesForXPath_error(ObjCRuntime.newNSString(Arena.global(), xpath), error)
    
    open fun objectsForXQuery_constants_error(xquery: MemorySegment, constants: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectsForXQuery:constants:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, xquery, constants, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun objectsForXQuery_constants_error(xquery: String, constants: MemorySegment, error: MemorySegment): MemorySegment = objectsForXQuery_constants_error(ObjCRuntime.newNSString(Arena.global(), xquery), constants, error)
    
    open fun objectsForXQuery_error(xquery: MemorySegment, error: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("objectsForXQuery:error:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, xquery, error) as MemorySegment
    }
    
    /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
    open fun objectsForXQuery_error(xquery: String, error: MemorySegment): MemorySegment = objectsForXQuery_error(ObjCRuntime.newNSString(Arena.global(), xquery), error)
    
    // @property kind
    open fun kind(): NSXMLNodeKind {
        val sel = ObjCRuntime.sel("kind")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSXMLNodeKind
    }
    
    // @property name
    open fun name(): MemorySegment {
        val sel = ObjCRuntime.sel("name")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setName(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setName:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun nameAsString(): String = ObjCRuntime.toJavaString(name())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setName(value: String) = setName(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property objectValue
    open fun objectValue(): MemorySegment {
        val sel = ObjCRuntime.sel("objectValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setObjectValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setObjectValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    // @property stringValue
    open fun stringValue(): MemorySegment {
        val sel = ObjCRuntime.sel("stringValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setStringValue(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setStringValue:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun stringValueAsString(): String = ObjCRuntime.toJavaString(stringValue())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setStringValue(value: String) = setStringValue(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property index
    open fun index(): NSUInteger {
        val sel = ObjCRuntime.sel("index")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property level
    open fun level(): NSUInteger {
        val sel = ObjCRuntime.sel("level")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property rootDocument
    open fun rootDocument(): MemorySegment {
        val sel = ObjCRuntime.sel("rootDocument")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property parent
    open fun parent(): MemorySegment {
        val sel = ObjCRuntime.sel("parent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property childCount
    open fun childCount(): NSUInteger {
        val sel = ObjCRuntime.sel("childCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSUInteger
    }
    
    // @property children
    /** @return NSArray<NSXMLNode *> * */
    open fun children(): MemorySegment {
        val sel = ObjCRuntime.sel("children")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property previousSibling
    open fun previousSibling(): MemorySegment {
        val sel = ObjCRuntime.sel("previousSibling")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property nextSibling
    open fun nextSibling(): MemorySegment {
        val sel = ObjCRuntime.sel("nextSibling")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property previousNode
    open fun previousNode(): MemorySegment {
        val sel = ObjCRuntime.sel("previousNode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property nextNode
    open fun nextNode(): MemorySegment {
        val sel = ObjCRuntime.sel("nextNode")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property XPath
    open fun XPath(): MemorySegment {
        val sel = ObjCRuntime.sel("XPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun XPathAsString(): String = ObjCRuntime.toJavaString(XPath())
    
    // @property localName
    open fun localName(): MemorySegment {
        val sel = ObjCRuntime.sel("localName")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun localNameAsString(): String = ObjCRuntime.toJavaString(localName())
    
    // @property prefix
    open fun prefix(): MemorySegment {
        val sel = ObjCRuntime.sel("prefix")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun prefixAsString(): String = ObjCRuntime.toJavaString(prefix())
    
    // @property URI
    open fun URI(): MemorySegment {
        val sel = ObjCRuntime.sel("URI")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    open fun setURI(value: MemorySegment) {
        val sel = ObjCRuntime.sel("setURI:")
        ObjCRuntime.msgSend(null, ptr, sel, value)
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun URIAsString(): String = ObjCRuntime.toJavaString(URI())
    
    /** Convenience overload — accepts Kotlin [String] for the NSString property. */
    open fun setURI(value: String) = setURI(ObjCRuntime.newNSString(Arena.global(), value))
    
    // @property description
    open fun description(): MemorySegment {
        val sel = ObjCRuntime.sel("description")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun descriptionAsString(): String = ObjCRuntime.toJavaString(description())
    
    // @property XMLString
    open fun XMLString(): MemorySegment {
        val sel = ObjCRuntime.sel("XMLString")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    open fun XMLStringAsString(): String = ObjCRuntime.toJavaString(XMLString())
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _parent: MemorySegment
    // ivar: _objectValue: MemorySegment
    // ivar: _kind: NSXMLNodeKind
    // ivar: _index: uint32_t
    // ivar: _private: int32_t
}

