/**
 * Kotlin/JVM wrapper for Objective-C class: NSExpression
 * Superclass: NSObject
 * Protocols: NSSecureCoding, NSCopying
 */
open class NSExpression(val ptr: MemorySegment) {
    companion object {
        private val _class: MemorySegment by lazy { ObjCRuntime.getClass("NSExpression") }
        
        fun expressionWithFormat_argumentArray(expressionFormat: MemorySegment, arguments: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionWithFormat:argumentArray:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, expressionFormat, arguments) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionWithFormat_argumentArray(expressionFormat: String, arguments: MemorySegment): MemorySegment = expressionWithFormat_argumentArray(ObjCRuntime.newNSString(Arena.global(), expressionFormat), arguments)
        
        fun expressionWithFormat(expressionFormat: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionWithFormat:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, expressionFormat) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionWithFormat(expressionFormat: String): MemorySegment = expressionWithFormat(ObjCRuntime.newNSString(Arena.global(), expressionFormat))
        
        fun expressionWithFormat_arguments(expressionFormat: MemorySegment, argList: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionWithFormat:arguments:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, expressionFormat, argList) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionWithFormat_arguments(expressionFormat: String, argList: MemorySegment): MemorySegment = expressionWithFormat_arguments(ObjCRuntime.newNSString(Arena.global(), expressionFormat), argList)
        
        fun expressionForConstantValue(obj: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForConstantValue:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, obj) as MemorySegment
        }
        
        fun expressionForEvaluatedObject(): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForEvaluatedObject")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun expressionForVariable(string: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForVariable:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, string) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionForVariable(string: String): MemorySegment = expressionForVariable(ObjCRuntime.newNSString(Arena.global(), string))
        
        fun expressionForKeyPath(keyPath: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForKeyPath:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, keyPath) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionForKeyPath(keyPath: String): MemorySegment = expressionForKeyPath(ObjCRuntime.newNSString(Arena.global(), keyPath))
        
        fun expressionForFunction_arguments(name: MemorySegment, parameters: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForFunction:arguments:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, name, parameters) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionForFunction_arguments(name: String, parameters: MemorySegment): MemorySegment = expressionForFunction_arguments(ObjCRuntime.newNSString(Arena.global(), name), parameters)
        
