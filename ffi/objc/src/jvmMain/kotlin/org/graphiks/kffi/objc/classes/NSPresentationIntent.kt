/**
 * Kotlin/JVM wrapper for Objective-C class: NSPresentationIntent
 * Superclass: NSObject
 * Protocols: NSCopying, NSSecureCoding
 */
open class NSPresentationIntent(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSPresentationIntent") }
        
        fun paragraphIntentWithIdentity_nestedInsideIntent(identity: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("paragraphIntentWithIdentity:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, parent) as MemorySegment
        }
        
        fun headerIntentWithIdentity_level_nestedInsideIntent(identity: NSInteger, level: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("headerIntentWithIdentity:level:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, level, parent) as MemorySegment
        }
        
        fun codeBlockIntentWithIdentity_languageHint_nestedInsideIntent(identity: NSInteger, languageHint: MemorySegment, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("codeBlockIntentWithIdentity:languageHint:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, languageHint, parent) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun codeBlockIntentWithIdentity_languageHint_nestedInsideIntent(identity: NSInteger, languageHint: String, parent: MemorySegment): MemorySegment = codeBlockIntentWithIdentity_languageHint_nestedInsideIntent(identity, ObjCRuntime.newNSString(Arena.global(), languageHint), parent)
        
        fun thematicBreakIntentWithIdentity_nestedInsideIntent(identity: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("thematicBreakIntentWithIdentity:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, parent) as MemorySegment
        }
        
        fun orderedListIntentWithIdentity_nestedInsideIntent(identity: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("orderedListIntentWithIdentity:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, parent) as MemorySegment
        }
        
        fun unorderedListIntentWithIdentity_nestedInsideIntent(identity: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("unorderedListIntentWithIdentity:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, parent) as MemorySegment
        }
        
        fun listItemIntentWithIdentity_ordinal_nestedInsideIntent(identity: NSInteger, ordinal: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("listItemIntentWithIdentity:ordinal:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, ordinal, parent) as MemorySegment
        }
        
        fun blockQuoteIntentWithIdentity_nestedInsideIntent(identity: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("blockQuoteIntentWithIdentity:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, parent) as MemorySegment
        }
        
        fun tableIntentWithIdentity_columnCount_alignments_nestedInsideIntent(identity: NSInteger, columnCount: NSInteger, alignments: MemorySegment, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tableIntentWithIdentity:columnCount:alignments:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, columnCount, alignments, parent) as MemorySegment
        }
        
        fun tableHeaderRowIntentWithIdentity_nestedInsideIntent(identity: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tableHeaderRowIntentWithIdentity:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, parent) as MemorySegment
        }
        
        fun tableRowIntentWithIdentity_row_nestedInsideIntent(identity: NSInteger, row: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tableRowIntentWithIdentity:row:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, row, parent) as MemorySegment
        }
        
        fun tableCellIntentWithIdentity_column_nestedInsideIntent(identity: NSInteger, column: NSInteger, parent: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("tableCellIntentWithIdentity:column:nestedInsideIntent:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, identity, column, parent) as MemorySegment
        }
        
    }
    
    fun init(): MemorySegment {
        val sel = ObjCRuntime.sel("init")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    fun isEquivalentToPresentationIntent(other: MemorySegment): BOOL {
        val sel = ObjCRuntime.sel("isEquivalentToPresentationIntent:")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_BOOLEAN, ptr, sel, other) as BOOL
    }
    
    // @property intentKind
    fun intentKind(): NSPresentationIntentKind {
        val sel = ObjCRuntime.sel("intentKind")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSPresentationIntentKind
    }
    
    // @property parentIntent
    fun parentIntent(): MemorySegment {
        val sel = ObjCRuntime.sel("parentIntent")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property identity
    fun identity(): NSInteger {
        val sel = ObjCRuntime.sel("identity")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property ordinal
    fun ordinal(): NSInteger {
        val sel = ObjCRuntime.sel("ordinal")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property columnAlignments
    /** @return NSArray<NSNumber *> * */
    fun columnAlignments(): MemorySegment {
        val sel = ObjCRuntime.sel("columnAlignments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property columnCount
    fun columnCount(): NSInteger {
        val sel = ObjCRuntime.sel("columnCount")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property headerLevel
    fun headerLevel(): NSInteger {
        val sel = ObjCRuntime.sel("headerLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property languageHint
    fun languageHint(): MemorySegment {
        val sel = ObjCRuntime.sel("languageHint")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun languageHintAsString(): String = ObjCRuntime.toJavaString(languageHint())
    
    // @property column
    fun column(): NSInteger {
        val sel = ObjCRuntime.sel("column")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property row
    fun row(): NSInteger {
        val sel = ObjCRuntime.sel("row")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
    // @property indentationLevel
    fun indentationLevel(): NSInteger {
        val sel = ObjCRuntime.sel("indentationLevel")
        return ObjCRuntime.msgSend(ValueLayout.JAVA_LONG, ptr, sel) as NSInteger
    }
    
}