        fun expressionForAggregate(subexpressions: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForAggregate:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, subexpressions) as MemorySegment
        }
        
        fun expressionForUnionSet_with(left: MemorySegment, right: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForUnionSet:with:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, left, right) as MemorySegment
        }
        
        fun expressionForIntersectSet_with(left: MemorySegment, right: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForIntersectSet:with:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, left, right) as MemorySegment
        }
        
        fun expressionForMinusSet_with(left: MemorySegment, right: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForMinusSet:with:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, left, right) as MemorySegment
        }
        
        fun expressionForSubquery_usingIteratorVariable_predicate(expression: MemorySegment, variable: MemorySegment, predicate: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForSubquery:usingIteratorVariable:predicate:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, expression, variable, predicate) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionForSubquery_usingIteratorVariable_predicate(expression: MemorySegment, variable: String, predicate: MemorySegment): MemorySegment = expressionForSubquery_usingIteratorVariable_predicate(expression, ObjCRuntime.newNSString(Arena.global(), variable), predicate)
        
        fun expressionForFunction_selectorName_arguments(target: MemorySegment, name: MemorySegment, parameters: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForFunction:selectorName:arguments:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, target, name, parameters) as MemorySegment
        }
        
        /** Convenience overload — accepts Kotlin [String] for NSString parameters. */
        fun expressionForFunction_selectorName_arguments(target: MemorySegment, name: String, parameters: MemorySegment): MemorySegment = expressionForFunction_selectorName_arguments(target, ObjCRuntime.newNSString(Arena.global(), name), parameters)
        
        fun expressionForAnyKey(): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForAnyKey")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel) as MemorySegment
        }
        
        fun expressionForBlock_arguments(block: MemorySegment, arguments: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForBlock:arguments:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, block, arguments) as MemorySegment
        }
        
        fun expressionForConditional_trueExpression_falseExpression(predicate: MemorySegment, trueExpression: MemorySegment, falseExpression: MemorySegment): MemorySegment {
            val sel = ObjCRuntime.sel("expressionForConditional:trueExpression:falseExpression:")
            return ObjCRuntime.msgSend(ValueLayout.ADDRESS, _class, sel, predicate, trueExpression, falseExpression) as MemorySegment
        }
        
    }
    
    fun initWithExpressionType(type: NSExpressionType): MemorySegment {
        val sel = ObjCRuntime.sel("initWithExpressionType:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, type) as MemorySegment
    }
    
    fun initWithCoder(coder: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("initWithCoder:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, coder) as MemorySegment
    }
    
    fun expressionValueWithObject_context(`object`: MemorySegment, context: MemorySegment): MemorySegment {
        val sel = ObjCRuntime.sel("expressionValueWithObject:context:")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel, `object`, context) as MemorySegment
    }
    
    fun allowEvaluation(): Unit {
        val sel = ObjCRuntime.sel("allowEvaluation")
        ObjCRuntime.msgSend(null, ptr, sel)
    }
    
    // @property expressionType
    fun expressionType(): NSExpressionType {
        val sel = ObjCRuntime.sel("expressionType")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as NSExpressionType
    }
    
    // @property constantValue
    fun constantValue(): MemorySegment {
        val sel = ObjCRuntime.sel("constantValue")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property keyPath
    fun keyPath(): MemorySegment {
        val sel = ObjCRuntime.sel("keyPath")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun keyPathAsString(): String = ObjCRuntime.toJavaString(keyPath())
    
    // @property function
    fun function(): MemorySegment {
        val sel = ObjCRuntime.sel("function")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun functionAsString(): String = ObjCRuntime.toJavaString(function())
    
    // @property variable
    fun variable(): MemorySegment {
        val sel = ObjCRuntime.sel("variable")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    /** Convenience overload — returns Kotlin [String] by converting the NSString via UTF8String. */
    fun variableAsString(): String = ObjCRuntime.toJavaString(variable())
    
    // @property operand
    fun operand(): MemorySegment {
        val sel = ObjCRuntime.sel("operand")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property arguments
    /** @return NSArray<NSExpression *> * */
    fun arguments(): MemorySegment {
        val sel = ObjCRuntime.sel("arguments")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property collection
    fun collection(): MemorySegment {
        val sel = ObjCRuntime.sel("collection")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property predicate
    fun predicate(): MemorySegment {
        val sel = ObjCRuntime.sel("predicate")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property leftExpression
    fun leftExpression(): MemorySegment {
        val sel = ObjCRuntime.sel("leftExpression")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property rightExpression
    fun rightExpression(): MemorySegment {
        val sel = ObjCRuntime.sel("rightExpression")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property trueExpression
    fun trueExpression(): MemorySegment {
        val sel = ObjCRuntime.sel("trueExpression")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property falseExpression
    fun falseExpression(): MemorySegment {
        val sel = ObjCRuntime.sel("falseExpression")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    // @property expressionBlock
    /** @return id  _Nonnull (^)(id  _Nullable __strong, NSArray<NSExpression *> * _Nonnull __strong, NSMutableDictionary * _Nullable __strong) */
    fun expressionBlock(): MemorySegment {
        val sel = ObjCRuntime.sel("expressionBlock")
        return ObjCRuntime.msgSend(ValueLayout.ADDRESS, ptr, sel) as MemorySegment
    }
    
    
    // ── Instance variables (direct field access not supported via Panama) ──
    // ivar: _expressionFlags: MemorySegment
    // ivar: reserved: uint32_t
    // ivar: _expressionType: NSExpressionType
}

